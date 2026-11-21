package com.guardian360.parent

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.GuardianMonoStyle
import com.example.ui.theme.PastelMint
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.SecureGreen
import com.guardian360.common.AdminPanelConfig
import com.guardian360.common.ApkDistributionHelper
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianCard
import com.guardian360.common.PreferencesManager
import com.guardian360.common.QrCodeView
import com.guardian360.common.SectionHeader
import com.guardian360.common.SecurityUtils
import com.guardian360.common.StatusPill
import com.guardian360.firebase.GuardianFirestoreRepository
import com.guardian360.website.GuardianDnsFilterVpnService
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanelScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    preferencesManager: PreferencesManager,
    repository: GuardianFirestoreRepository,
    viewModel: ParentViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val storedAdminCodeHash by preferencesManager.adminAccessCodeHash.collectAsStateWithLifecycle(initialValue = null)
    val adminConfig by preferencesManager.adminConfig.collectAsStateWithLifecycle(initialValue = AdminPanelConfig())

    var isUnlocked by rememberSaveable { mutableStateOf(false) }
    var codeAttempt by remember { mutableStateOf("") }
    var showUnlockCode by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf<String?>(null) }

    if (!isUnlocked) {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val freeBg = if (isDark) MaterialTheme.colorScheme.secondaryContainer else PastelMint
        val freeTitle = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF064E3B)
        val freeSub = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f) else Color(0xFF1E293B)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Free Technology & Zero-Paywall Highlight Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = freeBg,
                border = BorderStroke(1.dp, SecureGreen.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Verified, contentDescription = null, tint = if (isDark) MaterialTheme.colorScheme.secondary else SecureGreen, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "100% Free Supervision Technology",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = freeTitle
                        )
                        Text(
                            text = "Every Guardian360 capability — DNS VPN filtering, usage charts, emergency locks, pairing, and APK distribution — is unlocked free with zero paywalls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = freeSub
                        )
                    }
                }
            }

            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = RoyalCobaltStart.copy(alpha = 0.14f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = RoyalCobaltStart)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Admin Panel Access", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (storedAdminCodeHash.isNullOrBlank()) {
                                "Default access code is admin — you can edit & set a custom code inside."
                            } else {
                                "Enter your custom Admin Access Code to unlock system & QR controls."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                if (unlockError != null) {
                    ErrorBanner(message = unlockError!!)
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = codeAttempt,
                    onValueChange = {
                        codeAttempt = it
                        unlockError = null
                    },
                    label = { Text("Admin Access Code") },
                    placeholder = { Text(if (storedAdminCodeHash.isNullOrBlank()) "Default: admin" else "Enter code") },
                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showUnlockCode = !showUnlockCode }) {
                            Icon(
                                imageVector = if (showUnlockCode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showUnlockCode) "Hide Code" else "Show Code"
                            )
                        }
                    },
                    visualTransformation = if (showUnlockCode) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("admin_code_input")
                )

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (SecurityUtils.verifyAdminAccessCode(codeAttempt, storedAdminCodeHash)) {
                            unlockError = null
                            codeAttempt = ""
                            isUnlocked = true
                            viewModel.logAdminAudit("ADMIN_PANEL_UNLOCKED", "Parent unlocked Admin Panel")
                        } else {
                            unlockError = "Incorrect admin access code. Please verify and try again."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_unlock_btn")
                ) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock Admin Panel", fontWeight = FontWeight.Bold)
                }

                if (bundle.account?.biometricEnabled == true && SecurityUtils.isBiometricAvailable(context)) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            SecurityUtils.authenticateBiometric(
                                context = context,
                                title = "Unlock Admin Panel",
                                subtitle = "Authenticate to access Guardian360 Admin Controls",
                                negativeButtonText = "Cancel",
                                onSuccess = {
                                    isUnlocked = true
                                    viewModel.logAdminAudit("ADMIN_PANEL_UNLOCKED", "Unlocked via biometric check")
                                },
                                onError = { unlockError = it }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock with Biometric")
                    }
                }
            }
        }
        return
    }

    // --- UNLOCKED ADMIN PANEL ---
    val activeDevice = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val activePolicy = bundle.policies.firstOrNull { it.childUid == activeDevice?.childUid } ?: bundle.policies.firstOrNull()

    var statusBanner by remember { mutableStateOf<String?>(null) }
    var errorBanner by remember { mutableStateOf<String?>(null) }

    // Distribution & Real QR State
    var qrModeTab by remember { mutableIntStateOf(0) } // 0 = APK Download QR, 1 = Device Owner QR, 2 = Pairing QR
    var apkUrlDraft by remember(adminConfig.apkDownloadUrl) { mutableStateOf(adminConfig.apkDownloadUrl) }
    var adminPairingCode by remember { mutableStateOf<String?>(null) }
    var isPairingBusy by remember { mutableStateOf(false) }

    // Editable Workspace & App Configuration State
    var familyNameDraft by remember(bundle.family?.familyName) {
        mutableStateOf(bundle.family?.familyName ?: "My Family")
    }
    var subtitleDraft by remember(adminConfig.headerSubtitle) { mutableStateOf(adminConfig.headerSubtitle) }
    var noticeDraft by remember(adminConfig.adminNotice) { mutableStateOf(adminConfig.adminNotice) }
    var defaultLimitDraft by remember(adminConfig.defaultDailyLimitMinutes) {
        mutableFloatStateOf(adminConfig.defaultDailyLimitMinutes.toFloat())
    }
    var defaultDnsDraft by remember(adminConfig.defaultDnsServer) { mutableStateOf(adminConfig.defaultDnsServer) }
    var pairingTtlDraft by remember(adminConfig.defaultPairingTtlMinutes) {
        mutableFloatStateOf(adminConfig.defaultPairingTtlMinutes.toFloat())
    }
    var allowExtraTimeDraft by remember(adminConfig.allowChildExtraTimeRequests) {
        mutableStateOf(adminConfig.allowChildExtraTimeRequests)
    }
    var allowWebReqDraft by remember(adminConfig.allowChildWebsiteRequests) {
        mutableStateOf(adminConfig.allowChildWebsiteRequests)
    }
    var strictTamperDraft by remember(adminConfig.enforceStrictTamperAlerts) {
        mutableStateOf(adminConfig.enforceStrictTamperAlerts)
    }

    // Access Code Change State
    var newAdminCode by remember { mutableStateOf("") }
    var confirmAdminCode by remember { mutableStateOf("") }
    var showNewCodeText by remember { mutableStateOf(false) }

    // SAF Launchers (Zero Storage Permissions Required)
    val saveApkToDownloadsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ApkDistributionHelper.APK_MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            ApkDistributionHelper.exportInstalledApkToUri(context, uri)
                .onSuccess { bytes ->
                    val mb = String.format("%.2f", bytes / (1024.0 * 1024.0))
                    statusBanner = "Saved Guardian360 APK ($mb MB) to your selected Downloads folder."
                    viewModel.logAdminAudit("ADMIN_APK_EXPORTED", "Saved installed APK ($mb MB) via SAF")
                }
                .onFailure { errorBanner = it.localizedMessage }
        }
    }

    val openDownloadedApkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ApkDistributionHelper.openDownloadedApkUri(context, uri)
                .onSuccess {
                    statusBanner = "Opened APK file with Android system handler for installation."
                }
                .onFailure {
                    errorBanner = it.localizedMessage ?: "No handler available to open APK file."
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Unlocked Header Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Guardian360 Admin Panel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Full free supervision controls, workspace editor & real APK distribution QR.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { isUnlocked = false },
                modifier = Modifier.testTag("admin_lock_btn")
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Lock")
            }
        }

        if (statusBanner != null) {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val bannerBg = if (isDark) MaterialTheme.colorScheme.secondaryContainer else PastelMint
            val bannerText = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF064E3B)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bannerBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (isDark) MaterialTheme.colorScheme.secondary else SecureGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(statusBanner!!, style = MaterialTheme.typography.bodySmall, color = bannerText)
                }
            }
        }

        if (errorBanner != null) {
            ErrorBanner(message = errorBanner!!)
        }

        // 1. REAL QR CODE FOR DOWNLOADED APK & PHONE INSTALLATION
        GuardianCard(modifier = Modifier.testTag("admin_qr_section")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = RoyalCobaltStart)
                    Spacer(Modifier.width(8.dp))
                    Text("Real QR Installer & Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                StatusPill(text = "Free Full Access", color = SecureGreen)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Scan this QR with any real Android phone camera or Google Lens to open the APK download file and install Guardian360, or export the signed APK locally.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            TabRow(selectedTabIndex = qrModeTab) {
                Tab(selected = qrModeTab == 0, onClick = { qrModeTab = 0 }, text = { Text("APK Install QR") })
                Tab(selected = qrModeTab == 1, onClick = { qrModeTab = 1 }, text = { Text("Device Owner") })
                Tab(selected = qrModeTab == 2, onClick = { qrModeTab = 2 }, text = { Text("Pair QR") })
            }
            Spacer(Modifier.height(14.dp))

            when (qrModeTab) {
                0 -> {
                    OutlinedTextField(
                        value = apkUrlDraft,
                        onValueChange = {
                            apkUrlDraft = it
                            errorBanner = null
                        },
                        label = { Text("Hosted APK Download URL (https://... or Wi-Fi http://...)") },
                        placeholder = { Text("https://example.com/downloads/Guardian360.apk") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("admin_apk_url_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val cleaned = apkUrlDraft.trim()
                                if (cleaned.isNotEmpty() && !ApkDistributionHelper.isValidDownloadUrl(cleaned)) {
                                    errorBanner = "Enter a valid http:// or https:// APK URL."
                                } else {
                                    scope.launch {
                                        preferencesManager.setApkDownloadUrl(cleaned)
                                        statusBanner = if (cleaned.isBlank()) "APK URL cleared." else "Install QR updated for $cleaned"
                                        viewModel.logAdminAudit("ADMIN_QR_URL_UPDATED", "Updated APK QR target URL")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
                            modifier = Modifier.weight(1f).testTag("admin_save_apk_url_btn")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save & Generate QR")
                        }

                        if (ApkDistributionHelper.isValidDownloadUrl(apkUrlDraft)) {
                            OutlinedButton(
                                onClick = {
                                    ApkDistributionHelper.openDownloadUrlInBrowser(context, apkUrlDraft)
                                        .onFailure { errorBanner = it.localizedMessage }
                                }
                            ) {
                                Icon(Icons.Rounded.OpenInBrowser, contentDescription = "Open URL")
                                Spacer(Modifier.width(4.dp))
                                Text("Open")
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    if (ApkDistributionHelper.isValidDownloadUrl(apkUrlDraft)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            QrCodeView(payload = apkUrlDraft.trim(), size = 224.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = apkUrlDraft.trim(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Scan on real phone -> Opens browser -> Downloads APK -> Tap Install",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        EmptyStatePanel(
                            icon = Icons.Rounded.QrCode2,
                            title = "Enter Your Hosted APK Link Above",
                            message = "Paste an HTTPS or LAN APK download link to render a camera-scannable install QR, or use the real on-device APK export & installer buttons below."
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("On-Device APK File & Installer Tools", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    saveApkToDownloadsLauncher.launch(ApkDistributionHelper.DEFAULT_EXPORT_FILENAME)
                                }.onFailure {
                                    errorBanner = it.localizedMessage ?: "System file picker is unavailable on this device."
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("admin_save_apk_downloads_btn")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save APK", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                ApkDistributionHelper.shareInstalledApk(context)
                                    .onFailure { errorBanner = it.localizedMessage }
                            },
                            modifier = Modifier.weight(1f).testTag("admin_share_apk_btn")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share APK", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            runCatching {
                                openDownloadedApkLauncher.launch(arrayOf(ApkDistributionHelper.APK_MIME_TYPE))
                            }.onFailure {
                                errorBanner = it.localizedMessage ?: "System document picker is unavailable on this device."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldShield),
                        modifier = Modifier.fillMaxWidth().testTag("admin_open_downloaded_apk_btn")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Downloaded APK File & Install")
                    }
                }

                1 -> {
                    val jsonPayload = remember(apkUrlDraft) {
                        ApkDistributionHelper.buildEnterpriseProvisioningJson(context, apkUrlDraft)
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        QrCodeView(payload = jsonPayload, size = 228.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Android Enterprise Device Owner QR",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (ApkDistributionHelper.isValidDownloadUrl(apkUrlDraft)) {
                                "Includes auto-download URL + GuardianDeviceAdminReceiver for zero-touch setup."
                            } else {
                                "Set an HTTPS APK Download URL in the first tab to include auto-download in this provisioning QR."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                val famId = bundle.family?.id
                                if (famId.isNullOrBlank()) {
                                    errorBanner = "Family workspace not initialized yet."
                                } else {
                                    isPairingBusy = true
                                    scope.launch {
                                        val code = SecurityUtils.generatePairingCode(8)
                                        repository.createPairingSession(
                                            familyId = famId,
                                            pairingCode = code,
                                            ttlMinutes = pairingTtlDraft.roundToLong().coerceIn(5L, 60L)
                                        ).onSuccess {
                                            adminPairingCode = code
                                            statusBanner = "Generated SHA-256 pairing session ($code)."
                                        }.onFailure {
                                            errorBanner = it.localizedMessage
                                        }
                                        isPairingBusy = false
                                    }
                                }
                            },
                            enabled = !isPairingBusy
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Generate New Pairing QR")
                        }
                        Spacer(Modifier.height(12.dp))
                        if (adminPairingCode != null) {
                            val deepLink = "guardian360://pair?code=$adminPairingCode"
                            QrCodeView(payload = deepLink, size = 210.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(adminPairingCode!!, style = GuardianMonoStyle, fontWeight = FontWeight.Bold)
                            Text("Opens installed Guardian360 app directly to Child Pairing", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Tap above to generate a fresh child pairing QR.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // 2. EDIT WORKSPACE & SUPERVISION CONFIGURATION
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = RoyalCobaltStart)
                Spacer(Modifier.width(8.dp))
                Text("Edit App & Supervision Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = familyNameDraft,
                onValueChange = { familyNameDraft = it },
                label = { Text("Family / Organization Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_family_name_input")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = subtitleDraft,
                onValueChange = { subtitleDraft = it },
                label = { Text("App Header Subtitle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_subtitle_input")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = noticeDraft,
                onValueChange = { noticeDraft = it },
                label = { Text("Dashboard Admin Notice / Rule Banner (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("admin_notice_input")
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Default Daily Allowance: ${defaultLimitDraft.roundToLong()} min",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = defaultLimitDraft,
                onValueChange = { defaultLimitDraft = it },
                valueRange = 30f..480f,
                steps = 14
            )

            Text(
                text = "Pairing Code Expiry: ${pairingTtlDraft.roundToLong()} min",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = pairingTtlDraft,
                onValueChange = { pairingTtlDraft = it },
                valueRange = 5f..60f,
                steps = 10
            )

            Text("Default Safe DNS Resolver", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("8.8.8.8", "1.1.1.1", "1.1.1.3").forEach { dns ->
                    FilterChip(
                        selected = defaultDnsDraft == dns,
                        onClick = { defaultDnsDraft = dns },
                        label = { Text(dns) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Allow Child Extra-Time Requests", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = allowExtraTimeDraft, onCheckedChange = { allowExtraTimeDraft = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Allow Child Website Requests", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = allowWebReqDraft, onCheckedChange = { allowWebReqDraft = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Strict Security & Tamper Alerts", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = strictTamperDraft, onCheckedChange = { strictTamperDraft = it })
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        scope.launch {
                            val updatedCfg = adminConfig.copy(
                                headerSubtitle = subtitleDraft,
                                adminNotice = noticeDraft,
                                apkDownloadUrl = apkUrlDraft,
                                defaultDailyLimitMinutes = defaultLimitDraft.roundToLong(),
                                defaultDnsServer = defaultDnsDraft,
                                defaultPairingTtlMinutes = pairingTtlDraft.roundToLong(),
                                allowChildExtraTimeRequests = allowExtraTimeDraft,
                                allowChildWebsiteRequests = allowWebReqDraft,
                                enforceStrictTamperAlerts = strictTamperDraft
                            )
                            preferencesManager.updateAdminConfig(updatedCfg)
                            GuardianDnsFilterVpnService.setUpstreamDns(updatedCfg.defaultDnsServer)
                            val fam = bundle.family
                            if (fam != null && familyNameDraft.trim().isNotBlank() && familyNameDraft.trim() != fam.familyName) {
                                viewModel.updateFamilyName(familyNameDraft.trim())
                            }
                            viewModel.logAdminAudit("ADMIN_CONFIG_SAVED", "Updated Admin Panel workspace & default settings")
                            statusBanner = "Admin settings saved and applied live."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
                    modifier = Modifier.weight(1f).testTag("admin_save_settings_btn")
                ) {
                    Text("Save Changes")
                }

                if (activePolicy != null) {
                    OutlinedButton(
                        onClick = {
                            viewModel.savePolicyDirect(
                                activePolicy.copy(
                                    dailyLimitMinutes = defaultLimitDraft.roundToLong(),
                                    dnsFilteringEnabled = true
                                ),
                                activeDevice
                            )
                            GuardianDnsFilterVpnService.setUpstreamDns(defaultDnsDraft)
                            statusBanner = "Pushed default limits to child device policy."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sync to Child")
                    }
                }
            }
        }

        // 3. EDIT / SET ADMIN ACCESS CODE (Salted SHA-256)
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = RoyalCobaltStart)
                Spacer(Modifier.width(8.dp))
                Text("Edit / Set Admin Access Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (storedAdminCodeHash.isNullOrBlank()) {
                    "Currently using default code: admin. Set a custom access code below for enhanced security."
                } else {
                    "Custom salted SHA-256 access code is active."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = newAdminCode,
                onValueChange = {
                    newAdminCode = it
                    errorBanner = null
                },
                label = { Text("New Admin Access Code (4-32 chars)") },
                visualTransformation = if (showNewCodeText) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showNewCodeText = !showNewCodeText }) {
                        Icon(
                            imageVector = if (showNewCodeText) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle code visibility"
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_new_code_input")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmAdminCode,
                onValueChange = {
                    confirmAdminCode = it
                    errorBanner = null
                },
                label = { Text("Confirm New Access Code") },
                visualTransformation = if (showNewCodeText) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_confirm_code_input")
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val validation = SecurityUtils.validateAdminAccessCode(newAdminCode)
                        when {
                            validation != null -> errorBanner = validation
                            newAdminCode.trim() != confirmAdminCode.trim() -> errorBanner = "Access codes do not match."
                            else -> scope.launch {
                                preferencesManager.setAdminAccessCode(newAdminCode.trim())
                                viewModel.logAdminAudit("ADMIN_ACCESS_CODE_UPDATED", "Custom Admin Panel access code updated")
                                newAdminCode = ""
                                confirmAdminCode = ""
                                errorBanner = null
                                statusBanner = "Admin access code updated securely (SHA-256 salted)."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
                    modifier = Modifier.weight(1f).testTag("admin_update_code_btn")
                ) {
                    Text("Set Access Code")
                }

                if (!storedAdminCodeHash.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                preferencesManager.resetAdminAccessCodeToDefault()
                                viewModel.logAdminAudit("ADMIN_ACCESS_CODE_RESET", "Admin access code reset to default")
                                statusBanner = "Admin access code reset to default (admin)."
                            }
                        }
                    ) {
                        Text("Reset to 'admin'")
                    }
                }
            }
        }
    }
}
