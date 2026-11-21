package com.guardian360.auth

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.guardian360.common.ErrorBanner
import com.guardian360.common.GuardianCard
import com.guardian360.common.SecurityUtils
import com.guardian360.common.UserRole
import com.guardian360.firebase.GuardianFirestoreRepository
import com.guardian360.firebase.UserAccount
import kotlinx.coroutines.launch

@Composable
fun RoleOnboardingScreen(
    initialRole: UserRole,
    languageCode: String,
    initialPairingCode: String?,
    repository: GuardianFirestoreRepository,
    onCompleted: (UserRole, String?) -> Unit,
    onSignOut: () -> Unit
) {
    val authUser = Firebase.auth.currentUser
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeRole by remember(initialRole) {
        mutableStateOf(if (initialRole == UserRole.CHILD) UserRole.CHILD else UserRole.PARENT)
    }

    // Parent fields
    var fullName by remember { mutableStateOf(authUser?.displayName ?: "") }
    var mobileNumber by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var biometricEnabled by remember { mutableStateOf(SecurityUtils.isBiometricAvailable(context)) }
    var twoFactorEnabled by remember { mutableStateOf(true) }

    // Child fields
    var childName by remember { mutableStateOf(authUser?.displayName ?: "") }
    var dateOfBirth by remember { mutableStateOf("2014-05-12") }
    var username by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("${Build.MANUFACTURER} ${Build.MODEL}") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var pairingCode by remember { mutableStateOf(initialPairingCode ?: "") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var childProfileSaved by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) avatarUri = uri }
    )

    if (authUser == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBanner(message = "Your Google sign-in session expired or signed out. Please sign in again.")
                Button(onClick = onSignOut) {
                    Text("Return to Sign In")
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Complete Your Profile", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Signed in as ${authUser?.email ?: authUser?.uid ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSignOut, modifier = Modifier.testTag("onboarding_sign_out")) {
                    Icon(Icons.Rounded.Logout, contentDescription = "Sign Out")
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = activeRole == UserRole.PARENT,
                    onClick = { activeRole = UserRole.PARENT },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null) }
                ) {
                    Text("Parent Account")
                }
                SegmentedButton(
                    selected = activeRole == UserRole.CHILD,
                    onClick = { activeRole = UserRole.CHILD },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Rounded.ChildCare, contentDescription = null) }
                ) {
                    Text("Child Account")
                }
            }

            if (errorMessage != null) {
                ErrorBanner(message = errorMessage!!)
            }

            if (activeRole == UserRole.PARENT) {
                GuardianCard {
                    Text("Parent Authority Setup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("parent_name_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { mobileNumber = it },
                        label = { Text("Mobile Number (Emergency Contact)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("parent_mobile_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("Security PIN (4-6 digits)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("parent_pin_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                        label = { Text("Confirm Security PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("parent_confirm_pin_input")
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Unlock", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Require fingerprint/face before modifying strict policies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = biometricEnabled, onCheckedChange = { biometricEnabled = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Two-Factor Policy Guard", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Require PIN + Google session verification for device unlock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = twoFactorEnabled, onCheckedChange = { twoFactorEnabled = it })
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (fullName.isBlank()) {
                                errorMessage = "Please enter your full name."
                                return@Button
                            }
                            if (pinInput.length < 4 || pinInput != confirmPin) {
                                errorMessage = "Security PIN must be 4-6 digits and match confirmation."
                                return@Button
                            }
                            isSubmitting = true
                            errorMessage = null
                            scope.launch {
                                val familyResult = repository.ensureParentFamily(fullName.trim())
                                familyResult.onSuccess { familyId ->
                                    val account = UserAccount(
                                        userId = authUser?.uid ?: "",
                                        displayName = fullName.trim(),
                                        email = authUser?.email,
                                        role = UserRole.PARENT.name,
                                        familyId = familyId,
                                        mobileNumber = mobileNumber.trim().ifBlank { null },
                                        pinHash = SecurityUtils.hashPin(pinInput),
                                        biometricEnabled = biometricEnabled,
                                        twoFactorEnabled = twoFactorEnabled,
                                        sessionVersion = 1L,
                                        languageCode = languageCode
                                    )
                                    repository.upsertUserAccount(account)
                                        .onSuccess {
                                            isSubmitting = false
                                            onCompleted(UserRole.PARENT, null)
                                        }
                                        .onFailure {
                                            isSubmitting = false
                                            errorMessage = it.localizedMessage ?: "Failed to save profile"
                                        }
                                }.onFailure {
                                    isSubmitting = false
                                    errorMessage = it.localizedMessage ?: "Failed to create family"
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("complete_parent_setup_button")
                    ) {
                        if (isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Create Parent Authority Account")
                    }
                }
            } else {
                GuardianCard {
                    Text("Child Profile & Pairing", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            if (avatarUri != null) {
                                AsyncImage(
                                    model = avatarUri,
                                    contentDescription = "Child profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.ChildCare, contentDescription = "Avatar", modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }.onFailure { err ->
                                    errorMessage = "Photo picker unavailable on this device: ${err.localizedMessage ?: "Unsupported"}"
                                }
                            },
                            modifier = Modifier.testTag("pick_child_photo_button")
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose Photo")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = childName,
                        onValueChange = { childName = it },
                        label = { Text("Child Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("child_name_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it },
                        label = { Text("Date of Birth (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Device Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12) },
                        label = { Text("Parent-Issued Pairing Code (8 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("child_pairing_code_input")
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (childName.isBlank()) {
                                errorMessage = "Please enter the child's name."
                                return@Button
                            }
                            isSubmitting = true
                            errorMessage = null
                            scope.launch {
                                val account = UserAccount(
                                    userId = authUser?.uid ?: "",
                                    displayName = childName.trim(),
                                    email = authUser?.email,
                                    role = UserRole.CHILD.name,
                                    dateOfBirth = dateOfBirth.trim(),
                                    username = username.trim().ifBlank { childName.trim().lowercase().replace(" ", "_") },
                                    deviceName = deviceName.trim().ifBlank { Build.MODEL },
                                    avatarUri = avatarUri?.toString(),
                                    sessionVersion = 1L,
                                    languageCode = languageCode
                                )
                                repository.upsertUserAccount(account).onSuccess {
                                    childProfileSaved = true
                                    if (pairingCode.isNotBlank()) {
                                        repository.requestChildPairing(
                                            pairingCode = pairingCode,
                                            childName = childName.trim(),
                                            deviceName = deviceName.trim(),
                                            deviceModel = Build.MODEL
                                        ).onSuccess { sessionId ->
                                            isSubmitting = false
                                            onCompleted(UserRole.CHILD, sessionId)
                                        }.onFailure { err ->
                                            isSubmitting = false
                                            errorMessage = (err.localizedMessage ?: "Pairing request failed") +
                                                " — You can correct the code above or open Child Dashboard to pair later."
                                        }
                                    } else {
                                        isSubmitting = false
                                        onCompleted(UserRole.CHILD, null)
                                    }
                                }.onFailure { err ->
                                    isSubmitting = false
                                    errorMessage = err.localizedMessage ?: "Failed to save child profile"
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("complete_child_setup_button")
                    ) {
                        if (isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Register Child & Request Pairing")
                    }
                    if (childProfileSaved) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onCompleted(UserRole.CHILD, null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Child Dashboard & Pair Later")
                        }
                    }
                }
            }
        }
    }
}
