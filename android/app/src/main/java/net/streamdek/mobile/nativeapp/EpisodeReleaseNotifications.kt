package net.streamdek.mobile.nativeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.streamdek.mobile.MainActivity
import net.streamdek.mobile.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val TAG = "StreamDekEpisodes"

/** The hour a reminder lands, in the viewer's own timezone. */
private val EPISODE_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

/**
 * Reminders for the next episode of a series the viewer follows.
 *
 * Scheduled on the device rather than pushed from a server. Everything needed is already known
 * here — which series are followed, and when TMDB says the next episode airs — so there is nothing
 * for a server to add except a channel that has to be kept alive and a device token to go stale.
 *
 * One reminder per series, not per episode. TMDB only names the *next* episode, and a weekly show
 * would otherwise need its whole season scheduling ahead on air dates that get revised; asking
 * again after each one fires is both smaller and more accurate. [refresh] is what re-arms them,
 * and it runs whenever the followed list changes or the app is opened.
 */
object EpisodeReleaseNotifications {
  private const val CHANNEL_ID = "episode_releases"
  private const val WORK_TAG = "streamdek_episode_release"
  private const val PREFS_NAME = "streamdek_native_episode_reminders"
  private const val ENABLED_KEY = "enabled"
  private const val SCHEDULED_KEY = "scheduled_ids"

  internal const val KEY_TITLE = "title"
  internal const val KEY_BODY = "body"
  internal const val KEY_MEDIA_ID = "media_id"
  internal const val KEY_MEDIA_TYPE = "media_type"
  internal const val KEY_REQUEST_ID = "request_id"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(ENABLED_KEY, false)

  fun setEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(ENABLED_KEY, enabled).apply()
    if (!enabled) cancelAll(context)
  }

  /**
   * Whether reminders can actually be shown.
   *
   * From Android 13 the viewer has to grant notifications, and a channel they have since switched
   * off is the same dead end. Both are reported as "not permitted" so the settings row can say so
   * rather than quietly scheduling work that posts into nothing.
   */
  fun permitted(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
  }

  /**
   * Re-arms every reminder from the series currently followed.
   *
   * Cancel-then-schedule rather than a diff: the set is small, the work is cheap, and a series
   * dropped from the watchlist has to lose its reminder without anything having to notice that it
   * went. Episodes already aired are skipped — a delay in the past fires the moment it is
   * enqueued, which is how a reminder for last week's episode arrives at breakfast.
   */
  fun refresh(context: Context, series: List<SeriesEpisodeStatus>) {
    if (!isEnabled(context) || !permitted(context)) {
      cancelAll(context)
      return
    }
    ensureChannel(context)
    val workManager = WorkManager.getInstance(context.applicationContext)
    cancelTracked(context, workManager)

    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val scheduled = mutableSetOf<String>()

    series.forEach { entry ->
      val next = entry.nextEpisode ?: return@forEach
      val airsAt = reminderEpochMs(next.airDate, zone) ?: return@forEach
      val delay = airsAt - now
      if (delay <= 0L) return@forEach

      val requestId = "episode-${entry.tmdbId}-${next.airDate}"
      val data = Data.Builder()
        .putString(KEY_REQUEST_ID, requestId)
        .putString(KEY_TITLE, entry.title ?: "A series you follow")
        .putString(KEY_BODY, episodeReminderBody(next))
        .putString(KEY_MEDIA_ID, entry.tmdbId.toString())
        .putString(KEY_MEDIA_TYPE, "tv")
        .build()

      workManager.enqueueUniqueWork(
        "$WORK_TAG:$requestId",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<EpisodeReleaseWorker>()
          .setInputData(data)
          .setInitialDelay(delay, TimeUnit.MILLISECONDS)
          .addTag(WORK_TAG)
          .build(),
      )
      scheduled += requestId
    }

    prefs(context).edit().putStringSet(SCHEDULED_KEY, scheduled).apply()
    Log.i(TAG, "Scheduled ${scheduled.size} episode reminder(s) from ${series.size} followed series")
  }

  fun cancelAll(context: Context) {
    val workManager = WorkManager.getInstance(context.applicationContext)
    cancelTracked(context, workManager)
    prefs(context).edit().remove(SCHEDULED_KEY).apply()
  }

  private fun cancelTracked(context: Context, workManager: WorkManager) {
    workManager.cancelAllWorkByTag(WORK_TAG)
    prefs(context).getStringSet(SCHEDULED_KEY, emptySet()).orEmpty().forEach { requestId ->
      workManager.cancelUniqueWork("$WORK_TAG:$requestId")
    }
  }

  /**
   * The instant a reminder for an episode airing on [airDate] should fire.
   *
   * TMDB's air date carries no time and no timezone — it is the date the episode airs where it
   * airs. Treating it as a local morning is the honest reading: it puts the reminder at a
   * reasonable hour on the right day wherever the viewer is, rather than at an arbitrary time
   * derived from a broadcaster's schedule the app was never told.
   */
  internal fun reminderEpochMs(airDate: String, zone: ZoneId): Long? = runCatching {
    LocalDate.parse(airDate.trim()).atTime(EPISODE_REMINDER_TIME).atZone(zone).toInstant().toEpochMilli()
  }.getOrNull()

  internal fun episodeReminderBody(episode: AiringEpisode): String {
    val code = when {
      episode.season != null && episode.episode != null ->
        "S%02dE%02d".format(episode.season, episode.episode)
      episode.episode != null -> "Episode ${episode.episode}"
      else -> null
    }
    val name = episode.name?.trim()?.takeIf { it.isNotEmpty() }
    return when {
      code != null && name != null -> "$code · $name is out now."
      code != null -> "$code is out now."
      name != null -> "$name is out now."
      else -> "A new episode is out now."
    }
  }

  internal fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "When a new episode of a series you follow is out."
      },
    )
  }

  internal fun buildNotification(
    context: Context,
    requestId: String,
    title: String,
    body: String,
    mediaId: String?,
    mediaType: String?,
  ): android.app.Notification {
    // Opens the title rather than just the app. MainActivity reads these extras on launch, so a
    // reminder tapped from the shade lands on the series it is about.
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(KEY_MEDIA_ID, mediaId)
      putExtra(KEY_MEDIA_TYPE, mediaType)
    }
    val pending = PendingIntent.getActivity(
      context,
      abs(requestId.hashCode()),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setContentIntent(pending)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()
  }
}

/** Posts one reminder when its scheduled moment arrives. */
class EpisodeReleaseWorker(
  context: Context,
  parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    // Checked again here rather than only at scheduling time: the wait is measured in days, and
    // permission can be withdrawn inside it.
    if (!EpisodeReleaseNotifications.isEnabled(applicationContext)) return Result.success()
    if (!EpisodeReleaseNotifications.permitted(applicationContext)) return Result.success()

    val requestId = inputData.getString(EpisodeReleaseNotifications.KEY_REQUEST_ID) ?: return Result.failure()
    val title = inputData.getString(EpisodeReleaseNotifications.KEY_TITLE) ?: return Result.failure()
    val body = inputData.getString(EpisodeReleaseNotifications.KEY_BODY) ?: return Result.failure()

    EpisodeReleaseNotifications.ensureChannel(applicationContext)
    val notification = EpisodeReleaseNotifications.buildNotification(
      context = applicationContext,
      requestId = requestId,
      title = title,
      body = body,
      mediaId = inputData.getString(EpisodeReleaseNotifications.KEY_MEDIA_ID),
      mediaType = inputData.getString(EpisodeReleaseNotifications.KEY_MEDIA_TYPE),
    )
    runCatching {
      NotificationManagerCompat.from(applicationContext).notify(abs(requestId.hashCode()), notification)
    }.onFailure { Log.w(TAG, "Could not post episode reminder", it) }
    return Result.success()
  }
}
