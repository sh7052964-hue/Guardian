package com.guardian360.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.guardian360.applications.AppsCategoriesScreen
import com.guardian360.applications.GooglePlayControlsScreen
import com.guardian360.approvals.ApprovalsScreen
import com.guardian360.approvals.NotificationsScreen
import com.guardian360.common.AdminPanelConfig
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianTopHeaderBar
import com.guardian360.common.LoadingPanel
import com.guardian360.common.PinVerificationDialog
import com.guardian360.common.SecurityUtils
import com.guardian360.common.ThemePreference
import com.guardian360.common.UiState
import com.guardian360.dashboard.ParentDashboardScreen
import com.guardian360.di.AppContainer
import com.guardian360.firebase.SupervisionPolicy
import com.guardian360.location.LocationScreen
import com.guardian360.notifications.NotificationHelper
import com.guardian360.policies.PolicySimulationResult
import com.guardian360.screentime.ScreenTimeScreen
import com.guardian360.security.SecurityAndPermissionsScreen
import com.guardian360.website.GuardianDnsFilterVpnService
import com.guardian360.website.WebsiteManagementScreen
import com.guardian360.whatsapp.WhatsAppControlsScreen
import com.guardian360.youtube.YouTubeControlsScreen
import kotlinx.coroutines.launch

enum class ParentNavDestination(
    val titleRes: Int,
    val fallbackLabel: String,
    val icon: ImageVector
) {
    DASHBOARD(R.string.nav_dashboard, "Home", Icons.Rounded.Dashboard),
    EMERGENCY(R.string.nav_emergency, "Emergency Control", Icons.Rounded.Warning),
    SCREEN_TIME(R.string.nav_screen_time, "Screen Time", Icons.Rounded.Schedule),
    APPS_CATEGORIES(R.string.nav_apps_categories, "App Management", Icons.Rounded.Games),
    YOUTUBE_CONTROLS(R.string.nav_youtube, "YouTube Control", Icons.Rounded.PlayCircle),
    WHATSAPP_CONTROLS(R.string.nav_whatsapp, "WhatsApp Controls", Icons.AutoMirrored.Rounded.Chat),
    WEBSITE_MANAGEMENT(R.string.nav_websites, "Website & DNS", Icons.Rounded.Language),
    GOOGLE_PLAY(R.string.nav_google_play, "Google Play Controls", Icons.Rounded.Store),
    LOCATION(R.string.nav_location, "Location Tracking", Icons.Rounded.LocationOn),
    SECURITY(R.string.nav_security, "Security Center", Icons.Rounded.Security),
    APPROVALS(R.string.nav_approvals, "Approvals", Icons.Rounded.CheckCircle),
    NOTIFICATIONS(R.string.nav_notifications, "Alerts", Icons.Rounded.Notifications),
    DEVICES(R.string.nav_devices, "Devices & Pairing", Icons.Rounded.Devices),
    REPORTS(R.string.nav_reports, "Activity Report", Icons.Rounded.Assessment),
    ADMIN_PANEL(R.string.nav_admin_panel, "Admin Panel", Icons.Rounded.AdminPanelSettings),
    SETTINGS(R.string.nav_settings, "Settings", Icons.Rounded.Settings)
}

@Composable
fun ParentNavHost(
    currentUserId: String,
    familyId: String,
    languageCode: String,
    themePreference: ThemePreference,
    container: AppContainer,
    onSelectLanguage: (String) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onSignOut: () -> Unit
) {
    val parentVm: ParentViewModel = viewModel(
        key = "parent_vm_${currentUserId}_$familyId",
        factory = viewModelFactory {
            initializer {
                ParentViewModel(
                    repository = container.firestoreRepository,
                    roomDb = container.roomDatabase,
                    devicePolicyController = container.devicePolicyController,
                    usageStatsTracker = container.usageStatsTracker,
                    currentUserId = currentUserId,
                    familyId = familyId
                )
            }
        }
    )

    val overviewState by parentVm.overviewState.collectAsStateWithLifecycle()
    val selectedChildUid by parentVm.selectedChildUid.collectAsStateWithLifecycle()
    val lastActionState by parentVm.lastActionState.collectAsStateWithLifecycle()
    val actionMessage by parentVm.actionMessage.collectAsStateWithLifecycle()
    val pendingSimulation by parentVm.pendingSimulation.collectAsStateWithLifecycle()
    val adminConfig by container.preferencesManager.adminConfig.collectAsStateWithLifecycle(initialValue = AdminPanelConfig())

    var currentDestination by rememberSaveable { mutableStateOf(ParentNavDestination.DASHBOARD) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pinChallengePolicy by remember { mutableStateOf<SupervisionPolicy?>(null) }

    LaunchedEffect(adminConfig.defaultDnsServer) {
        GuardianDnsFilterVpnService.setUpstreamDns(adminConfig.defaultDnsServer)
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = currentDestination != ParentNavDestination.DASHBOARD && !drawerState.isOpen) {
        currentDestination = ParentNavDestination.DASHBOARD
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        parentVm.clearMessage()
    }

    val bundleOrNull = (overviewState as? UiState.Success)?.data
    val unreadNotifCount = bundleOrNull?.notifications?.count { !it.readFlag } ?: 0
    val pendingApprovalCount = bundleOrNull?.approvals?.count { it.status == "PENDING" } ?: 0
    val totalAlertsBadge = unreadNotifCount + pendingApprovalCount

    LaunchedEffect(bundleOrNull?.notifications?.firstOrNull()?.id) {
        val newest = bundleOrNull?.notifications?.firstOrNull { !it.readFlag } ?: return@LaunchedEffect
        val channel = when (newest.category) {
            "TAMPER", "SECURITY" -> NotificationHelper.CHANNEL_SECURITY
            else -> NotificationHelper.CHANNEL_APPROVALS
        }
        container.notificationHelper.showLocalAlert(
            channelId = channel,
            notificationId = newest.id.hashCode(),
            title = newest.title,
            body = newest.body
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        val drawerContent: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Guardian360",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(R.string.subtitle_advanced_control),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ParentNavDestination.entries.forEach { dest ->
                    val badgeCount = when (dest) {
                        ParentNavDestination.APPROVALS -> pendingApprovalCount
                        ParentNavDestination.NOTIFICATIONS -> unreadNotifCount
                        else -> 0
                    }
                    NavigationDrawerItem(
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.titleRes)) },
                        badge = if (badgeCount > 0) {
                            { Badge { Text(badgeCount.toString()) } }
                        } else null,
                        selected = currentDestination == dest,
                        onClick = {
                            currentDestination = dest
                            if (!isWideScreen) {
                                scope.launch { drawerState.close() }
                            }
                        },
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .testTag("drawer_nav_${dest.name.lowercase()}")
                    )
                }
            }
        }

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    val isDashboard = currentDestination == ParentNavDestination.DASHBOARD
                    GuardianTopHeaderBar(
                        title = if (isDashboard) stringResource(R.string.app_name) else stringResource(currentDestination.titleRes),
                        subtitle = if (isDashboard) adminConfig.headerSubtitle else stringResource(R.string.app_name),
                        showBackButton = !isDashboard,
                        showDrawerButton = isDashboard && !isWideScreen,
                        unreadAlertsCount = totalAlertsBadge,
                        onBackClick = { currentDestination = ParentNavDestination.DASHBOARD },
                        onDrawerClick = { scope.launch { drawerState.open() } },
                        onHomeClick = { currentDestination = ParentNavDestination.DASHBOARD },
                        onAlertsClick = { currentDestination = ParentNavDestination.NOTIFICATIONS },
                        onEmergencyClick = if (currentDestination != ParentNavDestination.EMERGENCY) {
                            { currentDestination = ParentNavDestination.EMERGENCY }
                        } else null
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        val bottomTabs = listOf(
                            ParentNavDestination.DASHBOARD to "Home",
                            ParentNavDestination.REPORTS to "Reports",
                            ParentNavDestination.NOTIFICATIONS to "Alerts",
                            ParentNavDestination.APPS_CATEGORIES to "Apps",
                            ParentNavDestination.SETTINGS to "Settings"
                        )
                        NavigationBar {
                            bottomTabs.forEach { (dest, shortLabel) ->
                                val badgeCount = if (dest == ParentNavDestination.NOTIFICATIONS) totalAlertsBadge else 0
                                NavigationBarItem(
                                    selected = currentDestination == dest,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (badgeCount > 0) Badge { Text(badgeCount.toString()) }
                                            }
                                        ) {
                                            Icon(dest.icon, contentDescription = shortLabel)
                                        }
                                    },
                                    label = { Text(shortLabel, maxLines = 1) },
                                    modifier = Modifier.testTag("bottom_nav_${dest.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (val state = overviewState) {
                        is UiState.Loading -> LoadingPanel("Syncing family supervision state...")
                        is UiState.Error -> Box(Modifier.padding(16.dp)) {
                            ErrorBanner(
                                message = state.message,
                                onRetry = { parentVm.retryLoading() }
                            )
                        }
                        is UiState.Success -> {
                            val bundle = state.data
                            when (currentDestination) {
                                ParentNavDestination.DASHBOARD -> ParentDashboardScreen(
                                    bundle = bundle,
                                    selectedChildUid = selectedChildUid,
                                    lastActionState = lastActionState,
                                    viewModel = parentVm,
                                    adminNotice = adminConfig.adminNotice,
                                    onNavigate = { currentDestination = it }
                                )
                                ParentNavDestination.EMERGENCY -> EmergencyControlScreen(
                                    bundle = bundle,
                                    selectedChildUid = selectedChildUid,
                                    viewModel = parentVm,
                                    onBackToDashboard = { currentDestination = ParentNavDestination.DASHBOARD }
                                )
                                ParentNavDestination.SCREEN_TIME -> ScreenTimeScreen(bundle, selectedChildUid, parentVm)
                                ParentNavDestination.APPS_CATEGORIES -> AppsCategoriesScreen(
                                    bundle = bundle,
                                    selectedChildUid = selectedChildUid,
                                    viewModel = parentVm,
                                    onOpenYouTubeControls = { currentDestination = ParentNavDestination.YOUTUBE_CONTROLS },
                                    onOpenReports = { currentDestination = ParentNavDestination.REPORTS }
                                )
                                ParentNavDestination.YOUTUBE_CONTROLS -> YouTubeControlsScreen(bundle, selectedChildUid, parentVm)
                                ParentNavDestination.WHATSAPP_CONTROLS -> WhatsAppControlsScreen(bundle, selectedChildUid, parentVm)
                                ParentNavDestination.WEBSITE_MANAGEMENT -> WebsiteManagementScreen(bundle, selectedChildUid, parentVm)
                                ParentNavDestination.GOOGLE_PLAY -> GooglePlayControlsScreen(bundle, selectedChildUid, parentVm)
                                ParentNavDestination.LOCATION -> LocationScreen(bundle, selectedChildUid, parentVm)
                                ParentNavDestination.SECURITY -> SecurityAndPermissionsScreen(bundle, selectedChildUid, parentVm)
                                ParentNavDestination.APPROVALS -> ApprovalsScreen(bundle, parentVm)
                                ParentNavDestination.NOTIFICATIONS -> NotificationsScreen(bundle, parentVm)
                                ParentNavDestination.DEVICES -> DevicesAndPairingScreen(bundle, parentVm, container.firestoreRepository)
                                ParentNavDestination.REPORTS -> ReportsScreen(bundle, selectedChildUid, parentVm)
                                ParentNavDestination.ADMIN_PANEL -> AdminPanelScreen(
                                    bundle = bundle,
                                    selectedChildUid = selectedChildUid,
                                    preferencesManager = container.preferencesManager,
                                    repository = container.firestoreRepository,
                                    viewModel = parentVm
                                )
                                ParentNavDestination.SETTINGS -> ParentSettingsScreen(
                                    bundle = bundle,
                                    languageCode = languageCode,
                                    themePreference = themePreference,
                                    viewModel = parentVm,
                                    onSelectLanguage = onSelectLanguage,
                                    onSelectTheme = onSelectTheme,
                                    onOpenAdminPanel = { currentDestination = ParentNavDestination.ADMIN_PANEL },
                                    onSignOut = onSignOut
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(268.dp)) {
                        drawerContent()
                    }
                },
                content = mainScaffold
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(292.dp)) {
                        drawerContent()
                    }
                },
                content = mainScaffold
            )
        }
    }

    // Policy Simulator Confirmation Dialog
    val simPair = pendingSimulation
    if (simPair != null) {
        val (candidate, simResult) = simPair
        val activeDevice = bundleOrNull?.devices?.firstOrNull { it.childUid == candidate.childUid }
        val account = bundleOrNull?.account
        PolicySimulationDialog(
            result = simResult,
            onConfirm = {
                if (account?.twoFactorEnabled == true && !account.pinHash.isNullOrBlank()) {
                    if (account.biometricEnabled && SecurityUtils.isBiometricAvailable(context)) {
                        SecurityUtils.authenticateBiometric(
                            context = context,
                            title = "Confirm Policy Update",
                            subtitle = "Verify identity before enforcing policy changes",
                            negativeButtonText = "Use PIN",
                            onSuccess = { parentVm.confirmAndApplyPolicy(candidate, activeDevice) },
                            onError = {
                                parentVm.dismissSimulation()
                                pinChallengePolicy = candidate
                            }
                        )
                    } else {
                        parentVm.dismissSimulation()
                        pinChallengePolicy = candidate
                    }
                } else {
                    parentVm.confirmAndApplyPolicy(candidate, activeDevice)
                }
            },
            onDismiss = { parentVm.dismissSimulation() }
        )
    }

    val pinPolicy = pinChallengePolicy
    if (pinPolicy != null) {
        val account = bundleOrNull?.account
        val activeDevice = bundleOrNull?.devices?.firstOrNull { it.childUid == pinPolicy.childUid }
        PinVerificationDialog(
            title = "Two-Factor Policy Verification",
            storedPinHash = account?.pinHash,
            onVerified = {
                pinChallengePolicy = null
                parentVm.confirmAndApplyPolicy(pinPolicy, activeDevice)
            },
            onDismiss = { pinChallengePolicy = null }
        )
    }
}

@Composable
private fun PolicySimulationDialog(
    result: PolicySimulationResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Policy Impact Simulator") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Apps affected/blocked: ${result.appsAffectedCount}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (result.affectedAppLabels.isNotEmpty()) {
                    Text(
                        text = result.affectedAppLabels.take(6).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Websites blocked: ${result.websitesAffectedCount}  •  Daily allowance: ${result.effectiveDailyMinutes}m",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (result.exceptionLabels.isNotEmpty()) {
                    Text(
                        text = "Allowed exceptions: ${result.exceptionLabels.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (result.conflicts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Detected Conflicts (${result.conflicts.size}):", style = MaterialTheme.typography.labelLarge)
                    result.conflicts.take(4).forEach { c ->
                        Text("• ${c.title}: ${c.description}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (result.unsupportedActions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Enrollment Limits:", style = MaterialTheme.typography.labelLarge)
                    result.unsupportedActions.forEach { u ->
                        Text("• $u", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.testTag("confirm_simulated_policy_btn")) {
                Text("Confirm & Apply")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
