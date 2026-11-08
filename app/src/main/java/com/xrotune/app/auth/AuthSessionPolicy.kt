package com.xrotune.app.auth

data class AuthSessionSnapshot(val account: UserAccount?, val isGuest: Boolean)

fun resolveAuthSession(firebaseAccount: UserAccount?, savedGuestMode: Boolean): AuthSessionSnapshot =
    if (firebaseAccount != null) AuthSessionSnapshot(firebaseAccount, false)
    else AuthSessionSnapshot(null, savedGuestMode)
