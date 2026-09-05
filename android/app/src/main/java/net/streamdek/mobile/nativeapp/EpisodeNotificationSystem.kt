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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.streamdek.mobile.MainActivity
import net.streamdek.mobile.R
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val EPISODE_NOTIFICATION_TAG = "StreamDekEpisodes"
private val DATE_ONLY_RELEASE_TIME: LocalTime = LocalTime.of(9, 0)

data class EpisodeNotificationTarget(val mediaId: String, val season: Int?, val episode: Int?)

data class EpisodeNotificationSettings(
  val availableEnabled: Boolean = false,
  val upcomingEnabled: Boolean = false,
  val upcomingDays: Int = 1,
)

enum class EpisodeNotificationType { Available, Upcoming }

data class EpisodeNotificationCandidate(
  val key: String,
  val type: EpisodeNotificationType,
  val seriesId: Int,
  val episode: AiringEpisode,
  val title: String,
  val body: String,
)

/** Pure release policy shared by the Home row and background notification checks. */
object EpisodeReleasePolicy {
  fun episodes(status: SeriesEpisodeStatus): List<AiringEpisode> =
    (status.episodes + listOfNotNull(status.lastEpisode, status.nextEpisode))
      .distinctBy { listOf(it.id, it.season, it.episode, it.airDate).joinToString(":") }

  fun releasedWithin(statuses: List<SeriesEpisodeStatus>, today: LocalDate, days: Long): List<Pair<SeriesEpisodeStatus, AiringEpisode>> =
    statuses.flatMap { status -> episodes(status).map { status to it } }
      .filter { (_, episode) ->
        val date = parseDate(episode.airDate) ?: return@filter false
        !date.isAfter(today) && !date.isBefore(today.minusDays(days))
      }
      .sortedByDescending { (_, episode) -> parseDate(episode.airDate) }

  fun candidates(
    statuses: List<SeriesEpisodeStatus>,
    settings: EpisodeNotificationSettings,
    delivered: Set<String>,
    clock: Clock,
  ): List<EpisodeNotificationCandidate> {
    if (!settings.availableEnabled && !settings.upcomingEnabled) return emptyList()
    val now = clock.instant()
    val today = LocalDate.now(clock)
    return statuses.flatMap { status ->
      episodes(status).mapNotNull { episode ->
        val airDate = parseDate(episode.airDate) ?: return@mapNotNull null
        val releaseAt = airDate.atTime(DATE_ONLY_RELEASE_TIME).atZone(clock.zone).toInstant()
        val daysUntil = ChronoUnit.DAYS.between(today, airDate).toInt()
        val type = when {
          settings.availableEnabled && !now.isBefore(releaseAt) && now.isBefore(releaseAt.plus(48, ChronoUnit.HOURS)) -> EpisodeNotificationType.Available
          settings.upcomingEnabled && daysUntil in 1..settings.upcomingDays -> EpisodeNotificationType.Upcoming
          else -> return@mapNotNull null
        }
        val key = stableKey(status.tmdbId, episode, type)
        if (key in delivered) return@mapNotNull null
        candidate(status, episode, type, daysUntil, key)
      }
    }
  }

  fun stableKey(seriesId: Int, episode: AiringEpisode, type: EpisodeNotificationType): String =
    "$seriesId:${episode.season ?: -1}:${episode.episode ?: episode.id ?: -1}:${type.name.lowercase()}"

  private fun candidate(status: SeriesEpisodeStatus, episode: AiringEpisode, type: EpisodeNotificationType, daysUntil: Int, key: String): EpisodeNotificationCandidate {
    val series = status.title?.trim().takeUnless { it.isNullOrEmpty() } ?: "A series you follow"
    val code = when {
      episode.season != null && episode.episode != null -> "S${episode.season} E${episode.episode}"
      episode.episode != null -> "Episode ${episode.episode}"
      else -> null
    }
    val name = episode.name?.trim()?.takeIf { it.isNotEmpty() }
    val identity = listOfNotNull(series, code, name).joinToString(" · ")
    val title = when (type) {
      EpisodeNotificationType.Available -> "New episode available"
      EpisodeNotificationType.Upcoming -> if (daysUntil == 1) "New episode tomorrow" else "New episode in $daysUntil days"
    }
    val body = when (type) {
      EpisodeNotificationType.Available -> "$identity is now available"
      EpisodeNotificationType.Upcoming -> "$identity arrives ${if (daysUntil == 1) "tomorrow" else "in $daysUntil days"}"
    }
    return EpisodeNotificationCandidate(key, type, status.tmdbId, episode, title, body)
  }

  private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim()) }.getOrNull()
}

object EpisodeNotificationSystem {
  private const val CHANNEL_ID = "episode_releases"
  private const val PREFS_NAME = "streamdek_native_episode_notifications_v2"
  private const val PERIODIC_WORK = "streamdek_episode_check"
  private const val IMMEDIATE_WORK = "streamdek_episode_check_now"
  private const val OLD_PREFS_NAME = "streamdek_native_episode_reminders"
  private const val HISTORY_RETENTION_MS = 180L * 24 * 60 * 60 * 1000
  private const val MAX_HISTORY = 1000

  const val KEY_MEDIA_ID = "episode_notification_media_id"
  const val KEY_SEASON = "episode_notification_season"
  const val KEY_EPISODE = "episode_notification_episode"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private fun key(owner: String, suffix: String) = "${owner.replace(Regex("[^A-Za-z0-9_.-]"), "_")}:$suffix"

  fun settings(context: Context, owner: String): EpisodeNotificationSettings {
    val prefs = prefs(context)
    val migrated = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE).getBoolean("enabled", false)
    return EpisodeNotificationSettings(
      availableEnabled = prefs.getBoolean(key(owner, "available"), migrated),
      upcomingEnabled = prefs.getBoolean(key(owner, "upcoming"), false),
      upcomingDays = prefs.getInt(key(owner, "upcoming_days"), 1).takeIf { it in setOf(1, 2, 7) } ?: 1,
    )
  }

  fun saveSettings(context: Context, owner: String, value: EpisodeNotificationSettings) {
    prefs(context).edit()
      .putBoolean(key(owner, "available"), value.availableEnabled)
      .putBoolean(key(owner, "upcoming"), value.upcomingEnabled)
      .putInt(key(owner, "upcoming_days"), value.upcomingDays)
      .apply()
    updateBackgroundWork(context, value)
  }

  fun cacheEligibleSeries(context: Context, owner: String, ids: List<Int>) {
    prefs(context).edit().putString(key(owner, "series"), JSONArray(ids.filter { it > 0 }.distinct()).toString()).apply()
  }

  internal fun cachedEligibleSeries(context: Context, owner: String): Set<Int> = runCatching {
    val array = JSONArray(prefs(context).getString(key(owner, "series"), "[]"))
    buildSet { for (index in 0 until array.length()) array.optInt(index).takeIf { it > 0 }?.let(::add) }
  }.getOrDefault(emptySet())

  fun ensureBackgroundWork(context: Context) {
    // Remove one-time work left by the pre-v2 reminder implementation. Its payload was global,
    // lacked profile identity and could otherwise still fire once after this upgrade.
    WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag("streamdek_episode_release")
    activeOwner(context)?.let { updateBackgroundWork(context, settings(context, it)) }
  }

  fun requestImmediateCheck(context: Context) {
    val owner = activeOwner(context) ?: return
    if (!settings(context, owner).run { availableEnabled || upcomingEnabled }) return
    WorkManager.getInstance(context).enqueueUniqueWork(
      IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE,
      OneTimeWorkRequestBuilder<EpisodeNotificationWorker>().setConstraints(networkConstraints()).build(),
    )
  }

  private fun updateBackgroundWork(context: Context, settings: EpisodeNotificationSettings) {
    val work = WorkManager.getInstance(context.applicationContext)
    if (!settings.availableEnabled && !settings.upcomingEnabled) {
      work.cancelUniqueWork(PERIODIC_WORK)
      work.cancelUniqueWork(IMMEDIATE_WORK)
      return
    }
    work.enqueueUniquePeriodicWork(
      PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE,
      PeriodicWorkRequestBuilder<EpisodeNotificationWorker>(12, TimeUnit.HOURS)
        .setConstraints(networkConstraints()).build(),
    )
    requestImmediateCheck(context)
  }

  private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

  fun permitted(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID) ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
  }

  internal fun activeOwner(context: Context): String? {
    val session = SessionStore(context).load() ?: return null
    val profileId = ProfileSelectionStore(context).load(session.user.uid) ?: return null
    return "${session.user.uid}:$profileId"
  }

  internal fun delivered(context: Context, owner: String, now: Long = System.currentTimeMillis()): Set<String> =
    readHistory(context, owner).filterValues { now - it <= HISTORY_RETENTION_MS }.keys

  internal fun markDelivered(context: Context, owner: String, stableKey: String, now: Long = System.currentTimeMillis()) {
    val cleaned = readHistory(context, owner).filterValues { now - it <= HISTORY_RETENTION_MS }.toMutableMap()
    cleaned[stableKey] = now
    val json = JSONObject().apply { cleaned.entries.sortedByDescending { it.value }.take(MAX_HISTORY).forEach { put(it.key, it.value) } }
    prefs(context).edit().putString(key(owner, "delivered"), json.toString()).apply()
  }

  private fun readHistory(context: Context, owner: String): Map<String, Long> = runCatching {
    val json = JSONObject(prefs(context).getString(key(owner, "delivered"), "{}") ?: "{}")
    buildMap { json.keys().forEach { entry -> put(entry, json.optLong(entry)) } }
  }.getOrDefault(emptyMap())

  internal fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    // localizedAppContext, not the raw context: a notification channel is named once, from a
    // background caller with no composition, and the application context is never locale-wrapped -
    // so reading straight off it would name the channel in the device language rather than the
    // one the viewer chose.
    val strings = localizedAppContext(context).resources
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        strings.getString(R.string.episode_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = strings.getString(R.string.episode_channel_description)
      },
    )
  }

  internal fun post(context: Context, candidate: EpisodeNotificationCandidate): Boolean {
    if (!permitted(context)) return false
    ensureChannel(context)
    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(KEY_MEDIA_ID, candidate.seriesId.toString())
      candidate.episode.season?.let { putExtra(KEY_SEASON, it) }
      candidate.episode.episode?.let { putExtra(KEY_EPISODE, it) }
    }
    val pending = PendingIntent.getActivity(context, abs(candidate.key.hashCode()), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(candidate.title).setContentText(candidate.body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(candidate.body))
      .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
    return runCatching {
      NotificationManagerCompat.from(context).notify(abs(candidate.key.hashCode()), notification)
      true
    }.onFailure { Log.w(EPISODE_NOTIFICATION_TAG, "Could not post episode notification", it) }.getOrDefault(false)
  }
}

class EpisodeNotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result {
    val owner = EpisodeNotificationSystem.activeOwner(applicationContext) ?: return Result.success()
    val settings = EpisodeNotificationSystem.settings(applicationContext, owner)
    if (!settings.availableEnabled && !settings.upcomingEnabled) return Result.success()
    val session = SessionStore(applicationContext).load() ?: return Result.success()
    val profileId = ProfileSelectionStore(applicationContext).load(session.user.uid) ?: return Result.success()
    val api = StreamDekApiClient(applicationContext)
    val ids = EpisodeNotificationSystem.cachedEligibleSeries(applicationContext, owner).toMutableSet()
    api.fetchSyncDekWatchlist(session, profileId).getOrDefault(emptyList())
      .filter { it.type.equals("tv", true) }.mapNotNullTo(ids) { it.tmdbId ?: it.id.toIntOrNull() }
    api.fetchPlaybackProgress(session, profileId, limit = 250).getOrDefault(emptyList())
      .filter { it.entityType.equals("tv", true) && !it.completed && !it.unwatched && (it.positionSec > 0 || it.progress > 0) }
      .mapNotNullTo(ids) { it.entityId.toIntOrNull() }
    EpisodeNotificationSystem.cacheEligibleSeries(applicationContext, owner, ids.toList())
    if (ids.isEmpty()) return Result.success()
    val statuses = api.fetchSeriesEpisodeStatus(ids.toList()).getOrElse {
      Log.w(EPISODE_NOTIFICATION_TAG, "Background episode metadata refresh failed", it)
      return Result.retry()
    }
    val candidates = EpisodeReleasePolicy.candidates(statuses, settings, EpisodeNotificationSystem.delivered(applicationContext, owner), Clock.systemDefaultZone())
    candidates.forEach { candidate ->
      if (EpisodeNotificationSystem.post(applicationContext, candidate)) EpisodeNotificationSystem.markDelivered(applicationContext, owner, candidate.key)
    }
    Log.i(EPISODE_NOTIFICATION_TAG, "Episode check profile=${owner.hashCode()} eligible=${ids.size} delivered=${candidates.size}")
    return Result.success()
  }
}
