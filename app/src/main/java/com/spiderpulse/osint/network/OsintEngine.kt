package com.spiderpulse.osint.network

import com.spiderpulse.osint.model.ApiModule
import com.spiderpulse.osint.model.AuthType
import com.spiderpulse.osint.model.HttpMethod
import com.spiderpulse.osint.model.ModuleResult
import com.spiderpulse.osint.model.TargetCategory
import com.spiderpulse.osint.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

object OsintEngine {

    private const val MAX_RESPONSE_CHARS = 512 * 1024 // 512 KB character limit to prevent OOM DoS
    private const val MAX_REDIRECTS = 3

    private val ipv4Regex = Regex(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
    )

    fun detectCategory(rawInput: String): TargetCategory {
        val trimmed = rawInput.trim()
        if (ipv4Regex.matches(trimmed) || (trimmed.contains(":") && trimmed.count { it == ':' } >= 2)) {
            return TargetCategory.IP
        }
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length in 7..15 && !trimmed.contains(".")) {
            return TargetCategory.PHONE
        }
        return if (trimmed.contains(".")) TargetCategory.IP else TargetCategory.PHONE
    }

    fun normalizeTarget(rawInput: String, category: TargetCategory): Pair<String, String> {
        val trimmed = rawInput.trim().take(256)
        return if (category == TargetCategory.PHONE) {
            var digits = trimmed.filter { it.isDigit() }.take(16)
            if (digits.length == 11 && digits.startsWith("8")) {
                digits = "7" + digits.substring(1)
            } else if (digits.length == 10 && digits.startsWith("9")) {
                digits = "7$digits"
            }
            val e164 = if (digits.isNotEmpty()) "+$digits" else trimmed
            e164 to digits
        } else {
            trimmed to trimmed
        }
    }

    suspend fun runLocalAnalysis(rawTarget: String, category: TargetCategory): ModuleResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val (normalized, clean) = normalizeTarget(rawTarget, category)
            val fields = mutableListOf<Pair<String, String>>()

            if (category == TargetCategory.IP) {
                fields.add("Целевой IP" to normalized)
                val isIpv6 = normalized.contains(":")
                fields.add("Версия протокола" to if (isIpv6) "IPv6 (128-бит)" else "IPv4 (32-бит)")

                runCatching {
                    val addr = InetAddress.getByName(normalized)
                    val scope = when {
                        addr.isLoopbackAddress -> "Loopback (Локальный интерфейс 127.0.0.0/8)"
                        addr.isSiteLocalAddress -> "Private RFC1918 (Локальная сеть LAN)"
                        addr.isLinkLocalAddress -> "Link-Local (Автоконфигурация 169.254.x.x)"
                        addr.isMulticastAddress -> "Multicast (Групповая рассылка)"
                        else -> "Public Routable (Глобальная сеть Интернет)"
                    }
                    fields.add("Тип адресации" to scope)

                    if (!isIpv6) {
                        val parts = normalized.split(".").map { it.toInt() }
                        if (parts.size == 4) {
                            val intVal = (parts[0].toLong() shl 24) or
                                    (parts[1].toLong() shl 16) or
                                    (parts[2].toLong() shl 8) or
                                    parts[3].toLong()
                            fields.add("Decimal (Integer)" to intVal.toString())
                            fields.add("Hexadecimal" to "0x" + parts.joinToString("") { "%02X".format(it) })
                            fields.add("PTR ARPA зона" to "${parts.reversed().joinToString(".")}.in-addr.arpa")
                        }
                    }

                    val canonicalHost = addr.canonicalHostName
                    if (canonicalHost.isNotBlank() && canonicalHost != normalized) {
                        fields.add("Reverse DNS (Системный резолвер)" to canonicalHost)
                    }
                }.onFailure {
                    fields.add("Статус синтаксиса" to "Нестандартный формат IP или хоста")
                }
            } else {
                fields.add("Формат E.164" to normalized)
                fields.add("Только цифры (Clean)" to clean)
                fields.add("Длина номера" to "${clean.length} цифр")

                val countryInfo = detectPhoneCountry(clean)
                fields.add("Страна / Код" to countryInfo)

                if (clean.startsWith("7") && clean.length == 11) {
                    val def = clean.substring(1, 4).toIntOrNull() ?: 0
                    val subscriber = clean.substring(4)
                    val formatted = "+7 ($def) ${subscriber.take(3)}-${subscriber.drop(3).take(2)}-${subscriber.drop(5)}"
                    fields.add("Национальный формат" to formatted)
                    fields.add("Код DEF / АВС" to def.toString())
                    fields.add("Базовый диапазон оператора (РФ)" to detectRuCarrierByDef(def))
                }

                fields.add("WhatsApp Direct URI" to "https://wa.me/$clean")
                fields.add("Telegram Direct URI" to "https://t.me/+$clean")
                fields.add("Google OSINT Dork" to "\"$normalized\" OR \"$clean\"")
            }

            val rawJson = JSONObject().apply {
                for ((k, v) in fields) {
                    put(k, v)
                }
            }.toString(2)

            ModuleResult(
                moduleId = "local_offline_engine",
                moduleName = "Локальный криминалистический анализатор (Offline)",
                category = category,
                statusCode = 200,
                success = true,
                durationMs = (System.currentTimeMillis() - start).coerceAtLeast(1L),
                extractedFields = fields,
                rawBody = rawJson,
                requestUrl = "local://offline-analyzer/$clean"
            )
        }

    private fun detectPhoneCountry(digits: String): String = when {
        digits.startsWith("79") || digits.startsWith("73") || digits.startsWith("74") || digits.startsWith("78") ->
            "Россия (+7)"
        digits.startsWith("76") || digits.startsWith("77") -> "Казахстан (+7 6xx / +7 7xx)"
        digits.startsWith("375") -> "Беларусь (+375)"
        digits.startsWith("380") -> "Украина (+380)"
        digits.startsWith("998") -> "Узбекистан (+998)"
        digits.startsWith("996") -> "Кыргызстан (+996)"
        digits.startsWith("992") -> "Таджикистан (+992)"
        digits.startsWith("374") -> "Армения (+374)"
        digits.startsWith("995") -> "Грузия (+995)"
        digits.startsWith("994") -> "Азербайджан (+994)"
        digits.startsWith("373") -> "Молдова (+373)"
        digits.startsWith("1") -> "США / Канада (NANP +1)"
        digits.startsWith("44") -> "Великобритания (+44)"
        digits.startsWith("49") -> "Германия (+49)"
        digits.startsWith("33") -> "Франция (+33)"
        digits.startsWith("90") -> "Турция (+90)"
        digits.startsWith("971") -> "ОАЭ (+971)"
        digits.startsWith("86") -> "Китай (+86)"
        else -> "Международный номер (+${digits.take(3)}...)"
    }

    private fun detectRuCarrierByDef(def: Int): String = when (def) {
        in 910..919, in 980..989 -> "ПАО «МТС» (Мобильные ТелеСистемы)"
        in 920..939 -> "ПАО «МегаФон»"
        in 903..906, in 909..909, in 960..969 -> "ПАО «ВымпелКом» (Билайн)"
        in 900..902, in 904..904, in 908..908, in 950..953, in 958..958, in 977..977, in 991..994 -> "ООО «Т2 Мобайл» (Tele2 / Ростелеком)"
        in 995..996, 999 -> "ООО «Скартел» (Yota)"
        997 -> "АО «Т-Мобайл» (Тинькофф Мобайл) / МВНО"
        970, 971 -> "ПАО «Ростелеком» / СберМобайл"
        in 300..499, in 800..899 -> "Географический / стационарный или сервисный код РФ"
        else -> "Мобильный оператор РФ (возможен перенос по MNP)"
    }

    suspend fun executeModule(module: ApiModule, rawTarget: String, category: TargetCategory): ModuleResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val (normalizedTarget, cleanTarget) = normalizeTarget(rawTarget, category)

            val keyTrimmed = module.apiKey.trim()

            val encodedTarget = runCatching { URLEncoder.encode(normalizedTarget, "UTF-8") }
                .getOrDefault(normalizedTarget)
            val encodedClean = runCatching { URLEncoder.encode(cleanTarget, "UTF-8") }
                .getOrDefault(cleanTarget)
            val encodedKey = runCatching { URLEncoder.encode(keyTrimmed, "UTF-8") }
                .getOrDefault(keyTrimmed)

            var finalUrl = module.urlTemplate
                .replace("{target}", encodedTarget)
                .replace("{target_clean}", encodedClean)
                .replace("{api_key}", encodedKey)

            if (module.authType == AuthType.QUERY_PARAM && module.authParamName.isNotBlank() && keyTrimmed.isNotEmpty()) {
                val sep = if (finalUrl.contains("?")) "&" else "?"
                val cleanParamName = sanitizeHeaderToken(module.authParamName.trim())
                finalUrl = "$finalUrl$sep$cleanParamName=$encodedKey"
            }

            val safeDisplayUrl = CryptoManager.redactSecret(finalUrl, keyTrimmed)

            if (module.authType == AuthType.URL_PLACEHOLDER && keyTrimmed.isEmpty() && module.urlTemplate.contains("{api_key}")) {
                return@withContext ModuleResult(
                    moduleId = module.id,
                    moduleName = module.name,
                    category = module.category,
                    statusCode = 0,
                    success = false,
                    durationMs = 1L,
                    extractedFields = emptyList(),
                    rawBody = "",
                    errorMessage = "Не указан API-ключ для шаблона {api_key}. Укажите ключ в настройках модуля.",
                    requestUrl = safeDisplayUrl
                )
            }

            var currentConnection: HttpURLConnection? = null
            try {
                var currentUrlObj = URL(finalUrl)
                val initialHost = currentUrlObj.host

                // 1. Validate URL scheme (only https or http)
                val initialProtocol = currentUrlObj.protocol.lowercase()
                if (initialProtocol != "https" && initialProtocol != "http") {
                    return@withContext buildSecurityBlockResult(
                        module = module,
                        safeUrl = safeDisplayUrl,
                        start = start,
                        reason = "Заблокировано: недопустимая схема протокола '$initialProtocol'. Разрешены только HTTPS/HTTP."
                    )
                }

                // 2. Block transmitting API keys or auth credentials over unencrypted HTTP
                val hasCredentials = (module.authType != AuthType.NONE && keyTrimmed.isNotEmpty()) ||
                        module.headersRaw.contains("Authorization", ignoreCase = true) ||
                        module.headersRaw.contains("x-apikey", ignoreCase = true)
                if (initialProtocol == "http" && hasCredentials) {
                    return@withContext buildSecurityBlockResult(
                        module = module,
                        safeUrl = safeDisplayUrl,
                        start = start,
                        reason = "Заблокировано политикой безопасности: передача API-ключей по незашифрованному протоколу HTTP запрещена. Используйте HTTPS."
                    )
                }

                // 3. Follow redirects safely with SSRF and downgrade validation on every hop
                var redirectCount = 0
                var activeMethod = module.method

                while (true) {
                    // SSRF check: verify all resolved IP addresses of the endpoint host are public
                    val hostName = currentUrlObj.host
                    if (hostName.isNullOrBlank() || isHostRestricted(hostName)) {
                        return@withContext buildSecurityBlockResult(
                            module = module,
                            safeUrl = safeDisplayUrl,
                            start = start,
                            reason = "Заблокировано SSRF-защитой: обращение к локальному или внутреннему хосту ($hostName) запрещено."
                        )
                    }

                    val sameHostAsInitial = hostName.equals(initialHost, ignoreCase = true)

                    currentConnection = (currentUrlObj.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        requestMethod = activeMethod.name
                        connectTimeout = module.timeoutMs.coerceIn(1000, 60000)
                        readTimeout = module.timeoutMs.coerceIn(1000, 60000)
                        useCaches = false
                        setRequestProperty("User-Agent", "SpiderPulse-OSINT-Android/1.3")
                        setRequestProperty("Accept", "application/json, text/plain, */*")

                        // Attach authentication headers only if talking to the original configured host
                        if (sameHostAsInitial) {
                            if (module.authType == AuthType.HEADER && module.authParamName.isNotBlank() && keyTrimmed.isNotEmpty()) {
                                val hName = sanitizeHeaderToken(module.authParamName)
                                val hVal = sanitizeHeaderValue(keyTrimmed)
                                if (hName.isNotEmpty()) setRequestProperty(hName, hVal)
                            } else if (module.authType == AuthType.BEARER && keyTrimmed.isNotEmpty()) {
                                val cleanKey = sanitizeHeaderValue(keyTrimmed)
                                val headerVal = if (cleanKey.startsWith("Bearer ", ignoreCase = true)) {
                                    cleanKey
                                } else {
                                    "Bearer $cleanKey"
                                }
                                setRequestProperty("Authorization", headerVal)
                            }

                            if (module.headersRaw.isNotBlank()) {
                                module.headersRaw.lines().forEach { line ->
                                    val idx = line.indexOf(':')
                                    if (idx > 0) {
                                        val hName = sanitizeHeaderToken(line.substring(0, idx))
                                        val hVal = sanitizeHeaderValue(
                                            line.substring(idx + 1)
                                                .replace("{api_key}", keyTrimmed)
                                                .replace("{target}", normalizedTarget)
                                                .replace("{target_clean}", cleanTarget)
                                        )
                                        if (hName.isNotEmpty() && hVal.isNotEmpty()) {
                                            setRequestProperty(hName, hVal)
                                        }
                                    }
                                }
                            }
                        }

                        if (activeMethod == HttpMethod.POST) {
                            doOutput = true
                            val bodyContent = module.postBodyTemplate
                                .replace("{target}", normalizedTarget)
                                .replace("{target_clean}", cleanTarget)
                                .replace("{api_key}", keyTrimmed)
                            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                                writer.write(bodyContent)
                                writer.flush()
                            }
                        }
                    }

                    val code = currentConnection.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val location = currentConnection.getHeaderField("Location")
                        currentConnection.disconnect()
                        currentConnection = null

                        if (location.isNullOrBlank() || redirectCount >= MAX_REDIRECTS) {
                            break
                        }

                        val nextUrl = URL(currentUrlObj, location)
                        val nextProto = nextUrl.protocol.lowercase()
                        if (nextProto != "https" && nextProto != "http") {
                            return@withContext buildSecurityBlockResult(
                                module = module,
                                safeUrl = safeDisplayUrl,
                                start = start,
                                reason = "Заблокировано: редирект на недопустимый протокол '$nextProto'."
                            )
                        }
                        if ( currentUrlObj.protocol.equals("https", ignoreCase = true) && nextProto == "http") {
                            return@withContext buildSecurityBlockResult(
                                module = module,
                                safeUrl = safeDisplayUrl,
                                start = start,
                                reason = "Заблокировано: попытка понижения безопасности (HTTPS -> HTTP downgrade redirect)."
                            )
                        }

                        if (code == 301 || code == 302 || code == 303) {
                            activeMethod = HttpMethod.GET
                        }
                        currentUrlObj = nextUrl
                        redirectCount++
                        continue
                    }
                    break
                }

                val conn = currentConnection ?: return@withContext buildSecurityBlockResult(
                    module = module,
                    safeUrl = safeDisplayUrl,
                    start = start,
                    reason = "Превышен лимит перенаправлений (HTTP Redirects)."
                )

                val code = conn.responseCode
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream ?: runCatching { conn.inputStream }.getOrNull()
                }

                val responseText = stream?.use { readBoundedStream(it, MAX_RESPONSE_CHARS) } ?: ""
                val redactedResponse = CryptoManager.redactSecret(responseText, keyTrimmed)

                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val (prettyBody, extracted) = parseAndFlattenResponse(redactedResponse)

                val isSuccess = code in 200..299
                val errMsg = if (!isSuccess) {
                    val serverDetail = extracted.firstOrNull {
                        it.first.contains("error", true) || it.first.contains("message", true)
                    }?.second ?: "Ошибка ответа сервера"
                    CryptoManager.redactSecret("HTTP $code: $serverDetail", keyTrimmed)
                } else null

                ModuleResult(
                    moduleId = module.id,
                    moduleName = module.name,
                    category = module.category,
                    statusCode = code,
                    success = isSuccess,
                    durationMs = elapsed,
                    extractedFields = extracted,
                    rawBody = prettyBody,
                    errorMessage = errMsg,
                    requestUrl = safeDisplayUrl
                )
            } catch (e: Exception) {
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val safeError = CryptoManager.redactSecret(
                    "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Сетевая ошибка"}",
                    keyTrimmed
                )
                ModuleResult(
                    moduleId = module.id,
                    moduleName = module.name,
                    category = module.category,
                    statusCode = 0,
                    success = false,
                    durationMs = elapsed,
                    extractedFields = emptyList(),
                    rawBody = "",
                    errorMessage = safeError,
                    requestUrl = safeDisplayUrl
                )
            } finally {
                currentConnection?.disconnect()
            }
        }

    private fun buildSecurityBlockResult(
        module: ApiModule,
        safeUrl: String,
        start: Long,
        reason: String
    ): ModuleResult {
        return ModuleResult(
            moduleId = module.id,
            moduleName = module.name,
            category = module.category,
            statusCode = 0,
            success = false,
            durationMs = (System.currentTimeMillis() - start).coerceAtLeast(1L),
            extractedFields = emptyList(),
            rawBody = "",
            errorMessage = reason,
            requestUrl = safeUrl
        )
    }

    private fun isHostRestricted(host: String): Boolean {
        val cleanHost = host.trim().lowercase()
        if (cleanHost == "localhost" || cleanHost.endsWith(".local") || cleanHost.endsWith(".internal")) {
            return true
        }
        return try {
            val addresses = InetAddress.getAllByName(cleanHost)
            if (addresses.isEmpty()) true else addresses.any { CryptoManager.isRestrictedInternalAddress(it) }
        } catch (_: Exception) {
            // If DNS fails, let the connection attempt report the standard UnknownHostException
            false
        }
    }

    private fun sanitizeHeaderToken(raw: String): String {
        return raw.replace("\r", "").replace("\n", "").trim()
    }

    private fun sanitizeHeaderValue(raw: String): String {
        return raw.replace("\r", "").replace("\n", "").trim()
    }

    private fun readBoundedStream(inputStream: InputStream, maxChars: Int): String {
        val reader = InputStreamReader(inputStream, Charsets.UTF_8)
        val sb = StringBuilder()
        val buffer = CharArray(4096)
        var totalRead = 0
        while (true) {
            val read = reader.read(buffer, 0, buffer.size)
            if (read == -1) break
            val allowed = (maxChars - totalRead).coerceAtMost(read)
            if (allowed > 0) {
                sb.append(buffer, 0, allowed)
                totalRead += allowed
            }
            if (totalRead >= maxChars) {
                sb.append("\n... [TRUNCATED BY SECURITY LIMIT]")
                break
            }
        }
        return sb.toString()
    }

    private fun parseAndFlattenResponse(raw: String): Pair<String, List<Pair<String, String>>> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "" to emptyList()

        val extracted = mutableListOf<Pair<String, String>>()
        try {
            if (trimmed.startsWith("{")) {
                val jsonObj = JSONObject(trimmed)
                flattenJsonObject("", jsonObj, extracted, maxFields = 45)
                return jsonObj.toString(2) to extracted
            } else if (trimmed.startsWith("[")) {
                val jsonArr = JSONArray(trimmed)
                flattenJsonArray("", jsonArr, extracted, maxFields = 45)
                return jsonArr.toString(2) to extracted
            }
        } catch (_: Exception) {
            // Not JSON; fall back to plain text lines
        }

        trimmed.lines().take(25).forEachIndexed { idx, line ->
            val cleanLine = line.trim()
            if (cleanLine.isNotEmpty()) {
                val sepIdx = cleanLine.indexOf(':')
                if (sepIdx in 1..40) {
                    extracted.add(
                        cleanLine.substring(0, sepIdx).trim() to cleanLine.substring(sepIdx + 1).trim()
                    )
                } else {
                    extracted.add("Строка ${idx + 1}" to cleanLine.take(200))
                }
            }
        }
        return trimmed to extracted
    }

    private fun flattenJsonObject(
        prefix: String,
        obj: JSONObject,
        out: MutableList<Pair<String, String>>,
        maxFields: Int
    ) {
        if (out.size >= maxFields) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            if (out.size >= maxFields) return
            val key = keys.next()
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = obj.opt(key)
            when (value) {
                null, JSONObject.NULL -> { /* skip null */ }
                is JSONObject -> flattenJsonObject(fullKey, value, out, maxFields)
                is JSONArray -> {
                    if (value.length() == 0) continue
                    val primitives = mutableListOf<String>()
                    var allPrimitives = true
                    for (i in 0 until value.length()) {
                        val item = value.opt(i)
                        if (item is JSONObject || item is JSONArray) {
                            allPrimitives = false
                            break
                        } else if (item != null && item != JSONObject.NULL) {
                            primitives.add(item.toString())
                        }
                    }
                    if (allPrimitives) {
                        val joined = primitives.joinToString(", ")
                        if (joined.isNotBlank()) {
                            out.add(fullKey to joined.take(300))
                        }
                    } else {
                        flattenJsonArray(fullKey, value, out, maxFields)
                    }
                }
                else -> {
                    val str = value.toString().trim()
                    if (str.isNotEmpty() && str != "null") {
                        out.add(fullKey to str.take(300))
                    }
                }
            }
        }
    }

    private fun flattenJsonArray(
        prefix: String,
        arr: JSONArray,
        out: MutableList<Pair<String, String>>,
        maxFields: Int
    ) {
        val count = arr.length().coerceAtMost(8)
        for (i in 0 until count) {
            if (out.size >= maxFields) return
            val item = arr.opt(i)
            val itemKey = if (prefix.isEmpty()) "[$i]" else "$prefix[$i]"
            when (item) {
                is JSONObject -> flattenJsonObject(itemKey, item, out, maxFields)
                is JSONArray -> flattenJsonArray(itemKey, item, out, maxFields)
                null, JSONObject.NULL -> {}
                else -> out.add(itemKey to item.toString().take(300))
            }
        }
    }
}
