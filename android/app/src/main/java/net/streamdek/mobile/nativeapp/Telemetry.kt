package net.streamdek.mobile.nativeapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Client-side funnel telemetry.
 *
 * The backend can see which add-ons it queried and which debrid providers it tried, but not
 * whether anything ever played — that only happens on the device. Without these events the
 * admin console can report that sources were resolved and still not answer "did it work",
 * which is the question that matters.
 *
 * Three rules shape this:
 *
 *  - It must never affect playback. Every path is wrapped, the queue is bounded, failures are
 *    swallowed, and nothing is ever awaited from the UI. Losing telemetry is acceptable;
 *    delaying a stream is not.
 *  - It must never report something it does not know. An event is only emitted where the
 *    outcome is actually established — a source that failed and was retried successfully is
 *    not a playback failure, and is not recorded as one.
 *  - It carries no content beyond the media id and title the catalogue already exposes. No
 *    stream URLs, no magnets, no credentials.
 */
object Telemetry {

  // Event names. These must match the taxonomy the backend accepts; anything else is rejected
  // there rather than silently counted, so they are constants instead of inline strings.
  const val CONTENT_OPENED = "content_opened"
  const val SEARCH_PERFORMED = "search_performed"
  const val PLAYBACK_STARTED = "playback_started"
  const val PLAYBACK_FAILED = "playback_failed"
  const val SESSION_STARTED = "session_started"

  /** Matches the backend's error taxonomy. Anything unrecognised is filed as `unknown` there. */
  const val CATEGORY_PLAYBACK = "playback"
  const val CATEGORY_RESOLVER = "resolver"
  const val CATEGORY_TIMEOUT = "timeout"
  const val CATEGORY_NETWORK = "network"
  const val CATEGORY_UNKNOWN = "unknown"

  private const val MAX_QUEUE = 200
  private const val FLUSH_AT = 20
  private const val FLUSH_INTERVAL_MS = 30_000L
  private const val MAX_BATCH = 50

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mutex = Mutex()
  private val queue = ArrayDeque<JSONObject>()

  private var client: StreamDekApiClient? = null
  private var sessionProvider: (() -> AuthSession?)? = null
  private var flushJob: Job? = null
  private var enabled: Boolean = true

  /**
   * Wires the emitter to an API client and a way to read the current session.
   *
   * The session is read at flush time rather than captured, so events queued before sign-in are
   * still attributed correctly once one exists — and events from a signed-out user are sent
   * anonymously rather than dropped, because anonymous activity is real activity.
   */
  fun configure(apiClient: StreamDekApiClient, session: () -> AuthSession?) {
    client = apiClient
    sessionProvider = session
    if (flushJob == null) {
      flushJob = scope.launch {
        while (true) {
          delay(FLUSH_INTERVAL_MS)
          flush()
        }
      }
    }
  }

  /** Turns capture off entirely, for a build or account that should not report. */
  fun setEnabled(value: Boolean) {
    enabled = value
    if (!value) scope.launch { runCatching { mutex.withLock { queue.clear() } } }
  }

  /**
   * A new correlation id for one playback attempt.
   *
   * The same id is sent as `x-correlation-id` on the stream request the attempt triggers, which
   * is what lets the console join this device's outcome to the add-on calls and debrid resolves
   * the backend made for it. One id per user action, not per retried source.
   */
  fun newCorrelationId(): String = "cl_${UUID.randomUUID().toString().replace("-", "").take(20)}"

  // ── Emission ────────────────────────────────────────────────────────────────

  fun contentOpened(mediaId: String?, mediaType: String?, title: String?) {
    track(CONTENT_OPENED) {
      putOpt("mediaId", mediaId)
      putOpt("mediaType", normaliseMediaType(mediaType))
      putOpt("mediaTitle", title)
    }
  }

  /**
   * A search and how many results it produced.
   *
   * The query itself is deliberately not sent — a search box is free text a user may type
   * anything into, and none of it is needed to answer "are searches returning nothing".
   * `resultCount` is what makes a zero-result rate computable.
   */
  fun searchPerformed(resultCount: Int) {
    track(SEARCH_PERFORMED) {
      put("resultCount", resultCount.coerceAtLeast(0))
      put("outcome", if (resultCount > 0) "success" else "empty")
    }
  }

  fun playbackStarted(
    correlationId: String?,
    mediaId: String?,
    mediaType: String?,
    title: String?,
    addonKey: String?,
    provider: String?,
    durationMs: Long?,
    sourcesTried: Int,
  ) {
    track(PLAYBACK_STARTED) {
      putOpt("correlationId", correlationId)
      putOpt("mediaId", mediaId)
      putOpt("mediaType", normaliseMediaType(mediaType))
      putOpt("mediaTitle", title)
      putOpt("addonKey", addonKey)
      putOpt("provider", provider)
      put("outcome", "success")
      durationMs?.let { put("durationMs", it.coerceIn(0L, Int.MAX_VALUE.toLong())) }
      // How many ranked sources were burned before one played. A rising value is a resolver
      // problem the success rate alone would hide.
      put("metadata", JSONObject().put("sourcesTried", sourcesTried))
    }
  }

  /**
   * A playback attempt that the user actually saw fail.
   *
   * Emitted only once the app has stopped retrying — a source that failed and was replaced by a
   * working one is not a failed playback, and counting it as one would make the success rate
   * meaningless while the app was doing exactly what it should.
   */
  fun playbackFailed(
    correlationId: String?,
    mediaId: String?,
    mediaType: String?,
    title: String?,
    addonKey: String?,
    provider: String?,
    errorCategory: String,
    errorCode: String?,
    durationMs: Long?,
    sourcesTried: Int,
  ) {
    track(PLAYBACK_FAILED) {
      putOpt("correlationId", correlationId)
      putOpt("mediaId", mediaId)
      putOpt("mediaType", normaliseMediaType(mediaType))
      putOpt("mediaTitle", title)
      putOpt("addonKey", addonKey)
      putOpt("provider", provider)
      put("outcome", "failure")
      put("errorCategory", errorCategory)
      putOpt("errorCode", errorCode)
      durationMs?.let { put("durationMs", it.coerceIn(0L, Int.MAX_VALUE.toLong())) }
      put("metadata", JSONObject().put("sourcesTried", sourcesTried))
    }
  }

  fun sessionStarted() {
    track(SESSION_STARTED) {}
  }

  // ── Internals ───────────────────────────────────────────────────────────────

  private fun track(type: String, build: JSONObject.() -> Unit) {
    if (!enabled || client == null) return

    runCatching {
      val event = JSONObject()
        .put("type", type)
        .put("occurredAt", Instant.now().toString())
      event.build()

      scope.launch {
        runCatching {
          val shouldFlush = mutex.withLock {
            // Bounded: a device that cannot reach the backend drops the oldest events rather
            // than growing until the process is killed.
            if (queue.size >= MAX_QUEUE) queue.removeFirstOrNull()
            queue.addLast(event)
            queue.size >= FLUSH_AT
          }
          if (shouldFlush) flush()
        }
      }
    }
  }

  /** Sends whatever is queued. Safe to call from anywhere; never throws. */
  fun flush() {
    val apiClient = client ?: return
    if (!enabled) return

    scope.launch {
      runCatching {
        val batch = mutex.withLock {
          if (queue.isEmpty()) return@launch
          val take = minOf(queue.size, MAX_BATCH)
          val items = ArrayList<JSONObject>(take)
          repeat(take) { queue.removeFirstOrNull()?.let(items::add) }
          items
        }
        if (batch.isEmpty()) return@launch

        val array = JSONArray().apply { batch.forEach { put(it) } }
        val sent = apiClient.sendTelemetry(sessionProvider?.invoke(), array)

        if (!sent) {
          // Put them back at the front so ordering survives a transient outage, but only up to
          // the cap — a permanently unreachable backend must not accumulate.
          mutex.withLock {
            batch.asReversed().forEach { event ->
              if (queue.size < MAX_QUEUE) queue.addFirst(event)
            }
          }
        }
      }
    }
  }

  /** The catalogue says `series`; the backend's analytics group on `movie`/`series`. */
  private fun normaliseMediaType(value: String?): String? = when (value?.lowercase()) {
    null, "" -> null
    "tv", "show", "series" -> "series"
    "movie", "film" -> "movie"
    else -> value.lowercase()
  }

  private fun JSONObject.putOpt(key: String, value: String?) {
    if (!value.isNullOrBlank()) put(key, value)
  }
}

/**
 * Maps a playback failure onto the backend's error taxonomy.
 *
 * Conservative on purpose: a wrong category sends an operator looking in the wrong place, so
 * anything not clearly recognisable is reported as `unknown` rather than guessed at.
 */
fun classifyPlaybackFailure(message: String?): String {
  val text = message?.lowercase().orEmpty()
  return when {
    text.isBlank() -> Telemetry.CATEGORY_UNKNOWN
    "timeout" in text || "timed out" in text -> Telemetry.CATEGORY_TIMEOUT
    "unable to resolve host" in text || "failed to connect" in text ||
      "network" in text || "unreachable" in text -> Telemetry.CATEGORY_NETWORK
    "no seeders" in text || "not cached" in text || "debrid" in text ||
      "resolve" in text -> Telemetry.CATEGORY_RESOLVER
    else -> Telemetry.CATEGORY_PLAYBACK
  }
}
