package net.streamdek.mobile.nativeapp

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * The trailer pipeline's own cache, kept apart from everything else the app stores.
 *
 * Trailers are the one part of StreamDek whose upstream actively works against being cached. A
 * YouTube media URL carries its own expiry, the player response that issued it is only valid for
 * that window, and the WebView the iframe fallback runs in keeps cookies and site storage that
 * YouTube uses to decide whether the caller looks like a browser or a bot. Stale state in any of
 * those makes trailers fail in ways that look like a broken app: a resolve that succeeds followed
 * by 403s from the media host, or an embed that reports ERROR before it has drawn anything.
 *
 * Clearing it therefore has to be possible without touching add-on responses, artwork, subtitles or
 * downloaded torrent data — which is why the trailer cache has its own directory and this object
 * owns everything that counts as trailer state.
 */
object TrailerCache {
  private const val TAG = "TrailerCache"
  private const val DIRECTORY = "trailer-cache"

  /** Enough for KinoCheck's answers and the embed's own assets, small enough to be disposable. */
  const val MAX_BYTES = 8L * 1024L * 1024L

  private const val PREFERENCES = "streamdek_trailer_cache"
  private const val KEY_LAST_CLEARED = "last_cleared_at"

  /** Where the trailer HTTP cache lives. Separate directory so clearing it hits nothing else. */
  fun directory(context: Context): File =
    File(context.cacheDir, DIRECTORY).apply { mkdirs() }

  /**
   * Wipes trailer state: the HTTP cache, and the WebView's cookies and site storage.
   *
   * The WebView half is not optional. It is shared process-wide rather than per-view, so a poisoned
   * YouTube cookie or a stale service worker outlives any single trailer, every detail page after
   * it, and the app restart in between.
   *
   * Must be called from the main thread — WebView and CookieManager both require it.
   */
  fun clear(context: Context, reason: String): Long {
    android.util.Log.d(TAG, "clearing trailer cache ($reason)")
    val removedBytes = runCatching {
      val directory = File(context.cacheDir, DIRECTORY)
      val size = directorySize(directory)
      directory.deleteRecursively()
      directory.mkdirs()
      size
    }.getOrElse { error ->
      android.util.Log.w(TAG, "could not delete the trailer cache directory: ${error.message}")
      0L
    }

    // Constructed first because it is what initialises the WebView provider: WebStorage and
    // CookieManager act on that provider's default profile, and calling either before it exists is
    // a silent no-op on some versions.
    runCatching { WebView(context).apply { clearCache(true); clearHistory(); destroy() } }
      .onFailure { android.util.Log.w(TAG, "could not clear the WebView cache: ${it.message}") }
    runCatching { WebStorage.getInstance().deleteAllData() }
      .onFailure { android.util.Log.w(TAG, "could not clear WebView site storage: ${it.message}") }

    // Logged either side, because this is the part that actually decides whether trailers play: it
    // is the state that survives Android's own "clear cache" and used to need a full data wipe.
    runCatching {
      val cookies = CookieManager.getInstance()
      val hadCookies = cookies.hasCookies()
      cookies.removeAllCookies(null)
      cookies.flush()
      android.util.Log.d(TAG, "WebView cookies cleared (had cookies before: $hadCookies, after: ${cookies.hasCookies()})")
    }.onFailure { android.util.Log.w(TAG, "could not clear WebView cookies: ${it.message}") }

    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
      .putLong(KEY_LAST_CLEARED, System.currentTimeMillis())
      .apply()
    android.util.Log.d(TAG, "cleared trailer cache ($reason), freed ${removedBytes / 1024}KB")
    return removedBytes
  }

  fun lastClearedAt(context: Context): Long =
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong(KEY_LAST_CLEARED, 0L)

  fun sizeBytes(context: Context): Long = directorySize(File(context.cacheDir, DIRECTORY))

  /**
   * Whether an automatic clear is due.
   *
   * Anchored to a time of day rather than to an interval from the last clear: "every 24 hours from
   * whenever you last opened the app" drifts, and a viewer who is told it happens at nine expects
   * it at nine. A run is due once the day's anchor has passed and the last clear was before it.
   */
  fun isClearDue(
    context: Context,
    intervalHours: Int,
    anchorHourOfDay: Int,
    now: Long = System.currentTimeMillis(),
  ): Boolean {
    if (intervalHours <= 0) return false
    val lastCleared = lastClearedAt(context)
    // A device that has never cleared should not do it the moment the app first opens; the first
    // run is the next anchor after installation.
    if (lastCleared == 0L) {
      context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
        .putLong(KEY_LAST_CLEARED, now)
        .apply()
      return false
    }
    return isClearDue(lastCleared, intervalHours, anchorHourOfDay, now)
  }

  /**
   * The scheduling decision on its own, without the stored state — so it can be tested.
   *
   * The schedule is a series of fixed points in the calendar — nine in the morning, every
   * [intervalHours] — and a clear is due when the most recent of those has gone past without one.
   *
   * Expressed against the calendar rather than as "[intervalHours] since the last clear" on purpose:
   * elapsed-time scheduling drifts. A clear that happened at ten because that is when the app was
   * opened would push the next one to the following day's ten, then eleven, until it no longer
   * happens at nine at all — or, with the interval enforced strictly, skips a day entirely. Anchoring
   * also gives catch-up for free: a phone that was not opened at nine clears at the next opportunity
   * instead of waiting a whole further cycle.
   */
  fun isClearDue(lastClearedAt: Long, intervalHours: Int, anchorHourOfDay: Int, now: Long): Boolean {
    if (intervalHours <= 0 || lastClearedAt <= 0L) return false
    return lastClearedAt < mostRecentAnchor(now, anchorHourOfDay, intervalHours)
  }

  /**
   * The latest scheduled point at or before [now].
   *
   * The grid runs from a fixed origin — the anchor hour on the first day of 2026, local time — in
   * [intervalHours] steps. The origin has to be fixed rather than "today at the anchor hour": with a
   * daily grid the two are the same, but a weekly one re-based each day would offer a fresh anchor
   * every morning and fire daily, which is what it is meant not to do.
   *
   * A daylight-saving change shifts the grid by an hour until the next origin recalculation. That is
   * a deliberate non-problem: this schedules cache housekeeping, not an alarm.
   */
  private fun mostRecentAnchor(now: Long, anchorHourOfDay: Int, intervalHours: Int): Long {
    val hour = anchorHourOfDay.coerceIn(0, 23)
    fun dayAtAnchor(millis: Long, plusDays: Int = 0): Long = Calendar.getInstance().apply {
      timeInMillis = millis
      add(Calendar.DAY_OF_YEAR, plusDays)
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // Sub-daily schedules simply repeat within the day, so the candidates either side of now are
    // enough — no grid to keep aligned.
    if (intervalHours < 24) {
      val step = TimeUnit.HOURS.toMillis(intervalHours.toLong())
      var anchor = dayAtAnchor(now, -1)
      while (anchor + step <= now) anchor += step
      return anchor
    }

    // A day or more: step whole days with the calendar rather than in milliseconds, so a clock
    // change moves the anchor with the day instead of dragging it an hour off nine o'clock.
    val stepDays = (intervalHours / 24).coerceAtLeast(1)
    val todayAnchor = dayAtAnchor(now)
    val elapsedDays = wholeDaysBetween(dayAtAnchor(GRID_ORIGIN_MILLIS), todayAnchor)
    val phase = ((elapsedDays % stepDays) + stepDays) % stepDays
    val candidate = dayAtAnchor(todayAnchor, -phase)
    return if (candidate <= now) candidate else dayAtAnchor(candidate, -stepDays)
  }

  /**
   * Whole local days between two instants, measured from midday so an hour gained or lost to a clock
   * change cannot round the count up or down.
   */
  private fun wholeDaysBetween(from: Long, to: Long): Int {
    fun noon(millis: Long): Long = Calendar.getInstance().apply {
      timeInMillis = millis
      set(Calendar.HOUR_OF_DAY, 12)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return Math.round((noon(to) - noon(from)).toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
  }

  /** Fixed start of the anchor grid, so schedules longer than a day keep their phase: 1 Jan 2026. */
  private val GRID_ORIGIN_MILLIS: Long = Calendar.getInstance().apply {
    clear()
    set(2026, Calendar.JANUARY, 1, 0, 0, 0)
  }.timeInMillis

  private fun directorySize(directory: File): Long = runCatching {
    directory.takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
  }.getOrDefault(0L)
}
