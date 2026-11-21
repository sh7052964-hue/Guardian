package com.guardian360.pairing

import android.os.Build
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.GuardianMonoStyle
import com.guardian360.common.EmptyStatePanel
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianCard
import com.guardian360.common.QrCodeView
import com.guardian360.common.SectionHeader
import com.guardian360.common.SecurityUtils
import com.guardian360.common.StatusPill
import com.guardian360.firebase.GuardianFirestoreRepository
import com.guardian360.firebase.PairingSession
import kotlinx.coroutines.launch

@Composable
fun ParentPairingPanel(
    familyId: String,
    sessions: List<PairingSession>,
    repository: GuardianFirestoreRepository
) {
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var localSessionCodes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val activePending = sessions.firstOrNull { !it.consumed && it.status == "PENDING" && it.expiresAtEpochMs > System.currentTimeMillis() }
    val requestedSessions = sessions.filter { !it.consumed && it.status == "REQUESTED" }
    val history = sessions.filter { it.consumed || it.status in listOf("APPROVED", "REJECTED", "EXPIRED") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            title = "Secure Parent-Child Pairing",
            subtitle = "One-time, SHA-256 hashed expiring sessions with device verification & replay protection."
        )

        if (feedback != null) {
            ErrorBanner(message = feedback!!)
        }

        // Generate or show active QR + Code
        GuardianCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pairing QR & Code", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        isBusy = true
                        feedback = null
                        scope.launch {
                            val code = SecurityUtils.generatePairingCode(8)
                            repository.createPairingSession(familyId, code)
                                .onSuccess { sessionId ->
                                    localSessionCodes = localSessionCodes + (sessionId to code)
                                }
                                .onFailure { feedback = it.localizedMessage }
                            isBusy = false
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("generate_pairing_code_btn")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("New Code")
                }
            }

            Spacer(Modifier.height(14.dp))
            if (activePending != null) {
                val rawOrMaskedCode = localSessionCodes[activePending.id]
                    ?: activePending.pairingCode
                    ?: activePending.pairingCodeHint.ifBlank { "••••••••" }
                val qrPayload = "guardian360://pair?code=$rawOrMaskedCode"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    QrCodeView(payload = qrPayload, size = 210.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = rawOrMaskedCode,
                        style = GuardianMonoStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    val minsLeft = ((activePending.expiresAtEpochMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1L)
                    Text(
                        text = if (localSessionCodes.containsKey(activePending.id) || !activePending.pairingCode.isNullOrBlank()) {
                            "SHA-256 hashed session • Expires in ~${minsLeft}m • Replay-protected"
                        } else {
                            "Active hashed session (${activePending.pairingCodeHint}) • Tap 'New Code' to display raw code"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                EmptyStatePanel(
                    icon = Icons.Rounded.QrCode2,
                    title = "No Active Pairing Code",
                    message = "Tap 'New Code' to generate a scannable QR code and 8-character pairing code for a child device."
                )
            }
        }

        // Incoming Verification Requests
        SectionHeader(
            title = "Incoming Pairing Requests (${requestedSessions.size})",
            subtitle = "Verify the child's device model and approve enrollment."
        )
        if (requestedSessions.isEmpty()) {
            EmptyStatePanel(
                icon = Icons.Rounded.Link,
                title = "No Pending Requests",
                message = "When a child enters your pairing code, their device details will appear here for verification."
            )
        } else {
            requestedSessions.forEach { session ->
                val displayCode = session.pairingCodeHint.ifBlank { session.pairingCode ?: "••••" }
                GuardianCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.childName ?: "Child", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${session.childDeviceName ?: "Device"} • ${session.deviceModel ?: "Android"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Session: $displayCode",
                                style = GuardianMonoStyle
                            )
                        }
                        StatusPill(text = "Awaiting Verify", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.approvePairingSession(session)
                                        .onFailure { feedback = it.localizedMessage }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("approve_pairing_${session.id}")
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Verify & Approve")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    repository.rejectPairingSession(session.id)
                                        .onFailure { feedback = it.localizedMessage }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reject")
                        }
                    }
                }
            }
        }

        // History
        if (history.isNotEmpty()) {
            SectionHeader(title = "Pairing History")
            history.take(8).forEach { item ->
                val hint = item.pairingCodeHint.ifBlank { item.pairingCode ?: "••••" }
                GuardianCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(item.childName ?: "Session $hint", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Code $hint • ${item.deviceModel ?: "Unclaimed"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = item.status,
                            color = if (item.status == "APPROVED") EmeraldShield else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChildPairingCard(
    childName: String,
    deviceName: String,
    activeSession: PairingSession?,
    repository: GuardianFirestoreRepository,
    onSessionStarted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var codeInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    GuardianCard {
        Text("Link to Parent Authority", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        when {
            activeSession?.status == "REQUESTED" -> {
                StatusPill(text = "Awaiting Parent Approval", color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pairing request (${activeSession.pairingCodeHint.ifBlank { activeSession.pairingCode ?: "sent" }}) sent. Ask your parent to verify and approve this device on their dashboard.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            activeSession?.status == "REJECTED" -> {
                StatusPill(text = "Pairing Rejected", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Previous pairing request was declined. Enter a new parent code below.", style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                Text(
                    "Enter the 8-character pairing code from your parent's QR screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (activeSession?.status != "REQUESTED") {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it.uppercase().filter(Char::isLetterOrDigit).take(12) },
                label = { Text("8-Character Pairing Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("child_dash_pairing_input")
            )
            if (errorMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    submitting = true
                    errorMsg = null
                    scope.launch {
                        repository.requestChildPairing(
                            pairingCode = codeInput,
                            childName = childName,
                            deviceName = deviceName,
                            deviceModel = Build.MODEL
                        ).onSuccess { sessionId ->
                            submitting = false
                            onSessionStarted(sessionId)
                        }.onFailure { err ->
                            submitting = false
                            errorMsg = err.localizedMessage ?: "Unable to pair"
                        }
                    }
                },
                enabled = !submitting && codeInput.length >= 6,
                modifier = Modifier.fillMaxWidth().testTag("child_send_pairing_btn")
            ) {
                Text(if (submitting) "Sending Request..." else "Send Pairing Request")
            }
        }
    }
}
