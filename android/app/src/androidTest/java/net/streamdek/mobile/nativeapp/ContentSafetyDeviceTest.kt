package net.streamdek.mobile.nativeapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import com.lagradost.cloudstream3.MainAPI
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Real Android parsers/storage/cache boundaries; synthetic metadata only, no explicit media. */
class ContentSafetyInstrumentation : Instrumentation() {
  private var arguments = Bundle()
  override fun onCreate(arguments: Bundle?) { this.arguments = arguments ?: Bundle(); super.onCreate(arguments); start() }
  override fun onStart() {
    val output = Bundle()
    try {
      output.putString("stream", ContentSafetyDeviceChecks(targetContext).run(arguments.getString("phase") ?: "write"))
      finish(Activity.RESULT_OK, output)
    } catch (failure: Throwable) {
      output.putString("stream", "Content safety FAILED: ${failure.stackTraceToString().take(2500)}")
      finish(Activity.RESULT_CANCELED, output)
    }
  }
}
class ContentSafetyDeviceChecks(private val context: Context) {
  fun run(phase: String): String = runBlocking {
    val fileName = "content-protection-device-test.json"
    val store = ContentPolicyStore(context, fileName)
    val report = StringBuilder()
    try {
      if (phase == "read") {
        AdultContentFilter.applyPolicy(false, emptyList())
        store.restore()
        check(AdultContentFilter.enabled && AdultContentFilter.isBlocked("devicefixtureblocked")) { "Restart lost downloaded restrictions" }
        File(context.noBackupFilesDir, fileName).delete()
        report.append("PASS persisted policy restored in a fresh instrumentation process\n")
        return@runBlocking report.toString()
      }
      AdultContentFilter.applyPolicy(true, emptyList())
      val names = listOf("pornmz", "xprimehub", "YesPornPlease", "Perverzija", "Brazzers", "PornHD", "EPorner", "Porntrex")
      for (providerName in names) {
        val provider = object : MainAPI() { override var name = providerName; override var mainUrl = "https://example.invalid" }
        check(PluginCatalogSearch.searchProvider(provider, "ordinary").items.isEmpty())
        check(AdultContentFilter.isBlocked(providerName.toList().joinToString(".")))
      }
      report.append("PASS eight provider search gates and obfuscated identities\n")
      val playlist = buildString {
        append("#EXTM3U\n#EXTINF:-1 group-title=\"News\",BBC News\nhttps://example.invalid/news\n")
        names.forEachIndexed { index, name -> append("#EXTINF:-1 tvg-id=\"$name\" group-title=\"Mixed\",Renamed $index\nhttps://example.invalid/$index\n") }
        append("#EXTINF:-1 group-title=\"Mixed\",Renamed domain\nhttps://eporner.com/live\n")
        append("#EXTINF:-1 group-title=\"Adult\",Renamed category\nhttps://example.invalid/category\n")
      }
      val permitted = parseM3uLines(playlist.lineSequence(), "safety-fixture", "Safety fixture")
      check(permitted.size == 1 && permitted.single().title == "BBC News") { "Mixed playlist retained ${permitted.map { it.title }}" }
      report.append("PASS mixed M3U: all eight EPG aliases, adult category and renamed domain removed; news retained\n")
      val provider = object : MainAPI() { override var name = "SafetyFixture"; override var mainUrl = "https://example.invalid" }
      val card = MediaItem(id = "fixture:1", type = "movie", title = "Fixture", year = null, poster = null, backdrop = null, rating = null, description = "")
      AdultContentFilter.applyPolicy(false, emptyList())
      PluginCatalogSearch.index(provider.name, listOf(card))
      check(PluginCatalogSearch.searchProvider(provider, "fixture").items.size == 1)
      AdultContentFilter.applyPolicy(true, listOf("fixture"))
      check(PluginCatalogSearch.searchProvider(provider, "fixture").items.isEmpty()) { "Cached search bypassed new policy" }
      report.append("PASS cached plugin search re-evaluated after policy enablement\n")
      check(!AdultContentFilter.isBlockedItem(title = "A documentary about pornography", genres = listOf("Documentary")))
      check(!AdultContentFilter.isBlockedItem(title = "Ordinary drama", genres = listOf("18+")))
      report.append("PASS documentary and mature-rating false-positive cases\n")
      store.accept(JSONObject().put("blockAdult", true).put("terms", org.json.JSONArray().put("devicefixtureblocked")))
      AdultContentFilter.applyPolicy(false, emptyList())
      ContentPolicyStore(context, fileName).restore()
      check(AdultContentFilter.isBlocked("devicefixtureblocked"))
      AdultContentFilter.applyPolicy(null, null)
      check(AdultContentFilter.isBlocked("devicefixtureblocked"))
      report.append("PASS atomic Android policy persistence and failed-refresh retention\n")
      report.toString()
    } finally {
      PluginCatalogSearch.clearCache()
      AdultContentFilter.applyPolicy(true, emptyList())
      ContentPolicyStore(context).restore()
    }
  }
}
