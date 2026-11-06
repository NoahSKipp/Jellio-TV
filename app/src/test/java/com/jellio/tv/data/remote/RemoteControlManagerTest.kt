package com.jellio.tv.data.remote

import com.jellio.tv.data.model.ClientCapabilitiesDto
import com.jellio.tv.data.network.JellyfinApi
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class RemoteControlManagerTest {

    private class FakeJellyfinApi : InvocationHandler {
        var lastPostedCapabilities: ClientCapabilitiesDto? = null

        override fun invoke(proxy: Any?, method: java.lang.reflect.Method, args: Array<out Any>?): Any? {
            if (method.name == "postCapabilities") {
                lastPostedCapabilities = args?.get(0) as? ClientCapabilitiesDto
                return retrofit2.Response.success(Unit)
            }
            return null
        }
    }

    private fun createManager(): Pair<RemoteControlManager, FakeJellyfinApi> {
        val fake = FakeJellyfinApi()
        val api = Proxy.newProxyInstance(
            JellyfinApi::class.java.classLoader,
            arrayOf(JellyfinApi::class.java),
            fake,
        ) as JellyfinApi
        val moshi = Moshi.Builder().build()
        val manager = RemoteControlManager(api, moshi)
        return Pair(manager, fake)
    }

    @Test
    fun parsesPlayCommandWithItemIdsArray() = runBlocking {
        val (manager, _) = createManager()
        val json = """
            {
                "MessageType": "Play",
                "Data": {
                    "ItemIds": ["abc-123-def"],
                    "PlayCommand": "PlayNow",
                    "StartPositionTicks": 50000000,
                    "MediaSourceId": "src-1",
                    "AudioStreamIndex": 1,
                    "SubtitleStreamIndex": 2
                }
            }
        """.trimIndent()

        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.playCommands.first() }
        val handled = manager.handleWebSocketMessage(json)
        assertTrue(handled)

        val cmd = deferred.await()
        assertEquals("abc123def", cmd.itemId)
        assertEquals("PlayNow", cmd.playCommand)
        assertEquals(50_000_000L, cmd.startPositionTicks)
        assertEquals("src-1", cmd.mediaSourceId)
        assertEquals(1, cmd.audioStreamIndex)
        assertEquals(2, cmd.subtitleStreamIndex)
    }

    @Test
    fun parsesPlayCommandWithFallbackItemIdString() = runBlocking {
        val (manager, _) = createManager()
        val json = """
            {
                "MessageType": "Play",
                "Data": {
                    "ItemId": "xyz-789",
                    "StartPositionTicks": 10000
                }
            }
        """.trimIndent()

        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.playCommands.first() }
        val handled = manager.handleWebSocketMessage(json)
        assertTrue(handled)

        val cmd = deferred.await()
        assertEquals("xyz789", cmd.itemId)
        assertEquals("PlayNow", cmd.playCommand)
        assertEquals(10_000L, cmd.startPositionTicks)
        assertNull(cmd.mediaSourceId)
    }

    @Test
    fun parsesPlaystateCommandWithSeek() = runBlocking {
        val (manager, _) = createManager()
        val json = """
            {
                "MessageType": "Playstate",
                "Data": {
                    "Command": "Seek",
                    "SeekPositionTicks": 12345678
                }
            }
        """.trimIndent()

        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.playstateCommands.first() }
        val handled = manager.handleWebSocketMessage(json)
        assertTrue(handled)

        val cmd = deferred.await()
        assertEquals("Seek", cmd.command)
        assertEquals(12345678L, cmd.seekPositionTicks)
    }

    @Test
    fun parsesPlaystateCommandForPauseAndPlay() = runBlocking {
        val (manager, _) = createManager()
        val json = """
            {
                "MessageType": "Playstate",
                "Data": {
                    "Command": "PlayPause"
                }
            }
        """.trimIndent()

        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.playstateCommands.first() }
        val handled = manager.handleWebSocketMessage(json)
        assertTrue(handled)

        val cmd = deferred.await()
        assertEquals("PlayPause", cmd.command)
        assertNull(cmd.seekPositionTicks)
    }

    @Test
    fun parsesGeneralCommand() = runBlocking {
        val (manager, _) = createManager()
        val json = """
            {
                "MessageType": "GeneralCommand",
                "Data": {
                    "Name": "SetVolume",
                    "Arguments": {
                        "Volume": "75"
                    }
                }
            }
        """.trimIndent()

        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.generalCommands.first() }
        val handled = manager.handleWebSocketMessage(json)
        assertTrue(handled)

        val cmd = deferred.await()
        assertEquals("SetVolume", cmd.name)
        assertEquals("75", cmd.arguments["Volume"])
    }

    @Test
    fun ignoresUnknownOrMalformedMessages() {
        val (manager, _) = createManager()

        assertFalse(manager.handleWebSocketMessage("""{"MessageType": "Unknown"}"""))
        assertFalse(manager.handleWebSocketMessage("""{"MessageType": "Play"}"""))
        assertFalse(manager.handleWebSocketMessage("""{"MessageType": "Play", "Data": {}}"""))
        assertFalse(manager.handleWebSocketMessage("invalid-json"))
    }

    @Test
    fun reportsCapabilitiesSuccessfully() = runBlocking {
        val (manager, fake) = createManager()
        val result = manager.reportCapabilities()
        assertTrue(result.isSuccess)
        val caps = fake.lastPostedCapabilities
        assertNotNull(caps)
        assertTrue(caps!!.SupportsMediaControl)
        assertTrue(caps.SupportedCommands.contains("Play"))
        assertTrue(caps.SupportedCommands.contains("Playstate"))
    }
}
