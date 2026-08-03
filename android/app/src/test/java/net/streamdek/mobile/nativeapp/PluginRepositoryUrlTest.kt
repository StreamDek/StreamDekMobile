package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
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
}