package com.pbcam.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.pbcam.app.R
import com.pbcam.app.data.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthManager(private val context: Context) {
    private val webClientId = context.getString(R.string.google_oauth_web_client_id)

    val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
            .requestScopes(
                Scope(DRIVE_FILE_SCOPE),
                Scope(DRIVE_READONLY_SCOPE),
                Scope(GMAIL_SEND_SCOPE)
            )
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isAuthenticated(): Boolean = getLastSignedInAccount() != null

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val googleAccount = getLastSignedInAccount() ?: run {
            Log.e("GoogleAuthManager", "getAccessToken: No last signed in account")
            return@withContext null
        }
        val account = googleAccount.account ?: run {
            Log.e("GoogleAuthManager", "getAccessToken: No account in googleAccount")
            return@withContext null
        }
        try {
            val scopeString = "oauth2:$DRIVE_FILE_SCOPE $DRIVE_READONLY_SCOPE $GMAIL_SEND_SCOPE"
            Log.d("GoogleAuthManager", "Requesting token with scopes: $scopeString")
            val token = GoogleAuthUtil.getToken(context, account, scopeString)
            Log.d("GoogleAuthManager", "Token successfully retrieved")
            token
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "getToken failed: $e", e)
            null
        }
    }

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            Log.d("GoogleAuthManager", "Sign in successful: ${SecurityUtils.maskEmail(account.email)}")
            account
        } catch (e: com.google.android.gms.common.api.ApiException) {
            Log.e("GoogleAuthManager", "Sign in failed: $e")
            null
        }
    }

    fun signOut(activity: Activity, onComplete: () -> Unit) {
        // Use a background thread to clear the token before signing out
        // This ensures the next login doesn't use a stale token without the new scopes
        java.lang.Thread {
            try {
                val googleAccount = getLastSignedInAccount()
                val account = googleAccount?.account
                if (account != null) {
                    val scopeString = "oauth2:$DRIVE_FILE_SCOPE $DRIVE_READONLY_SCOPE $GMAIL_SEND_SCOPE"
                    val token = GoogleAuthUtil.getToken(context, account, scopeString)
                    GoogleAuthUtil.clearToken(context, token)
                    Log.d("GoogleAuthManager", "Successfully cleared cached token during sign-out")
                }
            } catch (e: Exception) {
                Log.w("GoogleAuthManager", "Failed to clear token during sign out: $e")
            }
            
            activity.runOnUiThread {
                signInClient.signOut().addOnCompleteListener(activity) { onComplete() }
            }
        }.start()
    }

    companion object {
        const val RC_SIGN_IN = 9001
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        const val GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send"
    }
}
