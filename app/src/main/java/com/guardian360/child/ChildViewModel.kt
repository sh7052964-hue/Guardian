package com.guardian360.child

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.guardian360.common.ActionExecutionState
import com.guardian360.common.AgeRating
import com.guardian360.common.ApprovalRequestType
import com.guardian360.common.PreferencesManager
import com.guardian360.common.UiState
import com.guardian360.database.CachedAppInventoryEntity
import com.guardian360.database.CachedPolicyEntity
import com.guardian360.database.GuardianRoomDatabase
import com.guardian360.database.PendingRequestEntity
import com.guardian360.database.PendingSecurityEventEntity
import com.guardian360.database.UsageSummaryEntity
import com.guardian360.deviceowner.DeviceEnrollmentStatus
import com.guardian360.deviceowner.DevicePolicyController
import com.guardian360.deviceowner.PolicyApplyReport
import com.guardian360.firebase.AppUsageEntry
import com.guardian360.firebase.ApprovalRequest
import com.guardian360.firebase.Family
import com.guardian360.firebase.FamilyNotification
import com.guardian360.firebase.GuardianFirestoreRepository
import com.guardian360.firebase.LocationUpdate
import com.guardian360.firebase.PairingSession
import com.guardian360.firebase.SecurityEvent
import com.guardian360.firebase.SupervisedDevice
import com.guardian360.firebase.SupervisionPolicy
import com.guardian360.firebase.UserAccount
import com.guardian360.location.LocationTracker
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChildDashboardBundle(
    val account: UserAccount? = null,
    val family: Family? = null,
    val pairingSession: PairingSession? = null,
    val device: SupervisedDevice? = null,
    val policy: SupervisionPolicy? = null,
    val cachedPolicy: CachedPolicyEntity? = null,
    val approvals: List<ApprovalRequest> = emptyList(),
    val notifications: List<FamilyNotification> = emptyList(),
    val localApps: List<CachedAppInventoryEntity> = emptyList(),
    val pendingOfflineRequests: List<PendingRequestEntity> = emptyList(),
    val latestLocation: LocationUpdate? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChildViewModel(
    private val repository: GuardianFirestoreRepository,
    private val roomDb: GuardianRoomDatabase,
    private val devicePolicyController: DevicePolicyController,
    private val usageStatsTracker: UsageStatsTracker,
    private val locationTracker: LocationTracker,
    private val preferencesManager: PreferencesManager,
    private val currentUserId: String
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _enrollmentStatus = MutableStateFlow(
        devicePolicyController.getEnrollmentStatus(usageStatsTracker.hasUsageStatsPermission())
    )
    val enrollmentStatus: StateFlow<DeviceEnrollmentStatus> = _enrollmentStatus.asStateFlow()

    private val _lastApplyReport = MutableStateFlow<PolicyApplyReport?>(null)
    val lastApplyReport: StateFlow<PolicyApplyReport?> = _lastApplyReport.asStateFlow()

    private val _restrictedAppPreview = MutableStateFlow<CachedAppInventoryEntity?>(null)
    val restrictedAppPreview: StateFlow<CachedAppInventoryEntity?> = _restrictedAppPreview.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    private fun <T> Flow<List<T>>.resilientList(label: String): Flow<List<T>> =
        catch { error ->
            if (error is CancellationException) throw error
            Log.w("ChildVM", "Resilient fallback on $label stream: ${error.localizedMessage}", error)
            emit(emptyList())
        }

    private val pairingSessionFlow: Flow<PairingSession?> =
        combine(preferencesManager.activePairingSessionId, retryTrigger) { sessionId, _ -> sessionId }
            .flatMapLatest { sessionId ->
                if (sessionId.isNullOrBlank()) flowOf(null)
                else repository.observePairingSession(sessionId).catch { error ->
                    if (error is CancellationException) throw error
                    Log.w("ChildVM", "Resilient fallback on pairingSession stream", error)
                    emit(null)
                }
            }

    private val familyStreamFlow: Flow<Pair<Family?, FamilyLiveData>> =
        retryTrigger.flatMapLatest {
            repository.observeMyFamilies(currentUserId)
                .resilientList("families")
                .flatMapLatest { families ->
                    val fam = families.firstOrNull()
                    if (fam == null || fam.id.isBlank()) {
                        flowOf(null to FamilyLiveData())
                    } else {
                        combine(
                            repository.observeDevices(fam.id, currentUserId).resilientList("devices"),
                            repository.observePolicies(fam.id, currentUserId).resilientList("policies"),
                            repository.observeApprovals(fam.id, currentUserId).resilientList("approvals"),
                            repository.observeNotifications(fam.id, currentUserId).resilientList("notifications"),
                            repository.observeLocations(fam.id, currentUserId).resilientList("locations")
                        ) { devices, policies, approvals, notifications, locations ->
                            fam to FamilyLiveData(
                                device = devices.firstOrNull { it.childUid == currentUserId },
                                policy = policies.firstOrNull { it.childUid == currentUserId },
                                approvals = approvals.filter { it.childUid == currentUserId },
                                notifications = notifications.filter { it.childUid == currentUserId },
                                latestLocation = locations.firstOrNull { it.childUid == currentUserId }
                            )
                        }
                    }
                }
        }

    val dashboardState: StateFlow<UiState<ChildDashboardBundle>> = combine(
        repository.observeUserAccount(currentUserId).catch { error ->
            if (error is CancellationException) throw error
            Log.w("ChildVM", "Resilient fallback on userAccount stream", error)
            emit(null)
        },
        familyStreamFlow,
        pairingSessionFlow,
        roomDb.cachedPolicyDao().observePolicy(currentUserId),
        roomDb.cachedAppInventoryDao().observeInventory(),
        roomDb.pendingRequestDao().observePendingRequests()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val account = values[0] as? UserAccount
        @Suppress("UNCHECKED_CAST")
        val famPair = values[1] as Pair<Family?, FamilyLiveData>
        val pairingSession = values[2] as? PairingSession
        val cachedPolicy = values[3] as? CachedPolicyEntity
        @Suppress("UNCHECKED_CAST")
        val localApps = values[4] as List<CachedAppInventoryEntity>
        @Suppress("UNCHECKED_CAST")
        val pendingRequests = values[5] as List<PendingRequestEntity>

        val family = famPair.first
        val live = famPair.second

        if (live.policy != null && family != null) {
            cachePolicyOffline(family.id, live.policy)
            applyDnsRulesFromPolicy(live.policy)
        }

        UiState.Success(
            ChildDashboardBundle(
                account = account,
                family = family,
                pairingSession = pairingSession,
                device = live.device,
                policy = live.policy,
                cachedPolicy = cachedPolicy,
                approvals = live.approvals,
                notifications = live.notifications,
                localApps = localApps,
                pendingOfflineRequests = pendingRequests,
                latestLocation = live.latestLocation
            )
        )
    }.asUiErrorState("ChildVM")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        refreshLocalTelemetryOnly()
    }

    fun retryLoading() {
        retryTrigger.value += 1
        refreshLocalTelemetryOnly()
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    fun onPairingSessionStarted(sessionId: String) {
        viewModelScope.launch {
            preferencesManager.setActivePairingSessionId(sessionId)
            _statusMessage.value = "Pairing request sent to Parent for verification."
        }
    }

    fun openRestrictedPreview(app: CachedAppInventoryEntity?) {
        _restrictedAppPreview.value = app
    }

    fun isAppBlockedByPolicy(app: CachedAppInventoryEntity, policy: SupervisionPolicy?, deviceLocked: Boolean): Boolean {
        val decision = com.guardian360.policies.AppCategoryCatalog.evaluatePackageAccess(
            packageName = app.packageName,
            appCategory = com.guardian360.policies.AppCategory.fromString(app.category),
            appAgeRating = AgeRating.fromString(app.ageRating),
            guardianPackage = "com.aistudio.guardian360.vrmqkt",
            policy = policy,
            deviceLocked = deviceLocked,
            isDowntimeActive = false
        )
        return !decision.allowed
    }

    fun refreshLocalTelemetryOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = usageStatsTracker.collectSnapshot()
            roomDb.cachedAppInventoryDao().upsertAll(snap.apps)
            _enrollmentStatus.value = devicePolicyController.getEnrollmentStatus(snap.hasUsageAccess)
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            roomDb.usageSummaryDao().upsertSummary(
                UsageSummaryEntity(
                    dateKey = dateKey,
                    childUid = currentUserId,
                    totalMinutes = snap.totalMinutesToday,
                    topPackage = snap.apps.firstOrNull()?.packageName ?: "",
                    recordedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun syncTelemetryAndEnforcePolicy(bundle: ChildDashboardBundle, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val policy = bundle.policy ?: bundle.cachedPolicy?.toSupervisionPolicy()
            val blockedSet = computeBlockedPackageSet(bundle.localApps, policy)
            val snap = usageStatsTracker.collectSnapshot(blockedSet)
            roomDb.cachedAppInventoryDao().upsertAll(snap.apps)
            val enrollment = devicePolicyController.getEnrollmentStatus(snap.hasUsageAccess)
            _enrollmentStatus.value = enrollment

            if (policy != null) {
                applyDnsRulesFromPolicy(policy)
                val report = devicePolicyController.applyPolicy(policy, blockedSet.toList())
                _lastApplyReport.value = report

                if (bundle.device?.deviceLocked == true) {
                    devicePolicyController.lockDeviceNow()
                }
                if (bundle.family != null) {
                    repository.acknowledgePolicyState(
                        bundle.family.id,
                        currentUserId,
                        report.overallState.name
                    )
                }
            }

            val family = bundle.family
            val device = bundle.device
            if (family != null && device != null) {
                repository.syncChildDeviceTelemetry(
                    familyId = family.id,
                    childUid = currentUserId,
                    deviceName = bundle.account?.deviceName ?: device.deviceName,
                    deviceModel = Build.MODEL,
                    supervisionStatus = if (device.deviceLocked) "DEVICE_LOCKED" else "PAIRED_ACTIVE",
                    online = snap.isOnline,
                    batteryLevel = snap.batteryPercent,
                    dailyMinutes = snap.totalMinutesToday,
                    weeklyMinutes = snap.totalMinutesWeek
                ).onSuccess {
                    repository.recordHeartbeat(
                        familyId = family.id,
                        parentUid = device.parentUid,
                        memberUids = family.memberUids,
                        online = snap.isOnline,
                        batteryLevel = snap.batteryPercent
                    )
                    val entries = snap.apps.take(40).map { app ->
                        AppUsageEntry(
                            parentUid = device.parentUid,
                            childUid = currentUserId,
                            memberUids = family.memberUids,
                            packageName = app.packageName,
                            appLabel = app.appLabel,
                            category = app.category,
                            ageRating = app.ageRating,
                            minutesToday = app.minutesToday,
                            minutesWeek = app.minutesWeek,
                            blocked = app.packageName in blockedSet,
                            exceptionAllowed = policy?.allowedPackages?.contains(app.packageName) == true
                        )
                    }
                    repository.syncAppUsageBatch(family.id, entries)
                    _statusMessage.value = "Synced ${snap.apps.size} apps • ${snap.totalMinutesToday}m today • Battery ${snap.batteryPercent}%"
                }.onFailure { err ->
                    _statusMessage.value = "Sync deferred (offline): ${err.localizedMessage}"
                }
            } else {
                _statusMessage.value = "Local device inventory refreshed (${snap.apps.size} apps)."
            }
        }
    }

    fun shareLocationCheckIn(bundle: ChildDashboardBundle) {
        viewModelScope.launch {
            val family = bundle.family ?: run {
                _statusMessage.value = "Pair with a Parent device before sharing location."
                return@launch
            }
            val device = bundle.device ?: return@launch
            val policy = bundle.policy
            if (policy?.locationSharingEnabled == false) {
                _statusMessage.value = "Location sharing is currently disabled in Parent Policy."
                return@launch
            }
            if (!locationTracker.hasLocationPermission()) {
                _statusMessage.value = "Please grant Android Location permission first."
                return@launch
            }
            locationTracker.fetchCurrentOrLastLocation()
                .onSuccess { cap ->
                    var label = "Current Location"
                    var kind = "UPDATE"
                    if (policy != null && policy.homeLat != 0.0 && policy.homeLng != 0.0) {
                        val distHome = LocationTracker.distanceMeters(cap.latitude, cap.longitude, policy.homeLat, policy.homeLng)
                        if (distHome <= policy.homeRadiusMeters) {
                            label = "Home Safe Zone"
                            kind = "ARRIVAL"
                        }
                    }
                    if (policy != null && policy.schoolLat != 0.0 && policy.schoolLng != 0.0) {
                        val distSchool = LocationTracker.distanceMeters(cap.latitude, cap.longitude, policy.schoolLat, policy.schoolLng)
                        if (distSchool <= policy.schoolRadiusMeters) {
                            label = "School Safe Zone"
                            kind = "ARRIVAL"
                        }
                    }
                    repository.publishLocation(
                        family.id,
                        LocationUpdate(
                            parentUid = device.parentUid,
                            childUid = currentUserId,
                            memberUids = family.memberUids,
                            latitude = cap.latitude,
                            longitude = cap.longitude,
                            accuracyMeters = cap.accuracyMeters.toDouble(),
                            placeLabel = label,
                            eventKind = kind,
                            sharingActive = true,
                            capturedAtEpochMs = cap.capturedAtEpochMs
                        )
                    ).onSuccess {
                        _statusMessage.value = "Location shared: $label (±${cap.accuracyMeters.toInt()}m)"
                    }.onFailure {
                        _statusMessage.value = it.localizedMessage
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = "Unable to read GPS location: ${err.localizedMessage}"
                }
        }
    }

    fun submitApprovalRequest(
        bundle: ChildDashboardBundle,
        type: ApprovalRequestType,
        targetPackage: String? = null,
        targetLabel: String? = null,
        targetDomain: String? = null,
        requestedMinutes: Long? = null,
        reason: String
    ) {
        viewModelScope.launch {
            val family = bundle.family
            val device = bundle.device
            if (family == null || device == null) {
                _statusMessage.value = "Link with your Parent first before sending requests."
                return@launch
            }
            val request = ApprovalRequest(
                parentUid = device.parentUid,
                childUid = currentUserId,
                memberUids = family.memberUids,
                childName = bundle.account?.displayName ?: device.childName,
                requestType = type.name,
                targetPackage = targetPackage,
                targetLabel = targetLabel,
                targetDomain = targetDomain,
                requestedMinutes = requestedMinutes,
                reason = reason.ifBlank { type.label }
            )
            repository.submitApprovalRequest(family.id, request)
                .onSuccess {
                    _statusMessage.value = "${type.label} sent to Parent."
                    _restrictedAppPreview.value = null
                }
                .onFailure { err ->
                    roomDb.pendingRequestDao().insert(
                        PendingRequestEntity(
                            familyId = family.id,
                            childUid = currentUserId,
                            requestType = type.name,
                            targetPackage = targetPackage,
                            targetDomain = targetDomain,
                            requestedMinutes = requestedMinutes,
                            reason = reason,
                            createdAtEpochMs = System.currentTimeMillis()
                        )
                    )
                    _statusMessage.value = "Saved offline to retry queue: ${err.localizedMessage}"
                }
        }
    }

    fun logSecurityAnomaly(bundle: ChildDashboardBundle, eventType: String, title: String, details: String) {
        viewModelScope.launch {
            val family = bundle.family ?: return@launch
            val device = bundle.device ?: return@launch
            repository.logSecurityEvent(
                family.id,
                SecurityEvent(
                    parentUid = device.parentUid,
                    childUid = currentUserId,
                    memberUids = family.memberUids,
                    eventType = eventType,
                    severity = "WARNING",
                    title = title,
                    details = details
                )
            ).onFailure {
                roomDb.pendingSecurityEventDao().insert(
                    PendingSecurityEventEntity(
                        familyId = family.id,
                        childUid = currentUserId,
                        eventType = eventType,
                        severity = "WARNING",
                        title = title,
                        details = details,
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun computeBlockedPackageSet(
        apps: List<CachedAppInventoryEntity>,
        policy: SupervisionPolicy?
    ): Set<String> {
        if (policy == null) return emptySet()
        val maxAge = AgeRating.fromString(policy.maxAgeRating)
        val out = policy.blockedPackages.toMutableSet()
        apps.forEach { app ->
            val appAge = AgeRating.fromString(app.ageRating)
            if (app.packageName !in policy.allowedPackages &&
                (app.category in policy.blockedCategories || appAge.minAge > maxAge.minAge)
            ) {
                out.add(app.packageName)
            }
        }
        return out
    }

    private fun applyDnsRulesFromPolicy(policy: SupervisionPolicy) {
        val blocked = policy.blockedDomains.toMutableSet()
        if (policy.youtubeMode != "NORMAL") {
            blocked.add("shorts.youtube.com")
        }
        if (policy.whatsappMetaAiWebRestricted) {
            blocked.addAll(listOf("meta.ai", "ai.meta.com"))
        }
        GuardianDnsFilterVpnService.updateDomainRules(blocked, policy.allowedDomains.toSet())
    }

    private fun cachePolicyOffline(familyId: String, p: SupervisionPolicy) {
        viewModelScope.launch(Dispatchers.IO) {
            roomDb.cachedPolicyDao().upsertPolicy(
                CachedPolicyEntity(
                    childUid = currentUserId,
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

    private fun CachedPolicyEntity.toSupervisionPolicy(): SupervisionPolicy = SupervisionPolicy(
        childUid = childUid,
        policyVersion = policyVersion,
        dailyLimitMinutes = dailyLimitMinutes,
        downtimeEnabled = downtimeEnabled,
        bedtimeStart = bedtimeStart,
        bedtimeEnd = bedtimeEnd,
        schoolTimeStart = schoolTimeStart,
        schoolTimeEnd = schoolTimeEnd,
        maxAgeRating = maxAgeRating,
        youtubeMode = youtubeMode,
        dnsFilteringEnabled = dnsFilteringEnabled,
        blockedCategories = blockedCategoriesCsv.split(",").filter { it.isNotBlank() },
        blockedPackages = blockedPackagesCsv.split(",").filter { it.isNotBlank() },
        allowedPackages = allowedPackagesCsv.split(",").filter { it.isNotBlank() },
        blockedDomains = blockedDomainsCsv.split(",").filter { it.isNotBlank() },
        allowedDomains = allowedDomainsCsv.split(",").filter { it.isNotBlank() },
        lastActionState = lastActionState
    )

    private fun Flow<UiState<ChildDashboardBundle>>.asUiErrorState(tag: String): Flow<UiState<ChildDashboardBundle>> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(tag, "Error observing child dashboard", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load child supervision data"))
        }

    private data class FamilyLiveData(
        val device: SupervisedDevice? = null,
        val policy: SupervisionPolicy? = null,
        val approvals: List<ApprovalRequest> = emptyList(),
        val notifications: List<FamilyNotification> = emptyList(),
        val latestLocation: LocationUpdate? = null
    )
}
