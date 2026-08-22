package com.pbcam.keygenpbcam

import android.content.Context
import android.provider.Settings
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object AdminSecurityUtils {
    private const val MASK_KEY = "pbcam_security_key_2026"
    private val AUTHORIZED_ADMIN_IDS = listOf("PB-4B13-127A", "PB-9A1F-34F3") // Primary and Honor 90
    
    // CORRECT XOR values for "pbcam_premium_2026_secret" vs "pbcam_security_key_2026"
    // This MUST match SecurityUtils.kt in the main app exactly.
    private val SCRAMBLED_SALT = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x17, 0x06, 0x18, 
        0x1B, 0x1C, 0x19, 0x26, 0x6D, 0x5B, 0x57, 0x4F, 0x00, 0x41, 
        0x55, 0x51, 0x44, 0x15, 0x16
    )

    private fun getLicenseSalt(): String {
        val key = MASK_KEY.toByteArray(StandardCharsets.UTF_8)
        val result = ByteArray(SCRAMBLED_SALT.size)
        for (i in SCRAMBLED_SALT.indices) {
            result[i] = (SCRAMBLED_SALT[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return String(result, StandardCharsets.UTF_8)
    }

    fun generateSerialKey(deviceId: String): String {
        // Robust Hashing: Strip dashes and uppercase before math
        val cleanId = deviceId.replace("PB-", "").replace("-", "").trim().uppercase()
        if (cleanId.isEmpty()) return ""

        val input = cleanId + getLicenseSalt()
        val hashBytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        val hex = hashBytes.joinToString("") { "%02x".format(it) }.uppercase()
        
        // Return formatted 16-character key: XXXX-XXXX-XXXX-XXXX
        return hex.take(16).chunked(4).joinToString("-")
    }

    fun getDeviceId(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val hash = MessageDigest.getInstance("MD5")
            .digest(id.toByteArray(StandardCharsets.UTF_8))
        val raw = hash.joinToString("") { "%02x".format(it) }.take(8).uppercase()
        val parts = raw.chunked(4).joinToString("-")
        return "PB-$parts"
    }

    fun verifyAdminDevice(context: Context): Boolean {
        val currentId = getDeviceId(context)
        return AUTHORIZED_ADMIN_IDS.contains(currentId)
    }
}
