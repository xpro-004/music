package com.xrotune.app.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.xrotune.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom

private const val TAG = "AuthManager"

sealed interface SignInResult {
    data class Success(val account: UserAccount) : SignInResult
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

data class UserAccount(val id: String, val displayName: String?, val email: String?, val photoUrl: String?)

class AuthManager(private val appContext: Context) {
    fun currentAccount(): UserAccount? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return UserAccount(user.uid, user.displayName, user.email, user.photoUrl?.toString())
    }

    suspend fun signIn(activity: Activity): SignInResult {
        val clientId = try {
            appContext.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In client configuration is missing", e)
            return SignInResult.Failure("Google Sign-In is not configured. Check google-services.json and the OAuth client.")
        }

        return try {
            val credentialManager = CredentialManager.create(activity)
            val option = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .setNonce(generateNonce())
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = credentialManager.getCredential(activity, request).credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return SignInResult.Failure("Google returned an unsupported credential.")
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val token = googleCredential.idToken
            if (token.isBlank()) return SignInResult.Failure("Google Sign-In returned an empty ID token.")

            val firebaseUser = FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .await()
                .user
                ?: return SignInResult.Failure("Firebase Authentication did not return a user.")

            SignInResult.Success(
                UserAccount(
                    id = firebaseUser.uid,
                    displayName = firebaseUser.displayName ?: googleCredential.displayName,
                    email = firebaseUser.email ?: googleCredential.id,
                    photoUrl = firebaseUser.photoUrl?.toString() ?: googleCredential.profilePictureUri?.toString(),
                ),
            )
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager sign-in failed", e)
            SignInResult.Failure("Google Sign-In could not be completed. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign-in failed", e)
            SignInResult.Failure("Sign-in failed. Check your connection and Firebase configuration.")
        }
    }

    suspend fun signOut() {
        FirebaseAuth.getInstance().signOut()
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Credential state cleanup did not complete", e)
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
