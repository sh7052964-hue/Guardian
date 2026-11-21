package com.guardian360.child

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.EmeraldShield
import com.guardian360.common.AdminPanelConfig
import com.guardian360.common.ApprovalRequestType
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianCard
import com.guardian360.common.GuardianTopHeaderBar
import com.guardian360.common.LoadingPanel
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.common.ThemePreference
import com.guardian360.common.UiState
import com.guardian360.database.CachedAppInventoryEntity
import com.guardian360.di.AppContainer
import com.guardian360.pairing.ChildPairingCard
import com.guardian360.website.GuardianDnsFilterVpnService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChildDashboardScreen(
    currentUserId: String,
    languageCode: String,
    themePreference: ThemePreference,
    container: AppContainer,
    onSelectLanguage: (String) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onSignOut: () -> Unit
) {
    val childVm: ChildViewModel = viewModel(
        key = "child_vm_$currentUserId",
        factory = viewModelFactory {
            initializer {
                ChildViewModel(
                    repository = container.firestoreRepository,
                    roomDb = container.roomDatabase,
                    devicePolicyController = container.devicePolicyController,
                    usageStatsTracker = container.usageStatsTracker,
                    locationTracker = container.locationTracker,
                    preferencesManager = container.preferencesManager,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val state by childVm.dashboardState.collectAsStateWithLifecycle()
    val statusMessage by childVm.statusMessage.collectAsStateWithLifecycle()
    val enrollment by childVm.enrollmentStatus.collectAsStateWithLifecycle()
    val lastReport by childVm.lastApplyReport.collectAsStateWithLifecycle()
    val restrictedAppPreview by childVm.restrictedAppPreview.collectAsStateWithLifecycle()
    val vpnStatus by GuardianDnsFilterVpnService.status.collectAsStateWithLifecycle()
    val adminConfig by container.preferencesManager.adminConfig.collectAsStateWithLifecycle(initialValue = AdminPanelConfig())

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showExtraTimeDialog by remember { mutableStateOf(false) }
    var showWebsiteRequestDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Home, 1 = Apps, 2 = Requests & Safety
    var permRefreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permRefreshTick++
                childVm.refreshLocalTelemetryOnly()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasUsageAccess = remember(permRefreshTick) { container.usageStatsTracker.hasUsageStatsPermission() }
    val isAdminActive = remember(permRefreshTick, enrollment) { container.devicePolicyController.isAdminActive() }
    val hasNotifPerm = remember(permRefreshTick) { container.notificationHelper.hasNotificationPermission() }
    val vpnPrepared = remember(permRefreshTick, vpnStatus.running) {
        GuardianDnsFilterVpnService.prepareIntent(context) == null
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        permRefreshTick++
        if (res.resultCode == Activity.RESULT_OK) {
            GuardianDnsFilterVpnService.startVpnService(context)
                .onFailure { err ->
                    scope.launch {
                        snackbarHostState.showSnackbar(err.localizedMessage ?: "Unable to start DNS protection VPN.")
                    }
                }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("VPN consent declined. DNS protection stays paused until allowed.")
            }
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permRefreshTick++
        if (grants.values.any { it }) {
            (state as? UiState.Success)?.data?.let { childVm.shareLocationCheckIn(it) }
        } else {
            scope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = "Location permission denied. Open App Settings to allow Family Places check-ins.",
                    actionLabel = "Settings",
                    duration = SnackbarDuration.Long
                )
                if (res == SnackbarResult.ActionPerformed) {
                    container.usageStatsTracker.openAppDetailsSettings(context)
                }
            }
        }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permRefreshTick++
        if (!granted) {
            scope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = "Notifications are disabled. Enable them in App Settings for Parent approval alerts.",
                    actionLabel = "Settings",
                    duration = SnackbarDuration.Long
                )
                if (res == SnackbarResult.ActionPerformed) {
                    container.usageStatsTracker.openAppDetailsSettings(context)
                }
            }
        }
    }

    BackHandler(enabled = restrictedAppPreview != null || selectedTab != 0) {
        if (restrictedAppPreview != null) {
            childVm.openRestrictedPreview(null)
        } else {
            selectedTab = 0
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        childVm.clearMessage()
    }

    val bundle = (state as? UiState.Success)?.data

    // Automatic local sync when paired policy updates
    LaunchedEffect(bundle?.policy?.policyVersion, bundle?.device?.deviceLocked) {
        val b = bundle ?: return@LaunchedEffect
        if (b.family != null) {
            childVm.syncTelemetryAndEnforcePolicy(b, context)
        }
    }

    // Blocked / Restricted App Full-Screen View (Never exposes Parent PIN or credentials)
    val blockedPreview = restrictedAppPreview
    if (blockedPreview != null && bundle != null) {
        BlockedAppScreen(
            app = blockedPreview,
            onRequestPermission = {
                childVm.submitApprovalRequest(
                    bundle = bundle,
                    type = ApprovalRequestType.RESTRICTED_APP,
                    targetPackage = blockedPreview.packageName,
                    targetLabel = blockedPreview.appLabel,
                    reason = "Child requested permission to open ${blockedPreview.appLabel}"
                )
            },
            onRequestExtraTime = {
                childVm.submitApprovalRequest(
                    bundle = bundle,
                    type = ApprovalRequestType.EXTRA_TIME,
                    targetPackage = blockedPreview.packageName,
                    targetLabel = blockedPreview.appLabel,
                    requestedMinutes = 15L,
                    reason = "Requested 15 extra minutes"
                )
            },
            onReturnToDashboard = { childVm.openRestrictedPreview(null) }
        )
        return
    }

    // Device Locked Full-Screen Enforcement Banner when Parent locks the device
    if (bundle?.device?.deviceLocked == true) {
        DeviceLockedOverlay(
            childName = bundle.account?.displayName ?: bundle.device.childName,
            onRequestTemporaryUnlock = {
                childVm.submitApprovalRequest(
                    bundle = bundle,
                    type = ApprovalRequestType.TEMPORARY_UNLOCK,
                    requestedMinutes = 15L,
                    reason = "Child requested temporary 15-minute unlock"
                )
            },
            onSyncCheck = { childVm.syncTelemetryAndEnforcePolicy(bundle, context) }
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GuardianTopHeaderBar(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.child_mode),
                showAlertsButton = false,
                extraActions = {
                    if (bundle != null) {
                        IconButton(
                            onClick = { childVm.syncTelemetryAndEnforcePolicy(bundle, context) },
                            modifier = Modifier.size(48.dp).testTag("child_sync_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Sync Policy & Usage",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.size(48.dp).testTag("child_logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = "Sign Out",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sub-tabs for clean mobile navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("Dashboard") })
                FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("Apps") })
                FilterChip(selected = selectedTab == 2, onClick = { selectedTab = 2 }, label = { Text("Safety & Setup") })
            }

            when (val ui = state) {
                is UiState.Loading -> LoadingPanel("Loading Child Supervision Space...")
                is UiState.Error -> Box(Modifier.padding(16.dp)) {
                    ErrorBanner(message = ui.message, onRetry = { childVm.retryLoading() })
                }
                is UiState.Success -> {
                    val data = ui.data
                    val activePolicy = data.policy ?: bundle?.policy
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                if (!hasUsageAccess) {
                                    GuardianCard {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = AlertAmber)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Usage Access Permission Needed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "Allow Usage Access for Guardian360 so daily screen time and installed app usage stay accurate.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                container.usageStatsTracker.openUsageAccessSettings(context)
                                                    .onFailure { err ->
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(err.localizedMessage ?: "Open Android Usage Access Settings manually.")
                                                        }
                                                    }
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("child_grant_usage_access_banner_btn")
                                        ) {
                                            Text("Grant Usage Access")
                                        }
                                    }
                                }

                                if (adminConfig.adminNotice.isNotBlank()) {
                                    GuardianCard {
                                        Text("Family Admin Notice", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(adminConfig.adminNotice, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                // Child Profile Card
                                GuardianCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            val avatar = data.account?.avatarUri
                                            if (!avatar.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = avatar,
                                                    contentDescription = "Child Avatar",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(56.dp).clip(CircleShape)
                                                )
                                            } else {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Rounded.ChildCare, contentDescription = "Child Profile", modifier = Modifier.size(28.dp))
                                                }
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(data.account?.displayName ?: "Child Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = data.account?.deviceName ?: Build.MODEL,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill(
                                            text = if (data.family != null) "Parent Linked" else "Not Paired",
                                            color = if (data.family != null) EmeraldShield else AlertAmber
                                        )
                                    }
                                }

                                // Pairing Flow Card when child is not yet linked to a Family
                                if (data.family == null) {
                                    ChildPairingCard(
                                        childName = data.account?.displayName ?: "Child",
                                        deviceName = data.account?.deviceName ?: Build.MODEL,
                                        activeSession = data.pairingSession,
                                        repository = container.firestoreRepository,
                                        onSessionStarted = { childVm.onPairingSessionStarted(it) }
                                    )
                                }

                                // Screen Time Allowance Card
                                val dailyAllowance = (activePolicy?.dailyLimitMinutes ?: 120L) + (activePolicy?.bonusMinutesToday ?: 0L)
                                val usedToday = data.localApps.sumOf { it.minutesToday }
                                val remaining = (dailyAllowance - usedToday).coerceAtLeast(0L)
                                val ratio = if (dailyAllowance > 0) (usedToday.toFloat() / dailyAllowance.toFloat()).coerceIn(0f, 1f) else 0f

                                GuardianCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text("Remaining Screen Time", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "$remaining min remaining today ($usedToday / $dailyAllowance min used)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(8.dp))
                                    Spacer(Modifier.height(10.dp))
                                    if (activePolicy != null) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            StatusPill(
                                                text = "School ${activePolicy.schoolTimeStart}-${activePolicy.schoolTimeEnd}",
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            StatusPill(
                                                text = "Bedtime ${activePolicy.bedtimeStart}-${activePolicy.bedtimeEnd}",
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { showExtraTimeDialog = true },
                                            enabled = data.family != null && adminConfig.allowChildExtraTimeRequests,
                                            modifier = Modifier.weight(1f).testTag("child_request_extra_time_btn")
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Request Time")
                                        }
                                        OutlinedButton(
                                            onClick = { showWebsiteRequestDialog = true },
                                            enabled = data.family != null && adminConfig.allowChildWebsiteRequests,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Request Site")
                                        }
                                    }
                                }

                                // Visible Location Sharing Card (Transparent & Opt-In)
                                GuardianCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Visible Location Sharing", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = if (activePolicy?.locationSharingEnabled != false) {
                                                    "Location sharing is active and always visible to you."
                                                } else {
                                                    "Location sharing is paused by Parent policy."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    if (data.latestLocation != null) {
                                        Spacer(Modifier.height(6.dp))
                                        val age = DateUtils.getRelativeTimeSpanString(data.latestLocation.capturedAtEpochMs)
                                        Text(
                                            text = "Last Check-in: ${data.latestLocation.placeLabel ?: "Shared"} ($age)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            if (container.locationTracker.hasLocationPermission()) {
                                                childVm.shareLocationCheckIn(data)
                                            } else {
                                                locationPermLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            }
                                        },
                                        enabled = data.family != null,
                                        modifier = Modifier.fillMaxWidth().testTag("child_checkin_location_btn")
                                    ) {
                                        Icon(Icons.Rounded.LocationOn, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share Current Location Check-In")
                                    }
                                }

                                // Pending Requests & Emergency Contact
                                GuardianCard {
                                    Text("Pending Parent Requests (${data.approvals.size})", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(6.dp))
                                    if (data.approvals.isEmpty()) {
                                        Text("No requests submitted yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        data.approvals.take(4).forEach { req ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(req.targetLabel ?: req.targetDomain ?: req.requestType, style = MaterialTheme.typography.bodySmall)
                                                StatusPill(
                                                    text = req.status,
                                                    color = when (req.status) {
                                                        "APPROVED" -> EmeraldShield
                                                        "REJECTED" -> DangerCrimson
                                                        else -> AlertAmber
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Phone, contentDescription = null, tint = EmeraldShield, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Emergency Contact: ${data.family?.familyName ?: "Parent"} (Always Allowed)",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }

                            1 -> {
                                SectionHeader(
                                    title = "Allowed & Restricted Apps",
                                    subtitle = "Real installed app inventory evaluated with the 5-tier Guardian360 policy engine."
                                )
                                val blockedCount = data.localApps.count { childVm.isAppBlockedByPolicy(it, activePolicy, data.device?.deviceLocked == true) }
                                Text(
                                    text = "Total Installed: ${data.localApps.size}  •  Blocked: $blockedCount  •  Allowed: ${data.localApps.size - blockedCount}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (data.localApps.isEmpty()) {
                                    EmptyStatePanel(
                                        title = "Refreshing Installed Apps",
                                        message = "Tap Sync above to scan installed launcher apps and Usage Access stats."
                                    )
                                } else {
                                    data.localApps.take(50).forEach { app ->
                                        val isBlocked = childVm.isAppBlockedByPolicy(app, activePolicy, data.device?.deviceLocked == true)
                                        GuardianCard {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(app.appLabel, style = MaterialTheme.typography.titleSmall)
                                                    Text(
                                                        text = "${app.category} • ${app.minutesToday}m today",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isBlocked) {
                                                    OutlinedButton(onClick = { childVm.openRestrictedPreview(app) }) {
                                                        Icon(Icons.Rounded.Block, contentDescription = null, tint = DangerCrimson, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Restricted")
                                                    }
                                                } else {
                                                    StatusPill(text = "Allowed", color = EmeraldShield)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                SectionHeader(
                                    title = "Device Enrollment & Permissions",
                                    subtitle = "Honest Android Enterprise & local supervision readiness."
                                )
                                GuardianCard {
                                    StatusPill(
                                        text = enrollment.managementLevel.label,
                                        color = if (enrollment.isDeviceOwner) EmeraldShield else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(enrollment.summary, style = MaterialTheme.typography.bodySmall)
                                    if (lastReport != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Text("Policy Sync State: ${lastReport!!.overallState.label}", style = MaterialTheme.typography.labelLarge)
                                        lastReport!!.unsupportedActions.forEach { u ->
                                            Text("• $u", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                GuardianCard {
                                    Text("Supervision Permission Checklist", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            container.usageStatsTracker.openUsageAccessSettings(context)
                                                .onFailure { err ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(err.localizedMessage ?: "Usage Access screen unavailable on this device.")
                                                    }
                                                }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Schedule, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (hasUsageAccess) "Usage Access: GRANTED"
                                            else "Grant Usage Access (Settings)"
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            container.devicePolicyController.openActivateAdmin(context)
                                                .onFailure { err ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(err.localizedMessage ?: "Device Admin screen unavailable on this device.")
                                                    }
                                                }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (isAdminActive || enrollment.isDeviceAdminActive) "Device Admin: ACTIVE"
                                            else "Activate Device Admin"
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val prep = GuardianDnsFilterVpnService.prepareIntent(context)
                                            if (prep != null) {
                                                runCatching { vpnLauncher.launch(prep) }
                                                    .onFailure {
                                                        scope.launch { snackbarHostState.showSnackbar("Unable to open VPN permission prompt.") }
                                                    }
                                            } else if (vpnStatus.running) {
                                                GuardianDnsFilterVpnService.stopVpnService(context)
                                            } else {
                                                GuardianDnsFilterVpnService.startVpnService(context)
                                                    .onFailure { err ->
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(err.localizedMessage ?: "Unable to start DNS Protection VPN.")
                                                        }
                                                    }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.VpnLock, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            when {
                                                vpnStatus.running -> "DNS Protection VPN: RUNNING (Tap to Stop)"
                                                vpnPrepared -> "Start DNS Protection VPN"
                                                else -> "Enable DNS Protection VPN"
                                            }
                                        )
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                if (hasNotifPerm) {
                                                    container.usageStatsTracker.openAppDetailsSettings(context)
                                                } else {
                                                    runCatching { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                                        .onFailure { container.usageStatsTracker.openAppDetailsSettings(context) }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                if (hasNotifPerm) "Notifications: GRANTED"
                                                else "Allow Safety Notifications"
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            container.usageStatsTracker.openAppDetailsSettings(context)
                                                .onFailure { err ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(err.localizedMessage ?: "App Settings unavailable.")
                                                    }
                                                }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Open Guardian360 App Settings")
                                    }
                                }

                                // Child Language & Theme
                                GuardianCard {
                                    Text(stringResource(R.string.language_label), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("en" to "English", "hi" to "हिन्दी", "as" to "অসমীয়া").forEach { (code, label) ->
                                            FilterChip(selected = languageCode == code, onClick = { onSelectLanguage(code) }, label = { Text(label) })
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ThemePreference.entries.forEach { pref ->
                                            FilterChip(
                                                selected = themePreference == pref,
                                                onClick = { onSelectTheme(pref) },
                                                label = { Text(pref.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExtraTimeDialog && bundle != null) {
        var mins by remember { mutableLongStateOf(15L) }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExtraTimeDialog = false },
            title = { Text("Request Extra Screen Time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15L, 30L, 60L).forEach { m ->
                            FilterChip(selected = mins == m, onClick = { mins = m }, label = { Text("+${m}m") })
                        }
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason (e.g., Homework or study)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    childVm.submitApprovalRequest(
                        bundle = bundle,
                        type = ApprovalRequestType.EXTRA_TIME,
                        requestedMinutes = mins,
                        reason = reason
                    )
                    showExtraTimeDialog = false
                }) { Text("Send Request") }
            },
            dismissButton = {
                TextButton(onClick = { showExtraTimeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showWebsiteRequestDialog && bundle != null) {
        var domain by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWebsiteRequestDialog = false },
            title = { Text("Request Website Access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it.trim().lowercase() },
                        label = { Text("Website domain (e.g., khanacademy.org)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Why do you need access?") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (domain.isNotBlank()) {
                        childVm.submitApprovalRequest(
                            bundle = bundle,
                            type = ApprovalRequestType.WEBSITE_ACCESS,
                            targetDomain = domain,
                            targetLabel = domain,
                            reason = reason
                        )
                        showWebsiteRequestDialog = false
                    }
                }) { Text("Send Request") }
            },
            dismissButton = {
                TextButton(onClick = { showWebsiteRequestDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BlockedAppScreen(
    app: CachedAppInventoryEntity,
    onRequestPermission: () -> Unit,
    onRequestExtraTime: () -> Unit,
    onReturnToDashboard: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = DangerCrimson.copy(alpha = 0.14f),
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Restricted App",
                        tint = DangerCrimson,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = app.appLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.blocked_screen_message),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("blocked_screen_request_permission")
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.request_permission))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRequestExtraTime,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("blocked_screen_request_time")
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.request_extra_time))
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onReturnToDashboard,
                modifier = Modifier.testTag("blocked_screen_return_dashboard")
            ) {
                Text(stringResource(R.string.return_to_dashboard))
            }
        }
    }
}

@Composable
private fun DeviceLockedOverlay(
    childName: String,
    onRequestTemporaryUnlock: () -> Unit,
    onSyncCheck: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = "Device Locked",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "$childName's Device is Locked",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your Parent has paused device access or Bedtime/School Time is currently active. Emergency calling remains available.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRequestTemporaryUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Request Temporary Unlock (15 min)")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSyncCheck,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Check Policy Status", color = Color.White)
            }
        }
    }
}
