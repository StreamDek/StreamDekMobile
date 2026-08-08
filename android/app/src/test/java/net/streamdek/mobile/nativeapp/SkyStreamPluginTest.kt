package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SkyStreamPluginTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  private val provider = SkyProvider(
    repoUrl = "https://repo.test/repo.json",
    packageName = "com.arranoust.torrentio",
    name = "Torrentio",
    version = 7,
    downloadUrl = "https://repo.test/dist/com.arranoust.torrentio.sky",
    description = null,
  )

  private fun writeZip(name: String, entries: Map<String, String>): File {
    val file = temporaryFolder.newFile(name)
    ZipOutputStream(file.outputStream()).use { zip ->
      entries.forEach { (entryName, content) ->
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content.toByteArray())
        zip.closeEntry()
      }
    }
    return file
  }

  // ── Repo entry classification ───────────────────────────────────────────────────────────────

  @Test fun `tells a sky bundle apart from a cloudstream extension`() {
    assertTrue(isSkyDownloadUrl("https://x.test/dist/com.a.b.sky"))
    assertTrue(isSkyDownloadUrl("https://x.test/dist/com.a.b.SKY"))
    // Query strings and fragments are common on CDN links and must not defeat the check.
    assertTrue(isSkyDownloadUrl("https://x.test/dist/com.a.b.sky?raw=1"))
    assertTrue(!isSkyDownloadUrl("https://x.test/dist/Provider.cs3"))
    assertTrue(!isSkyDownloadUrl("https://x.test/dist/skyline.cs3"))
    assertTrue(!isSkyDownloadUrl(""))
  }

  // ── Bundle unpacking ────────────────────────────────────────────────────────────────────────

  @Test fun `reads plugin json and plugin js out of a sky bundle`() {
    val file = writeZip(
      "good.sky",
      mapOf(
        "plugin.json" to """{"packageName":"com.a.b","name":"B","version":3}""",
        "plugin.js" to "globalThis.loadStreams=function(){};",
      ),
    )
    val bundle = readBundle(file)
    assertTrue(bundle.manifestJson.contains("com.a.b"))
    assertTrue(bundle.script.contains("loadStreams"))
  }

  @Test fun `rejects a cs3 style bundle with a clear message`() {
    // A CloudStream extension carries manifest.json plus classes.dex. Routing one here should say
    // so rather than failing later with something about JavaScript.
    val file = writeZip("wrong.sky", mapOf("manifest.json" to "{}", "classes.dex" to "dex"))
    val error = runCatching { readBundle(file) }.exceptionOrNull()
    assertTrue(error?.message.orEmpty().contains("No plugin.json inside wrong.sky"))
  }

  @Test fun `rejects a bundle whose script is missing`() {
    val file = writeZip("noscript.sky", mapOf("plugin.json" to "{}"))
    val error = runCatching { readBundle(file) }.exceptionOrNull()
    assertTrue(error?.message.orEmpty().contains("No plugin.js"))
  }

  // ── Result mapping ──────────────────────────────────────────────────────────────────────────

  @Test fun `maps a magnet reply onto an info-hash stream`() {
    val raw = """
      {"success":true,"data":[
        {"url":"magnet:?xt=urn:btih:C9E15763F722F23E98A29DECDFAE341B98D53056&dn=Movie","quality":"1080p","source":"Torrentio 1080p","headers":{}}
      ]}
    """.trimIndent()

    val streams = parseSkyStreams(raw, provider)

    assertEquals(1, streams.size)
    val stream = streams.single()
    // A magnet has to arrive as an info-hash so it flows through the same debrid resolution as an
    // add-on torrent result rather than being handed to the player as a URL it cannot open.
    assertEquals("C9E15763F722F23E98A29DECDFAE341B98D53056", stream.infoHash)
    assertNull(stream.url)
    assertEquals("1080p", stream.quality)
    assertEquals("Torrentio 1080p", stream.name)
    assertEquals("sky:com.arranoust.torrentio", stream.addonId)
    assertEquals("Torrentio", stream.addonName)
  }

  @Test fun `maps a direct link reply onto a url stream and keeps its headers`() {
    val raw = """
      {"success":true,"data":[
        {"url":"https://cdn.test/file.mkv","quality":"2160p","source":"RD","headers":{"Referer":"https://cdn.test/"}}
      ]}
    """.trimIndent()

    val stream = parseSkyStreams(raw, provider).single()

    assertEquals("https://cdn.test/file.mkv", stream.url)
    assertNull(stream.infoHash)
    assertEquals("https://cdn.test/", stream.requestHeaders["Referer"])
  }

  @Test fun `drops entries with no url and treats Unknown quality as absent`() {
    val raw = """
      {"success":true,"data":[
        {"quality":"1080p","source":"No URL"},
        {"url":"https://cdn.test/a.mkv","quality":"Unknown","source":"Plain"}
      ]}
    """.trimIndent()

    val streams = parseSkyStreams(raw, provider)

    assertEquals(1, streams.size)
    assertNull(streams.single().quality)
  }

  @Test fun `returns nothing for a declined or unreadable reply`() {
    assertTrue(parseSkyStreams("""{"success":false,"error":"IMDB ID tidak tersedia"}""", provider).isEmpty())
    assertTrue(parseSkyStreams("not json", provider).isEmpty())
    assertTrue(parseSkyStreams("""{"success":true}""", provider).isEmpty())
  }

  @Test fun `surfaces the plugin's own reason separately from the streams`() {
    assertEquals("IMDB ID tidak tersedia", skyStreamError("""{"success":false,"error":"IMDB ID tidak tersedia"}"""))
    assertEquals("No streams returned", skyStreamError("""{"success":false}"""))
    assertEquals("Unreadable plugin reply", skyStreamError("not json"))
    assertNull(skyStreamError("""{"success":true,"data":[]}"""))
  }

  // ── Request shaping ─────────────────────────────────────────────────────────────────────────

  @Test fun `treats every show spelling as series and everything else as movie`() {
    assertEquals("series", normalizeSkyType("tv"))
    assertEquals("series", normalizeSkyType("Series"))
    assertEquals("series", normalizeSkyType("show"))
    assertEquals("movie", normalizeSkyType("movie"))
  }

  @Test fun `only reads an info-hash out of a magnet`() {
    assertEquals(
      "C9E15763F722F23E98A29DECDFAE341B98D53056",
      skyMagnetInfoHash("magnet:?xt=urn:btih:C9E15763F722F23E98A29DECDFAE341B98D53056&dn=x"),
    )
    assertNull(skyMagnetInfoHash("https://cdn.test/file.mkv"))
    assertNull(skyMagnetInfoHash("magnet:?xt=urn:sha1:nothing"))
  }
}
