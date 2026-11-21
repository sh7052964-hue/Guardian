package com.guardian360.policies

import com.guardian360.common.ActionExecutionState
import com.guardian360.common.AgeRating
import com.guardian360.common.EventSeverity
import com.guardian360.database.CachedAppInventoryEntity
import com.guardian360.deviceowner.DeviceEnrollmentStatus
import com.guardian360.firebase.SupervisionPolicy

data class PolicyConflict(
    val code: String,
    val title: String,
    val description: String,
    val severity: EventSeverity
)

data class PolicySimulationResult(
    val appsAffectedCount: Int,
    val affectedAppLabels: List<String>,
    val websitesAffectedCount: Int,
    val effectiveDailyMinutes: Long,
    val exceptionLabels: List<String>,
    val conflicts: List<PolicyConflict>,
    val unsupportedActions: List<String>
)

object PolicyConflictDetector {
    fun detect(
        policy: SupervisionPolicy,
        apps: List<CachedAppInventoryEntity>,
        enrollment: DeviceEnrollmentStatus,
        isDeviceOnline: Boolean,
        hasUsageAccess: Boolean,
        hasLocationPermission: Boolean,
        isVpnPrepared: Boolean
    ): List<PolicyConflict> {
        val conflicts = mutableListOf<PolicyConflict>()

        // 1. Conflicting App Rules (both allowed and blocked)
        val overlap = policy.allowedPackages.intersect(policy.blockedPackages.toSet())
        if (overlap.isNotEmpty()) {
            conflicts.add(
                PolicyConflict(
                    code = "CONFLICTING_APP_RULES",
                    title = "Conflicting App Rules (${overlap.size})",
                    description = "Packages appear in both Allow and Block lists: ${overlap.joinToString(", ")}. Tier 3 Allow takes precedence.",
                    severity = EventSeverity.WARNING
                )
            )
        }

        // 2. Duplicate / Overridden Category vs Exception
        val overriddenApps = apps.filter {
            it.packageName in policy.allowedPackages && it.category in policy.blockedCategories
        }
        if (overriddenApps.isNotEmpty()) {
            conflicts.add(
                PolicyConflict(
                    code = "CATEGORY_EXCEPTION_OVERRIDE",
                    title = "Individual Exceptions Override Blocked Categories",
                    description = "${overriddenApps.joinToString { it.appLabel }} remain allowed despite category blocks.",
                    severity = EventSeverity.INFO
                )
            )
        }

        // 3. Domain allowlist/blocklist conflict
        val domainOverlap = policy.allowedDomains.intersect(policy.blockedDomains.toSet())
        if (domainOverlap.isNotEmpty()) {
            conflicts.add(
                PolicyConflict(
                    code = "DOMAIN_CONFLICT",
                    title = "Conflicting Website Rules",
                    description = "Domains present in both Allowlist and Blocklist: ${domainOverlap.joinToString()}",
                    severity = EventSeverity.WARNING
                )
            )
        }

        // 4. Invalid schedules
        if (policy.downtimeEnabled && policy.bedtimeStart == policy.bedtimeEnd) {
            conflicts.add(
                PolicyConflict(
                    code = "INVALID_BEDTIME",
                    title = "Invalid Bedtime Schedule",
                    description = "Bedtime start and end times (${policy.bedtimeStart}) are identical.",
                    severity = EventSeverity.WARNING
                )
            )
        }
        if (policy.schoolTimeStart == policy.schoolTimeEnd) {
            conflicts.add(
                PolicyConflict(
                    code = "INVALID_SCHOOL_TIME",
                    title = "Invalid School Time Schedule",
                    description = "School Time start and end times (${policy.schoolTimeStart}) are identical.",
                    severity = EventSeverity.WARNING
                )
            )
        }

        // 5. Unsupported policies on Standard Supervision
        if (!enrollment.isDeviceOwner &&
            (policy.uninstallBlocked || policy.unknownSourcesBlocked || policy.usbDebuggingBlocked || policy.playStoreApprovalRequired)
        ) {
            conflicts.add(
                PolicyConflict(
                    code = "UNSUPPORTED_WITHOUT_DO",
                    title = "Device Owner Enrollment Required",
                    description = "Uninstall lock, USB debugging lock, unknown sources lock, and Managed Play policies require Android Enterprise Device Owner provisioning.",
                    severity = EventSeverity.WARNING
                )
            )
        }

        // 6. Missing permissions
        if (!hasUsageAccess) {
            conflicts.add(
                PolicyConflict(
                    code = "MISSING_USAGE_STATS",
                    title = "Missing Usage Access Permission",
                    description = "App screen time tracking requires Usage Access on the supervised device.",
                    severity = EventSeverity.CRITICAL
                )
            )
        }
        if (policy.locationSharingEnabled && !hasLocationPermission) {
            conflicts.add(
                PolicyConflict(
                    code = "MISSING_LOCATION_PERMISSION",
                    title = "Missing Location Permission",
                    description = "Location sharing is enabled in policy, but Location permission is not granted.",
                    severity = EventSeverity.WARNING
                )
            )
        }
        if (policy.dnsFilteringEnabled && !isVpnPrepared) {
            conflicts.add(
                PolicyConflict(
                    code = "MISSING_VPN_PERMISSION",
                    title = "Local DNS VPN Permission Required",
                    description = "Website & DNS filtering requires user VPN authorization on the child device.",
                    severity = EventSeverity.WARNING
                )
            )
        }

        // 7. Offline / Failed Sync
        if (!isDeviceOnline) {
            conflicts.add(
                PolicyConflict(
                    code = "DEVICE_OFFLINE",
                    title = "Supervised Device Offline",
                    description = "Policy changes will queue in Room SyncQueue until the device reconnects.",
                    severity = EventSeverity.WARNING
                )
            )
        }
        if (policy.lastActionState == ActionExecutionState.FAILED.name) {
            conflicts.add(
                PolicyConflict(
                    code = "SYNC_FAILED",
                    title = "Last Policy Synchronization Failed",
                    description = "Retry synchronization or verify enrollment status.",
                    severity = EventSeverity.CRITICAL
                )
            )
        }
        return conflicts
    }
}

object PolicySimulator {
    fun simulate(
        policy: SupervisionPolicy,
        apps: List<CachedAppInventoryEntity>,
        enrollment: DeviceEnrollmentStatus,
        isDeviceOnline: Boolean,
        hasUsageAccess: Boolean,
        hasLocationPermission: Boolean,
        isVpnPrepared: Boolean
    ): PolicySimulationResult {
        val maxAge = AgeRating.fromString(policy.maxAgeRating)
        val affectedApps = apps.filter { app ->
            val appRating = AgeRating.fromString(app.ageRating)
            val isExplicitAllowed = app.packageName in policy.allowedPackages
            val isBlocked = !isExplicitAllowed && (
                app.packageName in policy.blockedPackages ||
                    app.category in policy.blockedCategories ||
                    appRating.minAge > maxAge.minAge
                )
            isBlocked
        }
        val exceptions = apps.filter { it.packageName in policy.allowedPackages }.map { it.appLabel }
        val conflicts = PolicyConflictDetector.detect(
            policy, apps, enrollment, isDeviceOnline, hasUsageAccess, hasLocationPermission, isVpnPrepared
        )
        val unsupported = mutableListOf<String>()
        if (!enrollment.isDeviceOwner) {
            if (policy.uninstallBlocked) unsupported.add("Uninstall lock (requires Device Owner)")
            if (policy.usbDebuggingBlocked) unsupported.add("USB debugging restriction (requires Device Owner)")
            if (policy.unknownSourcesBlocked) unsupported.add("Unknown sources restriction (requires Device Owner)")
            if (policy.playStoreApprovalRequired || policy.iapRestricted) {
                unsupported.add("Managed Google Play purchase/install lock (requires Device Owner)")
            }
        }
        return PolicySimulationResult(
            appsAffectedCount = affectedApps.size,
            affectedAppLabels = affectedApps.map { it.appLabel },
            websitesAffectedCount = policy.blockedDomains.size,
            effectiveDailyMinutes = policy.dailyLimitMinutes + policy.bonusMinutesToday,
            exceptionLabels = exceptions,
            conflicts = conflicts,
            unsupportedActions = unsupported
        )
    }
}
