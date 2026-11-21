package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.Guardian360Theme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.guardian360.auth.RoleOnboardingScreen
import com.guardian360.auth.WelcomeAuthScreen
import com.guardian360.auth.attemptAutoSignIn
import com.guardian360.auth.signOutGuardian
import com.guardian360.child.ChildDashboardScreen
import com.guardian360.common.ErrorBanner
import com.guardian360.common.LoadingPanel
import com.guardian360.common.LocalizationHelper
import com.guardian360.common.ThemePreference
import com.guardian360.common.UserRole
import com.guardian360.firebase.UserAccount
import com.guardian360.parent.ParentNavHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var deepLinkPairingCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractDeepLinkCode(intent)

        val appContainer = (application as GuardianApplication).container

        setContent {
            val languageCode by appContainer.preferencesManager.languageCode.collectAsStateWithLifecycle(initialValue = "en")
            val themePref by appContainer.preferencesManager.themePreference.collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)
            val selectedRolePref by appContainer.preferencesManager.selectedRole.collectAsStateWithLifecycle(initialValue = UserRole.PARENT)

            val localizedCtx = remember(languageCode, this) {
                LocalizationHelper.localizedContext(this, languageCode)
            }
            val darkTheme = when (themePref) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            CompositionLocalProvider(LocalContext provides localizedCtx) {
                GuardianRootContent(
                    container = appContainer,
                    languageCode = languageCode,
                    themePref = themePref,
                    darkTheme = darkTheme,
                    selectedRolePref = selectedRolePref,
                    deepLinkPairingCode = deepLinkPairingCode,
                    onConsumeDeepLink = { deepLinkPairingCode = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLinkCode(intent)
    }

    private fun extractDeepLinkCode(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "guardian360" && uri.host == "pair") {
            val code = uri.getQueryParameter("code")?.trim()?.uppercase()
            if (!code.isNullOrBlank()) {
                deepLinkPairingCode = code
            }
        }
    }
}

@Composable
private fun GuardianRootContent(
    container: com.guardian360.di.AppContainer,
    languageCode: String,
    themePref: ThemePreference,
    darkTheme: Boolean,
    selectedRolePref: UserRole,
    deepLinkPairingCode: String?,
    onConsumeDeepLink: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUid by remember { mutableStateOf(Firebase.auth.currentUser?.uid) }
    var isBootstrapping by remember { mutableStateOf(Firebase.auth.currentUser == null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUid = auth.currentUser?.uid
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUid = Firebase.auth.currentUser?.uid
                    isBootstrapping = false
                },
                onUnauthenticated = { isBootstrapping = false },
                scope = scope
            )
            // Hard failsafe so splash loading never gets stuck
            delay(5000L)
            isBootstrapping = false
        } else {
            isBootstrapping = false
        }
    }

    val handleSignOut: () -> Unit = {
        signOutGuardian(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = { currentUid = null }
        )
    }

    val handleSelectLanguage: (String) -> Unit = { code ->
        scope.launch {
            container.preferencesManager.setLanguageCode(code)
            if (currentUid != null) {
                container.firestoreRepository.updateLanguageCode(code)
            }
        }
    }

    val handleSelectTheme: (ThemePreference) -> Unit = { pref ->
        scope.launch { container.preferencesManager.setThemePreference(pref) }
    }

    val uid = currentUid
    if (isBootstrapping) {
        Guardian360Theme(darkTheme = darkTheme, isChildMode = selectedRolePref == UserRole.CHILD) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingPanel("Verifying Guardian360 Identity...")
                }
            }
        }
        return
    }

    if (uid == null) {
        Guardian360Theme(darkTheme = darkTheme, isChildMode = selectedRolePref == UserRole.CHILD) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                WelcomeAuthScreen(
                    selectedRole = selectedRolePref,
                    languageCode = languageCode,
                    onSelectRole = { role -> scope.launch { container.preferencesManager.setSelectedRole(role) } },
                    onSelectLanguage = handleSelectLanguage,
                    onAuthSuccess = { currentUid = Firebase.auth.currentUser?.uid }
                )
            }
        }
    } else {
        key(uid) {
            AuthenticatedGuardianSession(
                uid = uid,
                container = container,
                languageCode = languageCode,
                themePref = themePref,
                darkTheme = darkTheme,
                selectedRolePref = selectedRolePref,
                deepLinkPairingCode = deepLinkPairingCode,
                onConsumeDeepLink = onConsumeDeepLink,
                onSelectLanguage = handleSelectLanguage,
                onSelectTheme = handleSelectTheme,
                onSignOut = handleSignOut
            )
        }
    }
}

private sealed interface AccountGateState {
    data object Loading : AccountGateState
    data class Ready(val account: UserAccount?) : AccountGateState
    data class Error(val message: String) : AccountGateState
}

@Composable
private fun AuthenticatedGuardianSession(
    uid: String,
    container: com.guardian360.di.AppContainer,
    languageCode: String,
    themePref: ThemePreference,
    darkTheme: Boolean,
    selectedRolePref: UserRole,
    deepLinkPairingCode: String?,
    onConsumeDeepLink: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var profileRetryTick by remember(uid) { mutableIntStateOf(0) }
    var familyRetryTick by remember(uid) { mutableIntStateOf(0) }
    var familyInitError by remember(uid) { mutableStateOf<String?>(null) }

    val resolvedState by remember(uid, profileRetryTick) {
        container.firestoreRepository.observeUserAccount(uid)
            .map<UserAccount?, AccountGateState> { AccountGateState.Ready(it) }
            .catch { err ->
                if (err is CancellationException) throw err
                emit(
                    AccountGateState.Error(
                        err.localizedMessage ?: "Unable to verify your Guardian360 profile. Check network and retry."
                    )
                )
            }
    }.collectAsStateWithLifecycle(initialValue = AccountGateState.Loading)

    when (val state = resolvedState) {
        is AccountGateState.Loading -> {
            Guardian360Theme(darkTheme = darkTheme, isChildMode = selectedRolePref == UserRole.CHILD) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingPanel("Loading Guardian360 Profile...")
                    }
                }
            }
        }
        is AccountGateState.Error -> {
            Guardian360Theme(darkTheme = darkTheme, isChildMode = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ErrorBanner(
                                message = state.message,
                                onRetry = { profileRetryTick++ }
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { profileRetryTick++ }) { Text("Retry") }
                                OutlinedButton(onClick = onSignOut) { Text("Sign Out") }
                            }
                        }
                    }
                }
            }
        }
        is AccountGateState.Ready -> {
            val account = state.account
            val role = UserRole.fromString(account?.role)
            val isChild = role == UserRole.CHILD || (role == UserRole.UNSET && selectedRolePref == UserRole.CHILD)

            Guardian360Theme(darkTheme = darkTheme, isChildMode = isChild) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (role) {
                        UserRole.UNSET -> {
                            RoleOnboardingScreen(
                                initialRole = if (deepLinkPairingCode != null) UserRole.CHILD else selectedRolePref,
                                languageCode = languageCode,
                                initialPairingCode = deepLinkPairingCode,
                                repository = container.firestoreRepository,
                                onCompleted = { completedRole, pairingSessionId ->
                                    onConsumeDeepLink()
                                    scope.launch {
                                        container.preferencesManager.setSelectedRole(completedRole)
                                        if (!pairingSessionId.isNullOrBlank()) {
                                            container.preferencesManager.setActivePairingSessionId(pairingSessionId)
                                        }
                                    }
                                },
                                onSignOut = onSignOut
                            )
                        }
                        UserRole.PARENT -> {
                            val famId = account?.familyId
                            if (famId.isNullOrBlank()) {
                                LaunchedEffect(uid, familyRetryTick) {
                                    familyInitError = null
                                    container.firestoreRepository.ensureParentFamily(account?.displayName ?: "Guardian")
                                        .onSuccess { newFamId ->
                                            if (account != null) {
                                                container.firestoreRepository.upsertUserAccount(account.copy(familyId = newFamId))
                                                    .onFailure { err ->
                                                        familyInitError = err.localizedMessage ?: "Failed to link family workspace."
                                                    }
                                            }
                                        }
                                        .onFailure { err ->
                                            familyInitError = err.localizedMessage ?: "Unable to initialize family workspace. Check internet and retry."
                                        }
                                }
                                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                                    val err = familyInitError
                                    if (err != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            ErrorBanner(message = err, onRetry = { familyRetryTick++ })
                                            Spacer(Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(onClick = { familyRetryTick++ }) { Text("Retry Setup") }
                                                Spacer(Modifier.width(4.dp))
                                                OutlinedButton(onClick = onSignOut) { Text("Sign Out") }
                                            }
                                        }
                                    } else {
                                        LoadingPanel("Initializing Family Safety Workspace...")
                                    }
                                }
                            } else {
                                ParentNavHost(
                                    currentUserId = uid,
                                    familyId = famId,
                                    languageCode = languageCode,
                                    themePreference = themePref,
                                    container = container,
                                    onSelectLanguage = onSelectLanguage,
                                    onSelectTheme = onSelectTheme,
                                    onSignOut = onSignOut
                                )
                            }
                        }
                        UserRole.CHILD -> {
                            ChildDashboardScreen(
                                currentUserId = uid,
                                languageCode = languageCode,
                                themePreference = themePref,
                                container = container,
                                onSelectLanguage = onSelectLanguage,
                                onSelectTheme = onSelectTheme,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }
    }
}
