package com.teknofest.bahiskalkani

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teknofest.bahiskalkani.service.ScreenReaderService
import com.teknofest.bahiskalkani.stats.BlockStats
import com.teknofest.bahiskalkani.ui.theme.BahisKalkaniTheme

class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BahisKalkaniTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        serviceEnabled = serviceEnabled,
                        blockedCount = BlockStats.blockedCount,
                        showAnywayCount = BlockStats.showAnywayCount,
                        onOpenSettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onCallYedam = {
                            // ACTION_DIAL: numarayı arama ekranına yazar, aramayı
                            // KULLANICI kendi başlatır — CALL_PHONE izni gerekmez,
                            // uygulama kullanıcı adına arama başlatmaz.
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:115")))
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = isAccessibilityServiceEnabled()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val self = ComponentName(this, ScreenReaderService::class.java)
        return enabledServices.split(':').any { ComponentName.unflattenFromString(it) == self }
    }
}

@Composable
fun MainScreen(
    serviceEnabled: Boolean,
    blockedCount: Int,
    showAnywayCount: Int,
    onOpenSettings: () -> Unit,
    onCallYedam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header()
        StatusCard(serviceEnabled = serviceEnabled, onOpenSettings = onOpenSettings)
        CounterCard(blockedCount = blockedCount)
        if (showAnywayCount >= BlockStats.YEDAM_THRESHOLD) {
            YedamCard(onCallYedam = onCallYedam)
        }
        HowItWorksCard()
        PrivacyNote()
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🛡", fontSize = 28.sp)
        }
        Column {
            Text(
                text = stringResource(R.string.main_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.main_slogan),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(serviceEnabled: Boolean, onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (serviceEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = if (serviceEnabled) "✅" else "⚠️", fontSize = 24.sp)
                Column {
                    Text(
                        text = stringResource(
                            if (serviceEnabled) R.string.main_status_on else R.string.main_status_off,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(
                            if (serviceEnabled) {
                                R.string.main_status_on_hint
                            } else {
                                R.string.main_status_off_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            // Aynı ayar ekranı hem açmaya hem kapatmaya götürür; kullanıcı
            // korumayı kapatmak istediğinde de uygulamadan tek dokunuşla
            // ulaşabilmeli (servisi koddan kapatmak mümkün değil).
            if (serviceEnabled) {
                Text(
                    text = stringResource(R.string.main_disable_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.main_disable_button))
                }
            } else {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.main_enable_button))
                }
            }
        }
    }
}

@Composable
private fun CounterCard(blockedCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.main_blocked_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = blockedCount.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Kullanıcı korumayı sık sık "yine de göster" ile aştığında (bkz.
 * BlockStats.YEDAM_THRESHOLD) gösterilen nazik yönlendirme. Yargılayıcı
 * değil, kapatılamaz bir uyarı değil — sadece bir kapı: arama tek
 * dokunuşla değil, kullanıcı numarayı görüp kendi aramasıyla başlar.
 */
@Composable
private fun YedamCard(onCallYedam: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "💚", fontSize = 20.sp)
                Text(
                    text = stringResource(R.string.main_yedam_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.main_yedam_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            OutlinedButton(
                onClick = onCallYedam,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.main_yedam_call))
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.main_how_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HowRow(emoji = "👁️", textRes = R.string.main_how_1)
            HowRow(emoji = "🧠", textRes = R.string.main_how_2)
            HowRow(emoji = "🚫", textRes = R.string.main_how_3)
        }
    }
}

@Composable
private fun HowRow(emoji: String, textRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(text = "🔒", fontSize = 14.sp)
        Text(
            text = stringResource(R.string.main_kvkk_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, name = "Kalkan aktif")
@Composable
fun MainScreenActivePreview() {
    BahisKalkaniTheme {
        MainScreen(
            serviceEnabled = true,
            blockedCount = 27,
            showAnywayCount = 0,
            onOpenSettings = {},
            onCallYedam = {},
        )
    }
}

@Preview(showBackground = true, name = "Kalkan kapalı")
@Composable
fun MainScreenDisabledPreview() {
    BahisKalkaniTheme {
        MainScreen(
            serviceEnabled = false,
            blockedCount = 0,
            showAnywayCount = 0,
            onOpenSettings = {},
            onCallYedam = {},
        )
    }
}

@Preview(showBackground = true, name = "YEDAM yönlendirmesi")
@Composable
fun MainScreenYedamPreview() {
    BahisKalkaniTheme {
        MainScreen(
            serviceEnabled = true,
            blockedCount = 42,
            showAnywayCount = BlockStats.YEDAM_THRESHOLD,
            onOpenSettings = {},
            onCallYedam = {},
        )
    }
}
