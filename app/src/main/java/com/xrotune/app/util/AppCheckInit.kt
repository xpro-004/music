package com.xrotune.app.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.xrotune.app.BuildConfig

private const val TAG = "AppCheckInit"

/**
 * Installs the platform-appropriate App Check provider before app features use Firebase.
 * Debug tokens are generated and logged by the Firebase SDK; register them in Firebase Console.
 */
fun configureAppCheck(context: Context) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val providerFactory = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(providerFactory)
        Log.i(TAG, if (BuildConfig.DEBUG) "Debug App Check provider installed." else "Play Integrity provider installed.")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check; verify Firebase and provider setup.", e)
    }
}
