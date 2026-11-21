package com.guardian360.approvals

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.EmeraldShield
import com.guardian360.common.ApprovalRequestType
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.GuardianCard
import com.guardian360.common.SectionHeader
import com.guardian360.common.StatusPill
import com.guardian360.parent.ParentOverviewBundle
import com.guardian360.parent.ParentViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApprovalsScreen(
    bundle: ParentOverviewBundle,
    viewModel: ParentViewModel
) {
    val pending = bundle.approvals.filter { it.status == "PENDING" }
    val decided = bundle.approvals.filter { it.status != "PENDING" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "Approval Center & Temporary Permissions",
            subtitle = "Server-authorized decisions with 5m, 15m, 30m, and 1h expiring access."
        )

        if (pending.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Rounded.CheckCircle,
                title = "All Caught Up",
                message = "No pending child requests for extra screen time, apps, or websites."
            )
        } else {
            pending.forEach { req ->
                val policy = bundle.policies.firstOrNull { it.childUid == req.childUid }
                var selectedDuration by remember(req.id) { mutableLongStateOf(req.requestedMinutes ?: 15L) }
                GuardianCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ApprovalRequestType.fromString(req.requestType).label, style = MaterialTheme.typography.titleMedium)
                            Text("From: ${req.childName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(text = "PENDING", color = AlertAmber)
                    }
                    Spacer(Modifier.height(8.dp))
                    val target = req.targetLabel ?: req.targetPackage ?: req.targetDomain ?: "${req.requestedMinutes ?: 15} minutes"
                    Text("Target: $target", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (!req.reason.isNullOrBlank()) {
                        Text("Reason: ${req.reason}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Temporary Access Duration:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5L to "5m", 15L to "15m", 30L to "30m", 60L to "1h").forEach { (mins, label) ->
                            AssistChip(
                                onClick = { selectedDuration = mins },
                                label = { Text(if (selectedDuration == mins) "✓ $label" else label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.decideApproval(req, approved = true, durationMinutes = selectedDuration, policy = policy) },
                            modifier = Modifier.weight(1f).testTag("approve_req_${req.id}")
                        ) { Text("Approve ($selectedDuration m)") }
                        OutlinedButton(
                            onClick = { viewModel.decideApproval(req, approved = false, policy = policy) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Decline") }
                    }
                }
            }
        }

        if (decided.isNotEmpty()) {
            SectionHeader(title = "Decision Audit Log")
            decided.take(15).forEach { req ->
                GuardianCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${req.childName}: ${ApprovalRequestType.fromString(req.requestType).label}", style = MaterialTheme.typography.titleSmall)
                            Text(req.targetLabel ?: req.targetDomain ?: "${req.requestedMinutes ?: 0}m", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusPill(
                            text = req.status,
                            color = if (req.status == "APPROVED") EmeraldShield else DangerCrimson
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    bundle: ParentOverviewBundle,
    viewModel: ParentViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "Real-Time Family Notifications",
            subtitle = "Live Firestore notification stream + local Android NotificationChannels."
        )

        GuardianCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)) {
            Text("Background FCM Push Status", style = MaterialTheme.typography.titleSmall)
            Text(
                "In-app & active session notifications deliver in real time via Firestore listeners. Background terminated-app FCM push wakeups require adding Firebase Cloud Messaging + Cloud Functions in your Firebase console.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (bundle.notifications.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Rounded.Notifications,
                title = "No Notifications Yet",
                message = "New app installs, approval requests, screen time alerts, and tamper warnings appear here."
            )
        } else {
            bundle.notifications.forEach { n ->
                GuardianCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!n.readFlag) {
                            StatusPill(text = "New", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(n.body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val ts = n.createdAt?.toDate()?.time?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "Just now"
                        Text("${n.category} • $ts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!n.readFlag) {
                            OutlinedButton(onClick = { viewModel.markNotificationRead(n.id) }) {
                                Text("Mark Read")
                            }
                        }
                    }
                }
            }
        }
    }
}
