package com.guardian360.common

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.guardianPrefsDataStore by preferencesDataStore(name = "guardian360_prefs")

data class AdminPanelConfig(
    val headerSubtitle: String = "Advanced Parental Control",
    val adminNotice: String = "",
    val apkDownloadUrl: String = "",
    val defaultDailyLimitMinutes: Long = 120L,
    val defaultDnsServer: String = "8.8.8.8",
    val defaultPairingTtlMinutes: Long = 15L,
    val allowChildExtraTimeRequests: Boolean = true,
    val allowChildWebsiteRequests: Boolean = true,
    val enforceStrictTamperAlerts: Boolean = true,
    val customAccessCodeConfigured: Boolean = false
)

class PreferencesManager(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("language_code")
    private val keyTheme = stringPreferencesKey("theme_preference")
    private val keySelectedRole = stringPreferencesKey("selected_role")
    private val keyActiveSessionId = stringPreferencesKey("active_pairing_session_id")
    private val keyCachedFamilyId = stringPreferencesKey("cached_family_id")

    // Admin Panel Access & Configurable Controls
    private val keyAdminCodeHash = stringPreferencesKey("admin_access_code_hash")
    private val keyHeaderSubtitle = stringPreferencesKey("admin_header_subtitle")
    private val keyAdminNotice = stringPreferencesKey("admin_notice")
    private val keyApkDownloadUrl = stringPreferencesKey("admin_apk_download_url")
    private val keyDefaultDailyLimit = longPreferencesKey("admin_default_daily_limit")
    private val keyDefaultDnsServer = stringPreferencesKey("admin_default_dns_server")
    private val keyDefaultPairingTtl = longPreferencesKey("admin_default_pairing_ttl")
    private val keyAllowChildExtraTime = booleanPreferencesKey("admin_allow_child_extra_time")
    private val keyAllowChildWebReq = booleanPreferencesKey("admin_allow_child_web_req")
    private val keyStrictTamperAlerts = booleanPreferencesKey("admin_strict_tamper_alerts")

    val languageCode: Flow<String> = context.guardianPrefsDataStore.data.map { prefs ->
        prefs[keyLanguage] ?: "en"
    }

    val themePreference: Flow<ThemePreference> = context.guardianPrefsDataStore.data.map { prefs ->
        ThemePreference.fromString(prefs[keyTheme])
    }

    val selectedRole: Flow<UserRole> = context.guardianPrefsDataStore.data.map { prefs ->
        UserRole.fromString(prefs[keySelectedRole])
    }

    val activePairingSessionId: Flow<String?> = context.guardianPrefsDataStore.data.map { prefs ->
        prefs[keyActiveSessionId]
    }

    val cachedFamilyId: Flow<String?> = context.guardianPrefsDataStore.data.map { prefs ->
        prefs[keyCachedFamilyId]
    }

    val adminAccessCodeHash: Flow<String?> = context.guardianPrefsDataStore.data.map { prefs ->
        prefs[keyAdminCodeHash]
    }

    val adminConfig: Flow<AdminPanelConfig> = context.guardianPrefsDataStore.data.map { prefs ->
        AdminPanelConfig(
            headerSubtitle = prefs[keyHeaderSubtitle]?.takeIf { it.isNotBlank() } ?: "Advanced Parental Control",
            adminNotice = prefs[keyAdminNotice] ?: "",
            apkDownloadUrl = prefs[keyApkDownloadUrl] ?: "",
            defaultDailyLimitMinutes = (prefs[keyDefaultDailyLimit] ?: 120L).coerceIn(30L, 720L),
            defaultDnsServer = prefs[keyDefaultDnsServer]?.takeIf { it.isNotBlank() } ?: "8.8.8.8",
            defaultPairingTtlMinutes = (prefs[keyDefaultPairingTtl] ?: 15L).coerceIn(5L, 60L),
            allowChildExtraTimeRequests = prefs[keyAllowChildExtraTime] ?: true,
            allowChildWebsiteRequests = prefs[keyAllowChildWebReq] ?: true,
            enforceStrictTamperAlerts = prefs[keyStrictTamperAlerts] ?: true,
            customAccessCodeConfigured = !prefs[keyAdminCodeHash].isNullOrBlank()
        )
    }

    suspend fun setLanguageCode(code: String) {
        val sanitized = if (code in listOf("en", "hi", "as")) code else "en"
        context.guardianPrefsDataStore.edit { it[keyLanguage] = sanitized }
    }

    suspend fun setThemePreference(pref: ThemePreference) {
        context.guardianPrefsDataStore.edit { it[keyTheme] = pref.name }
    }

    suspend fun setSelectedRole(role: UserRole) {
        context.guardianPrefsDataStore.edit { it[keySelectedRole] = role.name }
    }

    suspend fun setActivePairingSessionId(sessionId: String?) {
        context.guardianPrefsDataStore.edit {
            if (sessionId.isNullOrBlank()) it.remove(keyActiveSessionId)
            else it[keyActiveSessionId] = sessionId
        }
    }

    suspend fun setCachedFamilyId(familyId: String?) {
        context.guardianPrefsDataStore.edit {
            if (familyId.isNullOrBlank()) it.remove(keyCachedFamilyId)
            else it[keyCachedFamilyId] = familyId
        }
    }

    suspend fun setAdminAccessCode(newCode: String) {
        val hashed = SecurityUtils.hashPin(newCode.trim())
        context.guardianPrefsDataStore.edit { it[keyAdminCodeHash] = hashed }
    }

    suspend fun resetAdminAccessCodeToDefault() {
        context.guardianPrefsDataStore.edit { it.remove(keyAdminCodeHash) }
    }

    suspend fun setApkDownloadUrl(url: String) {
        context.guardianPrefsDataStore.edit { it[keyApkDownloadUrl] = url.trim().take(260) }
    }

    suspend fun updateAdminConfig(config: AdminPanelConfig) {
        context.guardianPrefsDataStore.edit { prefs ->
            prefs[keyHeaderSubtitle] = config.headerSubtitle.trim().take(60).ifBlank { "Advanced Parental Control" }
            prefs[keyAdminNotice] = config.adminNotice.trim().take(240)
            prefs[keyApkDownloadUrl] = config.apkDownloadUrl.trim().take(260)
            prefs[keyDefaultDailyLimit] = config.defaultDailyLimitMinutes.coerceIn(30L, 720L)
            prefs[keyDefaultDnsServer] = config.defaultDnsServer.trim().take(64).ifBlank { "8.8.8.8" }
            prefs[keyDefaultPairingTtl] = config.defaultPairingTtlMinutes.coerceIn(5L, 60L)
            prefs[keyAllowChildExtraTime] = config.allowChildExtraTimeRequests
            prefs[keyAllowChildWebReq] = config.allowChildWebsiteRequests
            prefs[keyStrictTamperAlerts] = config.enforceStrictTamperAlerts
        }
    }
}

object LocalizationHelper {
    fun localizedContext(base: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        val configContext = base.createConfigurationContext(config)
        return object : ContextWrapper(base) {
            override fun getResources(): Resources = configContext.resources
            override fun getAssets(): AssetManager = configContext.assets
        }
    }
}
