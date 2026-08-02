package com.antiscroll.mobile

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiscroll.mobile.accessibility.AccessibilityServiceStatus
import com.antiscroll.mobile.data.PlatformLock
import com.antiscroll.mobile.data.PunitiveLockManager
import com.antiscroll.mobile.data.SettingsRepository
import com.antiscroll.mobile.ui.theme.AntiScrollTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(this) }
    private val punitiveLockManager by lazy { PunitiveLockManager(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceEnabled by mutableStateOf(false)
    private var nowMillis by mutableLongStateOf(System.currentTimeMillis())
    private val refreshClock = object : Runnable {
        override fun run() {
            nowMillis = System.currentTimeMillis()
            mainHandler.postDelayed(this, LOCK_REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AntiScrollTheme {
                AntiScrollApp(
                    serviceEnabled = serviceEnabled,
                    nowMillis = nowMillis,
                    settingsRepository = settingsRepository,
                    punitiveLockManager = punitiveLockManager,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled = AccessibilityServiceStatus.isEnabled(this)
        nowMillis = System.currentTimeMillis()
        mainHandler.removeCallbacks(refreshClock)
        mainHandler.postDelayed(refreshClock, LOCK_REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshClock)
        super.onPause()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private companion object {
        const val LOCK_REFRESH_INTERVAL_MS = 1_000L
    }
}

@Composable
private fun AntiScrollApp(
    serviceEnabled: Boolean,
    nowMillis: Long,
    settingsRepository: SettingsRepository,
    punitiveLockManager: PunitiveLockManager,
    onOpenAccessibilitySettings: () -> Unit,
) {
    var youtubeEnabled by rememberSaveable {
        mutableStateOf(settingsRepository.youtubeBlockingEnabled)
    }
    var instagramEnabled by rememberSaveable {
        mutableStateOf(settingsRepository.instagramBlockingEnabled)
    }
    var punitiveModeEnabled by rememberSaveable {
        mutableStateOf(settingsRepository.punitiveModeEnabled)
    }
    var lockRefreshToken by remember { mutableIntStateOf(0) }
    var showDisclosure by remember { mutableStateOf(false) }

    val youtubeLock = remember(nowMillis, lockRefreshToken) {
        punitiveLockManager.activeLock(SettingsRepository.YOUTUBE_PACKAGE)
    }
    val instagramLock = remember(nowMillis, lockRefreshToken) {
        punitiveLockManager.activeLock(SettingsRepository.INSTAGRAM_PACKAGE)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header()

            SectionLabel(text = stringResource(R.string.service_section_title))
            ProtectionCard(
                serviceEnabled = serviceEnabled,
                onClick = {
                    if (serviceEnabled) {
                        onOpenAccessibilitySettings()
                    } else {
                        showDisclosure = true
                    }
                },
            )

            SectionLabel(text = stringResource(R.string.blocked_content_title))
            SettingsCard(
                youtubeEnabled = youtubeEnabled,
                instagramEnabled = instagramEnabled,
                onYoutubeChanged = { enabled ->
                    youtubeEnabled = enabled
                    settingsRepository.youtubeBlockingEnabled = enabled
                    if (!enabled) {
                        punitiveLockManager.clear(SettingsRepository.YOUTUBE_PACKAGE)
                        lockRefreshToken += 1
                    }
                },
                onInstagramChanged = { enabled ->
                    instagramEnabled = enabled
                    settingsRepository.instagramBlockingEnabled = enabled
                    if (!enabled) {
                        punitiveLockManager.clear(SettingsRepository.INSTAGRAM_PACKAGE)
                        lockRefreshToken += 1
                    }
                },
            )

            SectionLabel(text = stringResource(R.string.punitive_section_title))
            PunitiveModeCard(
                enabled = punitiveModeEnabled,
                youtubeLock = youtubeLock,
                instagramLock = instagramLock,
                onEnabledChanged = { enabled ->
                    punitiveModeEnabled = enabled
                    settingsRepository.punitiveModeEnabled = enabled
                    if (!enabled) punitiveLockManager.clearAll()
                    lockRefreshToken += 1
                },
                onUnlockYoutube = {
                    punitiveLockManager.clear(SettingsRepository.YOUTUBE_PACKAGE)
                    lockRefreshToken += 1
                },
                onUnlockInstagram = {
                    punitiveLockManager.clear(SettingsRepository.INSTAGRAM_PACKAGE)
                    lockRefreshToken += 1
                },
            )

            InformationCard(punitiveModeEnabled = punitiveModeEnabled)
        }
    }

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onDismiss = { showDisclosure = false },
            onConfirm = {
                showDisclosure = false
                onOpenAccessibilitySettings()
            },
        )
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✦",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Text(
            text = stringResource(R.string.app_tagline),
            modifier = Modifier.padding(start = 48.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
    )
}

@Composable
private fun ProtectionCard(
    serviceEnabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                StatusDot(active = serviceEnabled)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (serviceEnabled) R.string.service_active
                            else R.string.service_inactive,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (serviceEnabled) R.string.service_active_description
                            else R.string.service_inactive_description,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (serviceEnabled) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.manage_accessibility_settings))
                }
            } else {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.open_accessibility_settings))
                }
            }
        }
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Canvas(
        modifier = Modifier
            .padding(top = 4.dp)
            .size(12.dp),
    ) {
        drawCircle(color = color)
    }
}

@Composable
private fun SettingsCard(
    youtubeEnabled: Boolean,
    instagramEnabled: Boolean,
    onYoutubeChanged: (Boolean) -> Unit,
    onInstagramChanged: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            PlatformSetting(
                accent = Color(0xFFE53935),
                title = stringResource(R.string.youtube_shorts_title),
                description = stringResource(R.string.youtube_shorts_description),
                checked = youtubeEnabled,
                onCheckedChange = onYoutubeChanged,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
            PlatformSetting(
                accent = Color(0xFFC13584),
                title = stringResource(R.string.instagram_reels_title),
                description = stringResource(R.string.instagram_reels_description),
                checked = instagramEnabled,
                onCheckedChange = onInstagramChanged,
            )
        }
    }
}

@Composable
private fun PlatformSetting(
    accent: Color,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = accent)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PunitiveModeCard(
    enabled: Boolean,
    youtubeLock: PlatformLock?,
    instagramLock: PlatformLock?,
    onEnabledChanged: (Boolean) -> Unit,
    onUnlockYoutube: () -> Unit,
    onUnlockInstagram: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.punitive_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.punitive_mode_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }

            if (enabled) {
                Text(
                    text = stringResource(R.string.punitive_mode_warning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (youtubeLock != null || instagramLock != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Text(
                    text = stringResource(R.string.active_locks_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                youtubeLock?.let { lock ->
                    ActiveLockRow(
                        platformName = stringResource(R.string.youtube_name),
                        remainingMillis = lock.remainingMillis,
                        onUnlock = onUnlockYoutube,
                    )
                }
                instagramLock?.let { lock ->
                    ActiveLockRow(
                        platformName = stringResource(R.string.instagram_name),
                        remainingMillis = lock.remainingMillis,
                        onUnlock = onUnlockInstagram,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveLockRow(
    platformName: String,
    remainingMillis: Long,
    onUnlock: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platformName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.lock_remaining,
                    formatRemainingTime(remainingMillis),
                ),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedButton(onClick = onUnlock) {
            Text(stringResource(R.string.unlock_platform))
        }
    }
}

private fun formatRemainingTime(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis + 999L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun InformationCard(punitiveModeEnabled: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.how_it_works_title),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    if (punitiveModeEnabled) R.string.how_it_works_punitive_body
                    else R.string.how_it_works_body,
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.privacy_badge),
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(50),
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AccessibilityDisclosureDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.disclosure_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(stringResource(R.string.disclosure_body))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.agree_and_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
