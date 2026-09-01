package com.jellio.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LivePathTest {
    @Test
    fun namesTheChosenPath() {
        assertEquals("channel c1: source audio ac3, server HLS with AAC", describeLivePath("c1", "AC3", viaHls = true))
        assertEquals("channel c2: source audio aac, direct play", describeLivePath("c2", "aac", viaHls = false))
        assertEquals("channel c3: source audio unknown, direct play", describeLivePath("c3", null, viaHls = false))
    }
}
