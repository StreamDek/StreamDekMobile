package net.streamdek.mobile.nativeapp

import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Timing instrumentation for the journeys a viewer actually waits through.
 *
 * Deliberately not built on [Telemetry]: this has to be readable from `adb logcat` on a stick while
 * a build is being driven by hand, and it has to cost nothing when it is switched off. Telemetry
 * batches and uploads; this prints one line per phase and keeps no history.
 *
 * Line format, chosen so `logcat -s StreamDekPerf` can be parsed with `cut`:
 *
 *     PERF|<span>|<phase>|<msSinceSpanStart>|<msSincePreviousPhase>|<detail>
 *
 * A "span" is one user-visible journey (a home load, a play press). Phases inside it are the
 * moments the viewer could notice something changed. Spans are identified by name plus an
 * incrementing id, so two overlapping home loads do not interleave into one unreadable trace.
 */
object Perf {

    private const val TAG = "StreamDekPerf"

    /**
     * Off by default in release. The overhead is a string build per phase, which is nothing next to
     * a network call, but a viewer's logcat is not the place for this either.
     */
    @Volatile
    var enabled: Boolean = net.streamdek.mobile.BuildConfig.DEBUG

    private val counter = AtomicLong(0)

    /** Uptime at which the process was forked, so cold start is measured from the real beginning. */
    val processStartUptimeMs: Long by lazy { Process.getStartUptimeMillis() }

    /**
     * Phases that belong to app startup rather than to any one span. Recorded once per process:
     * a warm relaunch that re-enters the same process must not overwrite the cold numbers.
     */
    private val oneShot = ConcurrentHashMap<String, Long>()

    class Span internal constructor(val name: String, val id: Long, val startUptimeMs: Long) {
        @Volatile private var lastMs: Long = 0L
        @Volatile private var closed = false

        val elapsedMs: Long get() = SystemClock.uptimeMillis() - startUptimeMs

        /** One observable moment inside the journey. Safe to call from any thread. */
        fun mark(phase: String, detail: String? = null) {
            if (!enabled || closed) return
            val now = SystemClock.uptimeMillis() - startUptimeMs
            val delta = now - lastMs
            lastMs = now
            emit(name, id, phase, now, delta, detail)
        }

        /**
         * The journey ended. Further marks are dropped rather than logged, so a cancelled search
         * that unwinds late cannot append phases to a trace the viewer already left behind.
         */
        fun end(outcome: String = "ok", detail: String? = null) {
            if (!enabled || closed) return
            val now = SystemClock.uptimeMillis() - startUptimeMs
            val delta = now - lastMs
            closed = true
            emit(name, id, "end:$outcome", now, delta, detail)
        }
    }

    /**
     * The playback journey currently on screen.
     *
     * Held here because the press that starts it and the decoder callback that ends it are in
     * different classes with no reference to each other — the screen owns the request, the view
     * owns the player. A single slot rather than a map: only one thing is playing at a time, and a
     * new press should retire the previous trace rather than accumulate one per abandoned attempt.
     */
    @Volatile
    var playback: Span? = null
        private set

    fun beginPlayback(detail: String? = null): Span = span("playback", detail).also { playback = it }

    fun endPlayback(outcome: String, detail: String? = null) {
        playback?.end(outcome, detail)
        playback = null
    }

    fun span(name: String, detail: String? = null): Span {
        val span = Span(name, counter.incrementAndGet(), SystemClock.uptimeMillis())
        if (enabled) emit(name, span.id, "start", 0, 0, detail)
        return span
    }

    /**
     * A startup milestone, timed from process fork. Only the first call for a given phase is
     * recorded — see [oneShot].
     */
    fun startupMark(phase: String, detail: String? = null) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (oneShot.putIfAbsent(phase, now) != null) return
        val sinceStart = now - processStartUptimeMs
        emit("startup", 0, phase, sinceStart, sinceStart, detail)
    }

    /** Times one suspending call and reports it as a phase of [span]. */
    suspend fun <T> timed(span: Span, phase: String, block: suspend () -> T): T {
        if (!enabled) return block()
        val began = SystemClock.uptimeMillis()
        return try {
            block().also {
                val took = SystemClock.uptimeMillis() - began
                emit(span.name, span.id, phase, span.elapsedMs, took, "took=$took")
            }
        } catch (t: Throwable) {
            val took = SystemClock.uptimeMillis() - began
            emit(span.name, span.id, "$phase:threw", span.elapsedMs, took, t.javaClass.simpleName)
            throw t
        }
    }

    private fun emit(span: String, id: Long, phase: String, atMs: Long, deltaMs: Long, detail: String?) {
        Log.i(TAG, "PERF|$span#$id|$phase|$atMs|$deltaMs|${detail.orEmpty()}")
    }
}
