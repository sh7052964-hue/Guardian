package com.guardian360.parent

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.GuardianMonoStyle
import com.example.ui.theme.PastelMint
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.SapphireTrust
import com.example.ui.theme.SecureGreen
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianCard
import com.guardian360.common.ManagementLevel
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.common.ThemePreference
import com.guardian360.common.safeStartActivity
import com.guardian360.firebase.FirebaseConfigValidator
import com.guardian360.pairing.ParentPairingPanel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

@Composable
fun DevicesAndPairingScreen(
    bundle: ParentOverviewBundle,
    viewModel: ParentViewModel,
    repository: com.guardian360.firebase.GuardianFirestoreRepository = com.guardian360.firebase.GuardianFirestoreRepository(LocalContext.current)
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "Managed Devices (${bundle.devices.size})",
                subtitle = "Enrollment level, tamper state, device removal & re-pairing."
            )
            if (bundle.devices.isEmpty()) {
                EmptyStatePanel(
                    icon = Icons.Rounded.Devices,
                    title = "No Enrolled Devices",
                    message = "Generate a pairing QR code below to enroll a child's Android device."
                )
            } else {
                bundle.devices.forEach { dev ->
                    GuardianCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${dev.childName} — ${dev.deviceName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(dev.deviceModel ?: "Android Device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                text = ManagementLevel.fromString(dev.managementLevel).label,
                                color = if (dev.deviceOnline) EmeraldShield else DangerCrimson
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.removeChildDevice(dev.childUid) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerCrimson)
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove Device & Require Re-Pairing")
                        }
                    }
                }
            }
        }
        ParentPairingPanel(
            familyId = viewModel.familyId,
            sessions = bundle.pairingSessions,
            repository = repository
        )
    }
}

@Composable
fun ReportsScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel? = null
) {
    val context = LocalContext.current
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }
    val childUsage = bundle.appUsage.filter { it.childUid == device?.childUid }.take(8)
    var rangeTab by remember { mutableIntStateOf(0) } // 0=Today, 1=7 Days, 2=30 Days Est
    var exportError by remember { mutableStateOf<String?>(null) }

    val donutPalette = listOf(
        RoyalCobaltStart,
        DangerCrimson,
        Color(0xFF0EA5E9),
        SecureGreen,
        AlertAmber
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Activity Report",
            subtitle = "Real child digital activity, app breakdown & immutable policy audit trail."
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "7 Days", "30 Days Est.").forEachIndexed { idx, label ->
                FilterChip(
                    selected = rangeTab == idx,
                    onClick = { rangeTab = idx },
                    label = { Text(label) }
                )
            }
        }

        val totalMinutes = when (rangeTab) {
            0 -> device?.dailyScreenTimeMinutes ?: childUsage.sumOf { it.minutesToday }
            1 -> device?.weeklyScreenTimeMinutes ?: childUsage.sumOf { it.minutesWeek }
            else -> (device?.weeklyScreenTimeMinutes ?: childUsage.sumOf { it.minutesWeek }) * 4L
        }

        if (childUsage.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Rounded.Assessment,
                title = "No Usage Telemetry Yet",
                message = "Once the child device grants Usage Access and syncs, the donut breakdown and app progress bars appear here."
            )
        } else {
            // Donut Chart + Top 4 Legend Card (Matching PDF Page 2 Left)
            val topSlices = childUsage.take(4)
            val sliceSum = topSlices.sumOf {
                when (rangeTab) {
                    0 -> it.minutesToday
                    1 -> it.minutesWeek
                    else -> it.minutesWeek * 4L
                }
            }.coerceAtLeast(1L)

            GuardianCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(128.dp)) {
                            var startAngle = -90f
                            topSlices.forEachIndexed { idx, app ->
                                val valMin = when (rangeTab) {
                                    0 -> app.minutesToday
                                    1 -> app.minutesWeek
                                    else -> app.minutesWeek * 4L
                                }
                                val sweep = (valMin.toFloat() / sliceSum.toFloat()) * 360f
                                drawArc(
                                    color = donutPalette[idx % donutPalette.size],
                                    startAngle = startAngle,
                                    sweepAngle = sweep.coerceAtLeast(4f),
                                    useCenter = false,
                                    style = Stroke(width = 24f, cap = StrokeCap.Butt)
                                )
                                startAngle += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${totalMinutes / 60}h ${totalMinutes % 60}m",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text("Total Usage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        topSlices.forEachIndexed { idx, app ->
                            val valMin = when (rangeTab) {
                                0 -> app.minutesToday
                                1 -> app.minutesWeek
                                else -> app.minutesWeek * 4L
                            }
                            val pct = ((valMin.toFloat() / sliceSum.toFloat()) * 100f).toInt()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(donutPalette[idx % donutPalette.size])
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = app.appLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Text(
                                    text = "${valMin}m ($pct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Top Apps Horizontal Bars Card (Matching PDF Page 2 Left)
            GuardianCard {
                Text("Top Apps Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val maxItemMin = childUsage.maxOf {
                    if (rangeTab == 0) it.minutesToday else it.minutesWeek
                }.coerceAtLeast(1L)

                childUsage.take(5).forEachIndexed { idx, item ->
                    val mins = if (rangeTab == 0) item.minutesToday else item.minutesWeek
                    val progress = (mins.toFloat() / maxItemMin.toFloat()).coerceIn(0f, 1f)
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.appLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("${mins}m", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = donutPalette[idx % donutPalette.size],
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50))
                        )
                    }
                }
            }
        }

        // Screen Time Trend Mon-Sun Line Chart (Matching PDF Page 2 Left)
        if (device != null) {
            val todayDayIndex = remember {
                when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 0
                    Calendar.TUESDAY -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY -> 3
                    Calendar.FRIDAY -> 4
                    Calendar.SATURDAY -> 5
                    else -> 6
                }
            }
            val avgPrior = ((device.weeklyScreenTimeMinutes - device.dailyScreenTimeMinutes).coerceAtLeast(0L) / 6L)
            val points = List(7) { idx -> if (idx == todayDayIndex) device.dailyScreenTimeMinutes else avgPrior }
            val maxY = (points.maxOrNull() ?: 1L).coerceAtLeast(1L)

            GuardianCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Screen Time Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusPill(
                        text = "${device.dailyScreenTimeMinutes / 60}h ${device.dailyScreenTimeMinutes % 60}m today",
                        color = SapphireTrust
                    )
                }
                Spacer(Modifier.height(10.dp))
                Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    val stepX = size.width / 6f
                    val coords = points.mapIndexed { i, v ->
                        val x = i * stepX
                        val y = size.height - 14f - ((v.toFloat() / maxY.toFloat()) * (size.height - 28f))
                        Offset(x, y)
                    }
                    for (i in 0 until coords.size - 1) {
                        drawLine(
                            color = RoyalCobaltStart,
                            start = coords[i],
                            end = coords[i + 1],
                            strokeWidth = 5f
                        )
                    }
                    coords.forEachIndexed { idx, pt ->
                        drawCircle(
                            color = if (idx == todayDayIndex) SecureGreen else RoyalCobaltStart,
                            radius = if (idx == todayDayIndex) 9f else 6f,
                            center = pt
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Limit Compliance Status Banner (Matching PDF Page 2 Left Bottom)
        val dailyLimit = (policy?.dailyLimitMinutes ?: 120L) + (policy?.bonusMinutesToday ?: 0L)
        val withinLimit = (device?.dailyScreenTimeMinutes ?: 0L) <= dailyLimit
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val complianceBg = when {
            withinLimit && isDark -> MaterialTheme.colorScheme.secondaryContainer
            withinLimit -> PastelMint
            isDark -> MaterialTheme.colorScheme.errorContainer
            else -> Color(0xFFFEE2E2)
        }
        val complianceTitleColor = when {
            withinLimit && isDark -> MaterialTheme.colorScheme.onSecondaryContainer
            withinLimit -> Color(0xFF064E3B)
            isDark -> MaterialTheme.colorScheme.onErrorContainer
            else -> DangerCrimson
        }
        val complianceSubColor = when {
            isDark -> complianceTitleColor.copy(alpha = 0.85f)
            else -> Color(0xFF334155)
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = complianceBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (withinLimit) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (withinLimit) (if (isDark) MaterialTheme.colorScheme.secondary else SecureGreen) else DangerCrimson
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (withinLimit) "Usage is within limit" else "Daily Screen Time Limit Exceeded",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = complianceTitleColor
                    )
                    Text(
                        text = "Allowance: ${dailyLimit}m today • Audit logs recorded: ${bundle.auditLogs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = complianceSubColor
                    )
                }
            }
        }

        // Immutable Audit Trail Summary
        if (bundle.auditLogs.isNotEmpty()) {
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = RoyalCobaltStart)
                    Spacer(Modifier.width(8.dp))
                    Text("Recent Immutable Audit Logs (${bundle.auditLogs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                bundle.auditLogs.take(5).forEach { log ->
                    val ts = log.createdAt?.toDate()?.time?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "Just now"
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(log.action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(log.details ?: log.targetPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(ts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (exportError != null) {
            ErrorBanner(message = exportError!!)
        }

        Button(
            onClick = {
                exportError = null
                val json = JSONObject().apply {
                    put("familyId", bundle.account?.familyId ?: "")
                    put("deviceCount", bundle.devices.size)
                    put("devices", JSONArray(bundle.devices.map { d ->
                        JSONObject().apply {
                            put("childName", d.childName)
                            put("deviceName", d.deviceName)
                            put("dailyScreenTimeMinutes", d.dailyScreenTimeMinutes)
                            put("managementLevel", d.managementLevel)
                        }
                    }))
                    put("securityEventsCount", bundle.securityEvents.size)
                    put("auditLogsCount", bundle.auditLogs.size)
                }.toString(2)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "Guardian360 Family Report")
                    putExtra(Intent.EXTRA_TEXT, json)
                }
                context.safeStartActivity(Intent.createChooser(share, "Export Guardian360 Report"))
                    .onFailure { exportError = it.localizedMessage ?: "No sharing app available on this device." }
            },
            colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Export Family Report (JSON)")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentSettingsScreen(
    bundle: ParentOverviewBundle,
    languageCode: String,
    themePreference: ThemePreference,
    viewModel: ParentViewModel,
    onSelectLanguage: (String) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onOpenAdminPanel: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val account = bundle.account
    val firebaseReport = remember(account?.userId) { FirebaseConfigValidator.inspect(context) }

    var showPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFirebaseDiagnostics by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "General Settings",
            subtitle = "Theme, Language (English / Hindi / Assamese), Security PIN & Firebase Diagnostics."
        )

        // Parent Authority Profile
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = RoyalCobaltStart.copy(alpha = 0.14f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = RoyalCobaltStart)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(account?.displayName ?: "Guardian Parent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(account?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Session Version: v${account?.sessionVersion ?: 1}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Theme & Language Card (Matching PDF Page 1 Center)
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = RoyalCobaltStart)
                Spacer(Modifier.width(8.dp))
                Text("Theme Mode (Light / Dark)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { pref ->
                    FilterChip(
                        selected = themePreference == pref,
                        onClick = { onSelectTheme(pref) },
                        label = { Text(pref.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, contentDescription = null, tint = EmeraldShield)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.language_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "en" to stringResource(R.string.lang_english),
                    "hi" to stringResource(R.string.lang_hindi),
                    "as" to stringResource(R.string.lang_assamese)
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = languageCode == code,
                        onClick = { onSelectLanguage(code) },
                        label = { Text(label) }
                    )
                }
            }
        }

        // Security PIN, Biometric, 2FA, Session Management
        if (account != null) {
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = RoyalCobaltStart)
                    Spacer(Modifier.width(8.dp))
                    Text("Authentication & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Biometric Unlock", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = account.biometricEnabled,
                        onCheckedChange = { viewModel.updateAccountSecurityFlags(account, it, account.twoFactorEnabled) }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Two-Factor Policy Verification", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = account.twoFactorEnabled,
                        onCheckedChange = { viewModel.updateAccountSecurityFlags(account, account.biometricEnabled, it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showPinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Change Security PIN")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.logoutAllDevices() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Logout from All Devices (Rotate Session)")
                }
            }
        }

        // Admin Panel & QR Installer Entry Card
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = RoyalCobaltStart)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Admin Panel & QR Installer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Edit app settings, set/change admin access code (default: admin), export APK & scan real install QR.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onOpenAdminPanel,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_open_admin_panel_btn")
            ) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Admin Panel")
            }
        }

        // Firebase Configuration & Verification Diagnostics Report (Honest Backend Status)
        GuardianCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = SecureGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Firebase Backend Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { showFirebaseDiagnostics = !showFirebaseDiagnostics }) {
                    Text(if (showFirebaseDiagnostics) "Hide" else "Inspect")
                }
            }
            Text(
                text = "Project: ${firebaseReport.projectId.ifBlank { "Unconfigured" }} • Audit logs: ${bundle.auditLogs.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showFirebaseDiagnostics) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                firebaseReport.checks.forEach { check ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(check.serviceName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(
                            text = if (check.ready) "Verified" else "Action Req.",
                            color = if (check.ready) SecureGreen else AlertAmber
                        )
                    }
                }
            }
        }

        // About & Help Card (Matching PDF Page 1 Bottom Right)
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = RoyalCobaltStart)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Guardian360", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Protect • Monitor • Guide • v1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAboutDialog = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.HelpOutline, contentDescription = null, tint = RoyalCobaltStart, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("About, Help & Privacy Policy", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_logout_btn")
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Logout")
            }
        }
    }

    if (showPinDialog && account != null) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Update Security PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("New 4-6 Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length >= 4) {
                            viewModel.updateParentPin(account, newPin)
                            newPin = ""
                            showPinDialog = false
                        }
                    }
                ) { Text("Save PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = { Text("About Guardian360") },
            text = {
                Text(
                    "Guardian360 v1.0\nEnterprise Family Safety & Android Device Policy Controller.\n\n• Zero-Trust Firestore Security Rules & Audit Logs\n• SHA-256 Hashed One-Time Pairing Sessions\n• Room Offline Policy Cache & SyncQueue\n• Genuine DevicePolicyManager, UsageStatsManager, FusedLocation & VpnService DNS Filtering.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }
}
