package com.guardian360.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class UserRole {
    PARENT,
    CHILD,
    UNSET;

    companion object {
        fun fromString(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: UNSET
    }
}

enum class ManagementLevel(val label: String) {
    STANDARD_SUPERVISION("Standard Supervision"),
    FULLY_MANAGED("Fully Managed (Device Owner)"),
    ENROLLMENT_PENDING("Enrollment Pending"),
    ENROLLMENT_FAILED("Enrollment Failed"),
    DEVICE_OFFLINE("Device Offline"),
    MANAGEMENT_LOST("Management Lost");

    companion object {
        fun fromString(raw: String?): ManagementLevel =
            entries.firstOrNull { it.name == raw } ?: STANDARD_SUPERVISION
    }
}

enum class ActionExecutionState(val label: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    APPLIED("Applied"),
    FAILED("Failed"),
    DEVICE_OFFLINE("Device Offline"),
    UNSUPPORTED("Unsupported"),
    REQUIRES_ENROLLMENT("Requires Device Enrollment");

    companion object {
        fun fromString(raw: String?): ActionExecutionState =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class AgeRating(val label: String, val minAge: Int) {
    AGE_3("3+", 3),
    AGE_7("7+", 7),
    AGE_12("12+", 12),
    AGE_16("16+", 16),
    AGE_18("18+", 18);

    companion object {
        fun fromString(raw: String?): AgeRating =
            entries.firstOrNull { it.name == raw } ?: AGE_12
    }
}

enum class YouTubeMode(val label: String, val description: String) {
    NORMAL("Normal Mode", "Standard YouTube access within daily limits."),
    SHORTS_RESTRICTION(
        "Shorts Restriction Mode",
        "Applies Managed Play / Restricted Mode where supported and blocks known Shorts web URLs & deep links. Note: Official YouTube app may still surface native shelf UI."
    ),
    STRICT_SHORTS_WEB_BLOCKING(
        "Strict Shorts Web Blocking Mode",
        "Blocks YouTube Shorts web feeds, direct /shorts/ URLs, and applies strict domain rules via local DNS filter."
    );

    companion object {
        fun fromString(raw: String?): YouTubeMode =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class ApprovalRequestType(val label: String) {
    APP_INSTALL("App Installation Request"),
    RESTRICTED_APP("Restricted App Access"),
    EXTRA_TIME("Extra Screen Time"),
    TEMPORARY_UNLOCK("Temporary Unlock"),
    WEBSITE_ACCESS("Website Access"),
    CATEGORY_EXCEPTION("Category Exception"),
    PERMISSION_CHANGE("Permission Change");

    companion object {
        fun fromString(raw: String?): ApprovalRequestType =
            entries.firstOrNull { it.name == raw } ?: EXTRA_TIME
    }
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED;

    companion object {
        fun fromString(raw: String?): ApprovalStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class EventSeverity {
    INFO,
    WARNING,
    CRITICAL;

    companion object {
        fun fromString(raw: String?): EventSeverity =
            entries.firstOrNull { it.name == raw } ?: INFO
    }
}

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromString(raw: String?): ThemePreference =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
