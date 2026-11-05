package com.spiderpulse.osint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spiderpulse.osint.ui.theme.SpiderLogo

@Composable
fun DisclaimerDialog(
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Mandatory acceptance */ },
        icon = {
            SpiderLogo(modifier = Modifier.size(48.dp))
        },
        title = {
            Text(
                text = "Предупреждение и ответственность",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ВСЕ ПОИСКОВЫЕ ЗАПРОСЫ ВЫПОЛНЯЮТСЯ НА ВАШ СТРАХ И РИСК",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Приложение SpiderPulse OSINT предназначено исключительно для легального анализа сетевой инфраструктуры, диагностики безопасности, проверки собственных ресурсов и образовательных OSINT-исследований.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Text(
                    text = "1. Полная ответственность пользователя:\n" +
                            "Всё, что вы ищете, какие сторонние API подключаете и как используете полученные данные (по IP-адресам или номерам телефонов), вы делаете исключительно на свой собственный страх и риск. Разработчик не несет ответственности за неправомерное использование информации или нарушение условий использования сторонних сервисов.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "2. 100% локальное хранение данных:\n" +
                            "Приложение не имеет собственных серверов, аналитики или телеметрии. Все ваши API-ключи, шаблоны запросов и история сканирований хранятся строго локально во внутренней памяти вашего устройства. Запросы отправляются напрямую с вашего телефона к выбранным вами API.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "3. Соблюдение законодательства:\n" +
                            "Продолжая работу, вы подтверждаете, что будете соблюдать применимое законодательство о защите персональных данных и компьютерной информации.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Принимаю (На свой страх и риск)", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}

@Composable
fun RiskNoticeBanner(
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onOpenDetails,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.GppMaybe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Все запросы — на ваш страх и риск",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "100% локальная база • Нажмите для условий",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
