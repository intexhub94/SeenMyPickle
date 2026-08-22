package com.pbcam.app.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var rtspUrl: String
        get() {
            val stored = prefs.getString(KEY_RTSP_URL, "").orEmpty()
            if (stored.isBlank()) return DEFAULT_RTSP_URL
            // Migration check: if it looks like a URL, it's not obfuscated yet
            if (stored.startsWith("rtsp://")) return stored
            
            val decrypted = SecurityUtils.deobfuscate(stored)
            return decrypted.ifBlank { DEFAULT_RTSP_URL }
        }
        set(value) = prefs.edit().putString(KEY_RTSP_URL, SecurityUtils.obfuscate(value)).apply()

    var rtspSubUrl: String
        get() {
            val stored = prefs.getString(KEY_RTSP_SUB_URL, "").orEmpty()
            if (stored.isBlank()) return ""
            // Migration check: if it looks like a URL, it's not obfuscated yet
            if (stored.startsWith("rtsp://")) return stored
            
            val decrypted = SecurityUtils.deobfuscate(stored)
            return decrypted.ifBlank { "" }
        }
        set(value) = prefs.edit().putString(KEY_RTSP_SUB_URL, SecurityUtils.obfuscate(value)).apply()

    var courtTag: String
        get() = prefs.getString(KEY_COURT_TAG, "Court 1").orEmpty()
        set(value) = prefs.edit().putString(KEY_COURT_TAG, value).apply()

    var alertEmail: String
        get() = prefs.getString(KEY_ALERT_EMAIL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ALERT_EMAIL, value).apply()

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_IS_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_SETUP_COMPLETE, value).apply()

    var adminPasscode: String
        get() = prefs.getString(KEY_ADMIN_PASSCODE, "1234").orEmpty().ifBlank { "1234" }
        set(value) = prefs.edit().putString(KEY_ADMIN_PASSCODE, value).apply()

    var cameraSource: CameraSource
        get() {
            val name = prefs.getString(KEY_CAMERA_SOURCE, CameraSource.RTSP.name)
            return try { CameraSource.valueOf(name!!) } catch (e: Exception) { CameraSource.RTSP }
        }
        set(value) = prefs.edit().putString(KEY_CAMERA_SOURCE, value.name).apply()

    var themeMode: AppTheme
        get() {
            val name = prefs.getString(KEY_THEME_MODE, AppTheme.MIDNIGHT.name)
            return try { AppTheme.valueOf(name!!) } catch (e: Exception) { AppTheme.MIDNIGHT }
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var isPreviewMuted: Boolean
        get() = prefs.getBoolean(KEY_PREVIEW_MUTED, false)
        set(value) { prefs.edit().putBoolean(KEY_PREVIEW_MUTED, value).apply() }

    var maxRecordingMinutes: Int
        get() = prefs.getInt(KEY_MAX_RECORDING_MINUTES, 120)
        set(value) { prefs.edit().putInt(KEY_MAX_RECORDING_MINUTES, value).apply() }

    var previewTimeoutRecMins: Int
        get() = prefs.getInt(KEY_PREVIEW_TIMEOUT_REC, 1)
        set(value) { prefs.edit().putInt(KEY_PREVIEW_TIMEOUT_REC, value).apply() }

    var previewTimeoutIdleMins: Int
        get() = prefs.getInt(KEY_PREVIEW_TIMEOUT_IDLE, 5)
        set(value) { prefs.edit().putInt(KEY_PREVIEW_TIMEOUT_IDLE, value).apply() }

    var licenseKey: String
        get() {
            val stored = prefs.getString(KEY_LICENSE_KEY, "").orEmpty()
            return SecurityUtils.deobfuscate(stored)
        }
        set(value) = prefs.edit().putString(KEY_LICENSE_KEY, SecurityUtils.obfuscate(value)).apply()

    var lastLicenseCheck: Long
        get() = prefs.getLong(KEY_LAST_LICENSE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_LICENSE_CHECK, value).apply()

    var lockoutEndTime: Long
        get() = prefs.getLong(KEY_LOCKOUT_END_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKOUT_END_TIME, value).apply()

    var passcodeAttempts: Int
        get() = prefs.getInt(KEY_PASSCODE_ATTEMPTS, 0)
        set(value) = prefs.edit().putInt(KEY_PASSCODE_ATTEMPTS, value).apply()

    var watermarkPosition: WatermarkPosition
        get() {
            val name = prefs.getString(KEY_WATERMARK_POSITION, WatermarkPosition.BOTTOM_RIGHT.name)
            return try { WatermarkPosition.valueOf(name!!) } catch (e: Exception) { WatermarkPosition.BOTTOM_RIGHT }
        }
        set(value) = prefs.edit().putString(KEY_WATERMARK_POSITION, value.name).apply()

    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, 5)
        set(value) = prefs.edit().putInt(KEY_RETENTION_DAYS, value).apply()

    var customWatermarkPath: String?
        get() = prefs.getString(KEY_CUSTOM_WATERMARK_PATH, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_WATERMARK_PATH, value).apply()

    fun isLicensed(context: Context): Boolean {
        return SecurityUtils.verifyLicense(context, licenseKey)
    }

    companion object {
        private const val PREFS_NAME = "pbcam_settings"
        private const val KEY_RTSP_URL = "rtsp_url"
        private const val KEY_RTSP_SUB_URL = "rtsp_sub_url"
        private const val KEY_COURT_TAG = "court_tag"
        private const val KEY_ALERT_EMAIL = "alert_email"
        private const val KEY_IS_SETUP_COMPLETE = "is_setup_complete"
        private const val KEY_ADMIN_PASSCODE = "admin_passcode"
        private const val KEY_CAMERA_SOURCE = "camera_source"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PREVIEW_MUTED = "preview_muted"
        private const val KEY_LICENSE_KEY = "license_key"
        private const val KEY_LAST_LICENSE_CHECK = "last_license_check"
        private const val KEY_MAX_RECORDING_MINUTES = "max_recording_minutes"
        private const val KEY_PREVIEW_TIMEOUT_REC = "preview_timeout_rec"
        private const val KEY_PREVIEW_TIMEOUT_IDLE = "preview_timeout_idle"
        private const val KEY_LOCKOUT_END_TIME = "lockout_end_time"
        private const val KEY_PASSCODE_ATTEMPTS = "passcode_attempts"
        private const val KEY_WATERMARK_POSITION = "watermark_position"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_CUSTOM_WATERMARK_PATH = "custom_watermark_path"
        private const val DEFAULT_RTSP_URL = "rtsp://192.168.1.100:554/stream"
    }
}
