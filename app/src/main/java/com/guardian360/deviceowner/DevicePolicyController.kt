package com.guardian360.deviceowner

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import com.guardian360.common.ActionExecutionState
import com.guardian360.common.ManagementLevel
import com.guardian360.common.YouTubeMode
import com.guardian360.common.safeStartActivity
import com.guardian360.firebase.SupervisionPolicy

data class DeviceEnrollmentStatus(
    val isDeviceOwner: Boolean,
    val isDeviceAdminActive: Boolean,
    val managementLevel: ManagementLevel,
    val summary: String
)

data class PolicyApplyReport(
    val overallState: ActionExecutionState,
    val appliedActions: List<String>,
    val unsupportedActions: List<String>
)

class DevicePolicyController(private val context: Context) {
    private val dpm: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    val adminComponent: ComponentName =
        ComponentName(context.applicationContext, GuardianDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean =
        runCatching { dpm?.isDeviceOwnerApp(context.packageName) == true }.getOrDefault(false)

    fun isAdminActive(): Boolean =
        runCatching { dpm?.isAdminActive(adminComponent) == true }.getOrDefault(false)

    fun getEnrollmentStatus(hasUsageStats: Boolean): DeviceEnrollmentStatus {
        val owner = isDeviceOwner()
        val admin = isAdminActive()
        val level = when {
            owner -> ManagementLevel.FULLY_MANAGED
            admin || hasUsageStats -> ManagementLevel.STANDARD_SUPERVISION
            else -> ManagementLevel.ENROLLMENT_PENDING
        }
        val summary = when (level) {
            ManagementLevel.FULLY_MANAGED ->
                "Device Owner active. Full Android Enterprise restrictions, uninstall lock, and Managed Play policies enabled."
            ManagementLevel.STANDARD_SUPERVISION ->
                "Standard Supervision active. Usage stats, DNS filtering, and location sharing operate via granted permissions; Device Owner restrictions require enterprise provisioning."
            ManagementLevel.ENROLLMENT_PENDING ->
                "Grant Usage Access and/or activate Device Admin to complete Standard Supervision."
            else -> level.label
        }
        return DeviceEnrollmentStatus(
            isDeviceOwner = owner,
            isDeviceAdminActive = admin,
            managementLevel = level,
            summary = summary
        )
    }

    fun buildActivateAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate Guardian360 Device Supervision for authorized device lock and tamper protection."
            )
        }

    fun openActivateAdmin(hostContext: Context = context): Result<Unit> {
        val candidates = listOf(
            buildActivateAdminIntent(),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            val res = hostContext.safeStartActivity(intent)
            if (res.isSuccess) return res
        }
        return Result.failure(IllegalStateException("Device Administrator settings are not available on this device."))
    }

    fun buildQrProvisioningPayload(enrollmentToken: String): String {
        return """
            {
              "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "${adminComponent.flattenToString()}",
              "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
                "guardian360_enrollment_token": "$enrollmentToken"
              }
            }
        """.trimIndent()
    }

    fun lockDeviceNow(): ActionExecutionState {
        val manager = dpm ?: return ActionExecutionState.UNSUPPORTED
        if (!isAdminActive() && !isDeviceOwner()) {
            return ActionExecutionState.REQUIRES_ENROLLMENT
        }
        return runCatching {
            manager.lockNow()
            ActionExecutionState.APPLIED
        }.getOrElse { ActionExecutionState.FAILED }
    }

    fun setLockTaskMode(activity: Activity, enable: Boolean): ActionExecutionState {
        val manager = dpm ?: return ActionExecutionState.UNSUPPORTED
        if (!isDeviceOwner()) return ActionExecutionState.REQUIRES_ENROLLMENT
        return runCatching {
            if (enable) {
                manager.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
                activity.startLockTask()
            } else {
                activity.stopLockTask()
            }
            ActionExecutionState.APPLIED
        }.getOrElse { ActionExecutionState.FAILED }
    }

    fun applyPolicy(policy: SupervisionPolicy, packagesToBlock: List<String>): PolicyApplyReport {
        val manager = dpm ?: return PolicyApplyReport(
            overallState = ActionExecutionState.UNSUPPORTED,
            appliedActions = emptyList(),
            unsupportedActions = listOf("DevicePolicyManager unavailable on this device")
        )
        val owner = isDeviceOwner()
        val applied = mutableListOf<String>()
        val unsupported = mutableListOf<String>()

        if (owner) {
            runCatching {
                manager.setUninstallBlocked(adminComponent, context.packageName, policy.uninstallBlocked)
                applied.add("Uninstall protection (${policy.uninstallBlocked})")
            }.onFailure { unsupported.add("Uninstall protection failed: ${it.localizedMessage}") }

            runCatching {
                if (policy.unknownSourcesBlocked) {
                    manager.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                } else {
                    manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                }
                applied.add("Unknown sources restriction (${policy.unknownSourcesBlocked})")
            }

            runCatching {
                if (policy.usbDebuggingBlocked) {
                    manager.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                } else {
                    manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
                }
                applied.add("USB debugging restriction (${policy.usbDebuggingBlocked})")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    val safeList = packagesToBlock.filter { it != context.packageName }.toTypedArray()
                    if (safeList.isNotEmpty()) {
                        manager.setPackagesSuspended(adminComponent, safeList, true)
                    }
                    applied.add("Package suspension applied (${safeList.size} packages)")
                }.onFailure { unsupported.add("Package suspension failed: ${it.localizedMessage}") }
            }

            // Managed YouTube restrictions via Android Enterprise App Restrictions
            runCatching {
                val ytMode = YouTubeMode.fromString(policy.youtubeMode)
                val ytBundle = Bundle().apply {
                    putBoolean("restrict_mode", ytMode != YouTubeMode.NORMAL)
                    putBoolean("enable_restricted_mode", ytMode != YouTubeMode.NORMAL)
                }
                manager.setApplicationRestrictions(adminComponent, "com.google.android.youtube", ytBundle)
                applied.add("Managed YouTube restriction bundle (${ytMode.label})")
            }

            // Managed Play Store restrictions bundle
            runCatching {
                val playBundle = Bundle().apply {
                    putBoolean("require_parent_approval", policy.playStoreApprovalRequired)
                    putBoolean("restrict_in_app_purchases", policy.iapRestricted)
                    putBoolean("restrict_paid_apps", policy.paidAppsRestricted)
                    putString("max_content_rating", policy.maxAgeRating)
                }
                manager.setApplicationRestrictions(adminComponent, "com.android.vending", playBundle)
                applied.add("Managed Google Play restriction bundle (${policy.maxAgeRating})")
            }
        } else {
            if (policy.uninstallBlocked) unsupported.add("Uninstall Lock requires Device Owner enrollment")
            if (policy.unknownSourcesBlocked) unsupported.add("Unknown Sources Lock requires Device Owner enrollment")
            if (policy.usbDebuggingBlocked) unsupported.add("USB Debugging Lock requires Device Owner enrollment")
            if (packagesToBlock.isNotEmpty()) unsupported.add("Silent OS Package Suspension requires Device Owner; Standard mode uses Screen-Time & Restricted Screen guidance")
            if (policy.playStoreApprovalRequired || policy.iapRestricted) {
                unsupported.add("Managed Google Play enterprise enforcement requires Device Owner provisioning")
            }
        }

        val state = when {
            owner && unsupported.isEmpty() -> ActionExecutionState.APPLIED
            owner && applied.isNotEmpty() -> ActionExecutionState.APPLIED
            !owner -> ActionExecutionState.REQUIRES_ENROLLMENT
            else -> ActionExecutionState.UNSUPPORTED
        }
        return PolicyApplyReport(state, applied, unsupported)
    }
}
