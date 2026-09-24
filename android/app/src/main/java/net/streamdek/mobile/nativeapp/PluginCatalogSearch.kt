package net.streamdek.mobile.nativeapp

import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Searching the titles and channels that plugins list, for Search and for StreamDek Fuse alike.
 *
 * The same model the television uses (its `PluginCatalogSearch`), so a query finds the same things on
 * both. Plugins reach StreamDek as CloudStream providers - `.cs3` plugins directly, SkyStream sources
 * through [SkyStreamMainApi] - so one path covers both. Regular StreamDek plugins resolve streams for
 * titles found elsewhere and list nothing of their own, so they have nothing here to search.
 *
 * Every provider is asked through its own search first. Whether it has one is learnt, not assumed:
 * a provider that never implemented `MainAPI.search` throws [NotImplementedError], which is
 * remembered for the session. Such a provider - typically a live-channel plugin whose catalogue is a
 * few rows - is still searched against the rows already loaded from it for Home and the Fuse
 * ([index]), at no cost: nothing is crawled to build that index.
 *
 * Providers answer independently: [searchAll] reports each as it lands, a slow one is dropped from
 * the query after [PROVIDER_TIMEOUT_MS], and a failing one never takes the others with it.
 */
internal object PluginCatalogSearch {
  private const val TAG = "StreamDekPluginSearch"

  /** How long one provider may take to answer a query before it is left out of it. */
  const val PROVIDER_TIMEOUT_MS = 15_000L
  private const val CONCURRENCY = 4
  private const val CACHE_TTL_MS = 5 * 60_000L
  private const val CACHE_LIMIT = 160
  private const val INDEX_LIMIT_PER_PROVIDER = 2_000

  enum class Capability { Native, CatalogueOnly }

  data class ProviderOutcome(
    val providerName: String,
    val items: List<MediaItem>,
    val capability: Capability,
    val failed: Boolean = false,
  )

  private data class CachedOutcome(val at: Long, val outcome: ProviderOutcome)

  private val capabilities = ConcurrentHashMap<String, Capability>()
  private val index = ConcurrentHashMap<String, List<MediaItem>>()
  private val cache = ConcurrentHashMap<String, CachedOutcome>()

  fun capability(providerName: String): Capability? = capabilities[providerName]

  /** Keeps what a provider's row just returned, so a provider without search can still be searched. */
  fun index(providerName: String, items: List<MediaItem>) {
    if (items.isEmpty()) return
    index[providerName] = (items + index[providerName].orEmpty()).distinctBy { it.id }.take(INDEX_LIMIT_PER_PROVIDER)
  }

  fun clearCache() = cache.clear()

  /** One provider's matches for [query]: its own search when it has one, its indexed rows either way. */
  suspend fun searchProvider(provider: MainAPI, query: String, forceRefresh: Boolean = false): ProviderOutcome {
        if (AdultContentFilter.isBlocked(provider.name, provider.mainUrl, provider.javaClass.name)) return ProviderOutcome(provider.name, emptyList(), Capability.CatalogueOnly)
    val needle = query.trim()
    val key = provider.name + "" + needle.lowercase(Locale.US)
    val now = System.currentTimeMillis()
    if (!forceRefresh) cache[key]?.takeIf { now - it.at < CACHE_TTL_MS }?.let { return it.outcome.copy(items = it.outcome.items.filterNot { item -> AdultContentFilter.isBlockedItem(title = item.title) || AdultContentFilter.isBlocked(item.id) }) }
    val indexed = matchIndexed(provider.name, needle)
    var failed = false
    val native = if (capabilities[provider.name] == Capability.CatalogueOnly) {
      emptyList()
    } else {
      try {
        withTimeout(PROVIDER_TIMEOUT_MS) { nativeSearch(provider, needle) }
          .also { capabilities[provider.name] = Capability.Native }
      } catch (timeout: TimeoutCancellationException) {
        Log.w(TAG, "${provider.name} timed out for '$needle'")
        failed = true
        emptyList()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: NotImplementedError) {
        capabilities[provider.name] = Capability.CatalogueOnly
        Log.i(TAG, "${provider.name} has no search; using its loaded rows")
        emptyList()
      } catch (failure: Throwable) {
        // Plugins throw linkage errors as well as exceptions.
        Log.w(TAG, "${provider.name} search failed: ${failure.javaClass.simpleName}: ${failure.message}")
        failed = true
        emptyList()
      }
    }
    val outcome = ProviderOutcome(
      providerName = provider.name,
      items = (relevant(native, needle) + indexed).distinctBy { it.id }.filterNot {
        AdultContentFilter.isBlockedItem(title = it.title, genres = it.genres)
      },
      capability = capabilities[provider.name] ?: Capability.Native,
      failed = failed,
    )
    if (!failed) {
      if (cache.size >= CACHE_LIMIT) cache.entries.sortedBy { it.value.at }.take(CACHE_LIMIT / 4).forEach { cache.remove(it.key) }
      cache[key] = CachedOutcome(now, outcome)
    }
    return outcome
  }

  /** Every provider's matches, reported as each answers; each emission is the whole set so far. */
  fun searchAll(providers: List<MainAPI>, query: String, forceRefresh: Boolean = false): Flow<List<ProviderOutcome>> = channelFlow {
    val needle = query.trim()
    if (needle.length < 2 || providers.isEmpty()) {
      send(emptyList())
      return@channelFlow
    }
    val gate = Semaphore(CONCURRENCY)
    val arrived = Channel<ProviderOutcome>(Channel.UNLIMITED)
    val collected = mutableListOf<ProviderOutcome>()
    launch {
      supervisorScope {
        providers.distinctBy { it.name }.map { provider ->
          async { gate.withPermit { arrived.send(searchProvider(provider, needle, forceRefresh)) } }
        }.awaitAll()
      }
      arrived.close()
    }
    for (outcome in arrived) {
      collected += outcome
      // In the order the providers are listed, so a result never moves when a slower one lands.
      val order = providers.map { it.name }
      send(collected.sortedBy { order.indexOf(it.providerName).let { index -> if (index < 0) Int.MAX_VALUE else index } })
    }
  }

  /** The paged overload first: it works for a provider that implemented either form of search. */
  private suspend fun nativeSearch(provider: MainAPI, query: String): List<MediaItem> = withContext(Dispatchers.IO) {
    val paged: List<SearchResponse>? = try {
      provider.search(query, 1)?.items
    } catch (_: NotImplementedError) {
      null
    }
    val results = paged?.takeIf { it.isNotEmpty() } ?: provider.search(query).orEmpty()
    val liveSource = CloudStreamProviderBridge.isLiveSource(provider.name)
    results.distinctBy { it.url }.map { result -> asSearchItem(provider.name, CloudStreamProviderBridge.toMediaItem(provider, result), liveSource || result.type == TvType.Live) }
  }

  /**
   * A result as Search shows it: named by the provider it came from, and - for a channel - carrying
   * the same live marking a Fuse row gives it, so it opens as a channel rather than as a film.
   */
  private fun asSearchItem(providerName: String, item: MediaItem, live: Boolean): MediaItem = item.copy(
    cardSubtitle = item.cardSubtitle ?: providerName,
    sourceAddonName = if (live) item.sourceAddonName ?: providerName else item.sourceAddonName,
    sourceCatalogType = if (live) "live" else item.sourceCatalogType,
  )

  private fun matchIndexed(providerName: String, needle: String): List<MediaItem> {
    val liveSource = CloudStreamProviderBridge.isLiveSource(providerName)
    return index[providerName].orEmpty()
      .mapNotNull { item -> matchRank(item.title, needle)?.let { it to item } }
      .sortedBy { it.first }
      .map { (_, item) -> asSearchItem(providerName, item, liveSource || item.sourceCatalogType == "live") }
  }

  /** When at least one result matches by title only matches are kept; otherwise the answer stands. */
  private fun relevant(items: List<MediaItem>, needle: String): List<MediaItem> {
    val ranked = items.mapNotNull { item -> matchRank(item.title, needle)?.let { it to item } }
    return if (ranked.isEmpty()) items else ranked.sortedBy { it.first }.map { it.second }
  }

  /** How well [title] answers [needle], lowest first, or null when it does not. */
  fun matchRank(title: String, needle: String): Int? {
    val haystack = fold(title)
    val query = fold(needle)
    if (query.isEmpty() || haystack.isEmpty()) return null
    return when {
      haystack == query -> 0
      haystack.startsWith(query) -> 1
      haystack.split(' ').any { it.startsWith(query) } -> 2
      haystack.contains(query) -> 3
      query.split(' ').filter { it.isNotEmpty() }.all { token -> haystack.contains(token) } -> 4
      haystack.replace(" ", "").contains(query.replace(" ", "")) -> 5
      else -> null
    }
  }

  private fun fold(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
      .replace(Regex("\\p{Mn}+"), "")
      .lowercase(Locale.US)
      .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .trim()
}

/**
 * Search's plugin results, as the app state holds them: one property rather than two, because the
 * state class is already at the size a single dex call can copy (see AppUiStateSizeTest).
 * Each result carries its provider's name in [MediaItem.cardSubtitle].
 */
@androidx.compose.runtime.Immutable
data class PluginSearchState(
  val results: List<MediaItem> = emptyList(),
  val loading: Boolean = false,
)
