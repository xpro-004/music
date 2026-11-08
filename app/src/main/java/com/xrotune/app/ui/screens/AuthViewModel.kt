package com.xrotune.app.ui.screens

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xrotune.app.auth.AuthManager
import com.xrotune.app.auth.SessionStore
import com.xrotune.app.auth.SignInResult
import com.xrotune.app.auth.UserAccount
import com.xrotune.app.auth.resolveAuthSession
import com.xrotune.app.data.repo.FirestoreRepository
import com.xrotune.app.data.repo.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AuthUiState(
    val account: UserAccount? = null,
    val isGuest: Boolean = false,
    val restoring: Boolean = true,
    val signingIn: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(
    private val authManager: AuthManager,
    private val sessionStore: SessionStore,
    private val repository: MusicRepository,
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val restored = resolveAuthSession(authManager.currentAccount(), sessionStore.isGuest.first())
                if (restored.account != null) {
                    _state.value = AuthUiState(account = restored.account, restoring = false)
                    try {
                        sessionStore.save(restored.account)
                        syncProfile(restored.account)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.value = _state.value.copy(
                            error = "You are signed in, but your cloud profile could not sync. Check your connection and retry.",
                        )
                    }
                } else {
                    sessionStore.clearAccount()
                    _state.value = AuthUiState(isGuest = restored.isGuest, restoring = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val firebaseAccount = authManager.currentAccount()
                _state.value = AuthUiState(
                    account = firebaseAccount,
                    restoring = false,
                    error = "Session restore was incomplete. Your Firebase sign-in is preserved; please retry sync.",
                )
            }
        }
    }

    fun signIn(activity: Activity) {
        if (_state.value.signingIn) return
        _state.value = _state.value.copy(signingIn = true, error = null)
        viewModelScope.launch {
            try {
                when (val result = authManager.signIn(activity)) {
                    is SignInResult.Success -> {
                        _state.value = _state.value.copy(account = result.account, isGuest = false)
                        sessionStore.save(result.account)
                        try {
                            syncProfile(result.account)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _state.value = _state.value.copy(
                                error = "You are signed in, but your cloud profile could not sync. Check your connection and retry.",
                            )
                        }
                    }
                    SignInResult.Cancelled -> Unit
                    is SignInResult.Failure -> _state.value = _state.value.copy(error = result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Sign-in could not be completed. Please try again.",
                )
            } finally {
                _state.value = _state.value.copy(signingIn = false)
            }
        }
    }

    private suspend fun syncProfile(account: UserAccount) {
        val email = account.email?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Google did not provide an email address.")
        firestoreRepository.saveUserProfile(
            displayName = account.displayName?.takeIf(String::isNotBlank) ?: "XroTune User",
            email = email,
            photoUrl = account.photoUrl,
        )
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            try {
                // A guest session must not leave an older Firebase identity active.
                authManager.signOut()
                sessionStore.setGuestMode(true)
                _state.value = AuthUiState(restoring = false, isGuest = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(restoring = false, error = "Guest mode could not be saved. Please try again.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authManager.signOut()
                repository.authHeader = null
                // Clear account metadata and persist guest mode in one DataStore transaction.
                sessionStore.setGuestMode(true)
                _state.value = AuthUiState(restoring = false, isGuest = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "You have been signed out of Firebase, but local session cleanup failed. Restart the app.",
                )
            }
        }
    }
}
