package com.jellio.tv.data

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.TimeoutCancellationException
import retrofit2.HttpException

// Why signing in failed. key names the message in the string tables; the
// UI resolves it, never the exception text.
enum class LoginFailure(val key: String) {
    ENTER_SERVER("auth_enter_server"),
    SERVER_NOT_FOUND("auth_server_not_found"),
    SERVER_UNREACHABLE("auth_server_unreachable"),
    TIMEOUT("auth_server_timeout"),
    SERVER_ERROR("auth_server_error"),
    WRONG_CREDENTIALS("auth_wrong_credentials"),
    NO_REMEMBERED_SIGNIN("auth_no_remembered_signin"),
    SESSION_EXPIRED("auth_session_expired"),
    SAVED_SIGNIN_EXPIRED("auth_saved_signin_expired"),
    SWITCH_PROFILE_ERROR("auth_switch_profile_error"),
    SERVER_TRY_AGAIN("auth_server_try_again"),
    RESET_CODE_INVALID("auth_reset_code_invalid"),
    RESET_FINISH_ERROR("auth_reset_finish_error"),
    RESET_TRY_AGAIN("auth_reset_try_again"),
    INVALID_USER_RESPONSE("auth_invalid_user_response"),
    MISSING_TOKEN("auth_missing_token"),
    QUICK_CONNECT_FAILED("auth_quick_connect_failed"),
}

class ServerAddressException(val reason: LoginFailure, detail: String) : IOException(detail)

// rejected is what a 401 or 403 means for the request that failed.
fun loginFailureOf(
    err: Throwable,
    fallback: LoginFailure,
    rejected: LoginFailure = LoginFailure.WRONG_CREDENTIALS,
): LoginFailure = when {
    err is ServerAddressException -> err.reason
    err is HttpException && (err.code() == 401 || err.code() == 403) -> rejected
    err is HttpException && err.code() >= 500 -> LoginFailure.SERVER_ERROR
    err is HttpException -> fallback
    err is InterruptedIOException || err is TimeoutCancellationException -> LoginFailure.TIMEOUT
    err is IOException -> LoginFailure.SERVER_UNREACHABLE
    else -> fallback
}
