package net.streamdek.mobile.nativeapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives real SkyStream plugins through the real QuickJS runtime and the same CloudStream bridge
 * calls the app makes: Home rows, Fuse search, a detail page, its streams, and the title search
 * behind a catalogue title's sources. Network-bound, so it reports per plugin rather than failing on
 * a site being down; the run fails only when nothing works or the runtime itself breaks.
 *
 *   adb shell am instrument -w -e suite skystream -e plugins a,b,c
 *     net.streamdek.mobile.test/net.streamdek.mobile.nativeapp.NextUpDeviceInstrumentation
 *
 * Runs under a throwaway profile key, so no viewer's collections are touched.
 */
class SkyStreamDeviceChecks(private val targetContext: Context, private val args: Bundle) {
  /** Number of plugins that produced streams from their own catalogue. */
  fun runAll(report: StringBuilder): Int = runBlocking { run(report) }

  private suspend fun run(report: StringBuilder): Int {
    // A query of its own makes this a different collection URL from any a real profile added, so the
    // bundles it downloads get file names of their own (they are named by package and collection):
    // removing them at the end can never delete a file a viewer's profile is using.
    val repo = (args.getString("repo") ?: "https://raw.githubusercontent.com/akashdh11/sky-universe/refs/heads/main/mega_repo.json") +
      "?streamdek-device-test"
    val wanted = (args.getString("plugins") ?: "dev.akash.stars.yts").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val prefs = targetContext.getSharedPreferences("streamdek_sky_plugins", 0)
    val keysBefore = prefs.all.keys.toSet()
    val manager = SkyStreamPluginManager(targetContext)
    val owner = "sky-instrumentation-${System.nanoTime()}"
    manager.selectProfileStorage(owner)
    var passed = 0
    try {
      manager.addRepo(repo).getOrThrow()
      report.append("Collection: ${manager.state.providers.size} sources listed\n")
      for (pkg in wanted) {
        val entry = manager.state.providers.firstOrNull { it.packageName == pkg }
        if (entry == null) { report.append("\n[$pkg] not in collection\n"); continue }
        manager.setProviderEnabled(entry.repoUrl, pkg, true).getOrThrow()
      }
      // Sub-providers and Home rows are learnt by the background probe; wait for it to settle.
      for (second in 0 until 120) {
        val sources = manager.activeSources()
        if (sources.isNotEmpty() && sources.all { isProbed(manager, it) }) break
        kotlinx.coroutines.delay(1000)
      }
      val providers = manager.mainApis().filterIsInstance<SkyStreamMainApi>()
      report.append("Providers: ${providers.joinToString { it.name }}\n")
      for (provider in providers.distinctBy { it.source.packageName }) {
        if (check(provider, report)) passed++
      }
      if (args.getString("sync") != "false") syncRoundTrip(manager, "$owner-other", report)
      return passed
    } finally {
      manager.state.repos.forEach { manager.removeRepo(it.url) }
      // Everything the run wrote — its profile, and the rows and sub-providers it learnt.
      prefs.edit().apply { (prefs.all.keys - keysBefore).forEach { remove(it) } }.commit()
    }
  }

  /**
   * What another device does with this profile's synced section: take the snapshot this manager
   * would push into a second profile and check the collections, switches and settings arrive — and
   * that the settings fields the portal draws from were published with it.
   */
  private fun syncRoundTrip(manager: SkyStreamPluginManager, otherOwner: String, report: StringBuilder) {
    val torrentio = manager.state.providers.firstOrNull { it.packageName == "com.arranoust.torrentio" && it.enabled }
    torrentio?.let { manager.saveProviderSettings(it.packageName, manager.providerSettings(it.packageName) + ("sort_by" to "size")) }
    val snapshot = org.json.JSONObject(manager.snapshotJson())
    val published = snapshot.getJSONArray("providers").let { list -> (0 until list.length()).map { list.getJSONObject(it) } }
    val withSchema = published.filter { it.has("settingsSchema") }.map { it.optString("packageName") }
    val withSubs = published.filter { it.has("subProviders") }.map { it.optString("packageName") to it.getJSONArray("subProviders").length() }
    report.append("\n[sync] snapshot: ${published.count { it.optBoolean("enabled") }} enabled, schemas for $withSchema, sub-providers $withSubs")
    check(published.none { it.has("installedFilePath") }) { "device paths leaked into the synced section" }

    val other = SkyStreamPluginManager(targetContext)
    other.selectProfileStorage(otherOwner)
    try {
      check(other.restoreCloudState(snapshot.toString())) { "restore reported nothing changed" }
      val wanted = manager.state.providers.filter { it.enabled }.map { it.packageName }.toSet()
      val got = other.state.providers.filter { it.enabled }.map { it.packageName }.toSet()
      check(wanted == got) { "switched-on sources differ after restore: $wanted vs $got" }
      check(other.state.repos.map { it.url } == manager.state.repos.map { it.url }) { "collections differ after restore" }
      torrentio?.let { check(other.providerSettings(it.packageName)["sort_by"] == "size") { "Torrentio setting did not arrive" } }
      check(other.state.updatedAt == manager.state.updatedAt) { "restored stamp differs" }
      check(!other.restoreCloudState(snapshot.toString())) { "restoring the same section twice changed something" }
      report.append("\n[sync] restored on a second profile: ${got.size} sources on, settings ${if (torrentio != null) "carried" else "n/a"}; re-apply is a no-op\n")
    } finally {
      other.state.repos.forEach { other.removeRepo(it.url) }
    }
  }

  private fun isProbed(manager: SkyStreamPluginManager, source: SkySource): Boolean =
    targetContext.getSharedPreferences("streamdek_sky_plugins", 0).contains("sections:${source.key}")

  /** One provider end to end. True when it produced a playable stream from its own catalogue. */
  private suspend fun check(provider: SkyStreamMainApi, report: StringBuilder): Boolean {
    val line = StringBuilder("\n[${provider.name}] types=${provider.supportedTypes}")
    var ok = false
    try {
      val rows = CloudStreamProviderBridge.mainPageRows(provider)
      line.append("\n  rows: ${rows.size} ${rows.take(4).map { it.page.name }}")
      val rowItems = rows.firstOrNull()?.let { row ->
        runCatching { CloudStreamProviderBridge.mainPageItems(provider, row.page) }.getOrElse { line.append(" (row failed: ${it.message})"); emptyList() }
      }.orEmpty()
      line.append("\n  first row: ${rowItems.size} item(s) ${rowItems.take(2).map { it.title }}")
      val query = when {
        com.lagradost.cloudstream3.TvType.Live in provider.supportedTypes -> "sports"
        com.lagradost.cloudstream3.TvType.Anime in provider.supportedTypes -> "naruto"
        else -> "avengers"
      }
      val found = runCatching { CloudStreamProviderBridge.hubSearch(provider, query) }.getOrElse { line.append("\n  search failed: ${it.message}"); emptyList() }
      line.append("\n  search '$query': ${found.size} ${found.take(3).map { "${it.title} (${it.type})" }}")
      val item = found.firstOrNull() ?: rowItems.firstOrNull()
      if (item != null) {
        val url = decodeCloudStreamMediaId(item.id)!!.second
        val detail = withTimeoutOrNull(60_000) { CloudStreamProviderBridge.loadItem(provider, url) }
        if (detail == null) {
          line.append("\n  load: nothing")
        } else {
          val meta = CloudStreamProviderBridge.toLocalMeta(item.id, detail) { "Episode $it" }
          line.append("\n  load: '${detail.name}' ${detail.javaClass.simpleName} episodes=${meta.episodes.size} live=${CloudStreamProviderBridge.isLive(provider, detail)}")
          val first = meta.episodes.firstOrNull()
          val streams = withTimeoutOrNull(90_000) {
            CloudStreamProviderBridge.originStreams(provider, url, first?.seasonNumber, first?.episodeNumber)
          }.orEmpty()
          line.append("\n  streams: ${streams.size}")
          streams.take(3).forEach { s ->
            line.append("\n    - ${s.name} q=${s.quality} ${(s.url ?: "magnet:" + s.infoHash).take(90)} headers=${s.requestHeaders.keys} drm=${s.drmClearKeys.isNotEmpty()}")
          }
          ok = streams.isNotEmpty()
        }
      }
      // The path a catalogue (TMDB) title takes: search by name, exact title match, then links.
      if (com.lagradost.cloudstream3.TvType.Movie in provider.supportedTypes) {
        val walk = withTimeoutOrNull(120_000) {
          CloudStreamProviderBridge.streams(listOf(provider), CloudStreamProviderBridge.StreamRequest("Avengers: Endgame", 2019, "movie"))
        }.orEmpty()
        line.append("\n  title walk 'Avengers: Endgame': ${walk.size} stream(s)")
      }
    } catch (error: Throwable) {
      line.append("\n  ERROR ${error.javaClass.simpleName}: ${error.message}")
      Log.w("SkyStreamDeviceTest", provider.name, error)
    }
    Log.i("SkyStreamDeviceTest", line.toString())
    report.append(line).append('\n')
    return ok
  }
}
