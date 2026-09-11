package net.streamdek.mobile.nativeapp

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import net.streamdek.mobile.R

/**
 * Transfer speed per download, smoothed.
 *
 * Media3 reports how many bytes a download has so far and nothing about how fast they are arriving,
 * so speed is measured here from successive readings. The Downloads page and the notification both
 * read through this one sampler, so the two never disagree about the same download.
 */
internal object DownloadRateSampler {
  private class Sample(val bytes: Long, val atMs: Long, val bytesPerSecond: Double?)

  /** Readings closer together than this say more about scheduling jitter than about the network. */
  private const val MIN_INTERVAL_MS = 750L

  /** Weight of the newest reading: low enough that a bursty CDN does not make the figure jump. */
  private const val SMOOTHING = 0.3

  private val samples = HashMap<String, Sample>()

  @Synchronized
  fun sample(id: String, bytes: Long, nowMs: Long): Double? {
    val previous = samples[id]
    if (previous == null || bytes < previous.bytes) {
      samples[id] = Sample(bytes, nowMs, null)
      return null
    }
    val elapsed = nowMs - previous.atMs
    if (elapsed < MIN_INTERVAL_MS) return previous.bytesPerSecond
    val instant = (bytes - previous.bytes) * 1000.0 / elapsed
    val smoothed = previous.bytesPerSecond?.let { it + SMOOTHING * (instant - it) } ?: instant
    samples[id] = Sample(bytes, nowMs, smoothed)
    return smoothed
  }

  /** Dropped when a download stops moving, so a resume does not average in the time it sat paused. */
  @Synchronized
  fun forget(id: String) {
    samples.remove(id)
  }
}

/**
 * The download's full size: the server's own figure when it gave one, otherwise projected from how
 * far through it is. HLS never states a length, so for most episode streams this is the projection.
 */
internal fun DownloadEntry.estimatedTotalBytes(): Long? = when {
  contentLength > 0 -> contentLength
  bytesDownloaded > 0 && percentDownloaded > 0f && percentDownloaded < 100f -> (bytesDownloaded * 100.0 / percentDownloaded).toLong()
  else -> null
}

internal fun DownloadEntry.remainingSeconds(): Long? {
  val rate = bytesPerSecond?.takeIf { it >= 1.0 } ?: return null
  val total = estimatedTotalBytes() ?: return null
  return ((total - bytesDownloaded).coerceAtLeast(0L) / rate).toLong()
}

internal val DownloadEntry.wholePercent: Int get() = percentDownloaded.coerceIn(0f, 100f).toInt()

/** Words for a download's progress, shared by the Downloads page and the notification. */
internal object DownloadProgressText {
  /** "Big Mouth — S1 E4" rather than just the show, which is all a series download is saved under. */
  fun title(context: Context, media: DownloadMedia): String {
    val episode = media.episodeNumber ?: return media.title
    val season = media.seasonNumber
    return if (season != null) {
      context.getString(R.string.download_title_season_episode, media.title, season, episode)
    } else {
      context.getString(R.string.download_title_episode, media.title, episode)
    }
  }

  fun status(context: Context, entry: DownloadEntry): String =
    context.getString(R.string.download_status_downloading_percent, entry.wholePercent)

  fun speed(context: Context, entry: DownloadEntry): String? =
    entry.bytesPerSecond?.let { context.getString(R.string.download_speed, Formatter.formatShortFileSize(context, it.toLong())) }

  fun size(context: Context, entry: DownloadEntry): String? {
    if (entry.bytesDownloaded <= 0L) return null
    val done = Formatter.formatShortFileSize(context, entry.bytesDownloaded)
    val total = entry.estimatedTotalBytes() ?: return done
    return context.getString(
      if (entry.contentLength > 0) R.string.download_size_progress else R.string.download_size_progress_estimated,
      done,
      Formatter.formatShortFileSize(context, total),
    )
  }

  /** What a finished download takes up on the device. */
  fun completedSize(context: Context, entry: DownloadEntry): String? =
    maxOf(entry.bytesDownloaded, entry.contentLength).takeIf { it > 0L }?.let { Formatter.formatShortFileSize(context, it) }

  /** Rounded to the minute: a countdown in seconds would change every refresh and mean nothing more. */
  fun eta(context: Context, entry: DownloadEntry): String? {
    val seconds = entry.remainingSeconds() ?: return null
    if (seconds < 60) return context.getString(R.string.download_eta_under_minute)
    val totalMinutes = (seconds + 30) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val measures = buildList {
      if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
      if (minutes > 0 || hours == 0L) add(Measure(minutes, MeasureUnit.MINUTE))
    }
    val locale = context.resources.configuration.locales[0]
    val duration = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).formatMeasures(*measures.toTypedArray())
    return context.getString(R.string.download_eta_about, duration)
  }
}

/**
 * The ongoing notification while anything is downloading.
 *
 * Media3's stock one reads "Downloading" over a bar and nothing else — not which title, not how fast,
 * not how long. One download gets its title, percentage, speed and time left; several get a line
 * each under a combined bar.
 */
internal fun buildDownloadNotification(
  context: Context,
  channelId: String,
  downloads: List<DownloadEntry>,
  contentIntent: PendingIntent?,
): Notification {
  val builder = NotificationCompat.Builder(context, channelId)
    .setSmallIcon(R.drawable.ic_stat_streamdek)
    .setContentIntent(contentIntent)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setSilent(true)
    .setShowWhen(false)
    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
  val active = downloads.filter { it.state == DownloadState.DOWNLOADING }

  if (active.isEmpty()) {
    // Queued, or held back by the platform (no network, storage low): nothing is moving yet.
    val waiting = downloads.singleOrNull()
    return builder
      .setContentTitle(waiting?.let { DownloadProgressText.title(context, it.media) } ?: context.getString(R.string.download_channel_name))
      .setContentText(context.getString(R.string.download_notification_waiting))
      .setProgress(0, 0, true)
      .build()
  }

  if (active.size == 1) {
    val entry = active.single()
    val status = DownloadProgressText.status(context, entry)
    val detail = listOfNotNull(
      DownloadProgressText.speed(context, entry)?.let { "⚡ $it" },
      DownloadProgressText.eta(context, entry),
    ).joinToString(" • ")
    return builder
      .setContentTitle(DownloadProgressText.title(context, entry.media))
      .setContentText(if (detail.isEmpty()) status else "$status • $detail")
      .setStyle(NotificationCompat.BigTextStyle().bigText(if (detail.isEmpty()) status else "$status\n$detail"))
      .setProgress(100, entry.wholePercent, entry.percentDownloaded < 0f)
      .build()
  }

  val combinedRate = active.mapNotNull { it.bytesPerSecond }.takeIf { it.isNotEmpty() }?.sum()
  val speedText = combinedRate?.let { "⚡ " + context.getString(R.string.download_speed, Formatter.formatShortFileSize(context, it.toLong())) }
  // The locale's own percent format: "43 %" in French and German, "43%" in English.
  val percent = java.text.NumberFormat.getPercentInstance(context.resources.configuration.locales[0])
  fun percentOf(entry: DownloadEntry): String = percent.format(entry.wholePercent / 100.0)
  val inbox = NotificationCompat.InboxStyle()
  active.forEach { entry -> inbox.addLine("${DownloadProgressText.title(context, entry.media)} — ${percentOf(entry)}") }
  speedText?.let(inbox::setSummaryText)
  return builder
    .setContentTitle(context.resources.getQuantityString(R.plurals.download_notification_multiple, active.size, active.size))
    .setContentText(listOfNotNull(active.joinToString(" • ", transform = ::percentOf), speedText).joinToString(" • "))
    .setStyle(inbox)
    .setProgress(100, active.map { it.wholePercent }.average().toInt(), false)
    .build()
}
