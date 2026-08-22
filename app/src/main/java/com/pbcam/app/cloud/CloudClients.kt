package com.pbcam.app.cloud

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection as HttpConn
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import com.pbcam.app.data.db.RecordingSession

object DriveUploader {
    private const val FOLDER_NAME = "pb cam footage"

    fun upload(accessToken: String, file: File, courtTag: String, onProgress: (Float) -> Unit = {}): String? {
        val totalSize = file.length()
        if (totalSize == 0L) return null

        val folderId = getOrCreateFolder(accessToken)
        
        // 3-Strike Retry for handshake
        var sessionUrl: String? = null
        for (attempt in 1..3) {
            sessionUrl = initiateResumableUpload(accessToken, file.name, courtTag, folderId)
            if (sessionUrl != null) break
            android.util.Log.w("DriveUploader", "Handshake attempt $attempt failed, retrying...")
            Thread.sleep(2000)
        }
        
        if (sessionUrl == null) return null
        
        // True Resume check
        val startByte = getUploadStatus(sessionUrl)
        if (startByte >= totalSize) {
             val fileId = finalizeUpload(sessionUrl) ?: return null
             makePublic(accessToken, fileId)
             return "https://drive.google.com/file/d/$fileId/view"
        }

        val connection = (URL(sessionUrl).openConnection() as HttpConn).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Range", "bytes $startByte-${totalSize - 1}/$totalSize")
            setRequestProperty("Content-Length", (totalSize - startByte).toString())
            setRequestProperty("Content-Type", "video/mp4")
            setChunkedStreamingMode(1024 * 1024) 
            doOutput = true
            connectTimeout = 60000
            readTimeout = 60000
        }

        var bytesUploaded = startByte
        connection.outputStream.use { out ->
            file.inputStream().buffered(1024 * 1024).use { input ->
                if (startByte > 0) input.skip(startByte)
                val buffer = ByteArray(1024 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    bytesUploaded += bytesRead
                    onProgress(bytesUploaded.toFloat() / totalSize.toFloat())
                }
            }
        }

        return if (connection.responseCode in 200..299) {
            val response = connection.inputStream.bufferedReader().readText()
            val fileId = JSONObject(response).optString("id", null) ?: return null
            makePublic(accessToken, fileId)
            "https://drive.google.com/file/d/$fileId/view"
        } else {
            android.util.Log.e("DriveUploader", "Upload failed with code: ${connection.responseCode}")
            null
        }
    }

    private fun getOrCreateFolder(accessToken: String): String? {
        val query = URLEncoder.encode("name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false", "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query"
        
        val connection = (URL(searchUrl).openConnection() as HttpConn).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val files = JSONObject(response).getJSONArray("files")
            if (files.length() > 0) {
                return files.getJSONObject(0).getString("id")
            }
        }

        val metadata = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", "application/vnd.google-apps.folder")

        val createConnection = (URL("https://www.googleapis.com/drive/v3/files").openConnection() as HttpConn).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        createConnection.outputStream.use { it.write(metadata.toString().toByteArray()) }
        
        return if (createConnection.responseCode in 200..299) {
            val response = createConnection.inputStream.bufferedReader().readText()
            JSONObject(response).getString("id")
        } else null
    }

    private fun initiateResumableUpload(accessToken: String, filename: String, courtTag: String, folderId: String?): String? {
        val metadata = JSONObject()
            .put("name", filename)
            .put("mimeType", "video/mp4")
            .put("description", "PBCam recording — $courtTag")
        
        if (folderId != null) {
            metadata.put("parents", JSONArray().put(folderId))
        }

            val connection = (URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                .openConnection() as HttpConn).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("X-Upload-Content-Type", "video/mp4")
                doOutput = true
                connectTimeout = 60000
                readTimeout = 60000
            }

        connection.outputStream.use { it.write(metadata.toString().toByteArray()) }

        if (connection.responseCode != 200) return null
        return connection.getHeaderField("Location")
    }

    private fun getUploadStatus(sessionUrl: String): Long {
        return try {
            val connection = (URL(sessionUrl).openConnection() as HttpConn).apply {
                requestMethod = "PUT"
                setRequestProperty("Content-Range", "bytes */*")
            }
            if (connection.responseCode == 308) {
                val range = connection.getHeaderField("Range")
                if (range != null && range.startsWith("bytes=")) {
                    return range.substringAfter("-").toLong() + 1
                }
            }
            0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun finalizeUpload(sessionUrl: String): String? {
        val connection = (URL(sessionUrl).openConnection() as HttpConn).apply {
            requestMethod = "PUT"
        }
        if (connection.responseCode in 200..299) {
            val response = connection.inputStream.bufferedReader().readText()
            return JSONObject(response).optString("id", null)
        }
        return null
    }

    private fun makePublic(accessToken: String, fileId: String) {
        val connection = (URL("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
            .openConnection() as HttpConn).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        val body = JSONObject()
            .put("role", "reader")
            .put("type", "anyone")
            .toString()
        connection.outputStream.use { it.write(body.toByteArray()) }
        connection.responseCode
    }

    fun cleanupOldFiles(accessToken: String, retentionDays: Int = 5) {
        val folderId = getOrCreateFolder(accessToken) ?: return
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val dateString = sdf.format(Date(cutoffTime))

        val query = URLEncoder.encode("'$folderId' in parents and createdTime < '$dateString' and trashed = false", "UTF-8")
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id, name)"
        
        val connection = (URL(searchUrl).openConnection() as HttpConn).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val files = JSONObject(response).getJSONArray("files")
            for (i in 0 until files.length()) {
                val fileId = files.getJSONObject(i).getString("id")
                deleteFile(accessToken, fileId)
            }
        }
    }

    private fun deleteFile(accessToken: String, fileId: String) {
        val connection = (URL("https://www.googleapis.com/drive/v3/files/$fileId").openConnection() as HttpConn).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        if (connection.responseCode != 204) {
            android.util.Log.e("DriveUploader", "Failed to delete file $fileId: ${connection.responseCode}")
        }
    }

    fun getStorageInfo(accessToken: String): Pair<Long, Long>? {
        return try {
            val url = "https://www.googleapis.com/drive/v3/about?fields=storageQuota"
            val connection = (URL(url).openConnection() as HttpConn).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 15000
                readTimeout = 15000
            }
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val quota = JSONObject(response).getJSONObject("storageQuota")
                val limit = quota.optLong("limit", -1L)
                val usage = quota.optLong("usage", 0L)
                Pair(limit, usage)
            } else {
                android.util.Log.e("DriveUploader", "Failed to fetch storage info: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("DriveUploader", "Error fetching storage info", e)
            null
        }
    }

    /**
     * Finds the latest APK in the developer's PUBLIC update folder using an API Key.
     * This avoids needing any user login to check for updates.
     */
    fun findReleaseApkAnonymous(apiKey: String, folderId: String): Pair<String, String>? {
        android.util.Log.d("DriveUpdate", "Starting findReleaseApkAnonymous...")
        return try {
            // Search for APKs inside the developer's public folder ID
            val query = URLEncoder.encode("'" + folderId + "' in parents and mimeType = 'application/vnd.android.package-archive' and trashed = false", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=" + query + "&key=" + apiKey + "&fields=files(id, name)&orderBy=createdTime%20desc"
            
            android.util.Log.d("DriveUpdate", "Querying public folder: " + folderId)
            val connection = (URL(url).openConnection() as HttpConn).apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                android.util.Log.d("DriveUpdate", "Public folder response: " + response)
                val apkFiles = JSONObject(response).getJSONArray("files")
                if (apkFiles.length() > 0) {
                    val latestApk = apkFiles.getJSONObject(0)
                    val apkName = latestApk.getString("name")
                    android.util.Log.d("DriveUpdate", "Found latest APK: " + apkName)
                    return Pair(latestApk.getString("id"), apkName)
                } else {
                    android.util.Log.d("DriveUpdate", "No APK files found in public folder")
                }
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText()
                android.util.Log.e("DriveUpdate", "Public search failed: " + connection.responseCode + " - " + error)
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("DriveUpdate", "Error finding release APK anonymously", e)
            null
        }
    }

    fun downloadFile(accessToken: String, fileId: String, outputFile: File, onProgress: (Float) -> Unit): Boolean {
        return try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val connection = (URL(url).openConnection() as HttpConn).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 30000
                readTimeout = 30000
            }

            if (connection.responseCode == 200) {
                val totalSize = connection.contentLength.toLong()
                var downloaded = 0L
                
                connection.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalSize > 0) {
                                onProgress(downloaded.toFloat() / totalSize.toFloat())
                            }
                        }
                    }
                }
                true
            } else {
                android.util.Log.e("DriveUploader", "Download failed with code: ${connection.responseCode}")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("DriveUploader", "Error downloading file", e)
            false
        }
    }
}

object GmailNotifier {
    private const val LOGO_CID = "seemy_pickle_logo"
    private const val BOUNDARY = "----=_Part_SeeMyPickle_Boundary"

    fun send(context: Context, accessToken: String, to: String, subject: String, body: String): Boolean {
        // Load logo from resources - Use direct ID for better stability
        val logoBase64 = try {
            val logoResId = com.pbcam.app.R.raw.app_logo_email
            context.resources.openRawResource(logoResId).use { input: InputStream -> 
                val bytes = input.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) { 
            android.util.Log.w("GmailNotifier", "Could not load logo from resources: ${e.message}")
            null 
        }

        val rawMessage = buildString {
            append("MIME-Version: 1.0\r\n")
            append("To: $to\r\n")
            append("Subject: $subject\r\n")
            append("Content-Type: multipart/related; boundary=\"$BOUNDARY\"\r\n\r\n")

            // HTML Part
            append("--$BOUNDARY\r\n")
            append("Content-Type: text/html; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 8bit\r\n\r\n")
            append(body)
            append("\r\n")

            // Logo Attachment Part
            if (logoBase64 != null) {
                append("--$BOUNDARY\r\n")
                append("Content-Type: image/png; name=\"logo.png\"\r\n")
                append("Content-ID: <$LOGO_CID>\r\n")
                append("Content-Disposition: inline; filename=\"logo.png\"\r\n")
                append("Content-Transfer-Encoding: base64\r\n\r\n")
                append(logoBase64)
                append("\r\n")
            }

            append("--$BOUNDARY--")
        }

        val encoded = Base64.encodeToString(
            rawMessage.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val payload = JSONObject().put("raw", encoded).toString()
        val connection = (URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .openConnection() as HttpConn).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 60000
            readTimeout = 60000
        }
        
        return try {
            connection.outputStream.use { it.write(payload.toByteArray()) }
            val isSuccess = connection.responseCode in 200..299
            if (!isSuccess) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                android.util.Log.e("GmailNotifier", "API Error: ${connection.responseCode} - $errorStream")
            }
            isSuccess
        } catch (e: Exception) {
            android.util.Log.e("GmailNotifier", "Failed to send email", e)
            false
        } finally {
            connection.disconnect()
        }
    }

    fun buildReadyBody(courtTag: String, dateStr: String, shareUrl: String, retentionDays: Int = 5): String {
        return """
            <html>
            <body style="font-family: 'Segoe UI', Arial, sans-serif; color: #333; line-height: 1.6; background-color: #f4f4f4; margin: 0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.1);">
                    <div style="background-color: #000000; padding: 30px; text-align: center;">
                        <img src="cid:$LOGO_CID" alt="SeeMyPickle Logo" style="max-width: 180px; height: auto;">
                    </div>
                    <div style="padding: 40px;">
                        <h2 style="color: #2E7D32; margin-top: 0; font-size: 24px; border-bottom: 2px solid #2E7D32; padding-bottom: 10px;">Footage Ready!</h2>
                        <p style="font-size: 16px;">Hello,</p>
                        <p style="font-size: 16px;">Your recording from <b>$courtTag</b> is now available for viewing.</p>
                        
                        <div style="background-color: #f9f9f9; padding: 20px; border-radius: 8px; margin: 25px 0; border: 1px solid #eee;">
                            <table style="width: 100%; border-collapse: collapse;">
                                <tr>
                                    <td style="padding: 5px 0; color: #666;"><b>Court:</b></td>
                                    <td style="padding: 5px 0; text-align: right;">$courtTag</td>
                                </tr>
                                <tr>
                                    <td style="padding: 5px 0; color: #666;"><b>Recorded On:</b></td>
                                    <td style="padding: 5px 0; text-align: right;">$dateStr</td>
                                </tr>
                            </table>
                        </div>
                        
                        <div style="text-align: center; margin: 35px 0;">
                            <a href="$shareUrl" style="background-color: #2E7D32; color: #ffffff; padding: 16px 45px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; display: inline-block; box-shadow: 0 4px 6px rgba(46,125,50,0.2);">WATCH RECORDING</a>
                        </div>
                        
                        <p style="font-size: 13px; color: #888; text-align: center;">
                            Button not working? Copy and paste this link:<br>
                            <span style="color: #1976D2;">$shareUrl</span>
                        </p>
                        
                        <hr style="border: 0; border-top: 1px solid #eee; margin: 30px 0;">
                        
                        <div style="background-color: #FFF3E0; padding: 15px; border-radius: 6px; border: 1px solid #FFE0B2; text-align: center;">
                            <p style="margin: 0; color: #E65100; font-size: 13px; font-weight: bold;">
                                ⚠️ DISCLAIMER: Rolling $retentionDays-Day Retention Policy
                            </p>
                            <p style="margin: 5px 0 0 0; color: #666; font-size: 12px;">
                                This footage is stored for $retentionDays days from the recording date and will be permanently deleted thereafter. Please download your footage if you wish to keep it longer.
                            </p>
                        </div>
                    </div>
                    <div style="background-color: #fafafa; padding: 20px; text-align: center; font-size: 12px; color: #999;">
                        SeenMyPickle Smart Court System &copy; 2026. All rights reserved.
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}

/**
 * Modern Excel Export for Android (compatible with API 24+)
 * Uses org.dhatim.fastexcel for memory-efficient standalone writing.
 */
object ExcelExporter {
    fun exportToExcel(sessions: List<RecordingSession>, outputStream: OutputStream) {
        val workbook = Workbook(outputStream, "SeeMyPickle", "1.0")
        val worksheet = workbook.newWorksheet("Session History")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

        // Header
        worksheet.value(0, 0, "ID")
        worksheet.value(0, 1, "Date")
        worksheet.value(0, 2, "Court")
        worksheet.value(0, 3, "Email")
        worksheet.value(0, 4, "Status")
        worksheet.value(0, 5, "Drive Link")
        worksheet.value(0, 6, "Errors")

        // Style header (simple bold-ish look via capitals)
        // Note: fastexcel is low-level; we focus on data first.

        // Data
        sessions.forEachIndexed { index, session ->
            val row = index + 1
            worksheet.value(row, 0, session.id)
            worksheet.value(row, 1, sdf.format(Date(session.startTime)))
            worksheet.value(row, 2, session.courtTag ?: "N/A")
            worksheet.value(row, 3, session.targetEmail ?: "N/A")
            worksheet.value(row, 4, session.status.name)
            worksheet.value(row, 5, session.gDriveUrl ?: "")
            worksheet.value(row, 6, session.errorMessage ?: "")
        }

        workbook.finish()
    }
}
