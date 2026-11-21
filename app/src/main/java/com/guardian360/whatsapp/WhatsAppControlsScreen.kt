package com.guardian360.whatsapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.GuardianCard
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel
import com.guardian360.website.GuardianDnsFilterVpnService

@Composable
fun WhatsAppControlsScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel
) {
    val context = LocalContext.current
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }

    if (device == null || policy == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStatePanel(title = "No Child Device Paired", message = "Pair a child device to configure WhatsApp & Meta AI controls.")
        }
        return
    }

    var waDailyLimit by remember(policy.policyVersion) { mutableFloatStateOf(policy.whatsappDailyLimitMinutes.toFloat()) }
    var blockWhatsApp by remember(policy.policyVersion) { mutableStateOf("com.whatsapp" in policy.blockedPackages) }
    var restrictMetaAiWeb by remember(policy.policyVersion) { mutableStateOf(policy.whatsappMetaAiWebRestricted) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "WhatsApp & Meta AI Controls",
            subtitle = "Usage limits, scheduling, supported Android permissions, and Meta AI web filtering."
        )

        GuardianCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)) {
            Text("Privacy & Boundary Guarantee", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Guardian360 NEVER modifies WhatsApp's internal UI and NEVER reads, logs, or intercepts private messages. Meta AI web endpoints (ai.meta.com, meta.ai) can be blocked via DNS filtering, while built-in app features remain governed by WhatsApp itself.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        GuardianCard {
            Text("WhatsApp Daily Limit: ${waDailyLimit.toInt()} minutes", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = waDailyLimit,
                onValueChange = { waDailyLimit = it },
                valueRange = 0f..180f,
                steps = 17
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Block WhatsApp Application", style = MaterialTheme.typography.titleSmall)
                    Text("Restricts com.whatsapp access on child device", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = blockWhatsApp, onCheckedChange = { blockWhatsApp = it })
            }
        }

        GuardianCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Meta AI Web & URL Restrictions", style = MaterialTheme.typography.titleMedium)
                    Text("Blocks ai.meta.com & meta.ai via local DNS filter", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = restrictMetaAiWeb, onCheckedChange = { restrictMetaAiWeb = it })
            }
            Spacer(Modifier.height(8.dp))
            StatusPill(
                text = if (restrictMetaAiWeb) "Web Endpoints Restricted" else "Web Endpoints Allowed",
                color = if (restrictMetaAiWeb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }

        Button(
            onClick = {
                val updatedBlockedPkgs = if (blockWhatsApp) {
                    (policy.blockedPackages + "com.whatsapp").distinct()
                } else {
                    policy.blockedPackages.filterNot { it == "com.whatsapp" }
                }
                val metaDomains = setOf("ai.meta.com", "meta.ai")
                val updatedDomains = if (restrictMetaAiWeb) {
                    (policy.blockedDomains + metaDomains).distinct()
                } else {
                    policy.blockedDomains.filterNot { it in metaDomains }
                }
                val candidate = policy.copy(
                    whatsappDailyLimitMinutes = waDailyLimit.toLong(),
                    whatsappMetaAiWebRestricted = restrictMetaAiWeb,
                    blockedPackages = updatedBlockedPkgs,
                    blockedDomains = updatedDomains
                )
                viewModel.simulatePolicyChange(
                    candidate,
                    device,
                    bundle.localInventory,
                    false,
                    GuardianDnsFilterVpnService.prepareIntent(context) == null
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Simulate & Apply WhatsApp Controls")
        }
    }
}
