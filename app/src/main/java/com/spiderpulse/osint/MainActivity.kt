package com.spiderpulse.osint

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spiderpulse.osint.ui.SpiderPulseViewModel
import com.spiderpulse.osint.ui.screens.ApiHubScreen
import com.spiderpulse.osint.ui.screens.DisclaimerDialog
import com.spiderpulse.osint.ui.screens.HistoryScreen
import com.spiderpulse.osint.ui.screens.ScannerScreen
import com.spiderpulse.osint.ui.theme.SpiderLogo
import com.spiderpulse.osint.ui.theme.SpiderPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }

            SpiderPulseTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SpiderPulseApp(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiderPulseApp(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: SpiderPulseViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewModel.screenProtectionEnabled) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (viewModel.screenProtectionEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    if (viewModel.showDisclaimerDialog) {
        DisclaimerDialog(
            onAccept = { viewModel.acceptDisclaimer() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpiderLogo(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SpiderPulse OSINT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Локальный агрегатор API",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showDisclaimerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Дисклеймер",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Переключить тему"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    label = { Text("Сканер", maxLines = 1) }
                )

                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text("${viewModel.modules.count { it.enabled }}/${viewModel.modules.size}")
                                }
                            }
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = null)
                        }
                    },
                    label = { Text("База API", maxLines = 1) }
                )

                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (viewModel.history.isNotEmpty()) {
                                    Badge { Text("${viewModel.history.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                        }
                    },
                    label = { Text("История", maxLines = 1) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> ScannerScreen(
                    viewModel = viewModel,
                    onNavigateToApis = { currentTab = 1 }
                )
                1 -> ApiHubScreen(
                    viewModel = viewModel
                )
                2 -> HistoryScreen(
                    viewModel = viewModel,
                    onSelectRecord = { record ->
                        viewModel.loadScanFromHistory(record)
                        currentTab = 0
                    }
                )
            }
        }
    }
}
