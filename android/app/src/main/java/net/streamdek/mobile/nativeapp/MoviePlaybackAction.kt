package net.streamdek.mobile.nativeapp

internal enum class MoviePlaybackAction { Play, Resume, PlayAgain }

/** Current progress wins over historical watched state during a rewatch. */
internal fun moviePlaybackAction(
  watched: Boolean,
  progressPercent: Double = 0.0,
  positionSec: Double = 0.0,
  completed: Boolean = false,
  unwatched: Boolean = false,
): MoviePlaybackAction = when {
  unwatched -> MoviePlaybackAction.Play
  completed || progressPercent >= 95.0 -> MoviePlaybackAction.PlayAgain
  progressPercent > 0.0 || positionSec > 0.0 -> MoviePlaybackAction.Resume
  watched -> MoviePlaybackAction.PlayAgain
  else -> MoviePlaybackAction.Play
}
