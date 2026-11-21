package com.guardian360.parent

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.guardian360.common.ActionExecutionState
import com.guardian360.common.SecurityUtils
import com.guardian360.common.UiState
import com.guardian360.database.CachedAppInventoryEntity
import com.guardian360.database.CachedPolicyEntity
import com.guardian360.database.GuardianRoomDatabase
import com.guardian360.database.SyncQueueEntity
import com.guardian360.deviceowner.DeviceEnrollmentStatus
import com.guardian360.deviceowner.DevicePolicyController
import com.guardian360.firebase.AppUsageEntry
import com.guardian360.firebase.ApprovalRequest
import com.guardian360.firebase.AuditLogEntry
import com.guardian360.firebase.Family
import com.guardian360.firebase.FamilyNotification
import com.guardian360.firebase.GuardianFirestoreRepository
import com.guardian360.firebase.LocationUpdate
import com.guardian360.firebase.PairingSession
import com.guardian360.firebase.PolicyVersionRecord
import com.guardian360.firebase.SecurityEvent
import com.guardian360.firebase.SupervisedDevice
import com.guardian360.firebase.SupervisionPolicy
import com.guardian360.firebase.UserAccount
import com.guardian360.policies.PolicyConflict
import com.guardian360.policies.PolicyConflictDetector
import com.guardian360.policies.PolicySimulationResult
import com.guardian360.policies.PolicySimulator
import com.guardian360.screentime.UsageStatsTracker
import com.guardian360.website.GuardianDnsFilterVpnService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ParentOverviewBundle(
    val account: UserAccount? = null,
    val family: Family? = null,
    val devices: List<SupervisedDevice> = emptyList(),
    val policies: List<SupervisionPolicy> = emptyList(),
    val approvals: List<ApprovalRequest> = emptyList(),
    val securityEvents: List<SecurityEvent> = emptyList(),
    val notifications: List<FamilyNotification> = emptyList(),
    val locations: List<LocationUpdate> = emptyList(),
    val appUsage: List<AppUsageEntry> = emptyList(),
    val pairingSessions: List<PairingSession> = emptyList(),
    val localInventory: List<CachedAppInventoryEntity> = emptyList(),
    val syncQueue: List<SyncQueueEntity> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val policyVersions: List<PolicyVersionRecord> = emptyList()
)

class ParentViewModel(
    private val repository: GuardianFirestoreRepository,
    private val roomDb: GuardianRoomDatabase,
    private val devicePolicyController: DevicePolicyController,
    private val usageStatsTracker: UsageStatsTracker,
    private val currentUserId: String,
    val familyId: String
) : ViewModel() {

    private val _selectedChildUid = MutableStateFlow<String?>(null)
    val selectedChildUid: StateFlow<String?> = _selectedChildUid.asStateFlow()

    private val _lastActionState = MutableStateFlow<ActionExecutionState?>(null)
    val lastActionState: StateFlow<ActionExecutionState?> = _lastActionState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _pendingSimulation = MutableStateFlow<Pair<SupervisionPolicy, PolicySimulationResult>?>(null)
    val pendingSimulation: StateFlow<Pair<SupervisionPolicy, PolicySimulationResult>?> = _pendingSimulation.asStateFlow()

    private val _enrollmentStatus = MutableStateFlow(
        devicePolicyController.getEnrollmentStatus(usageStatsTracker.hasUsageStatsPermission())
    )
    val enrollmentStatus: StateFlow<DeviceEnrollmentStatus> = _enrollmentStatus.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    private fun <T> Flow<List<T>>.resilientList(label: String): Flow<List<T>> =
        catch { error ->
            if (error is CancellationException) throw error
            Log.w("ParentVM", "Resilient fallback on $label stream: ${error.localizedMessage}", error)
            emit(emptyList())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val firebaseBundleFlow = retryTrigger.flatMapLatest {
        combine(
            repository.observeUserAccount(currentUserId).catch { err ->
                if (err is CancellationException) throw err
                Log.w("ParentVM", "Resilient fallback on userAccount stream", err)
                emit(null)
            },
            repository.observeDevices(familyId, currentUserId).resilientList("devices"),
            repository.observePolicies(familyId, currentUserId).resilientList("policies"),
            repository.observeApprovals(familyId, currentUserId).resilientList("approvals"),
            repository.observeSecurityEvents(familyId, currentUserId).resilientList("security_events"),
            repository.observeNotifications(familyId, currentUserId).resilientList("notifications"),
            repository.observeLocations(familyId, currentUserId).resilientList("locations"),
            repository.observeAppUsage(familyId, currentUserId).resilientList("app_usage"),
            repository.observeParentPairingSessions(currentUserId).resilientList("pairing_sessions"),
            repository.observeAuditLogs(familyId, currentUserId).resilientList("audit_logs"),
            repository.observePolicyVersions(familyId, currentUserId).resilientList("policy_versions"),
            repository.observeMyFamilies(currentUserId).resilientList("families")
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val fams = values[11] as List<Family>
            @Suppress("UNCHECKED_CAST")
            ParentOverviewBundle(
                account = values[0] as? UserAccount,
                family = fams.firstOrNull { it.id == familyId } ?: fams.firstOrNull(),
                devices = values[1] as List<SupervisedDevice>,
                policies = values[2] as List<SupervisionPolicy>,
                approvals = values[3] as List<ApprovalRequest>,
                securityEvents = values[4] as List<SecurityEvent>,
                notifications = values[5] as List<FamilyNotification>,
                locations = values[6] as List<LocationUpdate>,
                appUsage = values[7] as List<AppUsageEntry>,
                pairingSessions = values[8] as List<PairingSession>,
                auditLogs = values[9] as List<AuditLogEntry>,
                policyVersions = values[10] as List<PolicyVersionRecord>
            )
        }
    }

    val overviewState: StateFlow<UiState<ParentOverviewBundle>> = combine(
        firebaseBundleFlow,
        roomDb.cachedAppInventoryDao().observeInventory(),
        roomDb.syncQueueDao().observeQueue()
    ) { fbBundle, inventory, syncQueue ->
        val bundle = fbBundle.copy(
            localInventory = inventory,
            syncQueue = syncQueue
        )
        if (_selectedChildUid.value == null && bundle.devices.isNotEmpty()) {
            _selectedChildUid.value = bundle.devices.first().childUid
        }
        // Cache policies offline in Room
        bundle.policies.forEach { p ->
            viewModelScope.launch(Dispatchers.IO) {
                roomDb.cachedPolicyDao().upsertPolicy(
                    CachedPolicyEntity(
                        childUid = p.childUid,
                        familyId = familyId,
                        policyVersion = p.policyVersion,
                        dailyLimitMinutes = p.dailyLimitMinutes,
                        downtimeEnabled = p.downtimeEnabled,
                        bedtimeStart = p.bedtimeStart,
                        bedtimeEnd = p.bedtimeEnd,
                        schoolTimeStart = p.schoolTimeStart,
                        schoolTimeEnd = p.schoolTimeEnd,
                        maxAgeRating = p.maxAgeRating,
                        youtubeMode = p.youtubeMode,
                        dnsFilteringEnabled = p.dnsFilteringEnabled,
                        blockedCategoriesCsv = p.blockedCategories.joinToString(","),
                        blockedPackagesCsv = p.blockedPackages.joinToString(","),
                        allowedPackagesCsv = p.allowedPackages.joinToString(","),
                        blockedDomainsCsv = p.blockedDomains.joinToString(","),
                        allowedDomainsCsv = p.allowedDomains.joinToString(","),
                        lastActionState = p.lastActionState,
                        syncedAtEpochMs = System.currentTimeMillis()
                    )
                )
            }
        }
        UiState.Success(bundle)
    }.asUiErrorState("ParentVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        refreshLocalDeviceInventory()
    }

    fun retryLoading() {
        retryTrigger.value += 1
        refreshLocalDeviceInventory()
    }

    fun selectChild(childUid: String) {
        _selectedChildUid.value = childUid
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun refreshLocalDeviceInventory() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = usageStatsTracker.collectSnapshot()
            roomDb.cachedAppInventoryDao().upsertAll(snap.apps)
            _enrollmentStatus.value = devicePolicyController.getEnrollmentStatus(snap.hasUsageAccess)
        }
    }

    fun simulatePolicyChange(
        candidate: SupervisionPolicy,
        device: SupervisedDevice?,
        apps: List<CachedAppInventoryEntity>,
        hasLocationPermission: Boolean,
        isVpnPrepared: Boolean
    ) {
        val status = devicePolicyController.getEnrollmentStatus(usageStatsTracker.hasUsageStatsPermission())
        _enrollmentStatus.value = status
        val result = PolicySimulator.simulate(
            policy = candidate,
            apps = apps,
            enrollment = status,
            isDeviceOnline = device?.deviceOnline ?: true,
            hasUsageAccess = usageStatsTracker.hasUsageStatsPermission(),
            hasLocationPermission = hasLocationPermission,
            isVpnPrepared = isVpnPrepared
        )
        _pendingSimulation.value = candidate to result
    }

    fun dismissSimulation() {
        _pendingSimulation.value = null
    }

    fun confirmAndApplyPolicy(policy: SupervisionPolicy, device: SupervisedDevice?) {
        _pendingSimulation.value = null
        savePolicyDirect(policy, device)
    }

    fun savePolicyDirect(policy: SupervisionPolicy, device: SupervisedDevice?) {
        viewModelScope.launch {
            _lastActionState.value = ActionExecutionState.PROCESSING
            val stateToWrite = if (device != null && !device.deviceOnline) {
                ActionExecutionState.DEVICE_OFFLINE
            } else {
                ActionExecutionState.PENDING
            }
            val updated = policy.copy(lastActionState = stateToWrite.name)
            repository.savePolicy(familyId, updated)
                .onSuccess {
                    _lastActionState.value = stateToWrite
                    _actionMessage.value = "Policy v${policy.policyVersion + 1} queued (${stateToWrite.label})"
                    val members = device?.memberUids ?: listOf(currentUserId, policy.childUid)
                    repository.recordPolicyVersion(
                        familyId = familyId,
                        childUid = policy.childUid,
                        memberUids = members,
                        policyVersion = policy.policyVersion + 1L,
                        executionStatus = stateToWrite.name,
                        summary = "Limit ${policy.dailyLimitMinutes}m • Blocked Apps ${policy.blockedPackages.size} • Domains ${policy.blockedDomains.size}"
                    )
                    repository.logAudit(
                        familyId = familyId,
                        childUid = policy.childUid,
                        action = "POLICY_UPDATED",
                        targetPath = "families/$familyId/policies/${policy.childUid}",
                        details = "Updated policy to v${policy.policyVersion + 1}"
                    )
                }
                .onFailure { err ->
                    _lastActionState.value = ActionExecutionState.FAILED
                    roomDb.syncQueueDao().enqueue(
                        SyncQueueEntity(
                            operationType = "SAVE_POLICY",
                            targetPath = "families/$familyId/policies/${policy.childUid}",
                            payloadSummary = "Policy v${policy.policyVersion + 1}"
                        )
                    )
                    _actionMessage.value = "Queued offline after error: ${err.localizedMessage}"
                }
        }
    }

    fun toggleDeviceLock(device: SupervisedDevice, locked: Boolean) {
        viewModelScope.launch {
            _lastActionState.value = ActionExecutionState.PROCESSING
            repository.updateDeviceLockAndStrict(
                familyId = familyId,
                childUid = device.childUid,
                locked = locked,
                strictSupervision = device.strictSupervision,
                policyVersion = device.policyVersion + 1
            ).onSuccess {
                val state = if (device.deviceOnline) ActionExecutionState.APPLIED else ActionExecutionState.DEVICE_OFFLINE
                _lastActionState.value = state
                _actionMessage.value = if (locked) "Device Lock command sent (${state.label})" else "Device Unlock command sent (${state.label})"
                repository.logSecurityEvent(
                    familyId,
                    SecurityEvent(
                        parentUid = currentUserId,
                        childUid = device.childUid,
                        memberUids = device.memberUids,
                        eventType = if (locked) "DEVICE_LOCKED" else "DEVICE_UNLOCKED",
                        severity = "INFO",
                        title = if (locked) "Device Locked by Parent" else "Device Unlocked by Parent",
                        details = "Target: ${device.deviceName}"
                    )
                )
                repository.logAudit(
                    familyId = familyId,
                    childUid = device.childUid,
                    action = if (locked) "DEVICE_LOCKED" else "DEVICE_UNLOCKED",
                    targetPath = "families/$familyId/devices/${device.childUid}",
                    details = "Target: ${device.deviceName}"
                )
            }.onFailure {
                _lastActionState.value = ActionExecutionState.FAILED
                _actionMessage.value = it.localizedMessage
            }
        }
    }

    fun toggleStrictSupervision(device: SupervisedDevice, strict: Boolean) {
        viewModelScope.launch {
            _lastActionState.value = ActionExecutionState.PROCESSING
            repository.updateDeviceLockAndStrict(
                familyId = familyId,
                childUid = device.childUid,
                locked = device.deviceLocked,
                strictSupervision = strict,
                policyVersion = device.policyVersion + 1
            ).onSuccess {
                _lastActionState.value = ActionExecutionState.APPLIED
                _actionMessage.value = "Strict Supervision ${if (strict) "enabled" else "disabled"}"
            }.onFailure {
                _lastActionState.value = ActionExecutionState.FAILED
                _actionMessage.value = it.localizedMessage
            }
        }
    }

    fun decideApproval(request: ApprovalRequest, approved: Boolean, durationMinutes: Long? = null, policy: SupervisionPolicy?) {
        viewModelScope.launch {
            repository.decideApprovalRequest(familyId, request, approved, durationMinutes)
                .onSuccess {
                    if (approved && policy != null) {
                        val updated = when (request.requestType) {
                            "EXTRA_TIME" -> policy.copy(
                                bonusMinutesToday = policy.bonusMinutesToday + (durationMinutes ?: request.requestedMinutes ?: 15L)
                            )
                            "WEBSITE_ACCESS" -> {
                                val domain = request.targetDomain?.trim()?.lowercase()
                                if (!domain.isNullOrBlank()) {
                                    policy.copy(
                                        allowedDomains = (policy.allowedDomains + domain).distinct(),
                                        blockedDomains = policy.blockedDomains.filterNot { it == domain }
                                    )
                                } else policy
                            }
                            "RESTRICTED_APP", "APP_INSTALL", "CATEGORY_EXCEPTION" -> {
                                val pkg = request.targetPackage?.trim()
                                if (!pkg.isNullOrBlank()) {
                                    policy.copy(
                                        allowedPackages = (policy.allowedPackages + pkg).distinct(),
                                        blockedPackages = policy.blockedPackages.filterNot { it == pkg }
                                    )
                                } else policy
                            }
                            else -> policy
                        }
                        if (updated != policy) {
                            repository.savePolicy(familyId, updated)
                        }
                    }
                    _actionMessage.value = "Request ${if (approved) "approved" else "rejected"}."
                }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun acknowledgeEvent(eventId: String) {
        viewModelScope.launch {
            repository.acknowledgeSecurityEvent(familyId, eventId)
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(familyId, notificationId)
        }
    }

    fun removeChildDevice(childUid: String) {
        viewModelScope.launch {
            repository.removeChildDevice(familyId, childUid)
                .onSuccess {
                    if (_selectedChildUid.value == childUid) _selectedChildUid.value = null
                    _actionMessage.value = "Child device removed from family."
                    repository.logAudit(
                        familyId = familyId,
                        childUid = childUid,
                        action = "DEVICE_REVOKED",
                        targetPath = "families/$familyId/devices/$childUid",
                        details = "Removed paired child device and revoked policy"
                    )
                }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun updateParentPin(account: UserAccount, newPin: String) {
        viewModelScope.launch {
            val hashed = SecurityUtils.hashPin(newPin)
            repository.upsertUserAccount(account.copy(pinHash = hashed))
                .onSuccess { _actionMessage.value = "Security PIN updated (SHA-256 salted)." }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun updateAccountSecurityFlags(account: UserAccount, biometric: Boolean, twoFactor: Boolean) {
        viewModelScope.launch {
            repository.upsertUserAccount(account.copy(biometricEnabled = biometric, twoFactorEnabled = twoFactor))
                .onSuccess { _actionMessage.value = "Account security settings saved." }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun logoutAllDevices() {
        viewModelScope.launch {
            repository.incrementSessionVersion()
                .onSuccess { _actionMessage.value = "All remote sessions invalidated (sessionVersion incremented)." }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun updateFamilyName(newName: String) {
        viewModelScope.launch {
            repository.updateFamilyName(familyId, newName)
                .onSuccess { _actionMessage.value = "Family workspace name updated." }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun logAdminAudit(action: String, details: String) {
        viewModelScope.launch {
            repository.logAudit(
                familyId = familyId,
                childUid = null,
                action = action,
                targetPath = "families/$familyId/admin",
                details = details
            )
        }
    }

    fun computeConflicts(
        policy: SupervisionPolicy?,
        device: SupervisedDevice?,
        apps: List<CachedAppInventoryEntity>,
        context: Context
    ): List<PolicyConflict> {
        if (policy == null) return emptyList()
        val hasUsage = usageStatsTracker.hasUsageStatsPermission()
        val hasLoc = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        val vpnOk = GuardianDnsFilterVpnService.prepareIntent(context) == null
        return PolicyConflictDetector.detect(
            policy = policy,
            apps = apps,
            enrollment = _enrollmentStatus.value,
            isDeviceOnline = device?.deviceOnline ?: true,
            hasUsageAccess = hasUsage,
            hasLocationPermission = hasLoc,
            isVpnPrepared = vpnOk
        )
    }

    private fun Flow<UiState<ParentOverviewBundle>>.asUiErrorState(tag: String): Flow<UiState<ParentOverviewBundle>> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(tag, "Error observing family data", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load family data"))
        }
}
