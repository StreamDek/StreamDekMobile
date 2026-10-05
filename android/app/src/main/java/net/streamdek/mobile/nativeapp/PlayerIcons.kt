package net.streamdek.mobile.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The player's own icons, drawn as one family with the bottom navigation's [StreamDekNavIcons].
 *
 * The same rules as that set, so a control in the player and a tab in the bar read as the same
 * hand: a 24-unit grid with the artwork inside roughly 3 to 21, a 1.9 round-capped, round-joined
 * outline, a tonal fill inside any closed shape, and one solid detail where the shape has one (a
 * keyhole, a hub, a play mark). Marks that are pure direction or action - arrows, chevrons, play,
 * pause, seek - are solid or stroke only, because there is no interior to tone.
 *
 * Pairs that say on and off (captions, the live progress bar, volume) keep one silhouette and
 * change inside it: the off drawing is the same shape dimmed, with a stroke through it.
 *
 * No colour is baked in. Every path is one ink, replaced by the caller's `Icon(tint = ...)`, and
 * the lighter parts are alpha on that ink, which a tint preserves. The player tints them white or
 * its active blue; nothing here needs to know.
 *
 * The Episodes control is not here: it uses [StreamDekNavIcons.LibraryOutline], the media tray,
 * on purpose - it is the same idea, a shelf of things to watch, at series scale.
 */
internal object StreamDekPlayerIcons {
  private val Ink = SolidColor(Color.Black)

  /** Back: a shafted arrow, mirrored in right-to-left layouts. */
  val Back: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerBack", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = true).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(19.4f, 12f)
        lineTo(5.2f, 12f)
      }
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(11f, 5.9f)
        lineTo(4.9f, 12f)
        lineTo(11f, 18.1f)
      }
    }.build()
  }

  /** Open the drawer on the trailing edge. */
  val ChevronLeft: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerChevronLeft", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(14.6f, 5.6f)
        lineTo(8.2f, 12f)
        lineTo(14.6f, 18.4f)
      }
    }.build()
  }

  /** Close the drawer on the trailing edge. */
  val ChevronRight: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerChevronRight", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.4f, 5.6f)
        lineTo(15.8f, 12f)
        lineTo(9.4f, 18.4f)
      }
    }.build()
  }

  /** The swipe-up cue over a live channel. */
  val ChevronUp: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerChevronUp", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5.8f, 15f)
        lineTo(12f, 8.8f)
        lineTo(18.2f, 15f)
      }
    }.build()
  }

  /** Close a sheet or panel. */
  val Close: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerClose", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.4f, 6.4f)
        lineTo(17.6f, 17.6f)
      }
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(17.6f, 6.4f)
        lineTo(6.4f, 17.6f)
      }
    }.build()
  }

  /** The selected row in a list of options. */
  val Check: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerCheck", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.3f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5.4f, 12.6f)
        lineTo(9.9f, 17f)
        lineTo(18.6f, 7.4f)
      }
    }.build()
  }

  /** A watched episode: a ticked disc. */
  val Watched: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerWatched", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 3.45f)
        curveTo(16.72f, 3.45f, 20.55f, 7.28f, 20.55f, 12f)
        curveTo(20.55f, 16.72f, 16.72f, 20.55f, 12f, 20.55f)
        curveTo(7.28f, 20.55f, 3.45f, 16.72f, 3.45f, 12f)
        curveTo(3.45f, 7.28f, 7.28f, 3.45f, 12f, 3.45f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3.45f)
        curveTo(16.72f, 3.45f, 20.55f, 7.28f, 20.55f, 12f)
        curveTo(20.55f, 16.72f, 16.72f, 20.55f, 12f, 20.55f)
        curveTo(7.28f, 20.55f, 3.45f, 16.72f, 3.45f, 12f)
        curveTo(3.45f, 7.28f, 7.28f, 3.45f, 12f, 3.45f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.1f, 12.3f)
        lineTo(10.9f, 15f)
        lineTo(16f, 9.4f)
      }
    }.build()
  }

  /** Play: a soft-cornered triangle, optically centred in the box. */
  val Play: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerPlay", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(7.2f, 6.23f)
        quadTo(7.2f, 3.89f, 9.72f, 5.53f)
        lineTo(18.54f, 10.75f)
        quadTo(20.3f, 12f, 18.54f, 13.25f)
        lineTo(9.72f, 18.47f)
        quadTo(7.2f, 20.11f, 7.2f, 17.77f)
        close()
      }
    }.build()
  }

  /** Pause: two round-ended bars. */
  val Pause: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerPause", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(8f, 4.8f)
        lineTo(8.7f, 4.8f)
        quadTo(10.4f, 4.8f, 10.4f, 6.5f)
        lineTo(10.4f, 17.5f)
        quadTo(10.4f, 19.2f, 8.7f, 19.2f)
        lineTo(8f, 19.2f)
        quadTo(6.3f, 19.2f, 6.3f, 17.5f)
        lineTo(6.3f, 6.5f)
        quadTo(6.3f, 4.8f, 8f, 4.8f)
        close()
      }
      path(fill = Ink) {
        moveTo(15.3f, 4.8f)
        lineTo(16f, 4.8f)
        quadTo(17.7f, 4.8f, 17.7f, 6.5f)
        lineTo(17.7f, 17.5f)
        quadTo(17.7f, 19.2f, 16f, 19.2f)
        lineTo(15.3f, 19.2f)
        quadTo(13.6f, 19.2f, 13.6f, 17.5f)
        lineTo(13.6f, 6.5f)
        quadTo(13.6f, 4.8f, 15.3f, 4.8f)
        close()
      }
    }.build()
  }

  /** Back ten seconds. */
  val Replay10: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerReplay10", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 5.4f)
        curveTo(15.38f, 5.4f, 18.35f, 7.63f, 19.3f, 10.87f)
        curveTo(20.24f, 14.12f, 18.93f, 17.59f, 16.08f, 19.41f)
        curveTo(13.23f, 21.22f, 9.53f, 20.94f, 6.99f, 18.71f)
        curveTo(4.45f, 16.49f, 3.68f, 12.85f, 5.11f, 9.79f)
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(14.7f, 2.6f)
        lineTo(12f, 5.4f)
        lineTo(14.7f, 8.2f)
      }
      path(stroke = Ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.1f, 11.6f)
        lineTo(9.4f, 10.6f)
        lineTo(9.4f, 15.6f)
      }
      path(stroke = Ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(13.65f, 10.6f)
        lineTo(13.65f, 10.6f)
        quadTo(15.4f, 10.6f, 15.4f, 12.35f)
        lineTo(15.4f, 13.85f)
        quadTo(15.4f, 15.6f, 13.65f, 15.6f)
        lineTo(13.65f, 15.6f)
        quadTo(11.9f, 15.6f, 11.9f, 13.85f)
        lineTo(11.9f, 12.35f)
        quadTo(11.9f, 10.6f, 13.65f, 10.6f)
        close()
      }
    }.build()
  }

  /** Forward ten seconds: Replay10 with its arrow mirrored and its numerals left readable. */
  val Forward10: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerForward10", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 5.4f)
        curveTo(8.62f, 5.4f, 5.65f, 7.63f, 4.7f, 10.87f)
        curveTo(3.76f, 14.12f, 5.07f, 17.59f, 7.92f, 19.41f)
        curveTo(10.77f, 21.22f, 14.47f, 20.94f, 17.01f, 18.71f)
        curveTo(19.55f, 16.49f, 20.32f, 12.85f, 18.89f, 9.79f)
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.3f, 2.6f)
        lineTo(12f, 5.4f)
        lineTo(9.3f, 8.2f)
      }
      path(stroke = Ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.1f, 11.6f)
        lineTo(9.4f, 10.6f)
        lineTo(9.4f, 15.6f)
      }
      path(stroke = Ink, strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(13.65f, 10.6f)
        lineTo(13.65f, 10.6f)
        quadTo(15.4f, 10.6f, 15.4f, 12.35f)
        lineTo(15.4f, 13.85f)
        quadTo(15.4f, 15.6f, 13.65f, 15.6f)
        lineTo(13.65f, 15.6f)
        quadTo(11.9f, 15.6f, 11.9f, 13.85f)
        lineTo(11.9f, 12.35f)
        quadTo(11.9f, 10.6f, 13.65f, 10.6f)
        close()
      }
    }.build()
  }

  /** Seeking forward, and the hold-to-speed indicator. */
  val FastForward: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerFastForward", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(3.6f, 7.86f)
        quadTo(3.6f, 6.18f, 5.24f, 7.35f)
        lineTo(10.98f, 11.1f)
        quadTo(12.13f, 12f, 10.98f, 12.9f)
        lineTo(5.24f, 16.65f)
        quadTo(3.6f, 17.82f, 3.6f, 16.14f)
        close()
      }
      path(fill = Ink) {
        moveTo(12.2f, 7.86f)
        quadTo(12.2f, 6.18f, 13.84f, 7.35f)
        lineTo(19.58f, 11.1f)
        quadTo(20.73f, 12f, 19.58f, 12.9f)
        lineTo(13.84f, 16.65f)
        quadTo(12.2f, 17.82f, 12.2f, 16.14f)
        close()
      }
    }.build()
  }

  /** Seeking backward. */
  val FastRewind: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerFastRewind", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(20.4f, 7.86f)
        quadTo(20.4f, 6.18f, 18.76f, 7.35f)
        lineTo(13.02f, 11.1f)
        quadTo(11.87f, 12f, 13.02f, 12.9f)
        lineTo(18.76f, 16.65f)
        quadTo(20.4f, 17.82f, 20.4f, 16.14f)
        close()
      }
      path(fill = Ink) {
        moveTo(11.8f, 7.86f)
        quadTo(11.8f, 6.18f, 10.16f, 7.35f)
        lineTo(4.42f, 11.1f)
        quadTo(3.27f, 12f, 4.42f, 12.9f)
        lineTo(10.16f, 16.65f)
        quadTo(11.8f, 17.82f, 11.8f, 16.14f)
        close()
      }
    }.build()
  }

  /** Controls locked. */
  val Lock: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerLock", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(8.1f, 10.5f)
        lineTo(15.9f, 10.5f)
        quadTo(18.5f, 10.5f, 18.5f, 13.1f)
        lineTo(18.5f, 17.2f)
        quadTo(18.5f, 19.8f, 15.9f, 19.8f)
        lineTo(8.1f, 19.8f)
        quadTo(5.5f, 19.8f, 5.5f, 17.2f)
        lineTo(5.5f, 13.1f)
        quadTo(5.5f, 10.5f, 8.1f, 10.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.1f, 10.5f)
        lineTo(15.9f, 10.5f)
        quadTo(18.5f, 10.5f, 18.5f, 13.1f)
        lineTo(18.5f, 17.2f)
        quadTo(18.5f, 19.8f, 15.9f, 19.8f)
        lineTo(8.1f, 19.8f)
        quadTo(5.5f, 19.8f, 5.5f, 17.2f)
        lineTo(5.5f, 13.1f)
        quadTo(5.5f, 10.5f, 8.1f, 10.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.5f, 10.5f)
        lineTo(8.5f, 8.1f)
        curveTo(8.5f, 6.17f, 10.07f, 4.6f, 12f, 4.6f)
        curveTo(13.93f, 4.6f, 15.5f, 6.17f, 15.5f, 8.1f)
        lineTo(15.5f, 10.5f)
      }
      path(fill = Ink) {
        moveTo(12f, 13.75f)
        curveTo(12.75f, 13.75f, 13.35f, 14.35f, 13.35f, 15.1f)
        curveTo(13.35f, 15.85f, 12.75f, 16.45f, 12f, 16.45f)
        curveTo(11.25f, 16.45f, 10.65f, 15.85f, 10.65f, 15.1f)
        curveTo(10.65f, 14.35f, 11.25f, 13.75f, 12f, 13.75f)
        close()
      }
    }.build()
  }

  /** Lock the controls: the same lock with its shackle swung free. */
  val LockOpen: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerLockOpen", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(8.1f, 10.5f)
        lineTo(15.9f, 10.5f)
        quadTo(18.5f, 10.5f, 18.5f, 13.1f)
        lineTo(18.5f, 17.2f)
        quadTo(18.5f, 19.8f, 15.9f, 19.8f)
        lineTo(8.1f, 19.8f)
        quadTo(5.5f, 19.8f, 5.5f, 17.2f)
        lineTo(5.5f, 13.1f)
        quadTo(5.5f, 10.5f, 8.1f, 10.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.1f, 10.5f)
        lineTo(15.9f, 10.5f)
        quadTo(18.5f, 10.5f, 18.5f, 13.1f)
        lineTo(18.5f, 17.2f)
        quadTo(18.5f, 19.8f, 15.9f, 19.8f)
        lineTo(8.1f, 19.8f)
        quadTo(5.5f, 19.8f, 5.5f, 17.2f)
        lineTo(5.5f, 13.1f)
        quadTo(5.5f, 10.5f, 8.1f, 10.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.5f, 10.5f)
        lineTo(8.5f, 8.1f)
        curveTo(8.5f, 6.46f, 9.64f, 5.04f, 11.24f, 4.68f)
        curveTo(12.84f, 4.33f, 14.48f, 5.13f, 15.17f, 6.62f)
      }
      path(fill = Ink) {
        moveTo(12f, 13.75f)
        curveTo(12.75f, 13.75f, 13.35f, 14.35f, 13.35f, 15.1f)
        curveTo(13.35f, 15.85f, 12.75f, 16.45f, 12f, 16.45f)
        curveTo(11.25f, 16.45f, 10.65f, 15.85f, 10.65f, 15.1f)
        curveTo(10.65f, 14.35f, 11.25f, 13.75f, 12f, 13.75f)
        close()
      }
    }.build()
  }

  /** Hand playback to a television. */
  val HandOff: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerHandOff", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6f, 5f)
        lineTo(18f, 5f)
        quadTo(20.5f, 5f, 20.5f, 7.5f)
        lineTo(20.5f, 14.3f)
        quadTo(20.5f, 16.8f, 18f, 16.8f)
        lineTo(6f, 16.8f)
        quadTo(3.5f, 16.8f, 3.5f, 14.3f)
        lineTo(3.5f, 7.5f)
        quadTo(3.5f, 5f, 6f, 5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6f, 5f)
        lineTo(18f, 5f)
        quadTo(20.5f, 5f, 20.5f, 7.5f)
        lineTo(20.5f, 14.3f)
        quadTo(20.5f, 16.8f, 18f, 16.8f)
        lineTo(6f, 16.8f)
        quadTo(3.5f, 16.8f, 3.5f, 14.3f)
        lineTo(3.5f, 7.5f)
        quadTo(3.5f, 5f, 6f, 5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.6f, 19.9f)
        lineTo(15.4f, 19.9f)
      }
    }.build()
  }

  /** A favourite channel. */
  val Star: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerStar", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(12f, 4.1f)
        lineTo(14.35f, 9.46f)
        lineTo(20.18f, 10.04f)
        lineTo(15.8f, 13.94f)
        lineTo(17.05f, 19.66f)
        lineTo(12f, 16.7f)
        lineTo(6.95f, 19.66f)
        lineTo(8.2f, 13.94f)
        lineTo(3.82f, 10.04f)
        lineTo(9.65f, 9.46f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 4.1f)
        lineTo(14.35f, 9.46f)
        lineTo(20.18f, 10.04f)
        lineTo(15.8f, 13.94f)
        lineTo(17.05f, 19.66f)
        lineTo(12f, 16.7f)
        lineTo(6.95f, 19.66f)
        lineTo(8.2f, 13.94f)
        lineTo(3.82f, 10.04f)
        lineTo(9.65f, 9.46f)
        close()
      }
    }.build()
  }

  /** Not yet a favourite. */
  val StarOutline: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerStarOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 4.1f)
        lineTo(14.35f, 9.46f)
        lineTo(20.18f, 10.04f)
        lineTo(15.8f, 13.94f)
        lineTo(17.05f, 19.66f)
        lineTo(12f, 16.7f)
        lineTo(6.95f, 19.66f)
        lineTo(8.2f, 13.94f)
        lineTo(3.82f, 10.04f)
        lineTo(9.65f, 9.46f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 4.1f)
        lineTo(14.35f, 9.46f)
        lineTo(20.18f, 10.04f)
        lineTo(15.8f, 13.94f)
        lineTo(17.05f, 19.66f)
        lineTo(12f, 16.7f)
        lineTo(6.95f, 19.66f)
        lineTo(8.2f, 13.94f)
        lineTo(3.82f, 10.04f)
        lineTo(9.65f, 9.46f)
        close()
      }
    }.build()
  }

  /** Switch to the full controls: chevrons moving apart. */
  val MoreControls: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerMoreControls", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7.6f, 9.4f)
        lineTo(12f, 5f)
        lineTo(16.4f, 9.4f)
      }
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7.6f, 14.6f)
        lineTo(12f, 19f)
        lineTo(16.4f, 14.6f)
      }
    }.build()
  }

  /** Switch to the minimal controls: chevrons closing together. */
  val FewerControls: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerFewerControls", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7.6f, 5f)
        lineTo(12f, 9.4f)
        lineTo(16.4f, 5f)
      }
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7.6f, 19f)
        lineTo(12f, 14.6f)
        lineTo(16.4f, 19f)
      }
    }.build()
  }

  /** Picture scale: a frame with its corners pulling outward. */
  val Zoom: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerZoom", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.1f, 5.5f)
        lineTo(17.9f, 5.5f)
        quadTo(20.5f, 5.5f, 20.5f, 8.1f)
        lineTo(20.5f, 15.9f)
        quadTo(20.5f, 18.5f, 17.9f, 18.5f)
        lineTo(6.1f, 18.5f)
        quadTo(3.5f, 18.5f, 3.5f, 15.9f)
        lineTo(3.5f, 8.1f)
        quadTo(3.5f, 5.5f, 6.1f, 5.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.1f, 5.5f)
        lineTo(17.9f, 5.5f)
        quadTo(20.5f, 5.5f, 20.5f, 8.1f)
        lineTo(20.5f, 15.9f)
        quadTo(20.5f, 18.5f, 17.9f, 18.5f)
        lineTo(6.1f, 18.5f)
        quadTo(3.5f, 18.5f, 3.5f, 15.9f)
        lineTo(3.5f, 8.1f)
        quadTo(3.5f, 5.5f, 6.1f, 5.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(13.9f, 9f)
        lineTo(16.9f, 9f)
        lineTo(16.9f, 12f)
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.1f, 15f)
        lineTo(7.1f, 15f)
        lineTo(7.1f, 12f)
      }
    }.build()
  }

  /** Playback speed: a gauge. */
  val Speed: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerSpeed", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.66f, 16.82f)
        curveTo(3.07f, 13.42f, 4.03f, 9.37f, 6.98f, 7.05f)
        curveTo(9.92f, 4.72f, 14.08f, 4.72f, 17.02f, 7.05f)
        curveTo(19.97f, 9.37f, 20.93f, 13.42f, 19.34f, 16.82f)
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 13.4f)
        lineTo(15.9f, 9.1f)
      }
      path(fill = Ink) {
        moveTo(12f, 11.7f)
        curveTo(12.94f, 11.7f, 13.7f, 12.46f, 13.7f, 13.4f)
        curveTo(13.7f, 14.34f, 12.94f, 15.1f, 12f, 15.1f)
        curveTo(11.06f, 15.1f, 10.3f, 14.34f, 10.3f, 13.4f)
        curveTo(10.3f, 12.46f, 11.06f, 11.7f, 12f, 11.7f)
        close()
      }
    }.build()
  }

  /** Subtitles: a frame carrying two lines of text. */
  val Subtitles: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerSubtitles", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.1f, 5.5f)
        lineTo(17.9f, 5.5f)
        quadTo(20.5f, 5.5f, 20.5f, 8.1f)
        lineTo(20.5f, 15.9f)
        quadTo(20.5f, 18.5f, 17.9f, 18.5f)
        lineTo(6.1f, 18.5f)
        quadTo(3.5f, 18.5f, 3.5f, 15.9f)
        lineTo(3.5f, 8.1f)
        quadTo(3.5f, 5.5f, 6.1f, 5.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.1f, 5.5f)
        lineTo(17.9f, 5.5f)
        quadTo(20.5f, 5.5f, 20.5f, 8.1f)
        lineTo(20.5f, 15.9f)
        quadTo(20.5f, 18.5f, 17.9f, 18.5f)
        lineTo(6.1f, 18.5f)
        quadTo(3.5f, 18.5f, 3.5f, 15.9f)
        lineTo(3.5f, 8.1f)
        quadTo(3.5f, 5.5f, 6.1f, 5.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7f, 11.6f)
        lineTo(9.6f, 11.6f)
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12.2f, 11.6f)
        lineTo(17f, 11.6f)
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7f, 14.9f)
        lineTo(12.6f, 14.9f)
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15.2f, 14.9f)
        lineTo(17f, 14.9f)
      }
    }.build()
  }

  /** A live channel's captions, on. */
  val Captions: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerCaptions", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.1f, 5.5f)
        lineTo(17.9f, 5.5f)
        quadTo(20.5f, 5.5f, 20.5f, 8.1f)
        lineTo(20.5f, 15.9f)
        quadTo(20.5f, 18.5f, 17.9f, 18.5f)
        lineTo(6.1f, 18.5f)
        quadTo(3.5f, 18.5f, 3.5f, 15.9f)
        lineTo(3.5f, 8.1f)
        quadTo(3.5f, 5.5f, 6.1f, 5.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.1f, 5.5f)
        lineTo(17.9f, 5.5f)
        quadTo(20.5f, 5.5f, 20.5f, 8.1f)
        lineTo(20.5f, 15.9f)
        quadTo(20.5f, 18.5f, 17.9f, 18.5f)
        lineTo(6.1f, 18.5f)
        quadTo(3.5f, 18.5f, 3.5f, 15.9f)
        lineTo(3.5f, 8.1f)
        quadTo(3.5f, 5.5f, 6.1f, 5.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.72f, 13.46f)
        curveTo(9.95f, 14.1f, 8.81f, 14.04f, 8.12f, 13.3f)
        curveTo(7.43f, 12.57f, 7.43f, 11.43f, 8.12f, 10.7f)
        curveTo(8.81f, 9.96f, 9.95f, 9.9f, 10.72f, 10.54f)
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(16.12f, 13.46f)
        curveTo(15.35f, 14.1f, 14.21f, 14.04f, 13.52f, 13.3f)
        curveTo(12.83f, 12.57f, 12.83f, 11.43f, 13.52f, 10.7f)
        curveTo(14.21f, 9.96f, 15.35f, 9.9f, 16.12f, 10.54f)
      }
    }.build()
  }

  /** A live channel's captions, off. */
  val CaptionsOff: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerCaptionsOff", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeAlpha = 0.55f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.1f, 5.5f)
        lineTo(17.9f, 5.5f)
        quadTo(20.5f, 5.5f, 20.5f, 8.1f)
        lineTo(20.5f, 15.9f)
        quadTo(20.5f, 18.5f, 17.9f, 18.5f)
        lineTo(6.1f, 18.5f)
        quadTo(3.5f, 18.5f, 3.5f, 15.9f)
        lineTo(3.5f, 8.1f)
        quadTo(3.5f, 5.5f, 6.1f, 5.5f)
        close()
      }
      path(stroke = Ink, strokeAlpha = 0.55f, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.72f, 13.46f)
        curveTo(9.95f, 14.1f, 8.81f, 14.04f, 8.12f, 13.3f)
        curveTo(7.43f, 12.57f, 7.43f, 11.43f, 8.12f, 10.7f)
        curveTo(8.81f, 9.96f, 9.95f, 9.9f, 10.72f, 10.54f)
      }
      path(stroke = Ink, strokeAlpha = 0.55f, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(16.12f, 13.46f)
        curveTo(15.35f, 14.1f, 14.21f, 14.04f, 13.52f, 13.3f)
        curveTo(12.83f, 12.57f, 12.83f, 11.43f, 13.52f, 10.7f)
        curveTo(14.21f, 9.96f, 15.35f, 9.9f, 16.12f, 10.54f)
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.6f, 4.4f)
        lineTo(19.4f, 19.6f)
      }
    }.build()
  }

  /** Audio tracks, and the volume gesture. */
  val Audio: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerAudio", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(4.6f, 9.7f)
        lineTo(7.4f, 9.7f)
        lineTo(11.3f, 6.4f)
        quadTo(12.6f, 5.4f, 12.6f, 7f)
        lineTo(12.6f, 17f)
        quadTo(12.6f, 18.6f, 11.3f, 17.6f)
        lineTo(7.4f, 14.3f)
        lineTo(4.6f, 14.3f)
        quadTo(3.6f, 14.3f, 3.6f, 13.3f)
        lineTo(3.6f, 10.7f)
        quadTo(3.6f, 9.7f, 4.6f, 9.7f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.6f, 9.7f)
        lineTo(7.4f, 9.7f)
        lineTo(11.3f, 6.4f)
        quadTo(12.6f, 5.4f, 12.6f, 7f)
        lineTo(12.6f, 17f)
        quadTo(12.6f, 18.6f, 11.3f, 17.6f)
        lineTo(7.4f, 14.3f)
        lineTo(4.6f, 14.3f)
        quadTo(3.6f, 14.3f, 3.6f, 13.3f)
        lineTo(3.6f, 10.7f)
        quadTo(3.6f, 9.7f, 4.6f, 9.7f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15.37f, 9.32f)
        curveTo(16.74f, 10.84f, 16.74f, 13.16f, 15.37f, 14.68f)
      }
      path(stroke = Ink, strokeAlpha = 0.7f, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(17.99f, 7.31f)
        curveTo(20.27f, 10.02f, 20.27f, 13.98f, 17.99f, 16.69f)
      }
    }.build()
  }

  /** Volume at zero. */
  val AudioOff: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerAudioOff", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(4.6f, 9.7f)
        lineTo(7.4f, 9.7f)
        lineTo(11.3f, 6.4f)
        quadTo(12.6f, 5.4f, 12.6f, 7f)
        lineTo(12.6f, 17f)
        quadTo(12.6f, 18.6f, 11.3f, 17.6f)
        lineTo(7.4f, 14.3f)
        lineTo(4.6f, 14.3f)
        quadTo(3.6f, 14.3f, 3.6f, 13.3f)
        lineTo(3.6f, 10.7f)
        quadTo(3.6f, 9.7f, 4.6f, 9.7f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.6f, 9.7f)
        lineTo(7.4f, 9.7f)
        lineTo(11.3f, 6.4f)
        quadTo(12.6f, 5.4f, 12.6f, 7f)
        lineTo(12.6f, 17f)
        quadTo(12.6f, 18.6f, 11.3f, 17.6f)
        lineTo(7.4f, 14.3f)
        lineTo(4.6f, 14.3f)
        quadTo(3.6f, 14.3f, 3.6f, 13.3f)
        lineTo(3.6f, 10.7f)
        quadTo(3.6f, 9.7f, 4.6f, 9.7f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(16.1f, 9.6f)
        lineTo(20.6f, 14.4f)
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(20.6f, 9.6f)
        lineTo(16.1f, 14.4f)
      }
    }.build()
  }

  /** Sources, and the card view of a list: four tiles, the first solid. */
  val Sources: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerSources", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(6.2f, 4.3f)
        lineTo(8.7f, 4.3f)
        quadTo(10.6f, 4.3f, 10.6f, 6.2f)
        lineTo(10.6f, 8.7f)
        quadTo(10.6f, 10.6f, 8.7f, 10.6f)
        lineTo(6.2f, 10.6f)
        quadTo(4.3f, 10.6f, 4.3f, 8.7f)
        lineTo(4.3f, 6.2f)
        quadTo(4.3f, 4.3f, 6.2f, 4.3f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.2f, 4.3f)
        lineTo(8.7f, 4.3f)
        quadTo(10.6f, 4.3f, 10.6f, 6.2f)
        lineTo(10.6f, 8.7f)
        quadTo(10.6f, 10.6f, 8.7f, 10.6f)
        lineTo(6.2f, 10.6f)
        quadTo(4.3f, 10.6f, 4.3f, 8.7f)
        lineTo(4.3f, 6.2f)
        quadTo(4.3f, 4.3f, 6.2f, 4.3f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(15.3f, 4.3f)
        lineTo(17.8f, 4.3f)
        quadTo(19.7f, 4.3f, 19.7f, 6.2f)
        lineTo(19.7f, 8.7f)
        quadTo(19.7f, 10.6f, 17.8f, 10.6f)
        lineTo(15.3f, 10.6f)
        quadTo(13.4f, 10.6f, 13.4f, 8.7f)
        lineTo(13.4f, 6.2f)
        quadTo(13.4f, 4.3f, 15.3f, 4.3f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15.3f, 4.3f)
        lineTo(17.8f, 4.3f)
        quadTo(19.7f, 4.3f, 19.7f, 6.2f)
        lineTo(19.7f, 8.7f)
        quadTo(19.7f, 10.6f, 17.8f, 10.6f)
        lineTo(15.3f, 10.6f)
        quadTo(13.4f, 10.6f, 13.4f, 8.7f)
        lineTo(13.4f, 6.2f)
        quadTo(13.4f, 4.3f, 15.3f, 4.3f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.2f, 13.4f)
        lineTo(8.7f, 13.4f)
        quadTo(10.6f, 13.4f, 10.6f, 15.3f)
        lineTo(10.6f, 17.8f)
        quadTo(10.6f, 19.7f, 8.7f, 19.7f)
        lineTo(6.2f, 19.7f)
        quadTo(4.3f, 19.7f, 4.3f, 17.8f)
        lineTo(4.3f, 15.3f)
        quadTo(4.3f, 13.4f, 6.2f, 13.4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.2f, 13.4f)
        lineTo(8.7f, 13.4f)
        quadTo(10.6f, 13.4f, 10.6f, 15.3f)
        lineTo(10.6f, 17.8f)
        quadTo(10.6f, 19.7f, 8.7f, 19.7f)
        lineTo(6.2f, 19.7f)
        quadTo(4.3f, 19.7f, 4.3f, 17.8f)
        lineTo(4.3f, 15.3f)
        quadTo(4.3f, 13.4f, 6.2f, 13.4f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(15.3f, 13.4f)
        lineTo(17.8f, 13.4f)
        quadTo(19.7f, 13.4f, 19.7f, 15.3f)
        lineTo(19.7f, 17.8f)
        quadTo(19.7f, 19.7f, 17.8f, 19.7f)
        lineTo(15.3f, 19.7f)
        quadTo(13.4f, 19.7f, 13.4f, 17.8f)
        lineTo(13.4f, 15.3f)
        quadTo(13.4f, 13.4f, 15.3f, 13.4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15.3f, 13.4f)
        lineTo(17.8f, 13.4f)
        quadTo(19.7f, 13.4f, 19.7f, 15.3f)
        lineTo(19.7f, 17.8f)
        quadTo(19.7f, 19.7f, 17.8f, 19.7f)
        lineTo(15.3f, 19.7f)
        quadTo(13.4f, 19.7f, 13.4f, 17.8f)
        lineTo(13.4f, 15.3f)
        quadTo(13.4f, 13.4f, 15.3f, 13.4f)
        close()
      }
    }.build()
  }

  /** The list view of a list. */
  val ListView: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerListView", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(5.3f, 5.45f)
        curveTo(6.05f, 5.45f, 6.65f, 6.05f, 6.65f, 6.8f)
        curveTo(6.65f, 7.55f, 6.05f, 8.15f, 5.3f, 8.15f)
        curveTo(4.55f, 8.15f, 3.95f, 7.55f, 3.95f, 6.8f)
        curveTo(3.95f, 6.05f, 4.55f, 5.45f, 5.3f, 5.45f)
        close()
      }
      path(fill = Ink) {
        moveTo(5.3f, 10.65f)
        curveTo(6.05f, 10.65f, 6.65f, 11.25f, 6.65f, 12f)
        curveTo(6.65f, 12.75f, 6.05f, 13.35f, 5.3f, 13.35f)
        curveTo(4.55f, 13.35f, 3.95f, 12.75f, 3.95f, 12f)
        curveTo(3.95f, 11.25f, 4.55f, 10.65f, 5.3f, 10.65f)
        close()
      }
      path(fill = Ink) {
        moveTo(5.3f, 15.85f)
        curveTo(6.05f, 15.85f, 6.65f, 16.45f, 6.65f, 17.2f)
        curveTo(6.65f, 17.95f, 6.05f, 18.55f, 5.3f, 18.55f)
        curveTo(4.55f, 18.55f, 3.95f, 17.95f, 3.95f, 17.2f)
        curveTo(3.95f, 16.45f, 4.55f, 15.85f, 5.3f, 15.85f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.6f, 6.8f)
        lineTo(19.6f, 6.8f)
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.6f, 12f)
        lineTo(19.6f, 12f)
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.6f, 17.2f)
        lineTo(19.6f, 17.2f)
      }
    }.build()
  }

  /** Player engine and tuning: three sliders. */
  val Engine: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerEngine", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeAlpha = 0.7f, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.2f, 6.8f)
        lineTo(19.8f, 6.8f)
      }
      path(stroke = Ink, strokeAlpha = 0.7f, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.2f, 12f)
        lineTo(19.8f, 12f)
      }
      path(stroke = Ink, strokeAlpha = 0.7f, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.2f, 17.2f)
        lineTo(19.8f, 17.2f)
      }
      path(fill = Ink) {
        moveTo(9f, 4.5f)
        curveTo(10.27f, 4.5f, 11.3f, 5.53f, 11.3f, 6.8f)
        curveTo(11.3f, 8.07f, 10.27f, 9.1f, 9f, 9.1f)
        curveTo(7.73f, 9.1f, 6.7f, 8.07f, 6.7f, 6.8f)
        curveTo(6.7f, 5.53f, 7.73f, 4.5f, 9f, 4.5f)
        close()
      }
      path(fill = Ink) {
        moveTo(15.2f, 9.7f)
        curveTo(16.47f, 9.7f, 17.5f, 10.73f, 17.5f, 12f)
        curveTo(17.5f, 13.27f, 16.47f, 14.3f, 15.2f, 14.3f)
        curveTo(13.93f, 14.3f, 12.9f, 13.27f, 12.9f, 12f)
        curveTo(12.9f, 10.73f, 13.93f, 9.7f, 15.2f, 9.7f)
        close()
      }
      path(fill = Ink) {
        moveTo(8f, 14.9f)
        curveTo(9.27f, 14.9f, 10.3f, 15.93f, 10.3f, 17.2f)
        curveTo(10.3f, 18.47f, 9.27f, 19.5f, 8f, 19.5f)
        curveTo(6.73f, 19.5f, 5.7f, 18.47f, 5.7f, 17.2f)
        curveTo(5.7f, 15.93f, 6.73f, 14.9f, 8f, 14.9f)
        close()
      }
    }.build()
  }

  /** Stream information. */
  val Info: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerInfo", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 3.45f)
        curveTo(16.72f, 3.45f, 20.55f, 7.28f, 20.55f, 12f)
        curveTo(20.55f, 16.72f, 16.72f, 20.55f, 12f, 20.55f)
        curveTo(7.28f, 20.55f, 3.45f, 16.72f, 3.45f, 12f)
        curveTo(3.45f, 7.28f, 7.28f, 3.45f, 12f, 3.45f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3.45f)
        curveTo(16.72f, 3.45f, 20.55f, 7.28f, 20.55f, 12f)
        curveTo(20.55f, 16.72f, 16.72f, 20.55f, 12f, 20.55f)
        curveTo(7.28f, 20.55f, 3.45f, 16.72f, 3.45f, 12f)
        curveTo(3.45f, 7.28f, 7.28f, 3.45f, 12f, 3.45f)
        close()
      }
      path(fill = Ink) {
        moveTo(12f, 7.05f)
        curveTo(12.69f, 7.05f, 13.25f, 7.61f, 13.25f, 8.3f)
        curveTo(13.25f, 8.99f, 12.69f, 9.55f, 12f, 9.55f)
        curveTo(11.31f, 9.55f, 10.75f, 8.99f, 10.75f, 8.3f)
        curveTo(10.75f, 7.61f, 11.31f, 7.05f, 12f, 7.05f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 11.7f)
        lineTo(12f, 16.3f)
      }
    }.build()
  }

  /** A live channel's progress bar, shown. */
  val Progress: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerProgress", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeAlpha = 0.45f, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4f, 12f)
        lineTo(20f, 12f)
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4f, 12f)
        lineTo(10.6f, 12f)
      }
      path(fill = Ink) {
        moveTo(11.6f, 9.1f)
        curveTo(13.2f, 9.1f, 14.5f, 10.4f, 14.5f, 12f)
        curveTo(14.5f, 13.6f, 13.2f, 14.9f, 11.6f, 14.9f)
        curveTo(10f, 14.9f, 8.7f, 13.6f, 8.7f, 12f)
        curveTo(8.7f, 10.4f, 10f, 9.1f, 11.6f, 9.1f)
        close()
      }
    }.build()
  }

  /** A live channel's progress bar, hidden. */
  val ProgressOff: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerProgressOff", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeAlpha = 0.45f, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4f, 12f)
        lineTo(20f, 12f)
      }
      path(fill = Ink, fillAlpha = 0.45f) {
        moveTo(11.6f, 9.1f)
        curveTo(13.2f, 9.1f, 14.5f, 10.4f, 14.5f, 12f)
        curveTo(14.5f, 13.6f, 13.2f, 14.9f, 11.6f, 14.9f)
        curveTo(10f, 14.9f, 8.7f, 13.6f, 8.7f, 12f)
        curveTo(8.7f, 10.4f, 10f, 9.1f, 11.6f, 9.1f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5.2f, 5.2f)
        lineTo(18.8f, 18.8f)
      }
    }.build()
  }

  /** The Live / VOD badge switch: a television with a play mark. */
  val LiveBadge: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerLiveBadge", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6f, 7f)
        lineTo(18f, 7f)
        quadTo(20.5f, 7f, 20.5f, 9.5f)
        lineTo(20.5f, 16.8f)
        quadTo(20.5f, 19.3f, 18f, 19.3f)
        lineTo(6f, 19.3f)
        quadTo(3.5f, 19.3f, 3.5f, 16.8f)
        lineTo(3.5f, 9.5f)
        quadTo(3.5f, 7f, 6f, 7f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6f, 7f)
        lineTo(18f, 7f)
        quadTo(20.5f, 7f, 20.5f, 9.5f)
        lineTo(20.5f, 16.8f)
        quadTo(20.5f, 19.3f, 18f, 19.3f)
        lineTo(6f, 19.3f)
        quadTo(3.5f, 19.3f, 3.5f, 16.8f)
        lineTo(3.5f, 9.5f)
        quadTo(3.5f, 7f, 6f, 7f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.8f, 3.4f)
        lineTo(12f, 7f)
        lineTo(15.2f, 3.4f)
      }
      path(fill = Ink) {
        moveTo(10.3f, 11.13f)
        quadTo(10.3f, 10.29f, 11.22f, 10.88f)
        lineTo(14.44f, 12.75f)
        quadTo(15.08f, 13.2f, 14.44f, 13.65f)
        lineTo(11.22f, 15.52f)
        quadTo(10.3f, 16.11f, 10.3f, 15.27f)
        close()
      }
    }.build()
  }

  /** The brightness gesture. */
  val Brightness: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerBrightness", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 8.1f)
        curveTo(14.15f, 8.1f, 15.9f, 9.85f, 15.9f, 12f)
        curveTo(15.9f, 14.15f, 14.15f, 15.9f, 12f, 15.9f)
        curveTo(9.85f, 15.9f, 8.1f, 14.15f, 8.1f, 12f)
        curveTo(8.1f, 9.85f, 9.85f, 8.1f, 12f, 8.1f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 8.1f)
        curveTo(14.15f, 8.1f, 15.9f, 9.85f, 15.9f, 12f)
        curveTo(15.9f, 14.15f, 14.15f, 15.9f, 12f, 15.9f)
        curveTo(9.85f, 15.9f, 8.1f, 14.15f, 8.1f, 12f)
        curveTo(8.1f, 9.85f, 9.85f, 8.1f, 12f, 8.1f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(18.7f, 12f)
        lineTo(20.7f, 12f)
        moveTo(16.74f, 16.74f)
        lineTo(18.15f, 18.15f)
        moveTo(12f, 18.7f)
        lineTo(12f, 20.7f)
        moveTo(7.26f, 16.74f)
        lineTo(5.85f, 18.15f)
        moveTo(5.3f, 12f)
        lineTo(3.3f, 12f)
        moveTo(7.26f, 7.26f)
        lineTo(5.85f, 5.85f)
        moveTo(12f, 5.3f)
        lineTo(12f, 3.3f)
        moveTo(16.74f, 7.26f)
        lineTo(18.15f, 5.85f)
      }
    }.build()
  }

  /** Save a source for offline playback. */
  val Download: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerDownload", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 4.4f)
        lineTo(12f, 14.4f)
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7.6f, 10.4f)
        lineTo(12f, 14.8f)
        lineTo(16.4f, 10.4f)
      }
      path(stroke = Ink, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.9f, 16.4f)
        lineTo(4.9f, 17.9f)
        quadTo(4.9f, 19.6f, 6.6f, 19.6f)
        lineTo(17.4f, 19.6f)
        quadTo(19.1f, 19.6f, 19.1f, 17.9f)
        lineTo(19.1f, 16.4f)
      }
    }.build()
  }

  /** Clear every favourite. */
  val ClearAll: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekPlayerClearAll", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.4f, 7.2f)
        lineTo(7.2f, 17.8f)
        quadTo(7.35f, 19.7f, 9.2f, 19.7f)
        lineTo(14.8f, 19.7f)
        quadTo(16.65f, 19.7f, 16.8f, 17.8f)
        lineTo(17.6f, 7.2f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.4f, 7.2f)
        lineTo(7.2f, 17.8f)
        quadTo(7.35f, 19.7f, 9.2f, 19.7f)
        lineTo(14.8f, 19.7f)
        quadTo(16.65f, 19.7f, 16.8f, 17.8f)
        lineTo(17.6f, 7.2f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.6f, 7.2f)
        lineTo(19.4f, 7.2f)
      }
      path(stroke = Ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.4f, 7.2f)
        lineTo(9.4f, 5.6f)
        quadTo(9.4f, 4.4f, 10.6f, 4.4f)
        lineTo(13.4f, 4.4f)
        quadTo(14.6f, 4.4f, 14.6f, 5.6f)
        lineTo(14.6f, 7.2f)
      }
    }.build()
  }
}
