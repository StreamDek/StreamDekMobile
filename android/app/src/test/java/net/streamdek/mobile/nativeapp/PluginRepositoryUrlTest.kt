package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRepositoryUrlTest {
  @Test
  fun `keeps explicitly supplied repository filenames`() {
    assertEquals(
      listOf("https://plugins.test/catalog/plugins.json"),
      pluginRepositoryUrlCandidates("https://plugins.test/catalog/plugins.json"),
    )
    assertEquals(
      listOf("https://plugins.test/catalog/repository.txt"),
      pluginRepositoryUrlCandidates("https://plugins.test/catalog/repository.txt"),
    )
  }

  @Test
  fun `tries extensionless endpoint before manifest fallback`() {
    assertEquals(
      listOf(
        "https://plugins.test/catalog/repository",
        "https://plugins.test/catalog/repository/manifest.json",
      ),
      pluginRepositoryUrlCandidates("plugins.test/catalog/repository"),
    )
  }

  @Test
  fun `resolves provider scripts beside any repository filename`() {
    assertEquals(
      "https://plugins.test/catalog/providers/source.js",
      resolvePluginProviderUrl("https://plugins.test/catalog/plugins.json", "providers/source.js"),
    )
    assertEquals(
      "https://plugins.test/catalog/source.js?token=abc",
      resolvePluginProviderUrl("https://plugins.test/catalog/repository.data?token=abc", "source.js"),
    )
    assertEquals(
      "https://cdn.test/source.js",
      resolvePluginProviderUrl("https://plugins.test/catalog/plugins.json", "https://cdn.test/source.js"),
    )
  }

  @Test
  fun `normalizes common esm provider exports without copying provider logic`() {
    val normalized = normalizePluginJavaScript(
      """
        import cheerio from "cheerio-without-node-native";
        import { helper as renamed } from "helpers";
        async function getStreams() { return cheerio && renamed ? [] : []; }
        export { getStreams };
      """.trimIndent(),
    )

    assertTrue(normalized.contains("const cheerio = require(\"cheerio-without-node-native\");"))
    assertTrue(normalized.contains("const {helper: renamed} = require(\"helpers\");"))
    assertTrue(normalized.contains("module.exports = {getStreams};"))
    assertFalse(normalized.contains("import "))
  }

  @Test
  fun `turns javascript stacks into a user facing source error`() {
    val message = humanReadablePluginError(
      IllegalStateException("SyntaxError: Unexpected identifier 'cheerio'\n    at provider.js:58:2448"),
    )

    assertEquals("This source uses JavaScript syntax that could not be loaded. Refresh the collection and try again.", message)
    assertFalse(message.contains("provider.js"))
  }
}