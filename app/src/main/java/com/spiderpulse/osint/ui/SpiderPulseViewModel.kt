package com.spiderpulse.osint.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spiderpulse.osint.data.DefaultModules
import com.spiderpulse.osint.data.LocalStorage
import com.spiderpulse.osint.model.ApiModule
import com.spiderpulse.osint.model.ModuleResult
import com.spiderpulse.osint.model.ScanRecord
import com.spiderpulse.osint.model.TargetCategory
import com.spiderpulse.osint.network.OsintEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class SpiderPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = LocalStorage(application.applicationContext)

    var modules by mutableStateOf<List<ApiModule>>(emptyList())
        private set

    var history by mutableStateOf<List<ScanRecord>>(emptyList())
        private set

    var targetInput by mutableStateOf("")
    var selectedMode by mutableStateOf<TargetCategory?>(null) // null = AUTO

    var isScanning by mutableStateOf(false)
        private set

    var completedCount by mutableIntStateOf(0)
        private set

    var totalCount by mutableIntStateOf(0)
        private set

    var currentResults by mutableStateOf<List<ModuleResult>>(emptyList())
        private set

    var activeScanTarget by mutableStateOf("")
        private set

    var activeScanCategory by mutableStateOf(TargetCategory.IP)
        private set

    var showDisclaimerDialog by mutableStateOf(false)
    var disclaimerAccepted by mutableStateOf(false)
        private set

    var screenProtectionEnabled by mutableStateOf(true)
        private set

    var statusBannerMessage by mutableStateOf<String?>(null)

    private var scanJob: Job? = null

    init {
        modules = storage.loadModules()
        history = storage.loadHistory()
        disclaimerAccepted = storage.isDisclaimerAccepted()
        screenProtectionEnabled = storage.isScreenProtectionEnabled()
        if (!disclaimerAccepted) {
            showDisclaimerDialog = true
        }
    }

    fun acceptDisclaimer() {
        storage.setDisclaimerAccepted(true)
        disclaimerAccepted = true
        showDisclaimerDialog = false
    }

    fun toggleScreenProtection(enabled: Boolean) {
        screenProtectionEnabled = enabled
        storage.setScreenProtectionEnabled(enabled)
    }

    fun effectiveCategory(): TargetCategory {
        return selectedMode ?: OsintEngine.detectCategory(targetInput)
    }

    fun getApplicableModules(category: TargetCategory): List<ApiModule> {
        return modules.filter {
            it.enabled && (it.category == category || it.category == TargetCategory.UNIVERSAL)
        }
    }

    fun startScan() {
        val raw = targetInput.trim()
        if (raw.isEmpty()) {
            statusBannerMessage = "Введите IP-адрес или номер телефона для запуска сканирования"
            return
        }
        if (!disclaimerAccepted) {
            showDisclaimerDialog = true
            return
        }

        scanJob?.cancel()
        val category = effectiveCategory()
        val activeApis = getApplicableModules(category)

        activeScanTarget = raw
        activeScanCategory = category
        isScanning = true
        completedCount = 0
        totalCount = activeApis.size + 1 // +1 for local offline analyzer
        currentResults = emptyList()
        statusBannerMessage = null

        val mutex = Mutex()

        scanJob = viewModelScope.launch {
            val collected = mutableListOf<ModuleResult>()

            // 1. Run local offline analyzer immediately
            val localRes = OsintEngine.runLocalAnalysis(raw, category)
            mutex.withLock {
                collected.add(localRes)
                completedCount = collected.size
                currentResults = collected.toList()
            }

            // 2. Run all enabled matching API modules in parallel
            coroutineScope {
                val jobs = activeApis.map { module ->
                    async {
                        val res = OsintEngine.executeModule(module, raw, category)
                        mutex.withLock {
                            collected.add(res)
                            completedCount = collected.size
                            currentResults = collected.toList()
                        }
                        res
                    }
                }
                jobs.awaitAll()
            }

            isScanning = false

            // Save to encrypted local scan history
            val newRecord = ScanRecord(
                id = UUID.randomUUID().toString(),
                target = raw,
                detectedType = category,
                timestamp = System.currentTimeMillis(),
                results = collected.toList()
            )
            history = listOf(newRecord) + history.take(99)
            storage.saveHistory(history)
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        isScanning = false
        statusBannerMessage = "Сканирование остановлено пользователем"
    }

    fun loadScanFromHistory(record: ScanRecord) {
        targetInput = record.target
        selectedMode = record.detectedType
        activeScanTarget = record.target
        activeScanCategory = record.detectedType
        currentResults = record.results
        completedCount = record.results.size
        totalCount = record.results.size
    }

    fun deleteHistoryItem(id: String) {
        history = history.filterNot { it.id == id }
        storage.saveHistory(history)
    }

    fun clearAllHistory() {
        history = emptyList()
        storage.saveHistory(emptyList())
        statusBannerMessage = "Локальная история поиска очищена"
    }

    fun panicWipeAllData() {
        scanJob?.cancel()
        isScanning = false
        targetInput = ""
        activeScanTarget = ""
        currentResults = emptyList()
        completedCount = 0
        totalCount = 0
        history = emptyList()
        modules = storage.panicWipeAll()
        statusBannerMessage = "Экстренная очистка выполнена: ключи, история и криптоключ сброшены"
    }

    fun toggleModuleEnabled(id: String, enabled: Boolean) {
        modules = modules.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        storage.saveModules(modules)
    }

    fun saveOrUpdateModule(module: ApiModule) {
        val exists = modules.any { it.id == module.id }
        modules = if (exists) {
            modules.map { if (it.id == module.id) module else it }
        } else {
            listOf(module) + modules
        }
        storage.saveModules(modules)
        statusBannerMessage = "Модуль «${module.name}» сохранен в зашифрованное хранилище"
    }

    fun duplicateModule(module: ApiModule) {
        val copy = module.copy(
            id = UUID.randomUUID().toString(),
            name = "${module.name} (Копия)",
            isBuiltIn = false
        )
        modules = listOf(copy) + modules
        storage.saveModules(modules)
        statusBannerMessage = "Создана копия модуля «${module.name}»"
    }

    fun deleteModule(id: String) {
        modules = modules.filterNot { it.id == id }
        storage.saveModules(modules)
    }

    fun enableAllModules(category: TargetCategory?) {
        modules = modules.map {
            if (category == null || it.category == category) it.copy(enabled = true) else it
        }
        storage.saveModules(modules)
    }

    fun disableAllModules(category: TargetCategory?) {
        modules = modules.map {
            if (category == null || it.category == category) it.copy(enabled = false) else it
        }
        storage.saveModules(modules)
    }

    fun resetDefaultModules() {
        val customOnly = modules.filterNot { it.isBuiltIn }
        val defaults = DefaultModules.getInitialModules()
        modules = customOnly + defaults
        storage.saveModules(modules)
        statusBannerMessage = "Стандартные модули восстановлены (пользовательские сохранены)"
    }

    fun exportModulesJson(includeApiKeys: Boolean = false): String =
        storage.serializeModulesJson(modules, includeApiKeys = includeApiKeys)

    fun importModulesJson(rawJson: String, replaceAll: Boolean): Result<Int> {
        return runCatching {
            val parsed = storage.parseModulesJson(rawJson)
            if (parsed.isEmpty()) throw IllegalArgumentException("Не найдено корректных API модулей в JSON")
            modules = if (replaceAll) {
                parsed
            } else {
                val existingIds = modules.map { it.id }.toSet()
                val deduplicated = parsed.map {
                    if (it.id in existingIds) it.copy(id = UUID.randomUUID().toString()) else it
                }
                deduplicated + modules
            }
            storage.saveModules(modules)
            parsed.size
        }
    }
}
