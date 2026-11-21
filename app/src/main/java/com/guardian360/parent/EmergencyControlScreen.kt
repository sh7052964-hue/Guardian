package com.guardian360.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddAlarm
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.DangerCrimsonBg
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.RoyalIndigoEnd
import com.example.ui.theme.SecureGreen
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.GuardianCard
import com.guardian360.common.PinKeypadDialog

@Composable
fun EmergencyControlScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel,
    onBackToDashboard: () -> Unit
) {
    val activeDevice = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val activePolicy = bundle.policies.firstOrNull { it.childUid == activeDevice?.childUid }
    var showOverridePinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Emergency Hero Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = DangerCrimsonBg.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, DangerCrimson.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = DangerCrimson,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "Emergency Alert",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.emergency_control),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DangerCrimson
                    )
                    Text(
                        text = stringResource(R.string.emergency_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (activeDevice == null) {
            EmptyStatePanel(
                icon = Icons.Rounded.Shield,
                title = "No Child Device Enrolled",
                message = "Pair a child device first to execute instant remote emergency controls."
            )
            return@Column
        }

        // 3 Action Tiles: Red Lock / Amber Pause / Green Override (With PIN)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EmergencyActionTile(
                title = if (activeDevice.deviceLocked) "Unlock Device" else stringResource(R.string.lock_immediately),
                icon = if (activeDevice.deviceLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                gradient = Brush.verticalGradient(listOf(Color(0xFFEF4444), Color(0xFFB91C1C))),
                modifier = Modifier.weight(1f).testTag("emergency_lock_tile"),
                onClick = { viewModel.toggleDeviceLock(activeDevice, !activeDevice.deviceLocked) }
            )
            EmergencyActionTile(
                title = if (activeDevice.strictSupervision) stringResource(R.string.pause_controls) else "Enable Strict Mode",
                icon = Icons.Rounded.PauseCircle,
                gradient = Brush.verticalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
                modifier = Modifier.weight(1f).testTag("emergency_pause_tile"),
                onClick = { viewModel.toggleStrictSupervision(activeDevice, !activeDevice.strictSupervision) }
            )
            EmergencyActionTile(
                title = stringResource(R.string.emergency_override_pin),
                icon = Icons.Rounded.VerifiedUser,
                gradient = Brush.verticalGradient(listOf(Color(0xFF22C55E), Color(0xFF15803D))),
                modifier = Modifier.weight(1f).testTag("emergency_override_tile"),
                onClick = { showOverridePinDialog = true }
            )
        }

        // Quick Actions Card
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = RoyalCobaltStart)
                Spacer(Modifier.width(8.dp))
                Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))

            EmergencyQuickRow(
                icon = Icons.Rounded.PlayCircle,
                iconBg = DangerCrimson.copy(alpha = 0.14f),
                iconTint = DangerCrimson,
                label = "Block YouTube Shorts Now",
                enabled = activePolicy != null,
                onClick = {
                    if (activePolicy != null) {
                        val nextBlocked = (activePolicy.blockedDomains + "shorts.youtube.com").distinct()
                        viewModel.savePolicyDirect(
                            activePolicy.copy(
                                youtubeMode = "STRICT_SHORTS_WEB_BLOCKING",
                                blockedDomains = nextBlocked
                            ),
                            activeDevice
                        )
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            EmergencyQuickRow(
                icon = Icons.Rounded.Language,
                iconBg = RoyalCobaltStart.copy(alpha = 0.14f),
                iconTint = RoyalCobaltStart,
                label = "Block Distracting Social Sites",
                enabled = activePolicy != null,
                onClick = {
                    if (activePolicy != null) {
                        val added = (activePolicy.blockedDomains + listOf("youtube.com/shorts", "instagram.com", "facebook.com", "tiktok.com")).distinct()
                        viewModel.savePolicyDirect(
                            activePolicy.copy(dnsFilteringEnabled = true, blockedDomains = added),
                            activeDevice
                        )
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            EmergencyQuickRow(
                icon = Icons.Rounded.Block,
                iconBg = AlertAmber.copy(alpha = 0.16f),
                iconTint = AlertAmber,
                label = "Require Approval for New Apps",
                enabled = activePolicy != null,
                onClick = {
                    if (activePolicy != null) {
                        viewModel.savePolicyDirect(
                            activePolicy.copy(playStoreApprovalRequired = true),
                            activeDevice
                        )
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            EmergencyQuickRow(
                icon = Icons.Rounded.AddAlarm,
                iconBg = SecureGreen.copy(alpha = 0.14f),
                iconTint = SecureGreen,
                label = "Grant +30 Min Emergency Allowance",
                enabled = activePolicy != null,
                onClick = {
                    if (activePolicy != null) {
                        viewModel.savePolicyDirect(
                            activePolicy.copy(bonusMinutesToday = activePolicy.bonusMinutesToday + 30L),
                            activeDevice
                        )
                    }
                }
            )
        }

        // Warning Pill
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = DangerCrimsonBg.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = DangerCrimson, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Use only in emergency situations! All commands are audit-logged.",
                    style = MaterialTheme.typography.labelMedium,
                    color = DangerCrimson,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Button(
            onClick = onBackToDashboard,
            colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("emergency_back_dashboard_btn")
        ) {
            Icon(Icons.Rounded.Home, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Back to Dashboard", fontWeight = FontWeight.Bold)
        }
    }

    if (showOverridePinDialog && activeDevice != null) {
        PinKeypadDialog(
            title = "Emergency Override",
            subtitle = "Enter Parent PIN to temporarily unlock ${activeDevice.childName}'s device and enable emergency bypass",
            storedPinHash = bundle.account?.pinHash,
            onVerified = {
                showOverridePinDialog = false
                if (activeDevice.deviceLocked) {
                    viewModel.toggleDeviceLock(activeDevice, false)
                }
                if (activePolicy != null) {
                    viewModel.savePolicyDirect(
                        activePolicy.copy(
                            emergencyBypassEnabled = true,
                            bonusMinutesToday = activePolicy.bonusMinutesToday + 30L
                        ),
                        activeDevice
                    )
                }
            },
            onDismiss = { showOverridePinDialog = false }
        )
    }
}

@Composable
private fun EmergencyActionTile(
    title: String,
    icon: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(126.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmergencyQuickRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconBg,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
