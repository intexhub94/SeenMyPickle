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

data class PcServerRecording(
    val filename: String,
    val sizeBytes: Long,
    val sizeMb: Double,
    val createdDate: String,
    val streamUrl: String
)

data class PcServerStatus(
    val isOnline: Boolean = false,
    val ip: String = "",
    val port: Int = 5000,
    val storagePath: String = "",
    val freeStorageGb: Double = 0.0,
    val recordings: List<PcServerRecording> = emptyList()
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

    suspend fun probePcServer(ipAddress: String, port: Int = 5000): PcServerStatus? = withContext(Dispatchers.IO) {
        if (ipAddress.isBlank()) return@withContext null
        val targetUrl = "http://$ipAddress:$port/api/status"
        
        try {
            val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2000
            }
            
            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(jsonStr)
                
                val recsUrl = "http://$ipAddress:$port/api/recordings"
                val recsList = mutableListOf<PcServerRecording>()
                
                try {
                    val recConn = (URL(recsUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 2000
                        readTimeout = 2000
                    }
                    if (recConn.responseCode == 200) {
                        val recJsonStr = recConn.inputStream.bufferedReader().readText()
                        val recJson = JSONObject(recJsonStr)
                        val arr = recJson.optJSONArray("recordings")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.getJSONObject(i)
                                recsList.add(
                                    PcServerRecording(
                                        filename = item.optString("filename", ""),
                                        sizeBytes = item.optLong("size_bytes", 0L),
                                        sizeMb = item.optDouble("size_mb", 0.0),
                                        createdDate = item.optString("created_date", ""),
                                        streamUrl = item.optString("stream_url", "")
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch recordings list from PC server: ${e.message}")
                }

                PcServerStatus(
                    isOnline = true,
                    ip = json.optString("ip", ipAddress),
                    port = json.optInt("port", port),
                    storagePath = json.optString("storage_path", ""),
                    freeStorageGb = json.optDouble("free_storage_gb", 0.0),
                    recordings = recsList
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "PC Server probe failed for $ipAddress:$port - ${e.message}")
            null
        }
    }
}
