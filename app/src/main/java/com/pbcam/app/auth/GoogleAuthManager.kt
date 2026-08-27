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

    suspend fun getFreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val googleAccount = getLastSignedInAccount() ?: run {
            Log.w("GoogleAuthManager", "getFreshAccessToken: No signed-in Google account")
            return@withContext null
        }
        val account = googleAccount.account ?: run {
            Log.w("GoogleAuthManager", "getFreshAccessToken: No account in GoogleSignInAccount")
            return@withContext null
        }
        val scopeString = "oauth2:$DRIVE_FILE_SCOPE $DRIVE_READONLY_SCOPE $GMAIL_SEND_SCOPE"

        try {
            val token = GoogleAuthUtil.getToken(context, account, scopeString)
            Log.i("GoogleAuthManager", "Successfully obtained fresh access token")
            token
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "getFreshAccessToken primary attempt failed: ${e.message} (${e.javaClass.simpleName})")
            // Invalidate cached token if possible and retry once
            try {
                val cachedToken = GoogleAuthUtil.getToken(context, account, scopeString)
                GoogleAuthUtil.clearToken(context, cachedToken)
            } catch (_: Exception) {}

            try {
                val tokenRetry = GoogleAuthUtil.getToken(context, account, scopeString)
                Log.i("GoogleAuthManager", "Successfully obtained token on retry after clear")
                tokenRetry
            } catch (retryException: Exception) {
                Log.e("GoogleAuthManager", "getFreshAccessToken retry failed: ${retryException.message} (${retryException.javaClass.simpleName})", retryException)
                null
            }
        }
    }

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            Log.d("GoogleAuthManager", "Sign in successful: ${SecurityUtils.maskEmail(account.email)}")
            Result.success(account)
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val statusCode = e.statusCode
            val errorMsg = when (statusCode) {
                10 -> "DEVELOPER_ERROR (Check SHA-1 and Client ID in Firebase)"
                7 -> "NETWORK_ERROR (Check Wi-Fi/Internet)"
                12500 -> "SIGN_IN_FAILED"
                12501 -> "SIGN_IN_CANCELLED"
                12502 -> "SIGN_IN_IN_PROGRESS"
                else -> "Error Code: $statusCode"
            }
            Log.e("GoogleAuthManager", "Sign in failed: $errorMsg ($statusCode)")
            Result.failure(e)
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

    fun invalidateToken(token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
            Log.i("GoogleAuthManager", "Token invalidated manually.")
        } catch (e: Exception) {
            Log.w("GoogleAuthManager", "Failed to invalidate token: ${e.message}")
        }
    }

    companion object {
        const val RC_SIGN_IN = 9001
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        const val GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send"
    }
}
