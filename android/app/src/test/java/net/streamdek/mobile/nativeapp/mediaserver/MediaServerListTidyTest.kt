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
    fun `servers follow the chosen order and newcomers join the end`() {
        val servers = listOf(server("a", true), server("b", true), server("c", true))
        val order = listOf(mediaServerEntryKey("plex", "c"), mediaServerEntryKey("plex", "a"))
        assertEquals(listOf("c", "a", "b"), servers.inServerOrder("plex", order).map { it.id })
        assertEquals(listOf("a", "b", "c"), servers.inServerOrder("plex", emptyList()).map { it.id })
        // Another provider's order does not apply.
        assertEquals(listOf("a", "b", "c"), servers.inServerOrder("jellyfin", order).map { it.id })
    }

    @Test
    fun `moving a server moves it among those shown and keeps the rest`() {
        val a = mediaServerEntryKey("plex", "a")
        val b = mediaServerEntryKey("plex", "b")
        val c = mediaServerEntryKey("plex", "c")
        val hidden = mediaServerEntryKey("plex", "gone")
        assertEquals(listOf(b, a, c), movedServerOrder(emptyList(), listOf(a, b, c), b, -1))
        assertEquals(listOf(a, c, b), movedServerOrder(emptyList(), listOf(a, b, c), b, 1))
        // Already at an end: nothing changes.
        assertEquals(listOf(hidden), movedServerOrder(listOf(hidden), listOf(a, b, c), a, -1))
        // A server that is not shown keeps its remembered place after the shown ones.
        assertEquals(listOf(b, a, c, hidden), movedServerOrder(listOf(hidden, a), listOf(a, b, c), b, -1))
    }

    @Test
    fun `each server keeps its own library order and switched off libraries keep their place`() {
        val a = server("a", true, library("a", "1", true), library("a", "2", false), library("a", "3", true))
        val b = server("b", true, library("b", "1", true), library("b", "2", true))
        val shownA = a.libraries.map { mediaServerLibraryOrderKey("a", it.key) }
        // Library 3 to the top of server a.
        val order = movedServerOrder(emptyList(), shownA, mediaServerLibraryOrderKey("a", "3"), -2)
        assertEquals(listOf("3", "1", "2"), a.withLibrariesInOrder(order).libraries.map { it.key })
        // The same library keys on server b are untouched.
        assertEquals(listOf("1", "2"), b.withLibrariesInOrder(order).libraries.map { it.key })
        // Switching library 2 back on does not move it.
        val reenabled = a.copy(libraries = a.libraries.map { it.copy(enabled = true) })
        assertEquals(listOf("3", "1", "2"), reenabled.withLibrariesInOrder(order).libraries.map { it.key })
        // A library added to the server later joins the end.
        val grown = a.copy(libraries = a.libraries + library("a", "4", true))
        assertEquals(listOf("3", "1", "2", "4"), grown.withLibrariesInOrder(order).libraries.map { it.key })
    }

    @Test
    fun `page rows follow servers then libraries and keep each library's rows together`() {
        fun row(server: String, kind: MediaServerRowKind, library: String?) =
            MediaServerRow(id = "$server-$kind-$library", title = "$kind", serverId = server, serverName = server, kind = kind, mediaType = "movie", items = emptyList(), libraryKey = library)
        val next = row("a", MediaServerRowKind.NextUp, null)
        val added1 = row("a", MediaServerRowKind.RecentlyAdded, "1")
        val films = row("a", MediaServerRowKind.Library, "1")
        val shows = row("a", MediaServerRowKind.Library, "2")
        val other = row("b", MediaServerRowKind.Library, "9")
        val servers = listOf(
            server("b", true, library("b", "9", true)),
            server("a", true, library("a", "2", true), library("a", "1", true)),
        )
        assertEquals(listOf(other, next, shows, added1, films), listOf(next, added1, films, shows, other).inPageOrder(servers))
    }

    @Test
    fun `an order saved by the first version is carried over`() {
        val rowKeys = listOf(
            mediaServerEntryKey("row", "srv", "Library:2"),
            mediaServerEntryKey("row", "srv", "NextUp:"),
            mediaServerEntryKey("row", "srv", "RecentlyAdded:1"),
            mediaServerEntryKey("row", "srv", "Library:1"),
        )
        assertEquals(
            listOf(mediaServerLibraryOrderKey("srv", "2"), mediaServerLibraryOrderKey("srv", "1")),
            libraryOrderFromRowOrder(rowKeys),
        )
    }

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
