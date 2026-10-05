package net.streamdek.mobile.nativeapp

/*
 * Which engine plays a source, and which one takes over when the one playing cannot.
 *
 * The rules, in one place:
 *
 *  - "Auto" is Media3 first with one hand-over to mpv, as it always was.
 *  - libVLC plays when the viewer chose it - unless the source is one libVLC is known not to be
 *    able to open (see [LibVlcBlocker]), in which case nothing is gained by trying: the source
 *    goes straight to the Auto path and the viewer is told why.
 *  - When the engine that is playing turns out not to be able to play a source, the next engine
 *    that has not already failed on that source takes over. libVLC is never the one taking over:
 *    it shares FFmpeg with mpv, so it rarely rescues what mpv could not play.
 *  - An engine that has failed on a source is not tried on it again, so there are no loops.
 */

/** The engine a source starts on, given the viewer's preference and what is known about the source. */
internal fun routedPlaybackEngine(preference: String?, blocker: LibVlcBlocker?): ActivePlaybackEngine {
  val chosen = initialPlaybackEngine(preference.orEmpty())
  return if (chosen == ActivePlaybackEngine.VLC && blocker != null) ActivePlaybackEngine.Media3 else chosen
}

/**
 * The preference the automatic Media3 -> mpv hand-over is judged by. A viewer who chose libVLC and
 * is not on it - because the source was routed away, or libVLC could not play it - is on the Auto
 * path for that source, and gets all of it.
 */
internal fun effectiveEnginePreference(preference: String?, activeEngine: ActivePlaybackEngine): String =
  if (preference.equals("VLC", ignoreCase = true) && activeEngine != ActivePlaybackEngine.VLC) "Auto" else preference.orEmpty()

/**
 * The engines one source has been played on, in order, and which of them could not play it.
 *
 * Kept for one source at a time: a new source starts a new trail.
 */
internal class PlaybackEngineTrail(start: ActivePlaybackEngine) {
  private val visited = mutableListOf(start)
  private val failed = linkedSetOf<ActivePlaybackEngine>()

  val engines: List<ActivePlaybackEngine> get() = visited.toList()

  /** The engine is now playing this source - by the viewer's hand or by a hand-over. */
  fun moveTo(engine: ActivePlaybackEngine) {
    if (visited.last() != engine) visited.add(engine)
  }

  /** The engine could not play this source and must not be handed it again. */
  fun markFailed(engine: ActivePlaybackEngine) {
    failed.add(engine)
  }

  fun hasFailed(engine: ActivePlaybackEngine): Boolean = engine in failed

  /**
   * The engine to hand this source to after [from] failed on it, or null when every engine that
   * could take it has already failed.
   */
  fun nextAfterFailure(from: ActivePlaybackEngine, blocker: LibVlcBlocker? = null): ActivePlaybackEngine? {
    markFailed(from)
    val order = when {
      // Only Media3 decrypts ClearKey; there is nobody else to ask.
      blocker == LibVlcBlocker.ClearKey -> listOf(ActivePlaybackEngine.Media3)
      else -> listOf(ActivePlaybackEngine.Media3, ActivePlaybackEngine.MPV)
    }
    return order.firstOrNull { it != from && it !in failed }
  }

  /** "libVLC → ExoPlayer → mpv", for the log. */
  fun describe(): String = visited.joinToString(" → ") { it.displayName + if (it in failed) " (failed)" else "" }
}
