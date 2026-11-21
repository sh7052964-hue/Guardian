package com.guardian360.youtube

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
import androidx.compose.material3.RadioButton
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
import com.guardian360.common.YouTubeMode
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel
import com.guardian360.website.GuardianDnsFilterVpnService

@Composable
fun YouTubeControlsScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel
) {
    val context = LocalContext.current
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }

    if (device == null || policy == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStatePanel(title = "No Child Device Paired", message = "Pair a child device to configure YouTube supervision.")
        }
        return
    }

    var mode by remember(policy.policyVersion) { mutableStateOf(YouTubeMode.fromString(policy.youtubeMode)) }
    var ytDailyLimit by remember(policy.policyVersion) { mutableFloatStateOf(policy.youtubeDailyLimitMinutes.toFloat()) }
    var blockYouTubeApp by remember(policy.policyVersion) {
        mutableStateOf("com.google.android.youtube" in policy.blockedPackages)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "YouTube Advanced Control",
            subtitle = "Supported Managed Play Restricted Mode, Shorts web/URL filtering, and usage limits."
        )

        GuardianCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)) {
            Text("Platform Capability Transparency", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Guardian360 enforces YouTube daily limits, Managed Restricted Mode (Device Owner), and known Shorts web/link blocking via local DNS filtering. Official YouTube app internals cannot be altered directly, so Shorts shelves inside the native app cannot be guaranteed 100% removed; YouTube Kids is recommended for ages under 13.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        GuardianCard {
            Text("YouTube Supervision Mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            YouTubeMode.entries.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    RadioButton(selected = mode == option, onClick = { mode = option })
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(option.label, style = MaterialTheme.typography.titleSmall)
                        Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        GuardianCard {
            Text("YouTube Daily Limit: ${ytDailyLimit.toInt()} minutes", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = ytDailyLimit,
                onValueChange = { ytDailyLimit = it },
                valueRange = 0f..180f,
                steps = 17
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Block YouTube App Completely", style = MaterialTheme.typography.titleSmall)
                    Text("Adds com.google.android.youtube to blocked packages", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = blockYouTubeApp, onCheckedChange = { blockYouTubeApp = it })
            }
        }

        Button(
            onClick = {
                val updatedBlockedPkgs = if (blockYouTubeApp) {
                    (policy.blockedPackages + "com.google.android.youtube").distinct()
                } else {
                    policy.blockedPackages.filterNot { it == "com.google.android.youtube" }
                }
                val shortsDomains = if (mode != YouTubeMode.NORMAL) {
                    (policy.blockedDomains + "shorts.youtube.com").distinct()
                } else policy.blockedDomains
                val candidate = policy.copy(
                    youtubeMode = mode.name,
                    youtubeDailyLimitMinutes = ytDailyLimit.toLong(),
                    blockedPackages = updatedBlockedPkgs,
                    blockedDomains = shortsDomains
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
            Text("Simulate & Apply YouTube Controls")
        }
    }
}
