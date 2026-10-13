package com.jellio.tv.data

import org.junit.Assert.assertFalse
import org.junit.Test

class DetailFieldsTest {
    @Test
    fun titleInfoNeverAsksForStreams() {
        for (fields in listOf(DETAIL_FIELDS, PLAYER_DETAIL_FIELDS)) {
            assertFalse(fields, fields.contains("MediaSources"))
            assertFalse(fields, fields.contains("MediaStreams"))
        }
    }
}
