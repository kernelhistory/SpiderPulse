package com.spiderpulse.osint.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spiderpulse.osint.model.ApiModule
import com.spiderpulse.osint.model.AuthType
import com.spiderpulse.osint.model.HttpMethod
import com.spiderpulse.osint.model.TargetCategory
import com.spiderpulse.osint.ui.SpiderPulseViewModel
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiHubScreen(
    viewModel: SpiderPulseViewModel
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<TargetCategory?>(null) }
    var onlyEnabled by remember { mutableStateOf(false) }

    var editingModule by remember { mutableStateOf<ApiModule?>(null) }
    var showBulkJsonDialog by remember { mutableStateOf(false) }

    val filteredModules = remember(viewModel.modules, searchQuery, categoryFilter, onlyEnabled) {
        viewModel.modules.filter { m ->
            val matchesCat = categoryFilter == null || m.category == categoryFilter
            val matchesEnabled = !onlyEnabled || m.enabled
            val matchesQuery = searchQuery.isBlank() ||
                    m.name.contains(searchQuery, ignoreCase = true) ||
                    m.description.contains(searchQuery, ignoreCase = true) ||
                    m.urlTemplate.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesEnabled && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Actions Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Конструктор и База API (${viewModel.modules.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Добавляйте свои API, ключи, заголовки и JSON-списки (хранение AES-256-GCM)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                editingModule = ApiModule(
                                    id = UUID.randomUUID().toString(),
                                    name = "",
                                    description = "",
                                    category = categoryFilter ?: TargetCategory.IP,
                                    urlTemplate = "https://api.example.com/lookup?target={target}",
                                    method = HttpMethod.GET,
                                    authType = AuthType.NONE,
                                    enabled = true,
                                    isBuiltIn = false
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Новый API",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { showBulkJsonDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "JSON Импорт",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        label = {
                            Text(
                                text = "Поиск API по названию или URL",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить")
                                }
                            }
                        }
                    )

                    // Category & state filters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null },
                            label = { Text("Все (${viewModel.modules.size})", maxLines = 1, softWrap = false) }
                        )
                        FilterChip(
                            selected = categoryFilter == TargetCategory.IP,
                            onClick = { categoryFilter = TargetCategory.IP },
                            label = {
                                Text(
                                    "IP (${viewModel.modules.count { it.category == TargetCategory.IP }})",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                        FilterChip(
                            selected = categoryFilter == TargetCategory.PHONE,
                            onClick = { categoryFilter = TargetCategory.PHONE },
                            label = {
                                Text(
                                    "Телефон (${viewModel.modules.count { it.category == TargetCategory.PHONE }})",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                        FilterChip(
                            selected = categoryFilter == TargetCategory.UNIVERSAL,
                            onClick = { categoryFilter = TargetCategory.UNIVERSAL },
                            label = {
                                Text(
                                    "Универсальные (${viewModel.modules.count { it.category == TargetCategory.UNIVERSAL }})",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                        FilterChip(
                            selected = onlyEnabled,
                            onClick = { onlyEnabled = !onlyEnabled },
                            label = { Text("Только включенные", maxLines = 1, softWrap = false) }
                        )
                    }

                    // Batch controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { viewModel.enableAllModules(categoryFilter) },
                            label = { Text("Вкл все", maxLines = 1, softWrap = false) }
                        )
                        AssistChip(
                            onClick = { viewModel.disableAllModules(categoryFilter) },
                            label = { Text("Выкл все", maxLines = 1, softWrap = false) }
                        )
                        AssistChip(
                            onClick = { viewModel.resetDefaultModules() },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Сброс стандартных", maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        }

        items(filteredModules, key = { it.id }) { module ->
            ApiModuleCard(
                module = module,
                onToggle = { enabled -> viewModel.toggleModuleEnabled(module.id, enabled) },
                onEdit = { editingModule = module },
                onDuplicate = { viewModel.duplicateModule(module) },
                onDelete = { viewModel.deleteModule(module.id) }
            )
        }
    }

    editingModule?.let { mod ->
        ApiModuleEditorDialog(
            initial = mod,
            onDismiss = { editingModule = null },
            onSave = { updated ->
                viewModel.saveOrUpdateModule(updated)
                editingModule = null
            }
        )
    }

    if (showBulkJsonDialog) {
        BulkJsonImportExportDialog(
            onBuildExportJson = { includeKeys -> viewModel.exportModulesJson(includeApiKeys = includeKeys) },
            onDismiss = { showBulkJsonDialog = false },
            onImport = { jsonText, replaceAll ->
                val res = viewModel.importModulesJson(jsonText, replaceAll)
                res.onSuccess { count ->
                    Toast.makeText(
                        context,
                        "Успешно импортировано модулей API: $count",
                        Toast.LENGTH_LONG
                    ).show()
                    showBulkJsonDialog = false
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        "Ошибка JSON: ${err.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun ApiModuleCard(
    module: ApiModule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = when (module.category) {
                                TargetCategory.IP -> MaterialTheme.colorScheme.primaryContainer
                                TargetCategory.PHONE -> MaterialTheme.colorScheme.tertiaryContainer
                                TargetCategory.UNIVERSAL -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = module.category.badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = module.method.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = module.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = module.enabled,
                    onCheckedChange = onToggle
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = module.urlTemplate,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (module.apiKey.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = when {
                            module.authType == AuthType.NONE -> "Открытый API"
                            module.apiKey.isNotBlank() -> "Ключ зашифрован (${module.authType.name})"
                            else -> "Нужен ключ (${module.authType.name})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (module.apiKey.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Настроить API")
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Дублировать")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApiModuleEditorDialog(
    initial: ApiModule,
    onDismiss: () -> Unit,
    onSave: (ApiModule) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var category by remember { mutableStateOf(initial.category) }
    var urlTemplate by remember { mutableStateOf(initial.urlTemplate) }
    var method by remember { mutableStateOf(initial.method) }
    var authType by remember { mutableStateOf(initial.authType) }
    var authParamName by remember { mutableStateOf(initial.authParamName) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var showApiKeyPlain by remember { mutableStateOf(false) }
    var headersRaw by remember { mutableStateOf(initial.headersRaw) }
    var postBodyTemplate by remember { mutableStateOf(initial.postBodyTemplate) }
    var timeoutStr by remember { mutableStateOf(initial.timeoutMs.toString()) }
    var enabled by remember { mutableStateOf(initial.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial.name.isBlank()) "Добавить новый OSINT API" else "Настройка API модуля",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название сервиса / API *") },
                    placeholder = { Text("Напр.: My Custom Shodan / Numverify") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    placeholder = { Text("Что возвращает этот API...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Категория цели:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.titleRu) }
                        )
                    }
                }

                Text(
                    text = "HTTP-метод:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HttpMethod.entries.forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it },
                    label = { Text("URL-шаблон запроса (HTTPS) *") },
                    supportingText = {
                        Text("Макросы: {target} — цель, {target_clean} — только цифры номера, {api_key} — ключ")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    text = "Авторизация и API-ключ:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AuthType.entries.forEach { a ->
                        FilterChip(
                            selected = authType == a,
                            onClick = { authType = a },
                            label = { Text(a.name) }
                        )
                    }
                }
                Text(
                    text = authType.labelRu + " — " + authType.hintRu,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (authType == AuthType.QUERY_PARAM || authType == AuthType.HEADER) {
                    OutlinedTextField(
                        value = authParamName,
                        onValueChange = { authParamName = it },
                        label = {
                            Text(
                                if (authType == AuthType.QUERY_PARAM) "Имя GET-параметра (key, token)"
                                else "Имя заголовка (x-apikey, Key)"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (authType != AuthType.NONE) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Ключ / Токен (AES-256-GCM)") },
                        placeholder = { Text("Вставьте ваш ключ доступа") },
                        singleLine = true,
                        visualTransformation = if (showApiKeyPlain) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showApiKeyPlain = !showApiKeyPlain }) {
                                Icon(
                                    imageVector = if (showApiKeyPlain) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = "Показать/скрыть ключ"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = headersRaw,
                    onValueChange = { headersRaw = it },
                    label = { Text("Доп. заголовки (Header: Value)") },
                    placeholder = { Text("Accept: application/json\nX-Custom-Token: {api_key}") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (method == HttpMethod.POST) {
                    OutlinedTextField(
                        value = postBodyTemplate,
                        onValueChange = { postBodyTemplate = it },
                        label = { Text("POST Body шаблон (JSON / Form)") },
                        placeholder = { Text("{\"query\": \"{target}\"}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = timeoutStr,
                    onValueChange = { timeoutStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Таймаут ожидания (мс)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim().ifEmpty { "Custom OSINT API" }
                    val cleanUrl = urlTemplate.trim()
                    if (cleanUrl.isNotEmpty()) {
                        onSave(
                            initial.copy(
                                name = cleanName,
                                description = description.trim().ifEmpty { "Пользовательский модуль ($category)" },
                                category = category,
                                urlTemplate = cleanUrl,
                                method = method,
                                authType = authType,
                                authParamName = authParamName.trim(),
                                apiKey = apiKey.trim(),
                                headersRaw = headersRaw.trim(),
                                postBodyTemplate = postBodyTemplate,
                                timeoutMs = (timeoutStr.toIntOrNull() ?: 10000).coerceIn(1000, 60000),
                                enabled = enabled
                            )
                        )
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun BulkJsonImportExportDialog(
    onBuildExportJson: (Boolean) -> String,
    onDismiss: () -> Unit,
    onImport: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var includeSecretsInExport by remember { mutableStateOf(false) }
    var jsonText by remember { mutableStateOf(onBuildExportJson(false)) }
    var replaceAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Пакетный JSON Импорт / Экспорт",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "По умолчанию секретные API-ключи удаляются при экспорте для безопасной публикации или обмена конфигурацией.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SpiderPulse APIs", jsonText)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && includeSecretsInExport) {
                                clip.description.extras = PersistableBundle().apply {
                                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                            }
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "JSON всех API скопирован в буфер", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Копировать", maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!pasted.isNullOrBlank()) {
                                jsonText = pasted
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Вставить", maxLines = 1, softWrap = false)
                    }
                }

                FilterChip(
                    selected = includeSecretsInExport,
                    onClick = {
                        includeSecretsInExport = !includeSecretsInExport
                        jsonText = onBuildExportJson(includeSecretsInExport)
                    },
                    label = {
                        Text(
                            if (includeSecretsInExport) "Экспорт с API-ключами (Приватно)"
                            else "Экспорт без API-ключей (Безопасно)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )

                FilterChip(
                    selected = replaceAll,
                    onClick = { replaceAll = !replaceAll },
                    label = {
                        Text(
                            if (replaceAll) "При импорте: заменить текущие API"
                            else "При импорте: добавить к существующим",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("JSON список API модулей") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onImport(jsonText, replaceAll) }) {
                Text("Импортировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
