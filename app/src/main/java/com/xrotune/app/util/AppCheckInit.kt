package com.xrotune.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.xrotune.app.BuildConfig

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

/**
 * Installs App Check before any Firebase product is used.
 *
 * Debug builds use the Debug provider. Release builds use Play Integrity,
 * which is the supported native Android provider for apps distributed through
 * Google Play or sideloaded channels. Register the app and configure its
 * Play Integrity settings in the Firebase console before enforcing App Check.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrBlank()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE,
                ).edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .apply()
                Log.i(TAG, "App Check debug token configured.")
            } else {
                Log.w(TAG, "No debug token supplied; register the token printed by Firebase in the console.")
            }

            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
            )
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        }
        Log.i(TAG, if (BuildConfig.DEBUG) "Debug App Check provider installed." else "Play Integrity provider installed.")
    } catch (e: Exception) {
        // Do not silently pretend App Check is active. Firebase enforcement may
        // reject subsequent requests if provider installation failed.
        Log.e(TAG, "Failed to initialize Firebase App Check; verify Firebase and provider setup.", e)
    }
}
