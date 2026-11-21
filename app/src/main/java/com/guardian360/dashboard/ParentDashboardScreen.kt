package com.guardian360.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.PastelAmber
import com.example.ui.theme.PastelBlue
import com.example.ui.theme.PastelEmerald
import com.example.ui.theme.PastelIndigo
import com.example.ui.theme.PastelMint
import com.example.ui.theme.PastelPurple
import com.example.ui.theme.PastelRose
import com.example.ui.theme.PastelSky
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.SapphireTrust
import com.example.ui.theme.SecureGreen
import com.guardian360.common.ActionExecutionState
import com.guardian360.common.CircularUsageRing
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.GuardianCard
import com.guardian360.common.ManagementLevel
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.parent.ParentNavDestination
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel

private data class QuickTileItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val lightBg: Color,
    val iconTint: Color,
    val destination: ParentNavDestination
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentDashboardScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    lastActionState: ActionExecutionState?,
    viewModel: ParentViewModel,
    adminNotice: String = "",
    onNavigate: (ParentNavDestination) -> Unit
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f || isSystemInDarkTheme() && MaterialTheme.colorScheme.surface.luminance() < 0.35f
    val activeDevice = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val activePolicy = bundle.policies.firstOrNull { it.childUid == activeDevice?.childUid }
    val pendingApprovals = bundle.approvals.filter { it.status == "PENDING" }
    val unackedAlerts = bundle.securityEvents.filter { !it.acknowledged }
    val latestLocation = bundle.locations.firstOrNull { it.childUid == activeDevice?.childUid }
    val conflicts = viewModel.computeConflicts(activePolicy, activeDevice, bundle.localInventory, context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (adminNotice.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = adminNotice,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (bundle.devices.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bundle.devices.forEach { dev ->
                    FilterChip(
                        selected = dev.childUid == activeDevice?.childUid,
                        onClick = { viewModel.selectChild(dev.childUid) },
                        label = { Text("${dev.childName} (${dev.deviceName})") }
                    )
                }
            }
        }

        if (activeDevice == null) {
            EmptyStatePanel(
                icon = Icons.Rounded.QrCode2,
                title = "Pair Your First Child Device",
                message = "No supervised child devices are enrolled yet. Open Devices & Pairing to generate a one-time QR code and link your child's Android device."
            )
            Button(
                onClick = { onNavigate(ParentNavDestination.DEVICES) },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("dash_pair_first_device")
            ) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Pairing QR Code")
            }
            OutlinedButton(
                onClick = { onNavigate(ParentNavDestination.ADMIN_PANEL) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dash_open_admin_panel")
            ) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Admin Panel & APK QR")
            }
            return@Column
        }

        // 1. All Safe & Secure / Alert Banner (Matching PDF Page 6 & Page 1 Dark Mode)
        val hasWarnings = activeDevice.deviceLocked || unackedAlerts.isNotEmpty()
        val bannerBg = when {
            activeDevice.deviceLocked -> if (isDark) Color(0xFF450A0A) else Color(0xFFFEE2E2)
            hasWarnings -> if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
            else -> if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7)
        }
        val bannerTint = when {
            activeDevice.deviceLocked -> DangerCrimson
            hasWarnings -> AlertAmber
            else -> SecureGreen
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = bannerBg,
            border = BorderStroke(1.dp, bannerTint.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = bannerTint,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (hasWarnings) Icons.Rounded.Warning else Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            activeDevice.deviceLocked -> "Device Remotely Locked"
                            hasWarnings -> "${unackedAlerts.size} Alert(s) Need Review"
                            else -> stringResource(R.string.all_safe_secure)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${activeDevice.deviceName} • ${ManagementLevel.fromString(activeDevice.managementLevel).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (activeDevice.deviceOnline) "Online" else "Offline",
                    color = if (activeDevice.deviceOnline) SecureGreen else DangerCrimson
                )
            }
        }

        // 2. Child Profile & Screen Time Ring Split Card (Matching PDF Page 6)
        val dailyLimit = (activePolicy?.dailyLimitMinutes ?: 120L) + (activePolicy?.bonusMinutesToday ?: 0L)
        val usedToday = activeDevice.dailyScreenTimeMinutes
        val usedFormatted = "${usedToday / 60}h ${usedToday % 60}m"
        val limitFormatted = "Limit ${dailyLimit / 60}h ${dailyLimit % 60}m"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GuardianCard(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = RoyalCobaltStart.copy(alpha = 0.14f),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ChildCare, contentDescription = null, tint = RoyalCobaltStart, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(activeDevice.childName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = activeDevice.deviceModel ?: activeDevice.deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val lastSeen = if (activeDevice.lastSeenEpochMs > 0L) {
                    DateUtils.getRelativeTimeSpanString(activeDevice.lastSeenEpochMs)
                } else "Sync pending"
                Text(
                    text = "Battery: ${if (activeDevice.batteryLevel >= 0) "${activeDevice.batteryLevel}%" else "—"}\nSeen: $lastSeen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastActionState != null) {
                    Spacer(Modifier.height(6.dp))
                    StatusPill(text = lastActionState.label, color = SapphireTrust)
                }
            }

            GuardianCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(ParentNavDestination.SCREEN_TIME) }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Usage Today",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    CircularUsageRing(
                        usedMinutes = usedToday,
                        limitMinutes = dailyLimit,
                        centerPrimaryText = usedFormatted,
                        centerSecondaryText = limitFormatted,
                        ringSize = 92.dp,
                        strokeWidth = 9.dp,
                        activeColor = if (usedToday >= dailyLimit) DangerCrimson else SecureGreen
                    )
                }
            }
        }

        // 3. Quick Emergency Lock / Strict Mode / Approvals Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { viewModel.toggleDeviceLock(activeDevice, !activeDevice.deviceLocked) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeDevice.deviceLocked) EmeraldShield else DangerCrimson
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("quick_action_lock_toggle")
            ) {
                Icon(if (activeDevice.deviceLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (activeDevice.deviceLocked) "Unlock" else "Lock Now", maxLines = 1)
            }
            OutlinedButton(
                onClick = { onNavigate(ParentNavDestination.EMERGENCY) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("quick_action_emergency_btn")
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = DangerCrimson, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Emergency", maxLines = 1)
            }
        }

        // 4. Pastel Quick-Control Grid (Matching PDF Page 6 & Dark Mode Grid)
        val tiles = listOf(
            QuickTileItem("Screen Time", "$usedToday / ${dailyLimit}m", Icons.Rounded.Schedule, PastelBlue, SapphireTrust, ParentNavDestination.SCREEN_TIME),
            QuickTileItem("App Management", "${activePolicy?.blockedPackages?.size ?: 0} blocked", Icons.Rounded.Games, PastelMint, EmeraldShield, ParentNavDestination.APPS_CATEGORIES),
            QuickTileItem("YouTube Control", activePolicy?.youtubeMode?.replace("_", " ")?.take(14) ?: "Shorts Filter", Icons.Rounded.PlayCircle, PastelRose, DangerCrimson, ParentNavDestination.YOUTUBE_CONTROLS),
            QuickTileItem("Location", latestLocation?.placeLabel ?: "Check-in status", Icons.Rounded.LocationOn, PastelPurple, Color(0xFF7C3AED), ParentNavDestination.LOCATION),
            QuickTileItem("Website Filter", "${activePolicy?.blockedDomains?.size ?: 0} blocked sites", Icons.Rounded.Language, PastelAmber, AlertAmber, ParentNavDestination.WEBSITE_MANAGEMENT),
            QuickTileItem("DNS Protection", if (activePolicy?.dnsFilteringEnabled != false) "Safe DNS Active" else "Paused", Icons.Rounded.Dns, PastelSky, Color(0xFF0284C7), ParentNavDestination.WEBSITE_MANAGEMENT),
            QuickTileItem("Activity Report", "Weekly ${activeDevice.weeklyScreenTimeMinutes}m", Icons.Rounded.Assessment, PastelEmerald, SecureGreen, ParentNavDestination.REPORTS),
            QuickTileItem("Security Center", "${unackedAlerts.size} alerts • Perms", Icons.Rounded.Security, PastelIndigo, Color(0xFF4338CA), ParentNavDestination.SECURITY),
            QuickTileItem("Approvals", "${pendingApprovals.size} pending", Icons.Rounded.CheckCircle, PastelMint, EmeraldShield, ParentNavDestination.APPROVALS),
            QuickTileItem("Devices & QR", "${bundle.devices.size} enrolled", Icons.Rounded.QrCode2, PastelBlue, RoyalCobaltStart, ParentNavDestination.DEVICES),
            QuickTileItem("Admin Panel", "Edit app & APK QR", Icons.Rounded.AdminPanelSettings, PastelPurple, Color(0xFF6D28D9), ParentNavDestination.ADMIN_PANEL),
            QuickTileItem("Free Full Shield", "All tools unlocked", Icons.Rounded.VerifiedUser, PastelMint, SecureGreen, ParentNavDestination.SETTINGS)
        )

        tiles.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { tile ->
                    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else tile.lightBg.copy(alpha = 0.72f)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigate(tile.destination) }
                            .testTag("dash_tile_${tile.destination.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.5f else 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = tile.iconTint.copy(alpha = if (isDark) 0.22f else 0.14f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(tile.icon, contentDescription = tile.title, tint = tile.iconTint, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tile.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = tile.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Conflict Detector Summary (if any)
        if (conflicts.isNotEmpty()) {
            GuardianCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, contentDescription = "Policy Conflicts", tint = AlertAmber)
                    Spacer(Modifier.width(8.dp))
                    Text("Policy Advisories (${conflicts.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                conflicts.take(3).forEach { c ->
                    Text("• ${c.title}: ${c.description}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 6. Important Alerts Banner (Matching PDF Page 6 Bottom Card)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (isDark) MaterialTheme.colorScheme.surface else Color(0xFFFFF1F2),
            border = BorderStroke(1.dp, DangerCrimson.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = DangerCrimson.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = DangerCrimson)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.important_alerts),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DangerCrimson
                    )
                    val alertLine = unackedAlerts.firstOrNull()?.title
                        ?: pendingApprovals.firstOrNull()?.let { "Pending: ${it.targetLabel ?: it.requestType}" }
                        ?: "YouTube Mode: ${activePolicy?.youtubeMode ?: "SHORTS_RESTRICTION"}"
                    Text(
                        text = alertLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(
                    onClick = { onNavigate(ParentNavDestination.NOTIFICATIONS) },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.view_details), maxLines = 1)
                }
            }
        }
    }
}
