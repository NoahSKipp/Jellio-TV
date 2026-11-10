package com.jellio.tv.ui

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.prefs.StreamPreferences
import com.jellio.tv.ui.detail.EditionParser
import com.jellio.tv.ui.player.BadStreams

sealed interface PlayAction {
    data class Direct(val itemId: String, val mediaSourceId: String?) : PlayAction
    data class ShowPicker(val item: BaseItemDto) : PlayAction
}

// The remembered stream plays straight away when it is still listed, not
// marked bad and, with an edition picked, of that edition; anything else
// opens the picker.
suspend fun decidePlayAction(
    item: BaseItemDto,
    streamPreferences: StreamPreferences,
    forceChoice: Boolean,
    edition: String? = null,
    loadSources: suspend () -> List<MediaSourceDto>,
): PlayAction {
    if (!forceChoice && streamPreferences.isRememberEnabled()) {
        val remembered = streamPreferences.rememberedMediaSourceId(item.Id)
        if (remembered != null) {
            val sources = loadSources()
            val rememberedSource = sources.firstOrNull { it.Id == remembered && !BadStreams.isBad(item.Id, it) }
            if (rememberedSource != null) {
                val editionMatches = edition == null || EditionParser.detectSourceEdition(rememberedSource, sources) == edition
                if (editionMatches) return PlayAction.Direct(item.Id, remembered)
            }
        }
    }
    return PlayAction.ShowPicker(item)
}
