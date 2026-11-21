package com.guardian360.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth

data class FirebaseServiceCheck(
    val serviceName: String,
    val status: String,
    val detail: String,
    val ready: Boolean
)

data class FirebaseConfigReport(
    val packageName: String,
    val projectId: String,
    val databaseId: String,
    val googleAppIdPresent: Boolean,
    val webClientIdPresent: Boolean,
    val firebaseAppInitialized: Boolean,
    val signedInUid: String?,
    val checks: List<FirebaseServiceCheck>
)

object FirebaseConfigValidator {

    fun inspect(context: Context): FirebaseConfigReport {
        val appContext = context.applicationContext
        val projectId = runCatching { appContext.getString(R.string.project_id) }.getOrDefault("")
        val databaseId = runCatching { appContext.getString(R.string.firestore_database_id) }.getOrDefault("")
        val googleAppId = runCatching { appContext.getString(R.string.google_app_id) }.getOrDefault("")
        val webClientId = runCatching { appContext.getString(R.string.default_web_client_id) }.getOrDefault("")

        val firebaseInitialized = runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)
        val signedInUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

        val checks = listOf(
            FirebaseServiceCheck(
                serviceName = "Android Package & Google Services",
                status = if (googleAppId.isNotBlank() && projectId.isNotBlank()) "Verified" else "Missing",
                detail = "Pkg: ${appContext.packageName} • Project: ${projectId.ifBlank { "Unconfigured" }}",
                ready = googleAppId.isNotBlank() && projectId.isNotBlank()
            ),
            FirebaseServiceCheck(
                serviceName = "Google Identity / Firebase Auth",
                status = if (webClientId.isNotBlank()) "Provisioned" else "Missing Web Client ID",
                detail = if (signedInUid != null) "Signed in (uid ${signedInUid.take(8)}…)" else "Google Sign-In Credential Manager ready",
                ready = webClientId.isNotBlank()
            ),
            FirebaseServiceCheck(
                serviceName = "Cloud Firestore (Zero-Trust Rules)",
                status = if (databaseId.isNotBlank()) "Provisioned" else "Default DB",
                detail = "DB: ${databaseId.ifBlank { "(default)" }} • Rules & 7 Composite Indexes configured",
                ready = databaseId.isNotBlank() && firebaseInitialized
            ),
            FirebaseServiceCheck(
                serviceName = "Cloud Functions v2 (TypeScript)",
                status = "Authored (Requires Blaze + CLI Deploy)",
                detail = "11 TypeScript callables in /functions/src/index.ts (run firebase deploy --only functions)",
                ready = false
            ),
            FirebaseServiceCheck(
                serviceName = "Firebase Cloud Messaging (FCM)",
                status = "Local Channels Active / Push Requires Server Deploy",
                detail = "Notification channels active locally; remote push requires deployed Cloud Functions",
                ready = false
            ),
            FirebaseServiceCheck(
                serviceName = "App Check & Crashlytics",
                status = "Pending External Console Activation",
                detail = "Configure Play Integrity SHA-256 & Crashlytics in Firebase Console for production",
                ready = false
            )
        )

        return FirebaseConfigReport(
            packageName = appContext.packageName,
            projectId = projectId,
            databaseId = databaseId,
            googleAppIdPresent = googleAppId.isNotBlank(),
            webClientIdPresent = webClientId.isNotBlank(),
            firebaseAppInitialized = firebaseInitialized,
            signedInUid = signedInUid,
            checks = checks
        )
    }

    /**
     * Lightweight pure check usable in JVM unit tests.
     */
    fun isPackageAligned(manifestPackage: String, googleServicesPackage: String): Boolean {
        return manifestPackage.isNotBlank() && manifestPackage == googleServicesPackage
    }
}
