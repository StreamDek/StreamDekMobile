package net.streamdek.mobile.nativeapp

import android.app.Notification
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import java.io.File
import java.util.concurrent.Executor
import net.streamdek.mobile.R

enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, REMOVING }

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
    manager = DownloadManager(appContext, databaseProvider, downloadCache, cacheDataSourceFactory, Executor(Runnable::run)).apply {
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

  fun startDownload(id: String, url: String, title: String, mimeType: String?) {
    val request = DownloadRequest.Builder(id, android.net.Uri.parse(url))
      .apply { if (!mimeType.isNullOrBlank()) setMimeType(mimeType) }
      .setData(title.toByteArray(Charsets.UTF_8))
      .build()
    DownloadService.sendAddDownload(appContext, StreamDekDownloadService::class.java, request, false)
  }

  fun removeDownload(id: String) {
    DownloadService.sendRemoveDownload(appContext, StreamDekDownloadService::class.java, id, false)
  }

  /** Title stashed in [DownloadRequest.data] at download time - Media3's request model has
   * no title field of its own, and this is exactly what it exposes `data` for. */
  private fun titleFor(download: Download): String =
    download.request.data.takeIf { it.isNotEmpty() }?.let { String(it, Charsets.UTF_8) }
      ?: download.request.uri.lastPathSegment.orEmpty().ifBlank { "Download" }

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

  private fun Download.toEntry(): DownloadEntry = DownloadEntry(
    id = request.id,
    title = titleFor(this),
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
