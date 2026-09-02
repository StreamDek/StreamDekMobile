package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The magnet handed to the local engine and to the premium services.
 *
 * A magnet carrying only an info-hash can be answered through DHT alone, which on a mobile
 * connection frequently means no peers, no metadata and a source that looks dead — so the
 * add-on's announce list surviving into the magnet is what makes torrent playback work at all.
 */
class TorrentMagnetTest {

  @Test
  fun `the add-on's own trackers come first, with the public list behind them`() {
    val sources = listOf(
      "tracker:udp://tracker.example.org:1337/announce",
      "dht:1a2b3c",
      "tracker:http://tracker2.example.org:80/announce",
    )

    val trackers = streamTrackers(sources)

    assertEquals(
      listOf("udp://tracker.example.org:1337/announce", "http://tracker2.example.org:80/announce"),
      trackers.take(2),
    )
    // The regression this guards: an add-on that declared one stale announce URL used to replace
    // the entire public list with it, so a single dead tracker left the source with nowhere to ask.
    assertTrue(trackers.size > 2)
  }

  @Test
  fun `dht entries are not trackers`() {
    assertTrue(streamTrackers(listOf("dht:abc")).none { it.startsWith("dht") })
  }

  @Test
  fun `a source with no trackers falls back to the public list rather than none`() {
    val trackers = streamTrackers(emptyList())

    assertTrue(trackers.isNotEmpty())
    assertTrue(trackers.all { it.startsWith("udp://") || it.startsWith("http") })
  }

  @Test
  fun `duplicate trackers are announced once`() {
    val repeated = listOf(
      "tracker:udp://tracker.example.org:1337/announce",
      "tracker:udp://tracker.example.org:1337/announce",
    )

    assertEquals(1, streamTrackers(repeated).count { it == "udp://tracker.example.org:1337/announce" })
  }

  @Test
  fun `a tracker the add-on already declares is not repeated by the public list`() {
    val shared = streamTrackers(emptyList()).first()

    val trackers = streamTrackers(listOf("tracker:$shared"))

    assertEquals(1, trackers.count { it == shared })
  }

  @Test
  fun `no https tracker is announced`() {
    // The engine's OpenSSL has no certificate store it can reach on Android, so an https announce
    // cannot complete a handshake — it only adds a timeout to the wait for peers.
    assertTrue(streamTrackers(emptyList()).none { it.startsWith("https://") })
  }

  @Test
  fun `the magnet carries the hash, the name and every announce url`() {
    val magnet = buildMagnetLink(
      infoHash = "0123456789abcdef0123456789abcdef01234567",
      filename = "Some Movie 2024.mkv",
      sources = listOf("tracker:udp://tracker.example.org:1337/announce"),
    )

    assertTrue(magnet.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
    assertTrue(magnet.contains("&dn=Some+Movie+2024.mkv"))
    assertTrue(magnet.contains("&tr=udp%3A%2F%2Ftracker.example.org%3A1337%2Fannounce"))
  }

  @Test
  fun `a trackerless source still produces an announceable magnet`() {
    val magnet = buildMagnetLink("0123456789abcdef0123456789abcdef01234567", null, emptyList())

    // The regression this guards: the magnet used to be hash-and-name only, leaving both the local
    // engine and any debrid service that had to fetch the torrent with nothing but DHT.
    assertTrue(magnet.contains("&tr="))
  }

  @Test
  fun `whitespace around a hash does not reach the magnet`() {
    val magnet = buildMagnetLink("  0123456789abcdef0123456789abcdef01234567 ", null, emptyList())

    assertTrue(magnet.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&"))
  }
}
