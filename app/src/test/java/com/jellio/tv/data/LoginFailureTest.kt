package com.jellio.tv.data

import java.io.IOException
import java.net.SocketTimeoutException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class LoginFailureTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun failuresMapToTypedReasons() {
        val fallback = LoginFailure.SERVER_UNREACHABLE
        assertEquals(LoginFailure.WRONG_CREDENTIALS, loginFailureOf(http(401), fallback))
        assertEquals(LoginFailure.WRONG_CREDENTIALS, loginFailureOf(http(403), fallback))
        assertEquals(LoginFailure.SERVER_ERROR, loginFailureOf(http(503), fallback))
        assertEquals(fallback, loginFailureOf(http(404), fallback))
        assertEquals(LoginFailure.TIMEOUT, loginFailureOf(SocketTimeoutException("read timed out"), fallback))
        assertEquals(LoginFailure.SERVER_UNREACHABLE, loginFailureOf(IOException("No server address configured."), LoginFailure.QUICK_CONNECT_FAILED))
        assertEquals(LoginFailure.QUICK_CONNECT_FAILED, loginFailureOf(IllegalStateException("boom"), LoginFailure.QUICK_CONNECT_FAILED))
    }

    @Test
    fun aRejectedQuickConnectIsNotAWrongPassword() {
        assertEquals(
            LoginFailure.QUICK_CONNECT_FAILED,
            loginFailureOf(http(401), LoginFailure.QUICK_CONNECT_FAILED, rejected = LoginFailure.QUICK_CONNECT_FAILED),
        )
    }

    @Test
    fun serverAddressFailuresKeepTheirReason() {
        assertEquals(LoginFailure.SERVER_NOT_FOUND, loginFailureOf(ServerAddressException(LoginFailure.SERVER_NOT_FOUND, "tried"), LoginFailure.SERVER_UNREACHABLE))
        assertEquals(LoginFailure.ENTER_SERVER, loginFailureOf(ServerAddressException(LoginFailure.ENTER_SERVER, ""), LoginFailure.SERVER_UNREACHABLE))
    }

    @Test
    fun everyReasonHasAString() {
        val en = com.jellio.tv.data.i18n.Strings.tableFor(com.jellio.tv.data.prefs.AppLanguage.ENGLISH)
        LoginFailure.entries.forEach { assert(en.containsKey(it.key)) { "missing ${it.key}" } }
    }
}
