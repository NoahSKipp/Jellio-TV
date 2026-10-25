package com.jellio.tv.data.cache

import android.content.Context
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.ui.home.ComingSoonHomeRow
import com.jellio.tv.ui.home.HomeRow
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterHomeRow
import com.jellio.tv.ui.home.StudioHubsHomeRow
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class CachedRowDto(
    val key: String,
    val title: String,
    val items: List<BaseItemDto> = emptyList(),
    val landscape: Boolean = false,
    val comingSoon: List<CalendarEntryDto> = emptyList(),
    val studioHubs: List<String> = emptyList(),
    val isComingSoon: Boolean = false,
    val isStudioHubs: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class HomeSnapshot(
    val greeting: String = "",
    val heroItems: List<BaseItemDto> = emptyList(),
    val rows: List<CachedRowDto> = emptyList(),
    val savedAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class LibrarySnapshot(
    val title: String = "",
    val coverflowItems: List<BaseItemDto> = emptyList(),
    val coverflowBadge: String? = null,
    val mainRowTitle: String? = null,
    val mainRowItems: List<BaseItemDto> = emptyList(),
    val sections: List<CachedRowDto> = emptyList(),
    val genreOptions: List<String> = emptyList(),
    val selectedSort: String = "CommunityRating:Descending",
    val selectedGenre: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class CachedServiceRowDto(
    val title: String,
    val kind: String,
    val items: List<BaseItemDto> = emptyList(),
    val collectionId: String,
)

@JsonClass(generateAdapter = true)
data class ServiceSnapshot(
    val heroItem: BaseItemDto? = null,
    val rows: List<CachedServiceRowDto> = emptyList(),
    val topGenres: List<String> = emptyList(),
    val savedAt: Long = System.currentTimeMillis(),
)

fun HomeRow.toCachedRowDto(): CachedRowDto = when (this) {
    is PosterHomeRow -> CachedRowDto(
        key = key,
        title = displayName,
        items = section.items,
        landscape = landscape,
    )
    is ComingSoonHomeRow -> CachedRowDto(
        key = key,
        title = displayName,
        comingSoon = entries,
        items = items.values.toList(),
        isComingSoon = true,
    )
    is StudioHubsHomeRow -> CachedRowDto(
        key = key,
        title = displayName,
        studioHubs = services,
        isStudioHubs = true,
    )
}

fun CachedRowDto.toHomeRow(): HomeRow = when {
    isComingSoon -> ComingSoonHomeRow(comingSoon, items.associateBy { it.Id.replace("-", "").lowercase() })
    isStudioHubs -> StudioHubsHomeRow(studioHubs)
    else -> PosterHomeRow(
        section = HomeSection(title = title, items = items, key = key),
        landscape = landscape,
    )
}

fun HomeSection.toCachedRowDto(): CachedRowDto = CachedRowDto(
    key = key,
    title = title,
    items = items,
)

fun CachedRowDto.toHomeSection(): HomeSection = HomeSection(
    title = title,
    items = items,
    key = key,
)

// Disk-backed snapshot store for instant cold start rendering of
// Home screen rows, Library (Movies, Series, Anime) screens and Studio Hubs.
@Singleton
class RowSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val cacheDir: File by lazy {
        File(context.cacheDir, "jellio_row_snapshots").apply { mkdirs() }
    }

    private val homeAdapter by lazy { moshi.adapter(HomeSnapshot::class.java) }
    private val libraryAdapter by lazy { moshi.adapter(LibrarySnapshot::class.java) }
    private val serviceAdapter by lazy { moshi.adapter(ServiceSnapshot::class.java) }

    suspend fun loadHomeSnapshot(userId: String): HomeSnapshot? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "home_${sanitizeKey(userId)}.json")
        if (!file.exists()) return@withContext null
        runCatching {
            val json = file.readText()
            homeAdapter.fromJson(json)
        }.getOrNull()
    }

    suspend fun saveHomeSnapshot(userId: String, snapshot: HomeSnapshot) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "home_${sanitizeKey(userId)}.json")
        runCatching {
            val json = homeAdapter.toJson(snapshot)
            file.writeText(json)
        }
    }

    suspend fun loadLibrarySnapshot(userId: String, libraryId: String): LibrarySnapshot? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "library_${sanitizeKey(userId)}_${sanitizeKey(libraryId)}.json")
        if (!file.exists()) return@withContext null
        runCatching {
            val json = file.readText()
            libraryAdapter.fromJson(json)
        }.getOrNull()
    }

    suspend fun saveLibrarySnapshot(userId: String, libraryId: String, snapshot: LibrarySnapshot) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "library_${sanitizeKey(userId)}_${sanitizeKey(libraryId)}.json")
        runCatching {
            val json = libraryAdapter.toJson(snapshot)
            file.writeText(json)
        }
    }

    suspend fun loadServiceSnapshot(userId: String, serviceName: String): ServiceSnapshot? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "service_${sanitizeKey(userId)}_${sanitizeKey(serviceName)}.json")
        if (!file.exists()) return@withContext null
        runCatching { serviceAdapter.fromJson(file.readText()) }.getOrNull()
    }

    suspend fun saveServiceSnapshot(userId: String, serviceName: String, snapshot: ServiceSnapshot) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "service_${sanitizeKey(userId)}_${sanitizeKey(serviceName)}.json")
        runCatching { file.writeText(serviceAdapter.toJson(snapshot)) }
    }

    private fun sanitizeKey(key: String): String = key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
