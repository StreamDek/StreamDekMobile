package net.streamdek.mobile.nativeapp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStreamRepoManifestTest {
  private fun manifest(json: String) = JSONObject(json)

  private fun collect(
    rootUrl: String,
    root: String,
    children: Map<String, String> = emptyMap(),
    maxDepth: Int = 3,
  ): List<String> {
    val fetched = mutableListOf<String>()
    val urls = collectRepoPluginListUrls(rootUrl, manifest(root), maxDepth) { url ->
      fetched += url
      children[url]?.let(::manifest)
    }
    return urls
  }

  @Test fun `reads a plain collection that lists plugin lists directly`() {
    val urls = collect(
      "https://repo.test/CNC.json",
      """{"name":"CNC","pluginLists":["https://repo.test/dist/plugins.json"]}""",
    )
    assertEquals(listOf("https://repo.test/dist/plugins.json"), urls)
  }

  @Test fun `follows an aggregate repo down to its collections plugin lists`() {
    // The shape sky-universe's mega_repo.json uses: a manifest with `repos` and no `pluginLists`,
    // where each child is an ordinary collection.
    val urls = collect(
      rootUrl = "https://mega.test/mega_repo.json",
      root = """
        {
          "name":"SkyStream Universe Repository",
          "id":"sky.universe",
          "manifestVersion":1,
          "repos":["https://a.test/repo.json","https://b.test/repo.json"]
        }
      """.trimIndent(),
      children = mapOf(
        "https://a.test/repo.json" to """{"name":"A","pluginLists":["https://a.test/dist/plugins.json"]}""",
        "https://b.test/repo.json" to """{"name":"B","pluginLists":["https://b.test/dist/plugins.json"]}""",
      ),
    )
    assertEquals(
      listOf("https://a.test/dist/plugins.json", "https://b.test/dist/plugins.json"),
      urls,
    )
  }

  @Test fun `skips a child collection that cannot be read`() {
    val urls = collect(
      rootUrl = "https://mega.test/mega_repo.json",
      root = """{"name":"Mega","repos":["https://down.test/repo.json","https://up.test/repo.json"]}""",
      children = mapOf(
        "https://up.test/repo.json" to """{"name":"Up","pluginLists":["https://up.test/plugins.json"]}""",
      ),
    )
    assertEquals(listOf("https://up.test/plugins.json"), urls)
  }

  @Test fun `honours both keys when a manifest carries plugin lists and child repos`() {
    val urls = collect(
      rootUrl = "https://mixed.test/repo.json",
      root = """{"name":"Mixed","pluginLists":["https://mixed.test/own.json"],"repos":["https://c.test/repo.json"]}""",
      children = mapOf(
        "https://c.test/repo.json" to """{"name":"C","pluginLists":["https://c.test/plugins.json"]}""",
      ),
    )
    assertEquals(listOf("https://mixed.test/own.json", "https://c.test/plugins.json"), urls)
  }

  @Test fun `accepts repo entries given as objects rather than plain strings`() {
    val urls = collect(
      rootUrl = "https://mega.test/repo.json",
      root = """{"name":"Mega","repos":[{"url":"https://d.test/repo.json"}]}""",
      children = mapOf(
        "https://d.test/repo.json" to """{"name":"D","pluginLists":[{"url":"https://d.test/plugins.json"}]}""",
      ),
    )
    assertEquals(listOf("https://d.test/plugins.json"), urls)
  }

  @Test fun `does not revisit a repo that appears more than once or points back at itself`() {
    val fetched = mutableListOf<String>()
    val children = mapOf(
      "https://loop.test/repo.json" to
        """{"name":"Loop","repos":["https://mega.test/repo.json","https://loop.test/repo.json"],"pluginLists":["https://loop.test/plugins.json"]}""",
    )
    val urls = collectRepoPluginListUrls(
      "https://mega.test/repo.json",
      manifest("""{"name":"Mega","repos":["https://loop.test/repo.json","https://loop.test/repo.json"]}"""),
    ) { url ->
      fetched += url
      children[url]?.let(::manifest)
    }

    assertEquals(listOf("https://loop.test/plugins.json"), urls)
    assertEquals(listOf("https://loop.test/repo.json"), fetched)
  }

  @Test fun `stops descending once the nesting cap is reached`() {
    val children = mapOf(
      "https://l1.test/repo.json" to """{"name":"L1","repos":["https://l2.test/repo.json"]}""",
      "https://l2.test/repo.json" to """{"name":"L2","repos":["https://l3.test/repo.json"]}""",
      "https://l3.test/repo.json" to """{"name":"L3","pluginLists":["https://l3.test/plugins.json"]}""",
    )
    val reached = collect(
      rootUrl = "https://root.test/repo.json",
      root = """{"name":"Root","repos":["https://l1.test/repo.json"]}""",
      children = children,
      maxDepth = 3,
    )
    assertTrue(reached.contains("https://l3.test/plugins.json"))

    val capped = collect(
      rootUrl = "https://root.test/repo.json",
      root = """{"name":"Root","repos":["https://l1.test/repo.json"]}""",
      children = children,
      maxDepth = 2,
    )
    assertTrue(capped.isEmpty())
  }

  @Test fun `returns nothing for a manifest with neither key`() {
    assertEquals(emptyList<String>(), collect("https://repo.test/x.json", """{"name":"Empty"}"""))
  }
}
