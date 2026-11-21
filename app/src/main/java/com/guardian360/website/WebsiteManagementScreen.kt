package com.guardian360.website

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.GuardianMonoStyle
import com.example.ui.theme.PastelBlue
import com.example.ui.theme.PastelMint
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.SecureGreen
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianCard
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel

@Composable
fun WebsiteManagementScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel
) {
    val context = LocalContext.current
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }
    val vpnStatus by GuardianDnsFilterVpnService.status.collectAsStateWithLifecycle()

    if (device == null || policy == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStatePanel(
                title = "No Child Device Paired",
                message = "Pair a child device to manage website allowlists, blocklists, and local DNS filtering."
            )
        }
        return
    }

    var safeSearch by remember(policy.policyVersion) { mutableStateOf(policy.safeSearchEnabled) }
    var explicitFilter by remember(policy.policyVersion) { mutableStateOf(policy.explicitContentFilterEnabled) }
    var dnsFilter by remember(policy.policyVersion) { mutableStateOf(policy.dnsFilteringEnabled) }
    var blockedDomains by remember(policy.policyVersion) { mutableStateOf(policy.blockedDomains) }
    var allowedDomains by remember(policy.policyVersion) { mutableStateOf(policy.allowedDomains) }
    var selectedDns by remember { mutableStateOf(GuardianDnsFilterVpnService.getUpstreamDns()) }
    var customDnsInput by remember { mutableStateOf("") }
    var listTab by remember { mutableIntStateOf(0) } // 0 = Block List, 1 = Allow List
    var newDomain by remember { mutableStateOf("") }
    var testDomainInput by remember { mutableStateOf("") }
    var testDomainResult by remember { mutableStateOf<String?>(null) }
    var vpnActionError by remember { mutableStateOf<String?>(null) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GuardianDnsFilterVpnService.setUpstreamDns(selectedDns)
            GuardianDnsFilterVpnService.updateDomainRules(blockedDomains.toSet(), allowedDomains.toSet())
            GuardianDnsFilterVpnService.startVpnService(context)
                .onSuccess { vpnActionError = null }
                .onFailure { vpnActionError = it.localizedMessage ?: "Unable to start DNS VPN service." }
        } else {
            vpnActionError = "VPN permission was declined. DNS filtering remains on standby until approved."
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val heroBgColor = when {
        vpnStatus.running && isDark -> MaterialTheme.colorScheme.secondaryContainer
        vpnStatus.running -> PastelMint
        isDark -> MaterialTheme.colorScheme.primaryContainer
        else -> PastelBlue
    }
    val heroTitleColor = when {
        vpnStatus.running && isDark -> MaterialTheme.colorScheme.onSecondaryContainer
        isDark -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> androidx.compose.ui.graphics.Color(0xFF0F172A)
    }
    val heroSubColor = when {
        isDark -> heroTitleColor.copy(alpha = 0.85f)
        else -> androidx.compose.ui.graphics.Color(0xFF334155)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val activeError = vpnActionError ?: vpnStatus.lastError
        if (!activeError.isNullOrBlank()) {
            ErrorBanner(message = activeError)
        }

        // Section 1: DNS Protection Hero Card (Matching PDF Page 3 Left & Page 7)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = heroBgColor,
            border = BorderStroke(1.dp, (if (vpnStatus.running) SecureGreen else RoyalCobaltStart).copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (vpnStatus.running) SecureGreen else RoyalCobaltStart,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Dns, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (vpnStatus.running) "DNS is Protected" else "DNS Protection Ready",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = heroTitleColor
                    )
                    Text(
                        text = "Resolver: $selectedDns • Queries: ${vpnStatus.packetsInspected} • Blocked: ${vpnStatus.blockedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = heroSubColor
                    )
                }
                StatusPill(
                    text = if (vpnStatus.running) "Active" else "Standby",
                    color = if (vpnStatus.running) EmeraldShield else RoyalCobaltStart
                )
            }
        }

        // DNS Security & Resolver Card
        GuardianCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VpnLock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("DNS Security Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Switch(checked = dnsFilter, onCheckedChange = { dnsFilter = it })
            }
            Spacer(Modifier.height(8.dp))
            Text("Upstream Safe DNS Provider", style = MaterialTheme.typography.labelLarge)
            listOf(
                "8.8.8.8" to "Google DNS (8.8.8.8)",
                "1.1.1.1" to "Cloudflare DNS (1.1.1.1)",
                "1.1.1.3" to "Cloudflare Family (1.1.1.3)"
            ).forEach { (ip, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedDns == ip,
                        onClick = {
                            selectedDns = ip
                            GuardianDnsFilterVpnService.setUpstreamDns(ip)
                        }
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customDnsInput,
                    onValueChange = { customDnsInput = it.trim() },
                    label = { Text("Custom DNS IPv4 (e.g. 9.9.9.9)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        if (customDnsInput.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))) {
                            selectedDns = customDnsInput
                            GuardianDnsFilterVpnService.setUpstreamDns(customDnsInput)
                        }
                    }
                ) { Text("Set") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        vpnActionError = null
                        GuardianDnsFilterVpnService.setUpstreamDns(selectedDns)
                        GuardianDnsFilterVpnService.updateDomainRules(blockedDomains.toSet(), allowedDomains.toSet())
                        val prepare = GuardianDnsFilterVpnService.prepareIntent(context)
                        if (prepare != null) {
                            runCatching { vpnLauncher.launch(prepare) }
                                .onFailure { vpnActionError = "Unable to request VPN permission on this device." }
                        } else {
                            GuardianDnsFilterVpnService.startVpnService(context)
                                .onFailure { vpnActionError = it.localizedMessage ?: "Unable to start DNS VPN service." }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("start_dns_vpn_btn")
                ) { Text("Start DNS VPN") }

                OutlinedButton(
                    onClick = {
                        GuardianDnsFilterVpnService.stopVpnService(context)
                            .onFailure { vpnActionError = it.localizedMessage }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Stop VPN") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Local VpnService inspects unencrypted UDP port-53 DNS lookups only; never claims TLS payload inspection.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 2: Website Protection Card (Matching PDF Page 3 Center)
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, contentDescription = null, tint = RoyalCobaltStart)
                Spacer(Modifier.width(8.dp))
                Text("Website Protection Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Block Explicit & 18+ Domains", style = MaterialTheme.typography.titleSmall)
                    Text("Filter known adult & malware hostnames", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = explicitFilter, onCheckedChange = { explicitFilter = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SafeSearch Enforcement", style = MaterialTheme.typography.titleSmall)
                    Text("Apply SafeSearch filter preferences", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = safeSearch, onCheckedChange = { safeSearch = it })
            }
        }

        // Section 3: Block List / Allow List Tabs + Add Website (Matching PDF Page 7)
        GuardianCard {
            Text("Website Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = listTab == 0,
                    onClick = { listTab = 0 },
                    label = { Text("Block List (${blockedDomains.size})") }
                )
                FilterChip(
                    selected = listTab == 1,
                    onClick = { listTab = 1 },
                    label = { Text("Allow List (${allowedDomains.size})") }
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newDomain,
                onValueChange = { newDomain = it.lowercase().trim() },
                label = { Text("Add domain (e.g. shorts.youtube.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("domain_rule_input")
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (newDomain.isNotBlank()) {
                        if (listTab == 0) {
                            blockedDomains = (blockedDomains + newDomain).distinct()
                            allowedDomains = allowedDomains.filterNot { it == newDomain }
                        } else {
                            allowedDomains = (allowedDomains + newDomain).distinct()
                            blockedDomains = blockedDomains.filterNot { it == newDomain }
                        }
                        newDomain = ""
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (listTab == 0) "+ Add Website to Block List" else "+ Add Website to Allow List")
            }

            Spacer(Modifier.height(10.dp))
            val activeList = if (listTab == 0) blockedDomains else allowedDomains
            if (activeList.isEmpty()) {
                Text(
                    text = if (listTab == 0) "No custom blocked domains yet." else "No custom allowed domains yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                activeList.forEach { dom ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (listTab == 0) Icons.Rounded.RemoveCircle else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (listTab == 0) DangerCrimson else SecureGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(dom, style = GuardianMonoStyle)
                        }
                        IconButton(onClick = {
                            if (listTab == 0) blockedDomains = blockedDomains.filterNot { it == dom }
                            else allowedDomains = allowedDomains.filterNot { it == dom }
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove $dom", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Section 4: Test DNS Security (Matching PDF Page 3 Left Bottom CTA)
        GuardianCard {
            Text("Test DNS Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = testDomainInput,
                    onValueChange = { testDomainInput = it.lowercase().trim() },
                    label = { Text("Check hostname") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        GuardianDnsFilterVpnService.updateDomainRules(blockedDomains.toSet(), allowedDomains.toSet())
                        val blocked = GuardianDnsFilterVpnService.isDomainBlocked(testDomainInput)
                        testDomainResult = if (blocked) "BLOCKED by active DNS rules" else "ALLOWED by active DNS rules"
                    }
                ) { Text("Test") }
            }
            if (testDomainResult != null) {
                Spacer(Modifier.height(6.dp))
                Text(testDomainResult!!, style = GuardianMonoStyle, color = MaterialTheme.colorScheme.primary)
            }
        }

        Button(
            onClick = {
                GuardianDnsFilterVpnService.setUpstreamDns(selectedDns)
                GuardianDnsFilterVpnService.updateDomainRules(blockedDomains.toSet(), allowedDomains.toSet())
                val candidate = policy.copy(
                    safeSearchEnabled = safeSearch,
                    explicitContentFilterEnabled = explicitFilter,
                    dnsFilteringEnabled = dnsFilter,
                    blockedDomains = blockedDomains,
                    allowedDomains = allowedDomains
                )
                viewModel.simulatePolicyChange(
                    candidate,
                    device,
                    bundle.localInventory,
                    false,
                    GuardianDnsFilterVpnService.prepareIntent(context) == null
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = RoyalCobaltStart),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Rounded.Shield, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Simulate & Save DNS & Website Policy")
        }
    }
}
