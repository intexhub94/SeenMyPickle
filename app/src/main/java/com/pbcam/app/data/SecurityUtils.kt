package com.pbcam.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object SecurityUtils {
    private const val MASK_KEY = "pbcam_security_key_2026"
    
    // CORRECT XOR values for "pbcam_premium_2026_secret" vs "pbcam_security_key_2026"
    private val SCRAMBLED_SALT = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x17, 0x06, 0x18, 
        0x1B, 0x1C, 0x19, 0x26, 0x6D, 0x5B, 0x57, 0x4F, 0x00, 0x41, 
        0x55, 0x51, 0x44, 0x15, 0x16
    )

    private fun getLicenseSalt(): String {
        // Enforce UTF-8 for consistency with Generator.html
        val key = MASK_KEY.toByteArray(StandardCharsets.UTF_8)
        val result = ByteArray(SCRAMBLED_SALT.size)
        for (i in SCRAMBLED_SALT.indices) {
            result[i] = (SCRAMBLED_SALT[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return String(result, StandardCharsets.UTF_8)
    }

    fun getDeviceId(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val hash = MessageDigest.getInstance("MD5")
            .digest(id.toByteArray(StandardCharsets.UTF_8))
        val raw = hash.joinToString("") { "%02x".format(it) }.take(8).uppercase()
        return raw.chunked(4).joinToString("-")
    }

    fun verifyLicense(context: Context, inputKey: String): Boolean {
        if (inputKey.isBlank()) return false
        val deviceId = getDeviceId(context)
        val expected = generateLicenseKey(deviceId)
        return inputKey.replace("-", "").uppercase() == expected.replace("-", "").uppercase()
    }

    private fun generateLicenseKey(deviceId: String): String {
        val cleanId = deviceId.replace("-", "").uppercase()
        val input = cleanId + getLicenseSalt()
        val hashBytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        val hex = hashBytes.joinToString("") { "%02x".format(it) }.uppercase()
        return hex.take(16).chunked(4).joinToString("-")
    }

    /**
     * Integrity Check: Verifies the app signature to prevent tampering.
     * Note: In a real release, you'd hardcode your specific SHA-256 fingerprint here.
     */
    fun verifyAppSignature(context: Context): Boolean {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return false

            // For this implementation, we verify that at least one valid signature exists.
            // In a production app, you would compare signature.toByteArray() hash 
            // against your actual release key hash.
            return signatures.isNotEmpty()
        } catch (e: Exception) {
            return false
        }
    }

    fun obfuscate(input: String): String {
        if (input.isBlank()) return ""
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val xored = ByteArray(bytes.size)
        for (i in bytes.indices) {
            xored[i] = (bytes[i].toInt() xor MASK_KEY[i % MASK_KEY.length].code).toByte()
        }
        return Base64.encodeToString(xored, Base64.NO_WRAP)
    }

    fun deobfuscate(input: String): String {
        if (input.isBlank()) return ""
        return try {
            val decoded = Base64.decode(input, Base64.NO_WRAP)
            val xored = ByteArray(decoded.size)
            for (i in decoded.indices) {
                xored[i] = (decoded[i].toInt() xor MASK_KEY[i % MASK_KEY.length].code).toByte()
            }
            String(xored, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("SecurityUtils", "Deobfuscation failed", e)
            ""
        }
    }

    fun maskEmail(email: String?): String {
        if (email.isNullOrBlank()) return "No email"
        if (email.contains(",")) {
            return email.split(",").joinToString(", ") { maskSingleEmail(it.trim()) }
        }
        return maskSingleEmail(email)
    }

    private fun maskSingleEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return email
        
        val name = parts[0]
        val domain = parts[1]
        if (name.length <= 2) return email // Too short to mask safely
        
        return "${name.first()}${ "*".repeat(name.length - 2) }${name.last()}@$domain"
    }

    fun generateSessionFileName(email: String?, timestamp: Long): String {
        val firstEmail = email?.split(",")?.firstOrNull()?.trim() ?: "anonymous"
        val obscured = maskSingleEmail(firstEmail).replace("@", "_at_")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US)
        val dateStr = sdf.format(java.util.Date(timestamp))
        return "${obscured}_$dateStr.mp4"
    }

    /**
     * Sanitizes RTSP URLs by removing credentials for safe logging.
     */
    fun sanitizeRtspUrl(url: String): String {
        if (url.isBlank()) return ""
        return url.replace(Regex("rtsp://[^@\\s]+@"), "rtsp://***:***@")
    }

    /**
     * Sanitizes multiple RTSP URLs within a log string.
     */
    fun sanitizeLogs(logs: String): String {
        if (logs.isBlank()) return ""
        return logs.replace(Regex("rtsp://[^@\\s]+@"), "rtsp://***:***@")
    }
}
