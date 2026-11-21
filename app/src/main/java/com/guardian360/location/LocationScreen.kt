package com.guardian360.location

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.EmeraldShield
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianCard
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.common.safeStartActivity
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel
import com.guardian360.website.GuardianDnsFilterVpnService

@Composable
fun LocationScreen(
    bundle: ParentOverviewBundle,
    selectedChildUid: String?,
    viewModel: ParentViewModel
) {
    val context = LocalContext.current
    val device = bundle.devices.firstOrNull { it.childUid == selectedChildUid } ?: bundle.devices.firstOrNull()
    val policy = bundle.policies.firstOrNull { it.childUid == device?.childUid }

    if (device == null || policy == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStatePanel(
                icon = Icons.Rounded.LocationOn,
                title = "No Child Device Paired",
                message = "Pair a child device to view explicit, consent-based location updates and Family Places."
            )
        }
        return
    }

    val childLocations = bundle.locations.filter { it.childUid == device.childUid }
    val latest = childLocations.firstOrNull()

    var sharingEnabled by remember(policy.policyVersion) { mutableStateOf(policy.locationSharingEnabled) }
    var homeLat by remember(policy.policyVersion) { mutableStateOf(policy.homeLat.toString()) }
    var homeLng by remember(policy.policyVersion) { mutableStateOf(policy.homeLng.toString()) }
    var schoolLat by remember(policy.policyVersion) { mutableStateOf(policy.schoolLat.toString()) }
    var schoolLng by remember(policy.policyVersion) { mutableStateOf(policy.schoolLng.toString()) }
    var retentionDays by remember(policy.policyVersion) { mutableStateOf(policy.retentionDays.toString()) }
    var mapLaunchError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "Location & Family Places",
            subtitle = "Transparent location sharing with explicit freshness verification (stale locations are never labeled live)."
        )

        GuardianCard {
            if (latest == null) {
                EmptyStatePanel(
                    icon = Icons.Rounded.LocationOn,
                    title = "Awaiting Child Location Check-In",
                    message = "Location updates appear here when the child device shares location with explicit OS permission."
                )
            } else {
                val isLive = LocationTracker.isLive(latest.capturedAtEpochMs)
                val rel = DateUtils.getRelativeTimeSpanString(latest.capturedAtEpochMs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLive) "Current Location" else "Last Known Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    StatusPill(
                        text = if (isLive) "Live ($rel)" else "Last Known ($rel)",
                        color = if (isLive) EmeraldShield else AlertAmber
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Coordinates: ${"%.5f".format(latest.latitude)}, ${"%.5f".format(latest.longitude)}" +
                        (latest.accuracyMeters?.let { " (±${it.toInt()}m)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!latest.placeLabel.isNullOrBlank()) {
                    Text(
                        text = "Place: ${latest.placeLabel} (${latest.eventKind})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        mapLaunchError = null
                        val geoUri = Uri.parse("geo:${latest.latitude},${latest.longitude}?q=${latest.latitude},${latest.longitude}(${Uri.encode(device.childName)})")
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                        context.safeStartActivity(mapIntent).onFailure {
                            mapLaunchError = it.localizedMessage ?: "No Maps application is installed on this device."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Maps")
                }
                if (mapLaunchError != null) {
                    Spacer(Modifier.height(8.dp))
                    ErrorBanner(message = mapLaunchError!!)
                }
            }
        }

        // Family Places Configuration
        GuardianCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Visible Location Sharing", style = MaterialTheme.typography.titleSmall)
                    Text("Always displays a clear sharing badge on Child Dashboard", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = sharingEnabled, onCheckedChange = { sharingEnabled = it })
            }
            Spacer(Modifier.height(12.dp))
            Text("Home Place Coordinates", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = homeLat, onValueChange = { homeLat = it }, label = { Text("Home Lat") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = homeLng, onValueChange = { homeLng = it }, label = { Text("Home Lng") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("School Place Coordinates", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = schoolLat, onValueChange = { schoolLat = it }, label = { Text("School Lat") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = schoolLng, onValueChange = { schoolLng = it }, label = { Text("School Lng") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = retentionDays,
                onValueChange = { retentionDays = it.filter(Char::isDigit).take(3) },
                label = { Text("Location History Retention (Days)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val candidate = policy.copy(
                        locationSharingEnabled = sharingEnabled,
                        homeLat = homeLat.toDoubleOrNull() ?: policy.homeLat,
                        homeLng = homeLng.toDoubleOrNull() ?: policy.homeLng,
                        schoolLat = schoolLat.toDoubleOrNull() ?: policy.schoolLat,
                        schoolLng = schoolLng.toDoubleOrNull() ?: policy.schoolLng,
                        retentionDays = (retentionDays.toLongOrNull() ?: 30L).coerceIn(1L, 365L)
                    )
                    viewModel.simulatePolicyChange(
                        candidate,
                        device,
                        bundle.localInventory,
                        false,
                        GuardianDnsFilterVpnService.prepareIntent(context) == null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simulate & Save Location Places") }
        }

        // Arrival / Departure History
        if (childLocations.isNotEmpty()) {
            SectionHeader(title = "Location & Place History")
            childLocations.take(10).forEach { loc ->
                GuardianCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${loc.eventKind}: ${loc.placeLabel ?: "Check-in"}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = DateUtils.getRelativeTimeSpanString(loc.capturedAtEpochMs).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${"%.4f".format(loc.latitude)}, ${"%.4f".format(loc.longitude)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
