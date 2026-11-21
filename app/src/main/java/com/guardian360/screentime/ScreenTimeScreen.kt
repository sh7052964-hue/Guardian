package com.guardian360.screentime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.PastelMint
import com.example.ui.theme.PastelPurple
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.SapphireLight
import com.example.ui.theme.SapphireTrust
import com.example.ui.theme.SecureGreen
import com.guardian360.common.CircularUsageRing
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianCard
import com.guardian360.common.SectionHeader
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel
import com.guardian360.website.GuardianDnsFilterVpnService
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenTimeScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageTracker = remember(context) { UsageStatsTracker(context) }
    var permTick by remember { mutableIntStateOf(0) }
    var usageSettingsError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permTick++
                viewModel.refreshLocalDeviceInventory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasUsageAccess = remember(permTick) { usageTracker.hasUsageStatsPermission() }
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }

    if (device == null || policy == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStatePanel(
                title = "No Child Device Paired",
                message = "Pair a child device first to configure screen time limits, bedtime, school time, and bonus time."
            )
        }
        return
    }

    var selectedRangeTab by remember { mutableIntStateOf(0) } // 0 = Today, 1 = 7 Days, 2 = 30 Days Est
    var dailyLimit by remember(policy.childUid, policy.policyVersion) {
        mutableFloatStateOf(policy.dailyLimitMinutes.toFloat())
    }
    var bonusMinutes by remember(policy.childUid, policy.policyVersion) {
        mutableLongStateOf(policy.bonusMinutesToday)
    }
    var downtimeEnabled by remember(policy.childUid, policy.policyVersion) {
        mutableStateOf(policy.downtimeEnabled)
    }
    var emergencyBypass by remember(policy.childUid, policy.policyVersion) {
        mutableStateOf(policy.emergencyBypassEnabled)
    }
    var bedtimeStart by remember(policy.childUid, policy.policyVersion) { mutableStateOf(policy.bedtimeStart) }
    var bedtimeEnd by remember(policy.childUid, policy.policyVersion) { mutableStateOf(policy.bedtimeEnd) }
    var schoolStart by remember(policy.childUid, policy.policyVersion) { mutableStateOf(policy.schoolTimeStart) }
    var schoolEnd by remember(policy.childUid, policy.policyVersion) { mutableStateOf(policy.schoolTimeEnd) }

    val childUsage = bundle.appUsage.filter { it.childUid == device.childUid }
    val effectiveLimit = dailyLimit.toLong() + bonusMinutes
    val usedToday = device.dailyScreenTimeMinutes
    val remainingMin = (effectiveLimit - usedToday).coerceAtLeast(0L)
    val weeklyEst = device.weeklyScreenTimeMinutes
    val monthlyProjected = weeklyEst * 4L

    val displayedMinutes = when (selectedRangeTab) {
        0 -> usedToday
        1 -> weeklyEst
        else -> monthlyProjected
    }
    val displayedTarget = when (selectedRangeTab) {
        0 -> effectiveLimit
        1 -> effectiveLimit * 7L
        else -> effectiveLimit * 30L
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Screen Time",
            subtitle = "${device.childName} • Timezone: ${TimeZone.getDefault().id}"
        )

        if (!usageSettingsError.isNullOrBlank()) {
            ErrorBanner(message = usageSettingsError!!)
        }

        if (!hasUsageAccess) {
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = AlertAmber)
                    Spacer(Modifier.width(8.dp))
                    Text("Local Usage Access Disabled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Grant Android Usage Access on supervised devices so exact daily per-app durations appear automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        usageTracker.openUsageAccessSettings(context)
                            .onFailure { usageSettingsError = it.localizedMessage }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Usage Access Settings")
                }
            }
        }

        // Range Tabs (Today / 7 Days / 30 Days) matching PDF Page 5
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "7 Days", "30 Days Est.").forEachIndexed { idx, label ->
                FilterChip(
                    selected = selectedRangeTab == idx,
                    onClick = { selectedRangeTab = idx },
                    label = { Text(label) }
                )
            }
        }

        // Circular Usage Ring + Remaining + Bedtime Split Row (Matching PDF Page 5)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GuardianCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val primaryTxt = "${displayedMinutes / 60}h ${displayedMinutes % 60}m"
                    val secondaryTxt = "of ${displayedTarget / 60}h ${displayedTarget % 60}m"
                    CircularUsageRing(
                        usedMinutes = displayedMinutes,
                        limitMinutes = displayedTarget.coerceAtLeast(1L),
                        centerPrimaryText = primaryTxt,
                        centerSecondaryText = secondaryTxt,
                        ringSize = 122.dp,
                        strokeWidth = 12.dp,
                        activeColor = if (displayedMinutes > displayedTarget) DangerCrimson else SapphireTrust
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val remainingCardBg = if (isDark) MaterialTheme.colorScheme.secondaryContainer else PastelMint
                val remainingTitleColor = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF064E3B)
                val remainingSubColor = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f) else Color(0xFF065F46)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = remainingCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (isDark) MaterialTheme.colorScheme.secondary else SecureGreen, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${remainingMin / 60}h ${remainingMin % 60}m",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = remainingTitleColor
                            )
                        }
                        Text(
                            text = "Remaining today",
                            style = MaterialTheme.typography.labelSmall,
                            color = remainingSubColor
                        )
                    }
                }

                val bedtimeCardBg = if (isDark) MaterialTheme.colorScheme.primaryContainer else PastelPurple
                val bedtimeTitleColor = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF3B0764)
                val bedtimeSubColor = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else Color(0xFF4C1D95)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bedtimeCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF5B21B6), modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Bedtime",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = bedtimeTitleColor
                            )
                        }
                        Text(
                            text = if (downtimeEnabled) "$bedtimeStart – $bedtimeEnd" else "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = bedtimeSubColor
                        )
                    }
                }
            }
        }

        // Daily Usage Trend Mon-Sun Bar Chart (Honest breakdown from real daily & weekly telemetry)
        GuardianCard {
            Text("Daily Usage Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Today: ${usedToday}m • 7-Day Total: ${weeklyEst}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
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
            val avgPrior = ((weeklyEst - usedToday).coerceAtLeast(0L) / 6L)
            val barValues = List(7) { idx -> if (idx == todayDayIndex) usedToday else avgPrior }
            val maxVal = (barValues.maxOrNull() ?: 1L).coerceAtLeast(1L)

            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val slotW = size.width / 7f
                val barW = slotW * 0.48f
                barValues.forEachIndexed { idx, value ->
                    val ratio = value.toFloat() / maxVal.toFloat()
                    val h = (size.height - 12f) * ratio
                    val x = idx * slotW + (slotW - barW) / 2f
                    drawRoundRect(
                        color = if (idx == todayDayIndex) RoyalCobaltStart else SapphireLight.copy(alpha = 0.6f),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h.coerceAtLeast(6f)),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                dayLabels.forEachIndexed { idx, day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (idx == todayDayIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (idx == todayDayIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Daily Limit & Bonus Time
        GuardianCard {
            Text("Daily Limit: ${dailyLimit.toInt()} min (+ $bonusMinutes min Bonus)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Slider(
                value = dailyLimit,
                onValueChange = { dailyLimit = it },
                valueRange = 15f..480f,
                steps = 30,
                modifier = Modifier.testTag("daily_limit_slider")
            )
            Text("Grant Bonus Time Today", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L, 30L, 60L).forEach { addMin ->
                    AssistChip(
                        onClick = { bonusMinutes += addMin },
                        label = { Text("+$addMin min") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) }
                    )
                }
                AssistChip(
                    onClick = { bonusMinutes = 0L },
                    label = { Text("Reset Bonus") }
                )
            }
        }

        // Time Limit & Schedule Settings
        GuardianCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Time Limit & Bedtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Switch(checked = downtimeEnabled, onCheckedChange = { downtimeEnabled = it })
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = bedtimeStart,
                    onValueChange = { bedtimeStart = it.take(5) },
                    label = { Text("Bedtime Start") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = bedtimeEnd,
                    onValueChange = { bedtimeEnd = it.take(5) },
                    label = { Text("Bedtime End") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("School Time Schedule", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = schoolStart,
                    onValueChange = { schoolStart = it.take(5) },
                    label = { Text("School Start") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = schoolEnd,
                    onValueChange = { schoolEnd = it.take(5) },
                    label = { Text("School End") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Emergency App Exceptions", style = MaterialTheme.typography.titleSmall)
                    Text("Dialer & Emergency calls always remain accessible", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = emergencyBypass, onCheckedChange = { emergencyBypass = it })
            }
        }

        if (childUsage.isNotEmpty()) {
            GuardianCard {
                Text("Per-App Usage Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                childUsage.take(10).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.appLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(entry.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${entry.minutesToday}m / ${entry.minutesWeek}m wk", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val candidate = policy.copy(
                        dailyLimitMinutes = dailyLimit.toLong(),
                        bonusMinutesToday = bonusMinutes,
                        downtimeEnabled = downtimeEnabled,
                        emergencyBypassEnabled = emergencyBypass,
                        bedtimeStart = bedtimeStart,
                        bedtimeEnd = bedtimeEnd,
                        schoolTimeStart = schoolStart,
                        schoolTimeEnd = schoolEnd
                    )
                    viewModel.simulatePolicyChange(
                        candidate = candidate,
                        device = device,
                        apps = bundle.localInventory,
                        hasLocationPermission = false,
                        isVpnPrepared = GuardianDnsFilterVpnService.prepareIntent(context) == null
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(50.dp).testTag("simulate_screen_time_btn")
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save Limits")
            }

            Button(
                onClick = { viewModel.toggleDeviceLock(device, !device.deviceLocked) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (device.deviceLocked) SecureGreen else RoyalCobaltStart
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(if (device.deviceLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (device.deviceLocked) "Unlock Now" else "Lock Now")
            }
        }
    }
}
