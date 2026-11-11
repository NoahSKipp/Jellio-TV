package com.jellio.tv.data

import com.jellio.tv.data.model.AddedByResponseDto
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CommunityWatcherDto
import com.jellio.tv.data.model.FriendMuteBody
import com.jellio.tv.data.model.FriendPersonDto
import com.jellio.tv.data.model.FriendRequestBody
import com.jellio.tv.data.model.FriendStatusDto
import com.jellio.tv.data.model.FriendStatusResultDto
import com.jellio.tv.data.model.FriendsListDto
import com.jellio.tv.data.model.FriendsSettingsBody
import com.jellio.tv.data.model.FriendsSettingsDto
import com.jellio.tv.data.model.WatchlistFriendResultDto
import com.jellio.tv.data.model.WatchlistFriendsBody
import com.jellio.tv.data.network.JellyfinApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Friends and watchlist sharing. A plugin without these endpoints answers 404;
// from then on the feature counts as unavailable for the session and the
// screens hide their entry points.
@Singleton
class FriendsRepository @Inject constructor(
    private val api: JellyfinApi,
) {
    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    @Volatile
    private var addedBy: Map<String, List<CommunityWatcherDto>> = emptyMap()

    private suspend fun <T> call(block: suspend () -> T): Result<T> {
        if (!_available.value) return Result.failure(IllegalStateException("friends unavailable"))
        val result = catching { block() }
        val error = result.exceptionOrNull()
        if (error is retrofit2.HttpException && error.code() == 404) _available.value = false
        return result
    }

    // A new account or a fresh try: ask the server again.
    fun reset() {
        _available.value = true
        addedBy = emptyMap()
    }

    suspend fun list(): Result<FriendsListDto> = call { api.getFriends() }

    suspend fun status(userId: String): Result<FriendStatusDto> = call { api.getFriendStatus(userId) }

    suspend fun people(query: String): Result<List<FriendPersonDto>> = call { api.findFriendPeople(query) }

    // These answer with a status body on refusals too, so the response is read
    // here; a 404 is a request that is gone, not a missing route.
    private suspend fun action(block: suspend () -> retrofit2.Response<FriendStatusResultDto>): Result<String> {
        if (!_available.value) return Result.failure(IllegalStateException("friends unavailable"))
        val response = catching { block() }.getOrElse { return Result.failure(it) }
        if (response.isSuccessful) return Result.success(response.body()?.Status ?: "none")
        val body = catching { response.errorBody()?.string() }.getOrNull()
        return Result.failure(FriendRequestException(friendFailureOf(response.code(), statusFromBody(body))))
    }

    suspend fun sendRequest(userId: String): Result<String> = action { api.sendFriendRequest(FriendRequestBody(userId)) }

    suspend fun accept(requestId: String): Result<String> = action { api.acceptFriendRequest(requestId) }

    suspend fun decline(requestId: String): Result<String> = action { api.declineFriendRequest(requestId) }

    suspend fun cancel(requestId: String): Result<String> = action { api.cancelFriendRequest(requestId) }

    suspend fun remove(userId: String): Result<String> = call { api.removeFriend(userId).Status }

    suspend fun setMuted(userId: String, muted: Boolean): Result<Boolean> =
        call { api.muteFriend(userId, FriendMuteBody(muted)).Muted }

    suspend fun addToWatchlists(itemId: String, userIds: List<String>): Result<List<WatchlistFriendResultDto>> =
        call { api.addToFriendWatchlists(WatchlistFriendsBody(itemId, userIds)).Results }

    suspend fun settings(): Result<FriendsSettingsDto> = call {
        val profile = api.getProfileSettings()
        FriendsSettingsDto(profile.FriendsCanAddToWatchlist, profile.FriendRequestsFrom)
    }

    suspend fun saveSettings(canAdd: Boolean?, requestsFrom: String?): Result<FriendsSettingsDto> =
        call { api.setFriendsSettings(FriendsSettingsBody(canAdd, requestsFrom)) }

    // Fetched once when the Watchlist rows load and held in memory.
    suspend fun refreshAddedBy() {
        val dto: AddedByResponseDto = call { api.getWatchlistAddedBy() }.getOrNull() ?: return
        addedBy = addedByMap(dto)
    }

    fun withAddedBy(items: List<BaseItemDto>): List<BaseItemDto> = items.withAddedBy(addedBy)
}
