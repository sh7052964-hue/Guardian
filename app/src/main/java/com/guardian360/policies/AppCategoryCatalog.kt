package com.guardian360.policies

import android.content.pm.ApplicationInfo
import android.os.Build
import com.guardian360.common.AgeRating
import com.guardian360.firebase.SupervisionPolicy

enum class AppCategory(val displayName: String) {
    SOCIAL_MEDIA("Social Media"),
    COMMUNICATION_MESSAGING("Communication & Messaging"),
    DATING("Dating Applications"),
    ADULT_CONTENT("Adult Content"),
    GAMES("Games"),
    ENTERTAINMENT("Entertainment"),
    VIDEO_STREAMING("Video Streaming"),
    FINANCE_BANKING("Finance & Banking"),
    SHOPPING("Shopping"),
    VPN("VPN Applications"),
    BROWSERS("Browsers"),
    NEWS("News"),
    EDUCATIONAL("Educational Apps"),
    PRODUCTIVITY("Productivity"),
    HEALTH_FITNESS("Health & Fitness"),
    TRAVEL("Travel"),
    PHOTOGRAPHY("Photography"),
    VIDEO_EDITING("Video Editing"),
    FILE_SHARING("File Sharing"),
    UTILITIES("Utilities"),
    UNKNOWN("Unknown Applications");

    companion object {
        fun fromString(raw: String?): AppCategory =
            entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

enum class GameSubcategory(val displayName: String) {
    ACTION("Action"),
    ADVENTURE("Adventure"),
    ARCADE("Arcade"),
    BOARD("Board"),
    CARD("Card"),
    CASINO("Casino"),
    CASUAL("Casual"),
    EDUCATIONAL("Educational"),
    PUZZLE("Puzzle"),
    RACING("Racing"),
    RPG("RPG"),
    SIMULATION("Simulation"),
    SPORTS("Sports"),
    STRATEGY("Strategy"),
    WORD("Word Games")
}

data class PolicyDecision(
    val allowed: Boolean,
    val tierReason: String
)

object AppCategoryCatalog {
    private val knownPackages: Map<String, Pair<AppCategory, AgeRating>> = mapOf(
        "com.google.android.youtube" to (AppCategory.VIDEO_STREAMING to AgeRating.AGE_12),
        "com.google.android.apps.youtube.kids" to (AppCategory.EDUCATIONAL to AgeRating.AGE_3),
        "com.whatsapp" to (AppCategory.COMMUNICATION_MESSAGING to AgeRating.AGE_16),
        "com.instagram.android" to (AppCategory.SOCIAL_MEDIA to AgeRating.AGE_12),
        "com.facebook.katana" to (AppCategory.SOCIAL_MEDIA to AgeRating.AGE_12),
        "com.snapchat.android" to (AppCategory.SOCIAL_MEDIA to AgeRating.AGE_12),
        "com.zhiliaoapp.musically" to (AppCategory.SOCIAL_MEDIA to AgeRating.AGE_12),
        "org.telegram.messenger" to (AppCategory.COMMUNICATION_MESSAGING to AgeRating.AGE_16),
        "com.discord" to (AppCategory.COMMUNICATION_MESSAGING to AgeRating.AGE_16),
        "com.tinder" to (AppCategory.DATING to AgeRating.AGE_18),
        "com.bumble.app" to (AppCategory.DATING to AgeRating.AGE_18),
        "com.netflix.mediaclient" to (AppCategory.VIDEO_STREAMING to AgeRating.AGE_12),
        "com.amazon.avod.thirdpartyclient" to (AppCategory.VIDEO_STREAMING to AgeRating.AGE_12),
        "com.android.chrome" to (AppCategory.BROWSERS to AgeRating.AGE_3),
        "org.mozilla.firefox" to (AppCategory.BROWSERS to AgeRating.AGE_3),
        "com.opera.browser" to (AppCategory.BROWSERS to AgeRating.AGE_3),
        "org.khanacademy.android" to (AppCategory.EDUCATIONAL to AgeRating.AGE_3),
        "com.duolingo" to (AppCategory.EDUCATIONAL to AgeRating.AGE_3),
        "com.google.android.apps.classroom" to (AppCategory.EDUCATIONAL to AgeRating.AGE_3),
        "com.google.android.apps.docs" to (AppCategory.PRODUCTIVITY to AgeRating.AGE_3),
        "com.amazon.mShop.android.shopping" to (AppCategory.SHOPPING to AgeRating.AGE_12),
        "com.flipkart.android" to (AppCategory.SHOPPING to AgeRating.AGE_12),
        "com.nordvpn.android" to (AppCategory.VPN to AgeRating.AGE_12),
        "com.protonvpn.android" to (AppCategory.VPN to AgeRating.AGE_12),
        "com.google.android.apps.photos" to (AppCategory.PHOTOGRAPHY to AgeRating.AGE_3),
        "com.lemon.lvoverseas" to (AppCategory.VIDEO_EDITING to AgeRating.AGE_12),
        "com.supercell.clashofclans" to (AppCategory.GAMES to AgeRating.AGE_7),
        "com.mojang.minecraftpe" to (AppCategory.GAMES to AgeRating.AGE_7),
        "com.roblox.client" to (AppCategory.GAMES to AgeRating.AGE_12)
    )

    private val knownGameSubcategories: Map<String, GameSubcategory> = mapOf(
        "com.supercell.clashofclans" to GameSubcategory.STRATEGY,
        "com.mojang.minecraftpe" to GameSubcategory.ADVENTURE,
        "com.roblox.client" to GameSubcategory.ARCADE
    )

    private val emergencyPackages = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.emergency",
        "com.android.settings"
    )

    /**
     * Never guesses unknown application classifications.
     * Uses verified package metadata first, then Android OS ApplicationInfo.category (API 26+),
     * otherwise returns UNKNOWN.
     */
    fun classify(appInfo: ApplicationInfo): Triple<AppCategory, GameSubcategory?, AgeRating> {
        knownPackages[appInfo.packageName]?.let { (cat, rating) ->
            return Triple(cat, knownGameSubcategories[appInfo.packageName], rating)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val osCat = when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL_MEDIA
                ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO_STREAMING
                ApplicationInfo.CATEGORY_AUDIO -> AppCategory.ENTERTAINMENT
                ApplicationInfo.CATEGORY_IMAGE -> AppCategory.PHOTOGRAPHY
                ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
                ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.UTILITIES
                else -> AppCategory.UNKNOWN
            }
            return Triple(osCat, null, AgeRating.AGE_12)
        }
        return Triple(AppCategory.UNKNOWN, null, AgeRating.AGE_12)
    }

    /**
     * Evaluates the 5-Tier Policy Precedence:
     * 1. Emergency & legally required system functions
     * 2. Explicit Parent device-level policy (lock / downtime)
     * 3. Individual application exception
     * 4. Category & Age Rating policy
     * 5. Default allow policy
     */
    fun evaluatePackageAccess(
        packageName: String,
        appCategory: AppCategory,
        appAgeRating: AgeRating,
        guardianPackage: String,
        policy: SupervisionPolicy?,
        deviceLocked: Boolean,
        isDowntimeActive: Boolean
    ): PolicyDecision {
        // Tier 1: Emergency & Guardian360 itself
        if (packageName == guardianPackage || packageName in emergencyPackages) {
            return PolicyDecision(true, "Tier 1: Emergency / System Function")
        }
        if (policy == null) {
            return PolicyDecision(true, "Tier 5: No policy active")
        }
        // Tier 2: Explicit Parent device-level policy
        if (deviceLocked) {
            return PolicyDecision(false, "Tier 2: Device locked by Parent")
        }
        if (isDowntimeActive) {
            return PolicyDecision(false, "Tier 2: Scheduled Downtime / Bedtime active")
        }
        // Tier 3: Individual application exception
        if (packageName in policy.allowedPackages) {
            return PolicyDecision(true, "Tier 3: Individual Parent exception (Allowed)")
        }
        if (packageName in policy.blockedPackages) {
            return PolicyDecision(false, "Tier 3: Explicitly blocked by Parent")
        }
        // Tier 4: Category & Age Rating policy
        if (appCategory.name in policy.blockedCategories) {
            return PolicyDecision(false, "Tier 4: Blocked category (${appCategory.displayName})")
        }
        val maxRating = AgeRating.fromString(policy.maxAgeRating)
        if (appAgeRating.minAge > maxRating.minAge) {
            return PolicyDecision(false, "Tier 4: Exceeds max age rating (${maxRating.label})")
        }
        // Tier 5: Default allow
        return PolicyDecision(true, "Tier 5: Allowed by default policy")
    }
}
