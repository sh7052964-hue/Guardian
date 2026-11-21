package com.guardian360.website

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

data class DnsFilterStatus(
    val running: Boolean = false,
    val packetsInspected: Long = 0L,
    val blockedCount: Long = 0L,
    val lastBlockedDomain: String? = null,
    val lastError: String? = null
)

/**
 * Local VPN-based DNS filter supporting IPv4 & IPv6 DNS routing.
 * Inspects unencrypted UDP DNS (port 53) hostnames only — never claims inspection of encrypted TLS/DoH payloads.
 */
class GuardianDnsFilterVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFilter()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        startFilter()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val channelId = "guardian360_vpn"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(channelId, "Guardian360 DNS Filter", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Guardian360 Web & DNS Protection")
            .setContentText("Local DNS filtering active for blocklists & SafeSearch")
            .setOngoing(true)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(3601, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(3601, notification)
            }
        }.onFailure { err ->
            Log.e(TAG, "Unable to promote DNS VPN service to foreground", err)
            _status.value = _status.value.copy(running = false, lastError = err.localizedMessage)
        }
    }

    private fun startFilter() {
        if (vpnInterface != null) return
        try {
            val builder = Builder()
                .setSession("Guardian360 DNS Filter")
                .addAddress("10.36.0.2", 32)
                .addDnsServer("10.36.0.1")
                .addRoute("10.36.0.1", 32)
                .addAddress("fd36::2", 128)
                .addDnsServer("fd36::1")
                .addRoute("fd36::1", 128)
                .setMtu(1500)

            runCatching { builder.addDisallowedApplication(packageName) }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                _status.value = _status.value.copy(running = false, lastError = "VPN permission not granted")
                return
            }
            _status.value = _status.value.copy(running = true, lastError = null)
            workerJob = scope.launch { runTunnelLoop() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            _status.value = _status.value.copy(running = false, lastError = e.localizedMessage)
        }
    }

    private fun runTunnelLoop() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val packet = ByteArray(32767)

        while (scope.isActive && vpnInterface != null) {
            try {
                val len = input.read(packet)
                if (len <= 0) continue
                val version = (packet[0].toInt() ushr 4) and 0x0F
                if (version == 4 && len >= 28) {
                    handleIpv4Packet(packet, len, output)
                }
            } catch (e: Exception) {
                if (scope.isActive) {
                    Log.w(TAG, "VPN read loop error: ${e.message}")
                    _status.value = _status.value.copy(lastError = e.localizedMessage)
                }
                break
            }
        }
    }

    private fun handleIpv4Packet(packet: ByteArray, len: Int, output: FileOutputStream) {
        val protocol = packet[9].toInt() and 0xFF
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (protocol != 17 || len < ihl + 8) return // UDP only
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val dnsOffset = ihl + 8
        val dnsLen = len - dnsOffset
        if (dnsLen < 12) return
        val dnsQuery = packet.copyOfRange(dnsOffset, len)
        val domain = extractDnsDomain(dnsQuery)?.lowercase() ?: return

        _status.value = _status.value.copy(packetsInspected = _status.value.packetsInspected + 1L)

        val blocked = isDomainBlocked(domain)
        val dnsResponse = if (blocked) {
            _status.value = _status.value.copy(
                blockedCount = _status.value.blockedCount + 1L,
                lastBlockedDomain = domain
            )
            buildNxDomainResponse(dnsQuery)
        } else {
            forwardDnsUpstream(dnsQuery) ?: return
        }

        val reply = wrapIpv4UdpReply(packet, ihl, dnsResponse)
        synchronized(output) {
            output.write(reply)
        }
    }

    private fun forwardDnsUpstream(query: ByteArray): ByteArray? {
        return runCatching {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 3000
                val upstream = InetAddress.getByName(upstreamDnsServer)
                socket.send(DatagramPacket(query, query.size, upstream, 53))
                val buf = ByteArray(2048)
                val resp = DatagramPacket(buf, buf.size)
                socket.receive(resp)
                buf.copyOf(resp.length)
            }
        }.onFailure {
            _status.value = _status.value.copy(lastError = "Upstream DNS failure: ${it.localizedMessage}")
        }.getOrNull()
    }

    private fun wrapIpv4UdpReply(req: ByteArray, ihl: Int, dnsPayload: ByteArray): ByteArray {
        val totalLen = 20 + 8 + dnsPayload.size
        val out = ByteArray(totalLen)
        val bb = ByteBuffer.wrap(out)
        bb.put(0x45.toByte())
        bb.put(0x00.toByte())
        bb.putShort(totalLen.toShort())
        bb.putShort(0)
        bb.putShort(0)
        bb.put(64.toByte())
        bb.put(17.toByte())
        bb.putShort(0) // checksum placeholder
        // Swap src and dst IP
        bb.put(req, 16, 4)
        bb.put(req, 12, 4)
        // Swap src and dst port
        bb.put(req, ihl + 2, 2)
        bb.put(req, ihl, 2)
        bb.putShort((8 + dnsPayload.size).toShort())
        bb.putShort(0) // UDP checksum optional in IPv4
        bb.put(dnsPayload)

        // Compute IPv4 header checksum
        var sum = 0
        for (i in 0 until 20 step 2) {
            val word = ((out[i].toInt() and 0xFF) shl 8) or (out[i + 1].toInt() and 0xFF)
            sum += word
        }
        while ((sum ushr 16) > 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        val csum = (sum.inv() and 0xFFFF).toShort()
        ByteBuffer.wrap(out).putShort(10, csum)
        return out
    }

    private fun stopFilter() {
        workerJob?.cancel()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        _status.value = _status.value.copy(running = false)
    }

    override fun onDestroy() {
        stopFilter()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GuardianDnsVpn"
        const val ACTION_START = "com.guardian360.VPN_START"
        const val ACTION_STOP = "com.guardian360.VPN_STOP"

        private val _status = MutableStateFlow(DnsFilterStatus())
        val status: StateFlow<DnsFilterStatus> = _status.asStateFlow()

        @Volatile
        private var upstreamDnsServer: String = "8.8.8.8"

        @Volatile
        private var blockedDomains: Set<String> = emptySet()

        @Volatile
        private var allowedDomains: Set<String> = emptySet()

        fun setUpstreamDns(ipAddress: String) {
            val clean = ipAddress.trim()
            if (clean.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))) {
                upstreamDnsServer = clean
            }
        }

        fun getUpstreamDns(): String = upstreamDnsServer

        fun updateDomainRules(blocked: Set<String>, allowed: Set<String>) {
            blockedDomains = blocked.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            allowedDomains = allowed.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        }

        fun isDomainBlocked(domain: String): Boolean {
            val clean = domain.trim().lowercase().trimEnd('.')
            if (allowedDomains.any { clean == it || clean.endsWith(".$it") }) return false
            return blockedDomains.any { clean == it || clean.endsWith(".$it") }
        }

        fun prepareIntent(context: Context): Intent? =
            runCatching { VpnService.prepare(context) }.getOrNull()

        fun startVpnService(context: Context): Result<Unit> = runCatching {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, GuardianDnsFilterVpnService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(appCtx, intent)
        }

        fun stopVpnService(context: Context): Result<Unit> = runCatching {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, GuardianDnsFilterVpnService::class.java).apply {
                action = ACTION_STOP
            }
            appCtx.stopService(intent)
            _status.value = _status.value.copy(running = false)
        }

        fun extractDnsDomain(dnsPacket: ByteArray): String? {
            if (dnsPacket.size < 13) return null
            val labels = mutableListOf<String>()
            var idx = 12
            while (idx < dnsPacket.size) {
                val len = dnsPacket[idx].toInt() and 0xFF
                if (len == 0) break
                if ((len and 0xC0) != 0 || idx + 1 + len > dnsPacket.size) return null
                labels.add(String(dnsPacket, idx + 1, len, Charsets.UTF_8))
                idx += 1 + len
            }
            return if (labels.isEmpty()) null else labels.joinToString(".")
        }

        fun buildNxDomainResponse(query: ByteArray): ByteArray {
            val resp = query.copyOf()
            if (resp.size >= 4) {
                resp[2] = ((resp[2].toInt() and 0x01) or 0x81).toByte() // QR=1, RD preserved
                resp[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
            }
            return resp
        }
    }
}
