package com.jellio.tv.data.session

import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class ExpiredSession(val serverAddress: String, val userId: String, val userName: String)

// Watches the API client's 401s and reports a session the server no longer
// accepts (signed out from the dashboard, device removed, password changed).
@Singleton
class SessionGuard @Inject constructor(
    private val sessionManager: SessionManager,
    // A provider: the API's client is the one reporting to this guard.
    private val api: Provider<JellyfinApi>,
) {
    private val detector = SessionLossDetector()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _expired = MutableStateFlow<ExpiredSession?>(null)
    val expired: StateFlow<ExpiredSession?> = _expired.asStateFlow()

    fun onResponse(status: Int, requestHost: String, path: String, token: String?, carriesOwnCredentials: Boolean) {
        if (token.isNullOrBlank()) return
        val serverHost = sessionManager.getCachedServerAddress()?.toHttpUrlOrNull()?.host
        if (!SessionLossDetector.isCandidate(status, requestHost, serverHost, path, carriesOwnCredentials)) return
        if (!detector.beginCheck(token)) return
        scope.launch {
            val session = sessionManager.sessionFlow.first()
            if (session == null || session.accessToken != token) {
                detector.finishCheck(token, null)
                return@launch
            }
            val status = confirmStatus(session)
            if (detector.finishCheck(token, status) && sessionManager.getCachedAccessToken() == token) {
                android.util.Log.w("JellioSession", "session no longer accepted by the server")
                _expired.value = ExpiredSession(session.serverAddress, session.userId, session.userName)
            }
        }
    }

    // A cheap call with the same token: its own Authorization header keeps it
    // from being reported back here.
    private suspend fun confirmStatus(session: Session): Int? = try {
        val authorization = buildEmbyAuthorizationHeader(sessionManager.getCachedDeviceId(), APP_VERSION, session.accessToken)
        api.get().getUserWithToken(session.accessToken, session.userId, authorization)
        200
    } catch (err: retrofit2.HttpException) {
        err.code()
    } catch (err: kotlinx.coroutines.CancellationException) {
        throw err
    } catch (err: Exception) {
        null
    }

    fun acknowledge(expired: ExpiredSession) {
        _expired.compareAndSet(expired, null)
    }
}
