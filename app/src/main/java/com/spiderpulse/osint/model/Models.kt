package com.spiderpulse.osint.model

enum class TargetCategory(val titleRu: String, val badge: String) {
    IP("IP-адрес", "IP"),
    PHONE("Телефон", "TEL"),
    UNIVERSAL("Универсальный", "ALL")
}

enum class HttpMethod {
    GET, POST
}

enum class AuthType(val labelRu: String, val hintRu: String) {
    NONE("Без ключа (Открытый API)", "Ключ не требуется"),
    QUERY_PARAM("Query-параметр (?key=...)", "Имя параметра, напр. access_key или token"),
    HEADER("HTTP-заголовок (Header)", "Имя заголовка, напр. Key или x-apikey"),
    BEARER("Bearer Token (Authorization)", "Автоматически добавит Authorization: Bearer <ключ>"),
    URL_PLACEHOLDER("Внутри URL ({api_key})", "Заменит {api_key} в шаблоне ссылки")
}

data class ApiModule(
    val id: String,
    val name: String,
    val description: String,
    val category: TargetCategory,
    val urlTemplate: String,
    val method: HttpMethod = HttpMethod.GET,
    val authType: AuthType = AuthType.NONE,
    val authParamName: String = "",
    val apiKey: String = "",
    val headersRaw: String = "",
    val postBodyTemplate: String = "",
    val timeoutMs: Int = 10000,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false
)

data class ModuleResult(
    val moduleId: String,
    val moduleName: String,
    val category: TargetCategory,
    val statusCode: Int,
    val success: Boolean,
    val durationMs: Long,
    val extractedFields: List<Pair<String, String>>,
    val rawBody: String,
    val errorMessage: String? = null,
    val requestUrl: String = ""
)

data class ScanRecord(
    val id: String,
    val target: String,
    val detectedType: TargetCategory,
    val timestamp: Long,
    val results: List<ModuleResult>
)
