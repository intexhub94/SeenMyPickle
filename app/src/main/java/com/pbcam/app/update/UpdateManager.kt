package com.pbcam.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pbcam.app.cloud.DriveUploader
import java.io.File

object UpdateManager {

    fun getVersionFromFileName(filename: String): String? {
        // Expected format: pbcam_v1.1.apk or PBCam_1.2.apk
        val regex = Regex(""".*?(\d+\.\d+).*?\.apk""", RegexOption.IGNORE_CASE)
        val match = regex.find(filename)
        return match?.groupValues?.get(1)
    }

    fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
        try {
            val current = currentVersion.replace("v", "").split(".").map { it.toInt() }
            val remote = remoteVersion.replace("v", "").split(".").map { it.toInt() }
            
            for (i in 0 until minOf(current.size, remote.size)) {
                if (remote[i] > current[i]) return true
                if (remote[i] < current[i]) return false
            }
            return remote.size > current.size
        } catch (e: Exception) {
            return false
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
