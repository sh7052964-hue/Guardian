package com.guardian360.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.guardian360.deviceowner.GuardianDeviceAdminReceiver
import org.json.JSONObject
import java.io.File

/**
 * Policy-compliant distribution and APK helper:
 * - Opens HTTPS / LAN download links in the phone browser via Intent.ACTION_VIEW.
 * - Exports the currently installed signed APK cleanly using Storage Access Framework or FileProvider.
 * - Opens user-picked downloaded APK files cleanly via Intent.ACTION_VIEW + FLAG_GRANT_READ_URI_PERMISSION.
 * - Builds genuine Android Enterprise Device Owner QR Provisioning JSON payloads.
 */
object ApkDistributionHelper {
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    const val DEFAULT_EXPORT_FILENAME = "Guardian360-v1.0.apk"

    fun isValidDownloadUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.length < 10) return false
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
    }

    fun openDownloadUrlInBrowser(context: Context, url: String): Result<Unit> = runCatching {
        require(isValidDownloadUrl(url)) { "Enter a valid http:// or https:// APK download URL first." }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
        context.safeStartActivity(intent).getOrThrow()
    }

    fun openDownloadedApkUri(context: Context, uri: Uri): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.safeStartActivity(Intent.createChooser(intent, "Open Downloaded APK")).getOrThrow()
    }

    fun exportInstalledApkToUri(context: Context, destinationUri: Uri): Result<Long> = runCatching {
        val sourcePath = context.applicationInfo.sourceDir
            ?: error("Installed APK path unavailable on this environment.")
        val sourceFile = File(sourcePath)
        require(sourceFile.exists() && sourceFile.canRead()) {
            "Installed APK binary cannot be read directly."
        }
        var bytesCopied = 0L
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
            sourceFile.inputStream().use { input ->
                bytesCopied = input.copyTo(out)
            }
        } ?: error("Unable to open destination file stream.")
        bytesCopied
    }

    fun shareInstalledApk(context: Context): Result<Unit> = runCatching {
        val sourcePath = context.applicationInfo.sourceDir
            ?: error("Installed APK path unavailable.")
        val sourceFile = File(sourcePath)
        require(sourceFile.exists()) { "Installed APK file not found." }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, DEFAULT_EXPORT_FILENAME)
        sourceFile.inputStream().use { input ->
            exportFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Guardian360 Android Installer APK")
            putExtra(Intent.EXTRA_TEXT, "Install Guardian360 on the child Android phone to enable supervision.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Send Guardian360 APK")
        context.safeStartActivity(chooser).getOrThrow()
    }

    fun buildEnterpriseProvisioningJson(context: Context, apkDownloadUrl: String): String {
        val adminComp = ComponentName(context.applicationContext, GuardianDeviceAdminReceiver::class.java)
        return JSONObject().apply {
            put(
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME",
                adminComp.flattenToShortString()
            )
            if (isValidDownloadUrl(apkDownloadUrl)) {
                put(
                    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION",
                    apkDownloadUrl.trim()
                )
            }
            put("android.app.extra.PROVISIONING_SKIP_ENCRYPTION", true)
            put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
        }.toString()
    }
}
