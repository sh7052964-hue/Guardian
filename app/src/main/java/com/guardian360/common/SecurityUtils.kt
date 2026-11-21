package com.guardian360.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.security.SecureRandom

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Context.safeStartActivity(intent: Intent): Result<Unit> = runCatching {
    val activity = findActivity()
    if (activity != null) {
        activity.startActivity(intent)
    } else {
        val wrapped = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(wrapped)
    }
}.recoverCatching { err ->
    if (err is ActivityNotFoundException) {
        throw IllegalStateException("No compatible system screen is available on this device.", err)
    }
    throw err
}

object SecurityUtils {
    private val random = SecureRandom()
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val DEFAULT_ADMIN_ACCESS_CODE = "admin"

    fun generatePairingCode(length: Int = 8): String {
        return buildString(length) {
            repeat(length) {
                append(CODE_CHARS[random.nextInt(CODE_CHARS.length)])
            }
        }
    }

    /**
     * Produces a salted SHA-256 hash formatted as "saltHex:digestHex".
     * Raw PINs and admin access codes are never persisted locally or in Room.
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val digest = sha256(salt + pin.toByteArray(Charsets.UTF_8))
        return "${salt.toHex()}:${digest.toHex()}"
    }

    fun verifyPin(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank() || !storedHash.contains(":")) return false
        val parts = storedHash.split(":", limit = 2)
        val salt = parts[0].fromHexOrNull() ?: return false
        val expected = parts[1]
        val actual = sha256(salt + pin.toByteArray(Charsets.UTF_8)).toHex()
        return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }

    /**
     * Verifies the Admin Panel access code. When no custom hash has been saved yet,
     * falls back to the initial default "admin" code (per admin panel specification).
     */
    fun verifyAdminAccessCode(input: String, storedHash: String?): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        if (storedHash.isNullOrBlank()) {
            return MessageDigest.isEqual(
                DEFAULT_ADMIN_ACCESS_CODE.toByteArray(Charsets.UTF_8),
                trimmed.toByteArray(Charsets.UTF_8)
            )
        }
        return verifyPin(trimmed, storedHash)
    }

    fun validateAdminAccessCode(raw: String): String? {
        val trimmed = raw.trim()
        return when {
            trimmed.length < 4 -> "Admin access code must be at least 4 characters."
            trimmed.length > 32 -> "Admin access code must be 32 characters or fewer."
            trimmed.any { it.isWhitespace() } -> "Admin access code cannot contain spaces."
            else -> null
        }
    }

    fun normalizePairingCode(raw: String): String =
        raw.trim().uppercase().removePrefix("PAIR_").filter { it.isLetterOrDigit() }

    fun sha256Hex(input: String): String =
        sha256(input.toByteArray(Charsets.UTF_8)).toHex()

    fun maskPairingCode(code: String): String {
        val clean = normalizePairingCode(code)
        if (clean.length <= 4) return "••••"
        return "${clean.take(2)}••••${clean.takeLast(2)}"
    }

    fun pairingSessionDocId(code: String): String {
        val clean = normalizePairingCode(code)
        return "pair_${sha256Hex(clean).take(24)}"
    }

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHexOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
    }

    fun isBiometricAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val manager = context.getSystemService(BiometricManager::class.java) ?: return false
            manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    fun authenticateBiometric(
        context: Context,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Biometric prompt requires Android 9+")
            return
        }
        val hostContext = context.findActivity() ?: context
        runCatching {
            val executor = ContextCompat.getMainExecutor(hostContext)
            val prompt = BiometricPrompt.Builder(hostContext)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton(negativeButtonText, executor) { _, _ ->
                    onError("Biometric authentication cancelled")
                }
                .build()

            prompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        onError(errString?.toString() ?: "Biometric authentication failed")
                    }

                    override fun onAuthenticationFailed() {
                        onError("Biometric not recognized")
                    }
                }
            )
        }.onFailure { err ->
            onError(err.localizedMessage ?: "Biometric prompt unavailable on this device")
        }
    }
}

/**
 * Pure Kotlin ISO/IEC 18004 Byte-mode ECC-L QR Code Generator supporting Versions 3, 5, 6, 8, and 10
 * (up to 271 UTF-8 bytes) with full 8-mask penalty evaluation and multi-block RS interleaving.
 * Produces camera-scannable QR codes for both short pairing codes and full HTTPS APK URLs / JSON.
 */
object QrCodeGenerator {

    data class QrVersionSpec(
        val version: Int,
        val size: Int,
        val maxBytes: Int,
        val totalDataCodewords: Int,
        val ecPerBlock: Int,
        val blockDataCounts: List<Int>,
        val alignmentCenters: List<Int>,
        val remainderBits: Int
    )

    private val specs = listOf(
        QrVersionSpec(
            version = 3,
            size = 29,
            maxBytes = 53,
            totalDataCodewords = 55,
            ecPerBlock = 15,
            blockDataCounts = listOf(55),
            alignmentCenters = listOf(6, 22),
            remainderBits = 7
        ),
        QrVersionSpec(
            version = 5,
            size = 37,
            maxBytes = 106,
            totalDataCodewords = 108,
            ecPerBlock = 26,
            blockDataCounts = listOf(108),
            alignmentCenters = listOf(6, 30),
            remainderBits = 7
        ),
        QrVersionSpec(
            version = 6,
            size = 41,
            maxBytes = 134,
            totalDataCodewords = 136,
            ecPerBlock = 18,
            blockDataCounts = listOf(68, 68),
            alignmentCenters = listOf(6, 34),
            remainderBits = 7
        ),
        QrVersionSpec(
            version = 8,
            size = 49,
            maxBytes = 192,
            totalDataCodewords = 194,
            ecPerBlock = 24,
            blockDataCounts = listOf(97, 97),
            alignmentCenters = listOf(6, 24, 42),
            remainderBits = 0
        ),
        QrVersionSpec(
            version = 10,
            size = 57,
            maxBytes = 271,
            totalDataCodewords = 274,
            ecPerBlock = 18,
            blockDataCounts = listOf(68, 68, 69, 69),
            alignmentCenters = listOf(6, 28, 50),
            remainderBits = 0
        )
    )

    private val formatInfosL = intArrayOf(
        0x77C4, 0x72F3, 0x7DAA, 0x789D,
        0x662F, 0x6318, 0x6C41, 0x6976
    )

    fun selectSpecForByteCount(byteCount: Int): QrVersionSpec =
        specs.firstOrNull { byteCount <= it.maxBytes } ?: specs.last()

    fun generateMatrix(payload: String): Array<BooleanArray> {
        val rawBytes = payload.toByteArray(Charsets.UTF_8)
        val spec = selectSpecForByteCount(rawBytes.size)
        val clipped = if (rawBytes.size <= spec.maxBytes) rawBytes else rawBytes.copyOf(spec.maxBytes)

        val bits = MutableList(0) { 0 }
        fun pushBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator (0100); Versions 1..9 use 8-bit count, Versions 10+ use 16-bit count
        pushBits(0b0100, 4)
        pushBits(clipped.size, if (spec.version >= 10) 16 else 8)
        for (b in clipped) {
            pushBits(b.toInt() and 0xFF, 8)
        }

        val maxDataBits = spec.totalDataCodewords * 8
        val term = minOf(4, maxDataBits - bits.size)
        pushBits(0, term)
        while (bits.size % 8 != 0) bits.add(0)
        var padIndex = 0
        while (bits.size < maxDataBits) {
            pushBits(if (padIndex % 2 == 0) 0xEC else 0x11, 8)
            padIndex++
        }

        val dataCodewords = IntArray(spec.totalDataCodewords) { i ->
            var v = 0
            for (b in 0 until 8) v = (v shl 1) or bits[i * 8 + b]
            v
        }

        // Split data codewords across RS blocks and compute interleaved stream
        val dataBlocks = mutableListOf<IntArray>()
        val ecBlocks = mutableListOf<IntArray>()
        var offset = 0
        for (len in spec.blockDataCounts) {
            val blk = dataCodewords.copyOfRange(offset, offset + len)
            dataBlocks.add(blk)
            ecBlocks.add(reedSolomonEc(blk, spec.ecPerBlock))
            offset += len
        }

        val interleaved = ArrayList<Int>(
            spec.totalDataCodewords + spec.blockDataCounts.size * spec.ecPerBlock
        )
        val maxDataBlockLen = spec.blockDataCounts.maxOrNull() ?: 0
        for (i in 0 until maxDataBlockLen) {
            for (blk in dataBlocks) {
                if (i < blk.size) interleaved.add(blk[i])
            }
        }
        for (i in 0 until spec.ecPerBlock) {
            for (ec in ecBlocks) {
                interleaved.add(ec[i])
            }
        }

        val allBits = IntArray(interleaved.size * 8 + spec.remainderBits)
        for (i in interleaved.indices) {
            for (b in 0 until 8) {
                allBits[i * 8 + b] = (interleaved[i] ushr (7 - b)) and 1
            }
        }

        val size = spec.size
        val baseModules = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun setModule(r: Int, c: Int, dark: Boolean) {
            baseModules[r][c] = dark
            reserved[r][c] = true
        }

        fun drawFinder(top: Int, left: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = top + dr
                    val c = left + dc
                    if (r in 0 until size && c in 0 until size) {
                        val isBorder = dr == -1 || dr == 7 || dc == -1 || dc == 7
                        val isOuter = dr == 0 || dr == 6 || dc == 0 || dc == 6
                        val isInner = dr in 2..4 && dc in 2..4
                        setModule(r, c, !isBorder && (isOuter || isInner))
                    }
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Draw alignment patterns for all valid center pairs
        for (cr in spec.alignmentCenters) {
            for (cc in spec.alignmentCenters) {
                val overlapsFinder =
                    (cr <= 8 && cc <= 8) ||
                        (cr <= 8 && cc >= size - 9) ||
                        (cr >= size - 9 && cc <= 8)
                if (!overlapsFinder) {
                    for (dr in -2..2) {
                        for (dc in -2..2) {
                            val dark = dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0)
                            setModule(cr + dr, cc + dc, dark)
                        }
                    }
                }
            }
        }

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            if (!reserved[6][i]) setModule(6, i, dark)
            if (!reserved[i][6]) setModule(i, 6, dark)
        }

        // Dark module
        setModule(size - 8, 8, true)

        // Reserve format info
        for (i in 0..8) {
            if (i != 6) {
                reserved[8][i] = true
                reserved[i][8] = true
            }
        }
        for (i in 0..7) {
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }

        // Version Info blocks for Version >= 7
        if (spec.version >= 7) {
            val verInfo = computeVersionInfo(spec.version)
            for (i in 0 until 18) {
                val bit = ((verInfo ushr i) and 1) == 1
                val a = i / 3
                val b = size - 11 + (i % 3)
                setModule(a, b, bit)
                setModule(b, a, bit)
            }
        }

        // Evaluate all 8 ISO/IEC 18004 masks and pick the lowest penalty matrix
        var bestMatrix: Array<BooleanArray>? = null
        var bestPenalty = Int.MAX_VALUE

        for (maskId in 0 until 8) {
            val candidate = Array(size) { r -> baseModules[r].clone() }
            var bitIdx = 0
            var upward = true
            var rightCol = size - 1
            while (rightCol >= 1) {
                if (rightCol == 6) rightCol--
                for (step in 0 until size) {
                    val r = if (upward) size - 1 - step else step
                    for (off in 0..1) {
                        val c = rightCol - off
                        if (!reserved[r][c]) {
                            val bit = if (bitIdx < allBits.size) allBits[bitIdx++] else 0
                            val maskBit = if (isMasked(maskId, r, c)) bit xor 1 else bit
                            candidate[r][c] = maskBit == 1
                        }
                    }
                }
                upward = !upward
                rightCol -= 2
            }

            val fmt = formatInfosL[maskId]
            for (i in 0..5) candidate[8][i] = ((fmt ushr (14 - i)) and 1) == 1
            candidate[8][7] = ((fmt ushr 8) and 1) == 1
            candidate[8][8] = ((fmt ushr 7) and 1) == 1
            candidate[7][8] = ((fmt ushr 6) and 1) == 1
            for (i in 9..14) candidate[14 - i][8] = ((fmt ushr (14 - i)) and 1) == 1

            for (i in 0..7) candidate[size - 1 - i][8] = ((fmt ushr i) and 1) == 1
            for (i in 8..14) candidate[8][size - 15 + i] = ((fmt ushr i) and 1) == 1

            val penalty = evaluatePenalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMatrix = candidate
            }
        }

        return bestMatrix!!
    }

    private fun isMasked(maskId: Int, r: Int, c: Int): Boolean = when (maskId) {
        0 -> (r + c) % 2 == 0
        1 -> r % 2 == 0
        2 -> c % 3 == 0
        3 -> (r + c) % 3 == 0
        4 -> ((r / 2) + (c / 3)) % 2 == 0
        5 -> ((r * c) % 2) + ((r * c) % 3) == 0
        6 -> (((r * c) % 2) + ((r * c) % 3)) % 2 == 0
        else -> (((r + c) % 2) + ((r * c) % 3)) % 2 == 0
    }

    private fun evaluatePenalty(m: Array<BooleanArray>): Int {
        val n = m.size
        var penalty = 0
        // Rule 1: 5+ consecutive same-color modules in rows & cols
        for (r in 0 until n) {
            var runRow = 1
            var runCol = 1
            for (c in 1 until n) {
                if (m[r][c] == m[r][c - 1]) runRow++ else {
                    if (runRow >= 5) penalty += 3 + (runRow - 5)
                    runRow = 1
                }
                if (m[c][r] == m[c - 1][r]) runCol++ else {
                    if (runCol >= 5) penalty += 3 + (runCol - 5)
                    runCol = 1
                }
            }
            if (runRow >= 5) penalty += 3 + (runRow - 5)
            if (runCol >= 5) penalty += 3 + (runCol - 5)
        }
        // Rule 2: 2x2 blocks of same color
        for (r in 0 until n - 1) {
            for (c in 0 until n - 1) {
                val b = m[r][c]
                if (m[r][c + 1] == b && m[r + 1][c] == b && m[r + 1][c + 1] == b) {
                    penalty += 3
                }
            }
        }
        // Rule 4: dark ratio balance
        var darkCount = 0
        for (r in 0 until n) for (c in 0 until n) if (m[r][c]) darkCount++
        val pct = (darkCount * 100) / (n * n)
        val prev5 = (pct / 5) * 5
        val next5 = prev5 + 5
        val k = minOf(kotlin.math.abs(prev5 - 50) / 5, kotlin.math.abs(next5 - 50) / 5)
        penalty += k * 10
        return penalty
    }

    private fun computeVersionInfo(version: Int): Int {
        var data = version shl 12
        val gen = 0x1F25
        for (bit in 17 downTo 12) {
            if (((data ushr bit) and 1) != 0) {
                data = data xor (gen shl (bit - 12))
            }
        }
        return (version shl 12) or data
    }

    private fun reedSolomonEc(data: IntArray, ecLen: Int): IntArray {
        val gen = rsGenerator(ecLen)
        val res = IntArray(ecLen)
        for (b in data) {
            val factor = b xor res[0]
            System.arraycopy(res, 1, res, 0, ecLen - 1)
            res[ecLen - 1] = 0
            for (j in 0 until ecLen) {
                res[j] = res[j] xor gfMul(gen[j], factor)
            }
        }
        return res
    }

    private fun rsGenerator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = gfPow2(i)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], root)
            }
            poly = next
        }
        return poly.copyOfRange(1, poly.size)
    }

    private fun gfPow2(exp: Int): Int {
        var v = 1
        repeat(exp) {
            v = v shl 1
            if (v >= 256) v = v xor 0x11D
        }
        return v
    }

    private fun gfMul(a: Int, b: Int): Int {
        var x = a
        var y = b
        var res = 0
        while (y > 0) {
            if ((y and 1) != 0) res = res xor x
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
            y = y ushr 1
        }
        return res
    }
}
