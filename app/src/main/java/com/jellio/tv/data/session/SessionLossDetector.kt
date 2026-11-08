package com.jellio.tv.data.session

// Decides when a 401 means the signed-in token is gone. A single 401 can come
// from one endpoint the user has no rights to, so a candidate only starts one
// confirming call; only a 401 on that call too counts as a lost session.
class SessionLossDetector(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val recheckAfterMs: Long = DEFAULT_RECHECK_AFTER_MS,
) {
    private var checking: String? = null
    private val lastClearedAt = HashMap<String, Long>()
    private val lost = HashSet<String>()

    @Synchronized
    fun beginCheck(token: String): Boolean {
        if (token.isBlank() || token in lost || checking != null) return false
        val cleared = lastClearedAt[token]
        if (cleared != null && nowMs() - cleared < recheckAfterMs) return false
        checking = token
        return true
    }

    // confirmStatus is the HTTP status of the confirming call, or null when it
    // failed without one (network error, timeout). Returns true once per token
    // when the session is lost.
    @Synchronized
    fun finishCheck(token: String, confirmStatus: Int?): Boolean {
        if (checking == token) checking = null
        if (confirmStatus == 401) return lost.add(token)
        if (lastClearedAt.size > MAX_TRACKED) lastClearedAt.clear()
        lastClearedAt[token] = nowMs()
        return false
    }

    @Synchronized
    fun isLost(token: String): Boolean = token in lost

    companion object {
        const val DEFAULT_RECHECK_AFTER_MS = 60_000L
        private const val MAX_TRACKED = 16

        private val AUTH_PATHS = listOf(
            "/users/authenticatebyname",
            "/users/authenticatewithquickconnect",
            "/users/forgotpassword",
            "/quickconnect/",
        )

        // Only the signed-in token on the user's own server counts: requests
        // carrying their own credentials (a saved profile's token) and the
        // sign-in endpoints handle their own 401s.
        fun isCandidate(
            status: Int,
            requestHost: String,
            serverHost: String?,
            path: String,
            carriesOwnCredentials: Boolean,
        ): Boolean {
            if (status != 401 || carriesOwnCredentials) return false
            if (serverHost.isNullOrBlank() || !requestHost.equals(serverHost, ignoreCase = true)) return false
            val lower = path.lowercase().trimEnd('/') + "/"
            return AUTH_PATHS.none { lower.contains(it) }
        }
    }
}
