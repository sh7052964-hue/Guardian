package com.guardian360.security

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.PastelMint
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.SecureGreen
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.ErrorBanner
import com.guardian360.common.EventSeverity
import com.guardian360.common.GuardianCard
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.deviceowner.DevicePolicyController
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel
import com.guardian360.screentime.UsageStatsTracker
import com.guardian360.website.GuardianDnsFilterVpnService

private data class PermissionAuditRow(
    val name: String,
    val status: String,
    val granted: Boolean,
    val parentAction: String,
    val requirement: String,
    val onGrantClick: (() -> Unit)? = null
)

@Composable
fun SecurityAndPermissionsScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageTracker = remember(context) { UsageStatsTracker(context) }
    val policyController = remember(context) { DevicePolicyController(context) }

    val enrollment by viewModel.enrollmentStatus.collectAsStateWithLifecycle()
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }

    var uninstallBlocked by remember(policy?.policyVersion) { mutableStateOf(policy?.uninstallBlocked ?: true) }
    var usbBlocked by remember(policy?.policyVersion) { mutableStateOf(policy?.usbDebuggingBlocked ?: true) }
    var unknownBlocked by remember(policy?.policyVersion) { mutableStateOf(policy?.unknownSourcesBlocked ?: true) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var permFeedback by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
                viewModel.refreshLocalDeviceInventory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        refreshTick++
        if (result.resultCode == Activity.RESULT_OK) {
            GuardianDnsFilterVpnService.startVpnService(context)
                .onSuccess { permFeedback = null }
                .onFailure { permFeedback = it.localizedMessage }
        } else {
            permFeedback = "VPN authorization declined."
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        refreshTick++
        if (grants.values.none { it }) {
            permFeedback = "Location permission denied. Open App Settings to allow Family Places check-ins."
        } else {
            permFeedback = null
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTick++
        permFeedback = if (granted) null else "Notification permission denied. Allow it in App Settings for instant alerts."
    }

    fun checkPerm(perm: String): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    val vpnPrepared = remember(refreshTick) { GuardianDnsFilterVpnService.prepareIntent(context) == null }
    val usageAccessGranted = remember(refreshTick) { usageTracker.hasUsageStatsPermission() }
    val adminActive = remember(refreshTick, enrollment) {
        enrollment.isDeviceAdminActive || policyController.isAdminActive()
    }
    val locationGranted = remember(refreshTick) {
        checkPerm(Manifest.permission.ACCESS_FINE_LOCATION) || checkPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val notifGranted = remember(refreshTick) {
        Build.VERSION.SDK_INT < 33 || checkPerm(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionAudit = remember(enrollment, vpnPrepared, usageAccessGranted, adminActive, locationGranted, notifGranted) {
        listOf(
            PermissionAuditRow(
                name = "Device Administrator",
                status = if (adminActive) "Granted" else "Pending",
                granted = adminActive,
                parentAction = "Enables remote lock & uninstall protection",
                requirement = "System Admin Consent",
                onGrantClick = if (adminActive) null else {
                    {
                        policyController.openActivateAdmin(context)
                            .onFailure { permFeedback = it.localizedMessage }
                    }
                }
            ),
            PermissionAuditRow(
                name = "Usage Access",
                status = if (usageAccessGranted) "Granted" else "Pending",
                granted = usageAccessGranted,
                parentAction = "Monitor real per-app screen time",
                requirement = "UsageStatsManager",
                onGrantClick = if (usageAccessGranted) null else {
                    {
                        usageTracker.openUsageAccessSettings(context)
                            .onFailure { permFeedback = it.localizedMessage }
                    }
                }
            ),
            PermissionAuditRow(
                name = "DNS Protection VPN",
                status = if (vpnPrepared) "Granted" else "Pending",
                granted = vpnPrepared,
                parentAction = "Local domain filtering & SafeSearch",
                requirement = "Android VpnService",
                onGrantClick = if (vpnPrepared) null else {
                    {
                        val prep = GuardianDnsFilterVpnService.prepareIntent(context)
                        if (prep != null) {
                            runCatching { vpnLauncher.launch(prep) }
                                .onFailure { permFeedback = "VPN authorization screen unavailable." }
                        } else {
                            GuardianDnsFilterVpnService.startVpnService(context)
                                .onFailure { permFeedback = it.localizedMessage }
                        }
                    }
                }
            ),
            PermissionAuditRow(
                name = "Location Permission",
                status = if (locationGranted) "Granted" else "Pending",
                granted = locationGranted,
                parentAction = "Opt-in Family Places check-ins",
                requirement = "Runtime Consent",
                onGrantClick = if (locationGranted) null else {
                    {
                        runCatching {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }.onFailure { usageTracker.openAppDetailsSettings(context) }
                    }
                }
            ),
            PermissionAuditRow(
                name = "Notifications",
                status = if (notifGranted) "Granted" else "Pending",
                granted = notifGranted,
                parentAction = "Approval & tamper push alerts",
                requirement = "Android 13+ Runtime",
                onGrantClick = if (notifGranted || Build.VERSION.SDK_INT < 33) null else {
                    {
                        runCatching { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            .onFailure { usageTracker.openAppDetailsSettings(context) }
                    }
                }
            ),
            PermissionAuditRow(
                name = "Photos & Media Picker",
                status = "Zero-Permission Picker",
                granted = true,
                parentAction = "Profile avatar selection",
                requirement = "Android Photo Picker"
            ),
            PermissionAuditRow(
                name = "Emergency Calls",
                status = "Always Allowed",
                granted = true,
                parentAction = "Tier 1 Emergency Bypass",
                requirement = "System Protected"
            )
        )
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bannerBg = if (isDark) MaterialTheme.colorScheme.secondaryContainer else PastelMint
    val bannerTitle = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF064E3B)
    val bannerSub = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f) else Color(0xFF065F46)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!permFeedback.isNullOrBlank()) {
            ErrorBanner(message = permFeedback!!)
        }

        // Security Center Active Status Banner (Matching PDF Page 2 Center)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = bannerBg,
            border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.35f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = SecureGreen,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.security_is_active),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = bannerTitle
                    )
                    Text(
                        text = "${enrollment.managementLevel.label} • Real Android DevicePolicyManager",
                        style = MaterialTheme.typography.bodySmall,
                        color = bannerSub
                    )
                }
            }
        }

        // Protection Features Card (Matching PDF Page 2 Center)
        GuardianCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Protection Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusPill(
                    text = if (enrollment.isDeviceOwner) "Device Owner" else "Standard",
                    color = if (enrollment.isDeviceOwner) EmeraldShield else AlertAmber
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(enrollment.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (policy != null && device != null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tamper & Uninstall Protection", style = MaterialTheme.typography.titleSmall)
                        Text("Requires Device Owner on managed devices", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uninstallBlocked, onCheckedChange = { uninstallBlocked = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("USB Debugging Restriction", style = MaterialTheme.typography.titleSmall)
                        Text("Restrict ADB debugging when Device Owner", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = usbBlocked, onCheckedChange = { usbBlocked = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Unknown Sources Block", style = MaterialTheme.typography.titleSmall)
                        Text("Prevent third-party APK sideloading", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = unknownBlocked, onCheckedChange = { unknownBlocked = it })
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val candidate = policy.copy(
                            uninstallBlocked = uninstallBlocked,
                            usbDebuggingBlocked = usbBlocked,
                            unknownSourcesBlocked = unknownBlocked
                        )
                        viewModel.simulatePolicyChange(
                            candidate,
                            device,
                            bundle.localInventory,
                            false,
                            vpnPrepared
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Simulate & Save Security Policy") }
            }
        }

        // Permissions Manager Card (Matching PDF Page 1 Center)
        GuardianCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.permissions_manager), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = {
                        usageTracker.openAppDetailsSettings(context)
                            .onFailure { permFeedback = it.localizedMessage }
                    }
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("App Settings")
                }
            }
            Spacer(Modifier.height(8.dp))
            permissionAudit.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(row.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${row.parentAction} • ${row.requirement}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (row.onGrantClick != null) {
                            TextButton(onClick = row.onGrantClick) {
                                Text("Grant")
                            }
                        }
                        StatusPill(
                            text = row.status,
                            color = if (row.granted) SecureGreen else AlertAmber
                        )
                    }
                }
            }
        }

        // Recent Security Events (Matching PDF Page 2 Center)
        SectionHeader(title = "Recent Security Events (${bundle.securityEvents.size})")
        if (bundle.securityEvents.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Rounded.GppGood,
                title = "No Threats or Tamper Events",
                message = "Device pairings, permission revocations, policy syncs, and lock actions appear here."
            )
        } else {
            bundle.securityEvents.take(20).forEach { evt ->
                val sev = EventSeverity.fromString(evt.severity)
                val sevColor = when (sev) {
                    EventSeverity.CRITICAL -> DangerCrimson
                    EventSeverity.WARNING -> AlertAmber
                    EventSeverity.INFO -> EmeraldShield
                }
                GuardianCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(evt.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusPill(text = sev.name, color = sevColor)
                    }
                    if (!evt.details.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(evt.details, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val ts = evt.createdAt?.toDate()?.time?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "Just now"
                        Text(ts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!evt.acknowledged) {
                            OutlinedButton(onClick = { viewModel.acknowledgeEvent(evt.id) }) {
                                Text("Acknowledge")
                            }
                        } else {
                            Text("Acknowledged", style = MaterialTheme.typography.labelSmall, color = EmeraldShield)
                        }
                    }
                }
            }
        }
    }
}
