package net.streamdek.mobile.nativeapp

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.scheduler.Scheduler
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import net.streamdek.mobile.R
import org.json.JSONObject

private const val TAG = "StreamDekDownloads"

enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, REMOVING }

/**
 * Which title a download is actually of.
 *
 * A download is keyed by its stream URL, which says nothing about the media. Without this, a
 * downloaded title played back had no id, artwork or episode numbers to record against, so its
 * Continue Watching entry was keyed on the download hash: no poster, and tapping it resolved that
 * hash as a catalogue id and opened whatever it happened to match.
 */
data class DownloadMedia(
  val mediaId: String,
  val mediaType: String,
  val title: String,
  val year: String? = null,
  val poster: String? = null,
  val backdrop: String? = null,
  val titleLogo: String? = null,
  val seasonNumber: Int? = null,
  val episodeNumber: Int? = null,
  val episodeTitle: String? = null,
  val runtimeMinutes: Int? = null,
) {
  /** True once the download carries a real catalogue id, rather than a legacy title-only record. */
  val isResolvable: Boolean get() = mediaId.isNotBlank()
}

/** Plain, non-experimental view of a Media3 [Download] - keeps `@UnstableApi` Media3 offline
 * types out of [AppUiState] and the rest of the app so the opt-in doesn't have to spread
 * through every place that touches ui state. */
data class DownloadEntry(
  val id: String,
  val title: String,
  val url: String,
  val state: DownloadState,
  val percentDownloaded: Float,
  val startTimeMs: Long,
  val media: DownloadMedia,
)

/**
 * Opt-in offline downloads, built on Media3's own [DownloadManager]/[DownloadService] rather
 * than a hand-rolled downloader - it already handles progressive and HLS VOD downloads,
 * progress, pause/resume, and persistence. Scope: streams playable through the Media3/
 * ExoPlayer engine only (a plain HTTP(S) `url`, not `infoHash` torrent streams) and never
 * live channels, since there's no single finite file to save for those.
 */
@OptIn(UnstableApi::class)
object StreamDekDownloads {
  private const val DOWNLOAD_DIRECTORY = "downloads"
  private const val USER_AGENT = "StreamDek/1.0"

  @Volatile private var manager: DownloadManager? = null
  @Volatile private var cache: Cache? = null
  private lateinit var appContext: Context

  @Synchronized
  fun initialize(context: Context) {
    if (manager != null) return
    appContext = context.applicationContext
    val databaseProvider = ExoDatabaseProvider(appContext)
    val downloadDirectory = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, DOWNLOAD_DIRECTORY)
    val downloadCache = SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)
    cache = downloadCache
    val httpDataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT).setAllowCrossProtocolRedirects(true)
    val cacheDataSourceFactory = CacheDataSource.Factory().setCache(downloadCache).setUpstreamDataSourceFactory(httpDataSourceFactory)
    val executor = Executor(Runnable::run)
    // Built from an explicit index and downloader factory rather than the convenience constructor
    // purely so the factory can be wrapped - see [ResilientDownloaderFactory] for why.
    manager = DownloadManager(
      appContext,
      DefaultDownloadIndex(databaseProvider),
      ResilientDownloaderFactory(DefaultDownloaderFactory(cacheDataSourceFactory, executor)),
    ).apply {
      maxParallelDownloads = 2
    }
  }

  internal fun manager(): DownloadManager = manager ?: error("StreamDekDownloads.initialize() was not called")

  private fun currentDownloads(): List<Download> = runCatching {
    manager?.downloadIndex?.getDownloads()?.use { cursor ->
      buildList { while (cursor.moveToNext()) add(cursor.download) }
    }
  }.getOrNull().orEmpty()

  fun currentDownloadEntries(): List<DownloadEntry> = currentDownloads().map { it.toEntry() }

  fun startDownload(id: String, url: String, media: DownloadMedia, mimeType: String?) {
    val request = DownloadRequest.Builder(id, android.net.Uri.parse(url))
      .apply { if (!mimeType.isNullOrBlank()) setMimeType(mimeType) }
      .setData(encodeMedia(media).toByteArray(Charsets.UTF_8))
      .build()
    DownloadService.sendAddDownload(appContext, StreamDekDownloadService::class.java, request, false)
  }

  fun removeDownload(id: String) {
    DownloadService.sendRemoveDownload(appContext, StreamDekDownloadService::class.java, id, false)
  }

  /**
   * The media record is stashed in [DownloadRequest.data] at download time - Media3's request
   * model has no fields of its own for any of this, and `data` is exactly what it exposes for it.
   */
  private fun encodeMedia(media: DownloadMedia): String = JSONObject()
    .put("v", MEDIA_DATA_VERSION)
    .put("mediaId", media.mediaId)
    .put("mediaType", media.mediaType)
    .put("title", media.title)
    .put("year", media.year)
    .put("poster", media.poster)
    .put("backdrop", media.backdrop)
    .put("titleLogo", media.titleLogo)
    .put("seasonNumber", media.seasonNumber)
    .put("episodeNumber", media.episodeNumber)
    .put("episodeTitle", media.episodeTitle)
    .put("runtimeMinutes", media.runtimeMinutes)
    .toString()

  private fun decodeMedia(download: Download): DownloadMedia = parseDownloadMedia(
    raw = download.request.data.takeIf { it.isNotEmpty() }?.let { String(it, Charsets.UTF_8) },
    fallbackTitle = download.request.uri.lastPathSegment.orEmpty().ifBlank { "Download" },
  )

  /** Wraps [upstream] with the shared download cache in read-only mode: a URL that was
   * previously downloaded plays back from disk with no network needed, but ordinary streaming
   * of anything else is never written into this cache (it uses [NoOpCacheEvictor], which never
   * evicts - writing ad-hoc playback into it would grow it unbounded). Falls back to [upstream]
   * unchanged if downloads haven't been initialized yet. */
  fun wrapWithDownloadCache(upstream: androidx.media3.datasource.DataSource.Factory): androidx.media3.datasource.DataSource.Factory {
    val downloadCache = cache ?: return upstream
    return CacheDataSource.Factory()
      .setCache(downloadCache)
      .setUpstreamDataSourceFactory(upstream)
      .setCacheWriteDataSinkFactory(null)
      .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
  }

  private fun Download.toEntry(): DownloadEntry {
    val media = decodeMedia(this)
    return DownloadEntry(
      id = request.id,
      title = media.title,
      url = request.uri.toString(),
      state = when (state) {
        Download.STATE_COMPLETED -> DownloadState.COMPLETED
        Download.STATE_FAILED -> DownloadState.FAILED
        Download.STATE_REMOVING -> DownloadState.REMOVING
        Download.STATE_STOPPED -> DownloadState.PAUSED
        Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadState.QUEUED
        else -> DownloadState.DOWNLOADING
      },
      percentDownloaded = percentDownloaded,
      startTimeMs = startTimeMs,
      media = media,
    )
  }

  private const val MEDIA_DATA_VERSION = 1
}

/**
 * Wraps Media3's downloader factory so a request it cannot build a downloader for fails that one
 * download instead of taking the process down.
 *
 * [DownloadManager] resolves the downloader for a request on its own background thread and does
 * not guard that call, so anything thrown there is an uncaught exception on a bare
 * [android.os.HandlerThread] - a hard process kill. Because the manager re-resolves every download
 * still in the index at startup, a single unbuildable request meant the app died before it could
 * draw, on every launch, with no way in from the UI to delete the download that caused it.
 *
 * The one that happened was a DASH stream queued while the DASH module was missing from the build
 * (now added), but any future gap - an unknown content type, a stripped module, a request written
 * by a newer version - lands in exactly the same place. Failing the download keeps it visible and
 * removable in the Downloads list, which is the state the user can act on.
 */
@OptIn(UnstableApi::class)
private class ResilientDownloaderFactory(private val delegate: DownloaderFactory) : DownloaderFactory {
  override fun createDownloader(request: DownloadRequest): Downloader =
    runCatching { delegate.createDownloader(request) }.getOrElse { error ->
      Log.e(TAG, "No downloader for ${request.uri} (mimeType=${request.mimeType}); marking it failed", error)
      UnsupportedDownloader(error)
    }
}

/** Reports [cause] as an ordinary download failure, which [DownloadManager] handles by moving the
 * download to [Download.STATE_FAILED]. [remove] succeeds so the user can still delete it. */
@OptIn(UnstableApi::class)
private class UnsupportedDownloader(private val cause: Throwable) : Downloader {
  override fun download(progressListener: Downloader.ProgressListener?): Nothing =
    throw IOException("This source cannot be downloaded on this build.", cause)

  override fun cancel() = Unit

  override fun remove() = Unit
}

/**
 * Reads the media record out of a download's stored `data`.
 *
 * Downloads saved before that record existed hold a bare title string there. Those still list and
 * play; they just have no catalogue id, which [DownloadMedia.isResolvable] reports so callers
 * degrade to playing the file instead of looking the id up and opening an unrelated title.
 */
internal fun parseDownloadMedia(raw: String?, fallbackTitle: String): DownloadMedia {
  if (raw.isNullOrBlank()) return DownloadMedia(mediaId = "", mediaType = "movie", title = fallbackTitle)

  val json = runCatching { JSONObject(raw) }.getOrNull()
    ?: return DownloadMedia(mediaId = "", mediaType = "movie", title = raw)

  fun string(key: String): String? =
    if (json.has(key) && !json.isNull(key)) json.optString(key).takeIf { it.isNotBlank() } else null
  fun int(key: String): Int? = if (json.has(key) && !json.isNull(key)) json.optInt(key) else null

  return DownloadMedia(
    mediaId = string("mediaId").orEmpty(),
    mediaType = string("mediaType") ?: "movie",
    title = string("title") ?: fallbackTitle,
    year = string("year"),
    poster = string("poster"),
    backdrop = string("backdrop"),
    titleLogo = string("titleLogo"),
    seasonNumber = int("seasonNumber"),
    episodeNumber = int("episodeNumber"),
    episodeTitle = string("episodeTitle"),
    runtimeMinutes = int("runtimeMinutes"),
  )
}

@OptIn(UnstableApi::class)
class StreamDekDownloadService : DownloadService(
  NOTIFICATION_ID,
  DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
  CHANNEL_ID,
  R.string.download_channel_name,
  R.string.download_channel_description,
) {
  private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

  override fun getDownloadManager(): DownloadManager {
    StreamDekDownloads.initialize(applicationContext)
    return StreamDekDownloads.manager()
  }

  override fun getScheduler(): Scheduler? = null

  override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
    notificationHelper.buildProgressNotification(this, R.mipmap.ic_launcher, null, null, downloads, notMetRequirements)

  companion object {
    private const val NOTIFICATION_ID = 21001
    private const val CHANNEL_ID = "streamdek_downloads"
  }
}
