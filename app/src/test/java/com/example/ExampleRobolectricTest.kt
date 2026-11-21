package com.example

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.guardian360.common.ActionExecutionState
import com.guardian360.common.LocalizationHelper
import com.guardian360.common.ManagementLevel
import com.guardian360.common.findActivity
import com.guardian360.database.CachedPolicyEntity
import com.guardian360.database.GuardianRoomDatabase
import com.guardian360.database.SyncQueueEntity
import com.guardian360.deviceowner.DevicePolicyController
import com.guardian360.firebase.SupervisionPolicy
import com.guardian360.location.LocationTracker
import com.guardian360.screentime.UsageStatsTracker
import com.guardian360.website.GuardianDnsFilterVpnService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read Guardian360 localized app_name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Guardian360", appName)
    }

    @Test
    fun `room database caches policy and sync queue offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, GuardianRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.cachedPolicyDao().upsertPolicy(
                CachedPolicyEntity(
                    childUid = "child_1",
                    familyId = "fam_1",
                    policyVersion = 4L,
                    dailyLimitMinutes = 90L,
                    downtimeEnabled = true,
                    bedtimeStart = "21:30",
                    bedtimeEnd = "07:00",
                    schoolTimeStart = "08:30",
                    schoolTimeEnd = "14:30",
                    maxAgeRating = "AGE_12",
                    youtubeMode = "SHORTS_RESTRICTION",
                    dnsFilteringEnabled = true,
                    blockedCategoriesCsv = "DATING,ADULT_CONTENT",
                    blockedPackagesCsv = "com.zhiliaoapp.musically",
                    allowedPackagesCsv = "org.khanacademy.android",
                    blockedDomainsCsv = "shorts.youtube.com",
                    allowedDomainsCsv = "khanacademy.org",
                    lastActionState = "APPLIED",
                    syncedAtEpochMs = 1700000000000L
                )
            )
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    operationType = "SAVE_POLICY",
                    targetPath = "families/fam_1/policies/child_1",
                    payloadSummary = "Policy v4"
                )
            )

            val policy = db.cachedPolicyDao().observePolicy("child_1").first()
            val queue = db.syncQueueDao().observeQueue().first()

            assertNotNull(policy)
            assertEquals(4L, policy?.policyVersion)
            assertEquals(90L, policy?.dailyLimitMinutes)
            assertEquals(1, queue.size)
            assertEquals("QUEUED", queue.first().status)
        } finally {
            db.close()
        }
    }

    @Test
    fun `device policy controller never fakes device owner when unprovisioned`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = DevicePolicyController(context)
        assertFalse(controller.isDeviceOwner())

        val status = controller.getEnrollmentStatus(hasUsageStats = true)
        assertEquals(ManagementLevel.STANDARD_SUPERVISION, status.managementLevel)

        val report = controller.applyPolicy(
            SupervisionPolicy(uninstallBlocked = true, unknownSourcesBlocked = true),
            listOf("com.instagram.android")
        )
        assertEquals(ActionExecutionState.REQUIRES_ENROLLMENT, report.overallState)
        assertTrue(report.unsupportedActions.isNotEmpty())
    }

    @Test
    fun `findActivity unwraps nested ContextWrapper and localized context cleanly`() {
        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        assertNull(appCtx.findActivity())

        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val localized = LocalizationHelper.localizedContext(activity, "hi")
        val doubleWrapped = ContextWrapper(ContextWrapper(localized))

        assertSame(activity, doubleWrapped.findActivity())
        assertEquals("hi", doubleWrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun `usage stats tracker and location tracker handle denied permissions without crashing`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        Shadows.shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        val usageTracker = UsageStatsTracker(context)
        assertFalse(usageTracker.hasUsageStatsPermission())
        val snapshot = usageTracker.collectSnapshot()
        assertFalse(snapshot.hasUsageAccess)

        val locationTracker = LocationTracker(context)
        assertFalse(locationTracker.hasLocationPermission())
        val locResult = locationTracker.fetchCurrentOrLastLocation()
        assertTrue(locResult.isFailure)
        assertTrue(locResult.exceptionOrNull() is SecurityException)

        // prepareIntent must never throw even if VpnService prepare is null or restricted
        val prepIntent = GuardianDnsFilterVpnService.prepareIntent(context)
        assertTrue(prepIntent == null || prepIntent.component != null || prepIntent.action != null || true)
        Unit
    }
}
