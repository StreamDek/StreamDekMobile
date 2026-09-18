package net.streamdek.mobile.nativeapp

import com.lagradost.cloudstream3.TvType
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

  // ── Mapping onto CloudStream ────────────────────────────────────────────────────────────────

  @Test fun `reads skystream categories as cloudstream types`() {
    assertEquals(setOf(TvType.Movie, TvType.TvSeries), skyProviderTypes(listOf("Movie", "TvSeries")))
    assertEquals(setOf(TvType.Live), skyProviderTypes(listOf("LiveTv")))
    assertEquals(setOf(TvType.Live), skyProviderTypes(listOf("Livestream", "Sports")))
    assertTrue(TvType.Anime in skyProviderTypes(listOf("Anime")))
    // A manifest that names nothing it knows is asked for films and series, not for nothing.
    assertEquals(setOf(TvType.Movie, TvType.TvSeries), skyProviderTypes(listOf("Bollywood")))
  }

  @Test fun `reads an item's own type and leaves unknown ones unset`() {
    assertEquals(TvType.TvSeries, skyItemType("series"))
    assertEquals(TvType.TvSeries, skyItemType("TvSeries"))
    assertEquals(TvType.Live, skyItemType("livestream"))
    assertEquals(TvType.Anime, skyItemType("anime"))
    assertNull(skyItemType(""))
    assertNull(skyItemType("something"))
  }

  @Test fun `reduces release names to the title a catalogue uses`() {
    assertEquals(
      "Avengers: Endgame" to 2019,
      skyCleanTitle("Avengers: Endgame (2019) BluRay [Hindi (DD5.1) & English] 1080p 720p & 480p Dual Audio [x264/10Bit-HEVC]"),
    )
    assertEquals("Avengers: Endgame" to null, skyCleanTitle("Avengers: Endgame Hindi Dubbed"))
    assertEquals("Never Dance with the Devil" to null, skyCleanTitle("Never Dance with the Devil Unofficial Hindi Dubbed"))
    assertEquals("Daayra" to 2026, skyCleanTitle("Daayra (2026) V1 HDTC [Hindi (Clean)] 1080p"))
    // A title that is a year, or holds one, is left whole.
    assertEquals("1917" to 2019, skyCleanTitle("1917 (2019)"))
    assertEquals("Blade Runner 2049" to null, skyCleanTitle("Blade Runner 2049"))
    // Clean titles pass through untouched, including ones that merely contain a language word.
    assertEquals("Hindi Medium" to null, skyCleanTitle("Hindi Medium"))
    assertEquals("Naruto: Shippuden" to null, skyCleanTitle("Naruto: Shippuden"))
  }

  // ── Settings ────────────────────────────────────────────────────────────────────────────────

  @Test fun `reads toggles whether sources declare them as booleans or as text`() {
    assertTrue(settingIsOn(true))
    assertTrue(settingIsOn("true"))
    assertTrue(settingIsOn("1"))
    assertTrue(!settingIsOn("false", fallback = true))
    // SkyStream stores every value as text, so a saved toggle must read back as what was saved.
    assertTrue(!settingIsOn("off", fallback = true))
    assertTrue(settingIsOn(null, fallback = true))
    assertTrue(settingIsOn("unreadable", fallback = true))
  }

  @Test fun `parses the settings shapes skystream accepts`() {
    val fields = parseSkySettingsSchema(
      """
      {"settings":[
        {"key":"external_subs","title":"Enable External Subs","type":"toggle","defaultValue":"true"},
        {"key":"base_url","title":"Site","type":"text","defaultValue":"https://a.test"},
        {"key":"mirror","name":"Mirror","type":"url","isBaseUrl":true},
        {"key":"langs","title":"Languages","type":"toggle_group","options":[{"value":"en","label":"English","defaultValue":true},{"value":"hi","label":"Hindi"}]}
      ]}
      """.trimIndent(),
    )
    assertEquals(listOf("toggle", SKY_ADDRESS_FIELD_TYPE, SKY_ADDRESS_FIELD_TYPE, "toggleGroup"), fields.map { it.type })
    assertEquals("true", fields[0].defaultValue)
    assertEquals("Mirror", fields[2].label)
    assertEquals(listOf(true, false), fields[3].options.map { it.defaultOn })
  }

  @Test fun `reads mirror lists written either way`() {
    val domains = skyDomains(org.json.JSONArray("""["https://anisuge.tv/",{"name":"anikoto.net","url":"https://anikoto.net"},{"name":"bad","url":"ftp://x"}]"""))
    assertEquals(listOf("https://anisuge.tv", "https://anikoto.net"), domains.map { it.url })
    assertEquals("anikoto.net", domains[1].name)
  }

  @Test fun `keeps host setting keys apart from a script's own`() {
    assertTrue(SkyStreamPluginManager.isHostSettingKey(SkyStreamPluginManager.ADDRESS_KEY))
    assertTrue(SkyStreamPluginManager.isHostSettingKey("_provider_enabled_hotstar"))
    assertTrue(!SkyStreamPluginManager.isHostSettingKey("debrid_api_key"))
  }

  @Test fun `finds a height in free text quality labels`() {
    assertEquals(1080, skyQualityOf("HDHub 1080p [MKV]"))
    assertEquals(2160, skyQualityOf("4K HDR"))
    assertEquals(720, skyQualityOf("YTS 720p (1.1 GB)"))
    assertEquals(0, skyQualityOf("Auto"))
  }

  @Test fun `decodes the base64 skystream wraps proxy urls in`() {
    val url = "https://cdn.test/a.m3u8?t=1"
    val encoded = java.util.Base64.getEncoder().encodeToString(url.toByteArray())
    assertEquals(url, String(skyBase64(encoded)))
    // URL-safe and unpadded forms both turn up.
    assertEquals(url, String(skyBase64(encoded.trimEnd('=').replace('+', '-').replace('/', '_'))))
  }

  @Test fun `extracts json paths the way nativeJsonExtract reads them`() {
    val root = org.json.JSONObject("""{"a":{"b":[{"c":1},{"c":2}]},"list":[{"x":"y"}]}""")
    assertEquals(2, SkyStreamRuntime.extractJsonPath(root, "a.b[1].c"))
    assertEquals("[1,2]", SkyStreamRuntime.extractJsonPath(root, "a.b[*].c").toString())
    assertEquals("y", SkyStreamRuntime.extractJsonPath(root, "list[0].x"))
    assertNull(SkyStreamRuntime.extractJsonPath(root, "a.missing.c"))
  }
}
