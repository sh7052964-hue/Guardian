package com.example

import com.guardian360.common.ApkDistributionHelper
import com.guardian360.common.QrCodeGenerator
import com.guardian360.common.SecurityUtils
import com.guardian360.firebase.FirebaseConfigValidator
import com.guardian360.website.GuardianDnsFilterVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `pairing code normalization strips prefix and punctuation`() {
        assertEquals("K7M9X4Q2", SecurityUtils.normalizePairingCode(" pair_k7m9-x4q2 "))
        assertEquals("ABCD1234", SecurityUtils.normalizePairingCode("ab cd 1234"))
    }

    @Test
    fun `sha256Hex is deterministic and lowercase 64 hex chars`() {
        val hashA = SecurityUtils.sha256Hex("K7M9X4Q2")
        val hashB = SecurityUtils.sha256Hex("K7M9X4Q2")
        val hashOther = SecurityUtils.sha256Hex("K7M9X4Q3")

        assertEquals(64, hashA.length)
        assertEquals(hashA, hashB)
        assertNotEquals(hashA, hashOther)
        assertTrue(hashA.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `maskPairingCode preserves only boundary characters`() {
        assertEquals("K7••••Q2", SecurityUtils.maskPairingCode("K7M9X4Q2"))
        assertEquals("••••", SecurityUtils.maskPairingCode("ABCD"))
    }

    @Test
    fun `pairingSessionDocId produces stable pair_ prefixed deterministic id`() {
        val id1 = SecurityUtils.pairingSessionDocId("pair_k7m9-x4q2")
        val id2 = SecurityUtils.pairingSessionDocId("K7M9X4Q2")

        assertTrue(id1.startsWith("pair_"))
        assertEquals(29, id1.length)
        assertEquals(id1, id2)
    }

    @Test
    fun `pin hashing verifies valid pin and rejects wrong pin`() {
        val hash = SecurityUtils.hashPin("4829")
        assertTrue(SecurityUtils.verifyPin("4829", hash))
        assertFalse(SecurityUtils.verifyPin("4820", hash))
        assertFalse(SecurityUtils.verifyPin("4829", null))
    }

    @Test
    fun `admin access code defaults to admin and rotates securely with sha256`() {
        assertTrue(SecurityUtils.verifyAdminAccessCode("admin", null))
        assertTrue(SecurityUtils.verifyAdminAccessCode(" admin ", ""))
        assertFalse(SecurityUtils.verifyAdminAccessCode("wrong", null))

        val customHash = SecurityUtils.hashPin("Guard#2026")
        assertTrue(SecurityUtils.verifyAdminAccessCode("Guard#2026", customHash))
        assertFalse(SecurityUtils.verifyAdminAccessCode("admin", customHash))

        assertNull(SecurityUtils.validateAdminAccessCode("admin"))
        assertNotNull(SecurityUtils.validateAdminAccessCode("abc"))
        assertNotNull(SecurityUtils.validateAdminAccessCode("bad code"))
    }

    @Test
    fun `qr code generator scales across versions up to long https apk urls`() {
        val shortMatrix = QrCodeGenerator.generateMatrix("guardian360://pair?code=K7M9X4Q2")
        assertEquals(29, shortMatrix.size)

        val mediumUrl = "https://github.com/org/guardian360/releases/download/v1.0.0/Guardian360-release-signed.apk"
        val mediumMatrix = QrCodeGenerator.generateMatrix(mediumUrl)
        assertEquals(37, mediumMatrix.size)

        val longProvisionJson = "{\"android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME\":\"com.aistudio.guardian360.vrmqkt/com.guardian360.deviceowner.GuardianDeviceAdminReceiver\",\"android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION\":\"https://downloads.guardian360.org/v1/guardian360-universal.apk\"}"
        val largeMatrix = QrCodeGenerator.generateMatrix(longProvisionJson)
        assertEquals(57, largeMatrix.size)
        // Finder top-left corner check
        assertTrue(largeMatrix[0][0])
        assertTrue(largeMatrix[6][6])
        assertFalse(largeMatrix[1][1])
    }

    @Test
    fun `apk distribution url validation checks scheme and host`() {
        assertTrue(ApkDistributionHelper.isValidDownloadUrl("https://downloads.example.com/app.apk"))
        assertTrue(ApkDistributionHelper.isValidDownloadUrl("http://192.168.1.20:8080/Guardian360.apk"))
        assertFalse(ApkDistributionHelper.isValidDownloadUrl("javascript:alert(1)"))
        assertFalse(ApkDistributionHelper.isValidDownloadUrl("not a url"))
    }

    @Test
    fun `firebase config validator detects package alignment`() {
        assertTrue(
            FirebaseConfigValidator.isPackageAligned(
                manifestPackage = "com.aistudio.guardian360.vrmqkt",
                googleServicesPackage = "com.aistudio.guardian360.vrmqkt"
            )
        )
        assertFalse(
            FirebaseConfigValidator.isPackageAligned(
                manifestPackage = "com.aistudio.guardian360.vrmqkt",
                googleServicesPackage = "com.guardian360"
            )
        )
        assertFalse(
            FirebaseConfigValidator.isPackageAligned(
                manifestPackage = "",
                googleServicesPackage = "com.aistudio.guardian360.vrmqkt"
            )
        )
    }

    @Test
    fun `dns domain rules prioritize allowlist and build valid nxdomain response`() {
        GuardianDnsFilterVpnService.updateDomainRules(
            blocked = setOf("youtube.com", "example.org"),
            allowed = setOf("kids.youtube.com")
        )
        assertTrue(GuardianDnsFilterVpnService.isDomainBlocked("youtube.com"))
        assertTrue(GuardianDnsFilterVpnService.isDomainBlocked("shorts.youtube.com"))
        assertFalse(GuardianDnsFilterVpnService.isDomainBlocked("kids.youtube.com"))
        assertFalse(GuardianDnsFilterVpnService.isDomainBlocked("khanacademy.org"))

        val fakeQuery = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01)
        val nx = GuardianDnsFilterVpnService.buildNxDomainResponse(fakeQuery)
        assertEquals(fakeQuery.size, nx.size)
        assertEquals(0x81.toByte(), nx[2]) // QR=1, RD=1
        assertEquals(0x83.toByte(), nx[3]) // RA=1, RCODE=3 (NXDOMAIN)
    }
}
