package com.guardian360

import android.content.pm.ApplicationInfo
import com.guardian360.common.AgeRating
import com.guardian360.common.ManagementLevel
import com.guardian360.common.QrCodeGenerator
import com.guardian360.common.SecurityUtils
import com.guardian360.database.CachedAppInventoryEntity
import com.guardian360.deviceowner.DeviceEnrollmentStatus
import com.guardian360.firebase.SupervisionPolicy
import com.guardian360.location.LocationTracker
import com.guardian360.policies.AppCategory
import com.guardian360.policies.AppCategoryCatalog
import com.guardian360.policies.GameSubcategory
import com.guardian360.policies.PolicyConflictDetector
import com.guardian360.policies.PolicySimulator
import com.guardian360.website.GuardianDnsFilterVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianCoreTests {

    @Test
    fun `catalog defines all 21 categories and 15 game subcategories without guessing unknown`() {
        assertEquals(21, AppCategory.entries.size)
        assertEquals(15, GameSubcategory.entries.size)

        val unknownApp = ApplicationInfo().apply {
            packageName = "com.unknown.custom.app"
            category = ApplicationInfo.CATEGORY_UNDEFINED
        }
        val (cat, _, _) = AppCategoryCatalog.classify(unknownApp)
        assertEquals(AppCategory.UNKNOWN, cat)
    }

    @Test
    fun `5 tier policy precedence handles emergency lock exception and category rules`() {
        val policy = SupervisionPolicy(
            blockedCategories = listOf(AppCategory.SOCIAL_MEDIA.name),
            blockedPackages = listOf("com.roblox.client"),
            allowedPackages = listOf("com.instagram.android"),
            maxAgeRating = AgeRating.AGE_12.name
        )

        // Tier 1: Emergency dialer always allowed even when locked
        val dialer = AppCategoryCatalog.evaluatePackageAccess(
            packageName = "com.android.dialer",
            appCategory = AppCategory.UTILITIES,
            appAgeRating = AgeRating.AGE_3,
            guardianPackage = "com.aistudio.guardian360.vrmqkt",
            policy = policy,
            deviceLocked = true,
            isDowntimeActive = true
        )
        assertTrue(dialer.allowed)

        // Tier 2: Device lock blocks non-emergency apps even if allowed
        val lockedIg = AppCategoryCatalog.evaluatePackageAccess(
            packageName = "com.instagram.android",
            appCategory = AppCategory.SOCIAL_MEDIA,
            appAgeRating = AgeRating.AGE_12,
            guardianPackage = "com.aistudio.guardian360.vrmqkt",
            policy = policy,
            deviceLocked = true,
            isDowntimeActive = false
        )
        assertFalse(lockedIg.allowed)

        // Tier 3: Individual exception overrides blocked category
        val allowedIg = AppCategoryCatalog.evaluatePackageAccess(
            packageName = "com.instagram.android",
            appCategory = AppCategory.SOCIAL_MEDIA,
            appAgeRating = AgeRating.AGE_12,
            guardianPackage = "com.aistudio.guardian360.vrmqkt",
            policy = policy,
            deviceLocked = false,
            isDowntimeActive = false
        )
        assertTrue(allowedIg.allowed)

        // Tier 4: Age rating blocks 18+ app even if category not blocked
        val adultRated = AppCategoryCatalog.evaluatePackageAccess(
            packageName = "com.tinder",
            appCategory = AppCategory.ENTERTAINMENT,
            appAgeRating = AgeRating.AGE_18,
            guardianPackage = "com.aistudio.guardian360.vrmqkt",
            policy = policy,
            deviceLocked = false,
            isDowntimeActive = false
        )
        assertFalse(adultRated.allowed)
    }

    @Test
    fun `conflict detector and simulator report conflicts and affected counts`() {
        val apps = listOf(
            CachedAppInventoryEntity("com.instagram.android", "Instagram", "SOCIAL_MEDIA", null, "AGE_12", false, false, 15, 60, 0),
            CachedAppInventoryEntity("com.tinder", "Tinder", "DATING", null, "AGE_18", false, false, 0, 0, 0)
        )
        val policy = SupervisionPolicy(
            dailyLimitMinutes = 60L,
            bonusMinutesToday = 15L,
            allowedPackages = listOf("com.instagram.android"),
            blockedPackages = listOf("com.instagram.android"),
            blockedCategories = listOf("DATING"),
            blockedDomains = listOf("shorts.youtube.com", "meta.ai"),
            allowedDomains = listOf("meta.ai")
        )
        val enrollment = DeviceEnrollmentStatus(false, false, ManagementLevel.STANDARD_SUPERVISION, "Standard")
        val sim = PolicySimulator.simulate(
            policy = policy,
            apps = apps,
            enrollment = enrollment,
            isDeviceOnline = false,
            hasUsageAccess = false,
            hasLocationPermission = false,
            isVpnPrepared = false
        )
        assertEquals(1, sim.appsAffectedCount) // Tinder blocked, Instagram allowed via Tier 3 override
        assertEquals(75L, sim.effectiveDailyMinutes)
        assertTrue(sim.conflicts.any { it.code == "CONFLICTING_APP_RULES" })
        assertTrue(sim.conflicts.any { it.code == "DOMAIN_CONFLICT" })
        assertTrue(sim.conflicts.any { it.code == "DEVICE_OFFLINE" })
        assertTrue(sim.unsupportedActions.isNotEmpty())
    }

    @Test
    fun `salted pin hashing verifies valid pin and rejects wrong pin`() {
        val hash1 = SecurityUtils.hashPin("482910")
        val hash2 = SecurityUtils.hashPin("482910")
        assertNotEquals(hash1, hash2) // unique random salt per hash
        assertTrue(SecurityUtils.verifyPin("482910", hash1))
        assertFalse(SecurityUtils.verifyPin("000000", hash1))
    }

    @Test
    fun `qr generator produces valid 29x29 finder patterns`() {
        val matrix = QrCodeGenerator.generateMatrix("guardian360://pair?code=AB12CD34")
        assertEquals(29, matrix.size)
        assertTrue(matrix[0][0]) // top-left finder outer corner
        assertTrue(matrix[3][3]) // top-left finder center
        assertFalse(matrix[1][1]) // top-left finder ring
    }

    @Test
    fun `dns parser extracts domain builds nxdomain and respects allowlist precedence`() {
        // Minimal DNS query for "shorts.youtube.com"
        val header = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val qname = byteArrayOf(
            6, 's'.code.toByte(), 'h'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 't'.code.toByte(), 's'.code.toByte(),
            7, 'y'.code.toByte(), 'o'.code.toByte(), 'u'.code.toByte(), 't'.code.toByte(), 'u'.code.toByte(), 'b'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0, 0x00, 0x01, 0x00, 0x01
        )
        val packet = header + qname
        assertEquals("shorts.youtube.com", GuardianDnsFilterVpnService.extractDnsDomain(packet))

        val nx = GuardianDnsFilterVpnService.buildNxDomainResponse(packet)
        assertEquals(0x83.toByte(), nx[3])

        GuardianDnsFilterVpnService.updateDomainRules(
            blocked = setOf("youtube.com", "meta.ai"),
            allowed = setOf("edu.youtube.com")
        )
        assertTrue(GuardianDnsFilterVpnService.isDomainBlocked("shorts.youtube.com"))
        assertFalse(GuardianDnsFilterVpnService.isDomainBlocked("edu.youtube.com"))
    }

    @Test
    fun `location freshness and geofence distance calculation are accurate`() {
        val now = 1_700_000_000_000L
        assertTrue(LocationTracker.isLive(now - 2 * 60_000L, now))
        assertFalse(LocationTracker.isLive(now - 6 * 60_000L, now))
        val zeroDist = LocationTracker.distanceMeters(26.1445, 91.7362, 26.1445, 91.7362)
        assertEquals(0.0, zeroDist, 0.01)
    }
}
