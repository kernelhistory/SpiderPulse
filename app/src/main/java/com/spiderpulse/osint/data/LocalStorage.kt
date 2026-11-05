package com.spiderpulse.osint.data

import android.content.Context
import com.spiderpulse.osint.model.ApiModule
import com.spiderpulse.osint.model.AuthType
import com.spiderpulse.osint.model.HttpMethod
import com.spiderpulse.osint.model.ModuleResult
import com.spiderpulse.osint.model.ScanRecord
import com.spiderpulse.osint.model.TargetCategory
import com.spiderpulse.osint.security.CryptoManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID

class LocalStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("spiderpulse_local_prefs", Context.MODE_PRIVATE)

    // Encrypted v2 storage files (AES-256-GCM via AndroidKeyStore)
    private val modulesEncryptedFile = File(context.filesDir, "api_modules_v2.enc")
    private val historyEncryptedFile = File(context.filesDir, "scan_history_v2.enc")

    // Legacy v1 plaintext files (for automatic migration & secure deletion)
    private val legacyModulesFile = File(context.filesDir, "api_modules_v1.json")
    private val legacyHistoryFile = File(context.filesDir, "scan_history_v1.json")

    fun isDisclaimerAccepted(): Boolean = prefs.getBoolean("disclaimer_accepted", false)

    fun setDisclaimerAccepted(accepted: Boolean) {
        prefs.edit().putBoolean("disclaimer_accepted", accepted).apply()
    }

    fun isScreenProtectionEnabled(): Boolean = prefs.getBoolean("screen_protection_flag_secure", true)

    fun setScreenProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("screen_protection_flag_secure", enabled).apply()
    }

    fun loadModules(): List<ApiModule> {
        // 1. Migrate legacy plaintext file if present
        if (legacyModulesFile.exists()) {
            val migrated = runCatching {
                parseModulesJson(legacyModulesFile.readText())
            }.getOrNull()
            secureDeleteFile(legacyModulesFile)
            if (!migrated.isNullOrEmpty()) {
                saveModules(migrated)
                return migrated
            }
        }

        // 2. Load from AES-256-GCM encrypted storage
        if (!modulesEncryptedFile.exists()) {
            val defaults = DefaultModules.getInitialModules()
            saveModules(defaults)
            return defaults
        }

        return try {
            val decryptedJson = CryptoManager.decryptFromBytes(modulesEncryptedFile.readBytes())
            val parsed = parseModulesJson(decryptedJson)
            if (parsed.isEmpty()) {
                val defaults = DefaultModules.getInitialModules()
                saveModules(defaults)
                defaults
            } else {
                parsed
            }
        } catch (e: Exception) {
            val defaults = DefaultModules.getInitialModules()
            saveModules(defaults)
            defaults
        }
    }

    fun saveModules(modules: List<ApiModule>) {
        val json = serializeModulesJson(modules, includeApiKeys = true)
        runCatching {
            val encrypted = CryptoManager.encryptToBytes(json)
            modulesEncryptedFile.writeBytes(encrypted)
        }
    }

    fun serializeModulesJson(modules: List<ApiModule>, includeApiKeys: Boolean = false): String {
        val array = JSONArray()
        for (m in modules) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("description", m.description)
                put("category", m.category.name)
                put("urlTemplate", m.urlTemplate)
                put("method", m.method.name)
                put("authType", m.authType.name)
                put("authParamName", m.authParamName)
                put("apiKey", if (includeApiKeys) m.apiKey else "")
                put("headersRaw", if (includeApiKeys) m.headersRaw else maskSensitiveHeaders(m.headersRaw))
                put("postBodyTemplate", m.postBodyTemplate)
                put("timeoutMs", m.timeoutMs)
                put("enabled", m.enabled)
                put("isBuiltIn", m.isBuiltIn)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    private fun maskSensitiveHeaders(headersRaw: String): String {
        if (headersRaw.isBlank()) return ""
        return headersRaw.lines().joinToString("\n") { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val name = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (name.contains("key", ignoreCase = true) ||
                    name.contains("token", ignoreCase = true) ||
                    name.contains("secret", ignoreCase = true) ||
                    name.contains("auth", ignoreCase = true)
                ) {
                    if (value.contains("{api_key}")) "$name: $value" else "$name: "
                } else {
                    line
                }
            } else {
                line
            }
        }
    }

    fun parseModulesJson(rawJson: String): List<ApiModule> {
        val trimmed = rawJson.trim()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val wrapper = JSONObject(trimmed)
            wrapper.optJSONArray("modules") ?: wrapper.optJSONArray("apis") ?: JSONArray()
        }
        val list = mutableListOf<ApiModule>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name", "Custom API #${i + 1}").take(120)
            val url = obj.optString("urlTemplate", obj.optString("url", "")).trim()
            if (url.isEmpty()) continue
            if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
                continue
            }

            val categoryStr = obj.optString("category", "UNIVERSAL").uppercase()
            val category = runCatching { TargetCategory.valueOf(categoryStr) }
                .getOrDefault(TargetCategory.UNIVERSAL)

            val methodStr = obj.optString("method", "GET").uppercase()
            val method = runCatching { HttpMethod.valueOf(methodStr) }
                .getOrDefault(HttpMethod.GET)

            val authStr = obj.optString("authType", "NONE").uppercase()
            val authType = runCatching { AuthType.valueOf(authStr) }
                .getOrDefault(AuthType.NONE)

            list.add(
                ApiModule(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    description = obj.optString("description", "Пользовательский OSINT API модуль").take(500),
                    category = category,
                    urlTemplate = url,
                    method = method,
                    authType = authType,
                    authParamName = obj.optString("authParamName", "").trim(),
                    apiKey = obj.optString("apiKey", "").trim(),
                    headersRaw = obj.optString("headersRaw", ""),
                    postBodyTemplate = obj.optString("postBodyTemplate", ""),
                    timeoutMs = obj.optInt("timeoutMs", 10000).coerceIn(1000, 60000),
                    enabled = obj.optBoolean("enabled", true),
                    isBuiltIn = obj.optBoolean("isBuiltIn", false)
                )
            )
        }
        return list
    }

    fun loadHistory(): List<ScanRecord> {
        // 1. Migrate legacy plaintext history if present
        if (legacyHistoryFile.exists()) {
            val migrated = runCatching {
                parseHistoryJson(legacyHistoryFile.readText())
            }.getOrNull()
            secureDeleteFile(legacyHistoryFile)
            if (!migrated.isNullOrEmpty()) {
                saveHistory(migrated)
                return migrated
            }
        }

        // 2. Load from AES-256-GCM encrypted file
        if (!historyEncryptedFile.exists()) return emptyList()
        return try {
            val decrypted = CryptoManager.decryptFromBytes(historyEncryptedFile.readBytes())
            parseHistoryJson(decrypted)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseHistoryJson(rawText: String): List<ScanRecord> {
        val array = JSONArray(rawText)
        val list = mutableListOf<ScanRecord>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val resultsArr = obj.optJSONArray("results") ?: JSONArray()
            val results = mutableListOf<ModuleResult>()
            for (j in 0 until resultsArr.length()) {
                val rObj = resultsArr.optJSONObject(j) ?: continue
                val fieldsArr = rObj.optJSONArray("extractedFields") ?: JSONArray()
                val fields = mutableListOf<Pair<String, String>>()
                for (k in 0 until fieldsArr.length()) {
                    val fObj = fieldsArr.optJSONObject(k) ?: continue
                    fields.add(fObj.optString("k") to fObj.optString("v"))
                }
                val cat = runCatching {
                    TargetCategory.valueOf(rObj.optString("category", "UNIVERSAL"))
                }.getOrDefault(TargetCategory.UNIVERSAL)

                results.add(
                    ModuleResult(
                        moduleId = rObj.optString("moduleId"),
                        moduleName = rObj.optString("moduleName"),
                        category = cat,
                        statusCode = rObj.optInt("statusCode"),
                        success = rObj.optBoolean("success"),
                        durationMs = rObj.optLong("durationMs"),
                        extractedFields = fields,
                        rawBody = rObj.optString("rawBody"),
                        errorMessage = rObj.optString("errorMessage").ifBlank { null },
                        requestUrl = rObj.optString("requestUrl")
                    )
                )
            }
            val detectedType = runCatching {
                TargetCategory.valueOf(obj.optString("detectedType", "IP"))
            }.getOrDefault(TargetCategory.IP)

            list.add(
                ScanRecord(
                    id = obj.optString("id"),
                    target = obj.optString("target"),
                    detectedType = detectedType,
                    timestamp = obj.optLong("timestamp"),
                    results = results
                )
            )
        }
        return list
    }

    fun saveHistory(records: List<ScanRecord>) {
        if (records.isEmpty()) {
            secureDeleteFile(historyEncryptedFile)
            return
        }
        val array = JSONArray()
        for (rec in records.take(100)) {
            val resArr = JSONArray()
            for (r in rec.results) {
                val fArr = JSONArray()
                for ((k, v) in r.extractedFields) {
                    fArr.put(JSONObject().apply {
                        put("k", k)
                        put("v", v)
                    })
                }
                resArr.put(JSONObject().apply {
                    put("moduleId", r.moduleId)
                    put("moduleName", r.moduleName)
                    put("category", r.category.name)
                    put("statusCode", r.statusCode)
                    put("success", r.success)
                    put("durationMs", r.durationMs)
                    put("extractedFields", fArr)
                    put("rawBody", r.rawBody.take(15000))
                    put("errorMessage", r.errorMessage ?: "")
                    put("requestUrl", r.requestUrl)
                })
            }
            array.put(JSONObject().apply {
                put("id", rec.id)
                put("target", rec.target)
                put("detectedType", rec.detectedType.name)
                put("timestamp", rec.timestamp)
                put("results", resArr)
            })
        }
        runCatching {
            val encrypted = CryptoManager.encryptToBytes(array.toString())
            historyEncryptedFile.writeBytes(encrypted)
        }
    }

    /**
     * Emergency cryptographic wipe: overwrites storage files with random bytes,
     * deletes them, rotates the AndroidKeyStore AES-256 master key, and restores clean defaults.
     */
    fun panicWipeAll(): List<ApiModule> {
        secureDeleteFile(modulesEncryptedFile)
        secureDeleteFile(historyEncryptedFile)
        secureDeleteFile(legacyModulesFile)
        secureDeleteFile(legacyHistoryFile)
        CryptoManager.rotateMasterKey()
        val defaults = DefaultModules.getInitialModules()
        saveModules(defaults)
        return defaults
    }

    private fun secureDeleteFile(file: File) {
        runCatching {
            if (file.exists()) {
                val len = file.length().coerceIn(64L, 1_048_576L).toInt()
                val randomBytes = ByteArray(len)
                SecureRandom().nextBytes(randomBytes)
                file.writeBytes(randomBytes)
                file.delete()
            }
        }
    }
}
