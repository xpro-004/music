package com.xrotune.app.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthSessionPolicyTest {
    @Test
    fun firebaseAccountTakesPrecedenceOverSavedGuestMode() {
        val account = UserAccount("firebase-uid", "Test User", "test@example.com", null)
        val result = resolveAuthSession(account, savedGuestMode = true)

        assertEquals(account, result.account)
        assertEquals(false, result.isGuest)
    }

    @Test
    fun staleCachedAccountIsNotRestoredWithoutFirebaseAccount() {
        val result = resolveAuthSession(firebaseAccount = null, savedGuestMode = false)

        assertEquals(null, result.account)
        assertEquals(false, result.isGuest)
    }

    @Test
    fun guestModeSurvivesProcessRestartWithoutFirebaseAccount() {
        val result = resolveAuthSession(firebaseAccount = null, savedGuestMode = true)

        assertEquals(null, result.account)
        assertEquals(true, result.isGuest)
    }
}
