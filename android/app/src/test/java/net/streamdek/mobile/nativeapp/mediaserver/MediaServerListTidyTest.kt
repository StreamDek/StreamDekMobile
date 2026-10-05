package net.streamdek.mobile.nativeapp.mediaserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaServerListTidyTest {
    private fun library(server: String, key: String, enabled: Boolean) =
        MediaServerLibrary(serverId = server, key = key, title = "Library $key", kind = MediaServerLibraryKind.Movies, enabled = enabled)

    private fun server(id: String, enabled: Boolean, vararg libraries: MediaServerLibrary) =
        MediaServerView(id = id, name = "Server $id", owned = true, ownerName = null, enabled = enabled, reachability = MediaServerReachability.Unknown, libraries = libraries.toList())

    @Test
    fun `a server and a library with the same ids are different entries`() {
        assertFalse(mediaServerEntryKey("plex", "a", "1") == mediaServerEntryKey("plex", "a"))
        assertFalse(mediaServerEntryKey("plex", "a", "1") == mediaServerEntryKey("jellyfin", "a", "1"))
    }

    @Test
    fun `a removed library leaves the list and its neighbours stay`() {
        val servers = listOf(server("a", true, library("a", "1", false), library("a", "2", true)))
        val removed = setOf(mediaServerEntryKey("plex", "a", "1"))
        val listed = listedMediaServers("plex", servers, removed)
        assertEquals(listOf("2"), listed.single().libraries.map { it.key })
        val entries = removedMediaServerEntries("plex", servers, removed)
        assertEquals("1", entries.single().library?.key)
    }

    @Test
    fun `something switched back on elsewhere is listed again`() {
        val servers = listOf(server("a", true, library("a", "1", true)))
        val removed = setOf(mediaServerEntryKey("plex", "a", "1"))
        assertEquals(1, listedMediaServers("plex", servers, removed).single().libraries.size)
        assertTrue(removedMediaServerEntries("plex", servers, removed).isEmpty())
    }

    @Test
    fun `a removed server takes its libraries with it and is listed once`() {
        val servers = listOf(
            server("a", false, library("a", "1", false), library("a", "2", false)),
            server("b", true, library("b", "1", true)),
        )
        val removed = setOf(mediaServerEntryKey("plex", "a"), mediaServerEntryKey("plex", "a", "1"))
        assertEquals(listOf("b"), listedMediaServers("plex", servers, removed).map { it.id })
        val entries = removedMediaServerEntries("plex", servers, removed)
        assertEquals(1, entries.size)
        assertEquals(null, entries.single().library)
    }

    @Test
    fun `a server that is merely off stays on the list`() {
        val servers = listOf(server("a", false, library("a", "1", true)))
        assertEquals(1, listedMediaServers("plex", servers, emptySet()).size)
    }
}
