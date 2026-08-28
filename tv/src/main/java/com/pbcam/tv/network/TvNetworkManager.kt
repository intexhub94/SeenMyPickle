package com.pbcam.tv.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LocalTabletStatus(
    val isOnline: Boolean = false,
    val status: String = "IDLE",
    val rtspUrl: String = "",
    val rtspSubUrl: String = "",
    val courtTag: String = "",
    val deviceId: String = "",
    val timestamp: Long = 0L
)

object TvNetworkManager {
    private const val TAG = "TvNetworkManager"
    private const val PORT = 8080

    suspend fun probeLocalTablet(ipAddress: String): LocalTabletStatus? = withContext(Dispatchers.IO) {
        if (ipAddress.isBlank()) return@withContext null
        val targetUrl = "http://$ipAddress:$PORT/status"
        
        try {
            val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
            }
            
            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(jsonStr)
                Log.d(TAG, "Local probe succeeded for $ipAddress: $jsonStr")
                
                LocalTabletStatus(
                    isOnline = json.optBoolean("isOnline", true),
                    status = json.optString("status", "IDLE"),
                    rtspUrl = json.optString("rtspUrl", ""),
                    rtspSubUrl = json.optString("rtspSubUrl", ""),
                    courtTag = json.optString("courtTag", ""),
                    deviceId = json.optString("deviceId", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            } else {
                Log.w(TAG, "Local probe returned code ${connection.responseCode} for $ipAddress")
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Local probe unable to reach $ipAddress: ${e.message}")
            null
        }
    }
}
