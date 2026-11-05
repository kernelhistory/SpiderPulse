package com.spiderpulse.osint.data

import com.spiderpulse.osint.model.ApiModule
import com.spiderpulse.osint.model.AuthType
import com.spiderpulse.osint.model.HttpMethod
import com.spiderpulse.osint.model.TargetCategory

object DefaultModules {

    fun getInitialModules(): List<ApiModule> = listOf(
        // === KEYLESS IP OSINT MODULES (Work immediately out-of-the-box) ===
        ApiModule(
            id = "ip_api_com",
            name = "IP-API.com (Geo + Proxy/Hosting)",
            description = "Геолокация, провайдер, ASN, организация, мобильная сеть, детектор Proxy/VPN/Hosting. Работает без ключа.",
            category = TargetCategory.IP,
            urlTemplate = "http://ip-api.com/json/{target}?fields=status,message,continent,country,countryCode,regionName,city,zip,lat,lon,timezone,isp,org,as,asname,reverse,mobile,proxy,hosting,query&lang=ru",
            method = HttpMethod.GET,
            authType = AuthType.NONE,
            enabled = true,
            isBuiltIn = true
        ),
        ApiModule(
            id = "shodan_internetdb",
            name = "Shodan InternetDB (Порты и CVE)",
            description = "Открытые порты, теги хоста, имена хостов (PTR), CPE и известные уязвимости (CVE) от Shodan. Без API-ключа.",
            category = TargetCategory.IP,
            urlTemplate = "https://internetdb.shodan.io/{target}",
            method = HttpMethod.GET,
            authType = AuthType.NONE,
            enabled = true,
            isBuiltIn = true
        ),
        ApiModule(
            id = "ipwho_is",
            name = "ipwho.is (Extended Whois & ASN)",
            description = "Подробные данные по IP: страна, регион, координаты, ASN, провайдер, домен, часовой пояс и валюта.",
            category = TargetCategory.IP,
            urlTemplate = "https://ipwho.is/{target}",
            method = HttpMethod.GET,
            authType = AuthType.NONE,
            enabled = true,
            isBuiltIn = true
        ),
        ApiModule(
            id = "ipinfo_io",
            name = "IPInfo.io (ASN & Hostname)",
            description = "Быстрый поиск hostname, города, координат и организации. Работает без ключа (или с токеном при больших лимитах).",
            category = TargetCategory.IP,
            urlTemplate = "https://ipinfo.io/{target}/json",
            method = HttpMethod.GET,
            authType = AuthType.QUERY_PARAM,
            authParamName = "token",
            apiKey = "",
            enabled = true,
            isBuiltIn = true
        ),
        ApiModule(
            id = "ripe_stat_network",
            name = "RIPEstat Network Info & Abuse",
            description = "Официальная база RIPE NCC: префикс подсети, ASN и контакты abuse-службы для IP-адреса.",
            category = TargetCategory.IP,
            urlTemplate = "https://stat.ripe.net/data/abuse-contact-finder/data.json?resource={target}",
            method = HttpMethod.GET,
            authType = AuthType.NONE,
            enabled = true,
            isBuiltIn = true
        ),
        ApiModule(
            id = "bgpview_ip",
            name = "BGPView (BGP-префиксы и RIR)",
            description = "Маршрутизация BGP, PTR-запись, автономные системы (ASN) и распределение RIR.",
            category = TargetCategory.IP,
            urlTemplate = "https://api.bgpview.io/ip/{target}",
            method = HttpMethod.GET,
            authType = AuthType.NONE,
            enabled = true,
            isBuiltIn = true
        ),

        // === CONFIGURABLE IP OSINT MODULES (With API Keys) ===
        ApiModule(
            id = "abuseipdb_check",
            name = "AbuseIPDB v2 (Репутация и жалобы)",
            description = "Проверка IP в базе спама, брутфорса и DDoS-атак. Требуется бесплатный API-ключ AbuseIPDB.",
            category = TargetCategory.IP,
            urlTemplate = "https://api.abuseipdb.com/api/v2/check?ipAddress={target}&maxAgeInDays=90&verbose",
            method = HttpMethod.GET,
            authType = AuthType.HEADER,
            authParamName = "Key",
            apiKey = "",
            headersRaw = "Accept: application/json",
            enabled = false,
            isBuiltIn = true
        ),
        ApiModule(
            id = "virustotal_ip",
            name = "VirusTotal v3 (Threat Intelligence)",
            description = "Репутация IP по 90+ антивирусным и сетевым движкам, пассивный DNS и сертификаты. Требуется ключ x-apikey.",
            category = TargetCategory.IP,
            urlTemplate = "https://www.virustotal.com/api/v3/ip_addresses/{target}",
            method = HttpMethod.GET,
            authType = AuthType.HEADER,
            authParamName = "x-apikey",
            apiKey = "",
            enabled = false,
            isBuiltIn = true
        ),
        ApiModule(
            id = "shodan_host_full",
            name = "Shodan Host API (Полный профиль)",
            description = "Полное досье хоста в Shodan с баннерами сервисов и SSL. Требуется API-ключ Shodan.",
            category = TargetCategory.IP,
            urlTemplate = "https://api.shodan.io/shodan/host/{target}",
            method = HttpMethod.GET,
            authType = AuthType.QUERY_PARAM,
            authParamName = "key",
            apiKey = "",
            enabled = false,
            isBuiltIn = true
        ),
        ApiModule(
            id = "greynoise_community",
            name = "GreyNoise Community (Сканеры интернета)",
            description = "Определяет, является ли IP массовым интернет-сканером, ботом или легитимным сервисом (RIOT).",
            category = TargetCategory.IP,
            urlTemplate = "https://api.greynoise.io/v3/community/{target}",
            method = HttpMethod.GET,
            authType = AuthType.HEADER,
            authParamName = "key",
            apiKey = "",
            enabled = false,
            isBuiltIn = true
        ),
        ApiModule(
            id = "ipqualityscore_ip",
            name = "IPQualityScore (Fraud & Tor/VPN Score)",
            description = "Оценка фрод-риска, детекция Tor, VPN, прокси и ботнетов. Вставьте свой ключ.",
            category = TargetCategory.IP,
            urlTemplate = "https://ipqualityscore.com/api/json/ip/{api_key}/{target}?strictness=1",
            method = HttpMethod.GET,
            authType = AuthType.URL_PLACEHOLDER,
            apiKey = "",
            enabled = false,
            isBuiltIn = true
        ),

        // === PHONE NUMBER OSINT MODULES ===
        ApiModule(
            id = "htmlweb_phone_ru",
            name = "HTMLWeb Geo Phone (Оператор и регион РФ/СНГ)",
            description = "Определение мобильного оператора, региона, города и часового пояса по номеру телефона (РФ/СНГ/Мир).",
            category = TargetCategory.PHONE,
            urlTemplate = "https://htmlweb.ru/geo/api.php?json&telcod={target_clean}",
            method = HttpMethod.GET,
            authType = AuthType.QUERY_PARAM,
            authParamName = "api_key",
            apiKey = "",
            enabled = true,
            isBuiltIn = true
        ),
        ApiModule(
            id = "numverify_phone",
            name = "Numverify API (Глобальная валидация и оператор)",
            description = "Международная проверка номера, типа линии (mobile/landline), оператора и локации. Вставьте access_key.",
            category = TargetCategory.PHONE,
            urlTemplate = "https://apilayer.net/api/validate?number={target_clean}&format=1",
            method = HttpMethod.GET,
            authType = AuthType.QUERY_PARAM,
            authParamName = "access_key",
            apiKey = "",
            enabled = false,
            isBuiltIn = true
        ),
        ApiModule(
            id = "veriphone_io",
            name = "Veriphone.io (Carrier & E.164 Intelligence)",
            description = "Проверка активности номера, региона, оператора связи и международных форматов. Требуется ключ.",
            category = TargetCategory.PHONE,
            urlTemplate = "https://api.veriphone.io/v2/verify?phone={target}",
            method = HttpMethod.GET,
            authType = AuthType.QUERY_PARAM,
            authParamName = "key",
            apiKey = "",
            enabled = false,
            isBuiltIn = true
        ),
        ApiModule(
            id = "abstractapi_phone",
            name = "AbstractAPI Phone Validation",
            description = "Глобальный OSINT-модуль для определения страны, оператора и типа связи номера.",
            category = TargetCategory.PHONE,
            urlTemplate = "https://phonevalidation.abstractapi.com/v1/?phone={target_clean}",
            method = HttpMethod.GET,
            authType = AuthType.QUERY_PARAM,
            authParamName = "api_key",
            apiKey = "",
            enabled = false,
            isBuiltIn = true
        ),
        ApiModule(
            id = "dadata_phone_clean",
            name = "DaData.ru (Стандартизация и регион номера)",
            description = "Определение оператора с учетом MNP, региона, часового пояса и QC-кода (POST запрос).",
            category = TargetCategory.PHONE,
            urlTemplate = "https://cleaner.dadata.ru/api/v1/clean/phone",
            method = HttpMethod.POST,
            authType = AuthType.HEADER,
            authParamName = "Authorization",
            apiKey = "",
            headersRaw = "Content-Type: application/json\nX-Secret: ",
            postBodyTemplate = "[ \"{target}\" ]",
            enabled = false,
            isBuiltIn = true
        )
    )
}
