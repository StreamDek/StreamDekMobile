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
 * StreamDek's own Home, Search, Continue Watching, Watchlist, Library and Settings marks for the bottom navigation.
 *
 * Vectors on the same 24-unit grid as the Material icons beside them, with the same optical box
 * (about 3 to 21 on both axes) and a 1.9 stroke, which is the weight Material's rounded outlines
 * are drawn at. So the set sits in the row without being larger or heavier than its neighbours.
 *
 * Each destination has two drawings rather than one icon in two colours: at rest it is an outline
 * with a tonal interior and, where the shape has one, a solid detail (the door, the play mark);
 * selected, the silhouette fills and that detail is cut out of it. The outer shape never changes,
 * so nothing shifts when a tab is chosen.
 *
 * No colour is baked in. Every path is drawn in one ink and the bar's own `Icon(tint = ...)`
 * replaces it, exactly as it would for a Material icon, so light mode, dark mode and the chosen
 * theme all apply without this file knowing about any of them. The Plex and Jellyfin marks are
 * those services' own and are not part of this set. The lighter parts are alpha on that
 * same ink, which a tint preserves.
 */
internal object StreamDekNavIcons {
  private val Ink = SolidColor(Color.Black)

  /** Home, at rest: a bold outline with a tonal interior and a solid door. */
  val HomeOutline: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekHomeOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(4.5f, 12f)
        quadTo(4.5f, 10.4f, 5.71f, 9.35f)
        lineTo(10.56f, 5.15f)
        quadTo(12f, 3.9f, 13.44f, 5.15f)
        lineTo(18.29f, 9.35f)
        quadTo(19.5f, 10.4f, 19.5f, 12f)
        lineTo(19.5f, 17.4f)
        quadTo(19.5f, 20f, 16.9f, 20f)
        lineTo(7.1f, 20f)
        quadTo(4.5f, 20f, 4.5f, 17.4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.5f, 12f)
        quadTo(4.5f, 10.4f, 5.71f, 9.35f)
        lineTo(10.56f, 5.15f)
        quadTo(12f, 3.9f, 13.44f, 5.15f)
        lineTo(18.29f, 9.35f)
        quadTo(19.5f, 10.4f, 19.5f, 12f)
        lineTo(19.5f, 17.4f)
        quadTo(19.5f, 20f, 16.9f, 20f)
        lineTo(7.1f, 20f)
        quadTo(4.5f, 20f, 4.5f, 17.4f)
        close()
      }
      path(fill = Ink) {
        moveTo(9.5f, 20f)
        lineTo(9.5f, 15.6f)
        quadTo(9.5f, 13.9f, 11.2f, 13.9f)
        lineTo(12.8f, 13.9f)
        quadTo(14.5f, 13.9f, 14.5f, 15.6f)
        lineTo(14.5f, 20f)
        close()
      }
    }.build()
  }

  /** Home, selected: the same silhouette filled, with the door cut out of it. */
  val HomeFilled: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekHomeFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, pathFillType = PathFillType.EvenOdd) {
        moveTo(4.5f, 12f)
        quadTo(4.5f, 10.4f, 5.71f, 9.35f)
        lineTo(10.56f, 5.15f)
        quadTo(12f, 3.9f, 13.44f, 5.15f)
        lineTo(18.29f, 9.35f)
        quadTo(19.5f, 10.4f, 19.5f, 12f)
        lineTo(19.5f, 17.4f)
        quadTo(19.5f, 20f, 16.9f, 20f)
        lineTo(7.1f, 20f)
        quadTo(4.5f, 20f, 4.5f, 17.4f)
        close()
        moveTo(9.5f, 20f)
        lineTo(9.5f, 15.6f)
        quadTo(9.5f, 13.9f, 11.2f, 13.9f)
        lineTo(12.8f, 13.9f)
        quadTo(14.5f, 13.9f, 14.5f, 15.6f)
        lineTo(14.5f, 20f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.5f, 12f)
        quadTo(4.5f, 10.4f, 5.71f, 9.35f)
        lineTo(10.56f, 5.15f)
        quadTo(12f, 3.9f, 13.44f, 5.15f)
        lineTo(18.29f, 9.35f)
        quadTo(19.5f, 10.4f, 19.5f, 12f)
        lineTo(19.5f, 17.4f)
        quadTo(19.5f, 20f, 16.9f, 20f)
        lineTo(7.1f, 20f)
        quadTo(4.5f, 20f, 4.5f, 17.4f)
        close()
      }
    }.build()
  }

  /** Library, at rest: the media tray in outline over a tonal fill, its play mark solid. */
  val LibraryOutline: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekLibraryOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeAlpha = 0.45f, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.9f, 3.5f)
        quadTo(9.1f, 2.95f, 9.9f, 2.95f)
        lineTo(14.1f, 2.95f)
        quadTo(14.9f, 2.95f, 15.1f, 3.5f)
      }
      path(stroke = Ink, strokeAlpha = 0.7f, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.5f, 6.6f)
        quadTo(6.7f, 6f, 7.6f, 6f)
        lineTo(16.4f, 6f)
        quadTo(17.3f, 6f, 17.5f, 6.6f)
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.6f, 9.3f)
        lineTo(17.4f, 9.3f)
        quadTo(20.6f, 9.3f, 20.3f, 12.1f)
        lineTo(19.7f, 17.5f)
        quadTo(19.4f, 20f, 16.8f, 20f)
        lineTo(7.2f, 20f)
        quadTo(4.6f, 20f, 4.3f, 17.5f)
        lineTo(3.7f, 12.1f)
        quadTo(3.4f, 9.3f, 6.6f, 9.3f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.6f, 9.3f)
        lineTo(17.4f, 9.3f)
        quadTo(20.6f, 9.3f, 20.3f, 12.1f)
        lineTo(19.7f, 17.5f)
        quadTo(19.4f, 20f, 16.8f, 20f)
        lineTo(7.2f, 20f)
        quadTo(4.6f, 20f, 4.3f, 17.5f)
        lineTo(3.7f, 12.1f)
        quadTo(3.4f, 9.3f, 6.6f, 9.3f)
        close()
      }
      path(fill = Ink) {
        moveTo(10.3f, 12.75f)
        quadTo(10.3f, 11.6f, 11.3f, 12.17f)
        lineTo(14.55f, 14.05f)
        quadTo(15.4f, 14.6f, 14.55f, 15.15f)
        lineTo(11.3f, 17.03f)
        quadTo(10.3f, 17.6f, 10.3f, 16.45f)
        close()
      }
    }.build()
  }

  /** Search, at rest: a ring lens over a tonal fill, on a round-capped handle. */
  val SearchOutline: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSearchOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(10.6f, 4.5f)
        curveTo(13.97f, 4.5f, 16.7f, 7.23f, 16.7f, 10.6f)
        curveTo(16.7f, 13.97f, 13.97f, 16.7f, 10.6f, 16.7f)
        curveTo(7.23f, 16.7f, 4.5f, 13.97f, 4.5f, 10.6f)
        curveTo(4.5f, 7.23f, 7.23f, 4.5f, 10.6f, 4.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.6f, 4.5f)
        curveTo(13.97f, 4.5f, 16.7f, 7.23f, 16.7f, 10.6f)
        curveTo(16.7f, 13.97f, 13.97f, 16.7f, 10.6f, 16.7f)
        curveTo(7.23f, 16.7f, 4.5f, 13.97f, 4.5f, 10.6f)
        curveTo(4.5f, 7.23f, 7.23f, 4.5f, 10.6f, 4.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2.3f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15.3f, 15.3f)
        lineTo(19.7f, 19.7f)
      }
    }.build()
  }

  /** Search, selected: the lens filled. */
  val SearchFilled: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSearchFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(10.6f, 4.5f)
        curveTo(13.97f, 4.5f, 16.7f, 7.23f, 16.7f, 10.6f)
        curveTo(16.7f, 13.97f, 13.97f, 16.7f, 10.6f, 16.7f)
        curveTo(7.23f, 16.7f, 4.5f, 13.97f, 4.5f, 10.6f)
        curveTo(4.5f, 7.23f, 7.23f, 4.5f, 10.6f, 4.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.6f, 4.5f)
        curveTo(13.97f, 4.5f, 16.7f, 7.23f, 16.7f, 10.6f)
        curveTo(16.7f, 13.97f, 13.97f, 16.7f, 10.6f, 16.7f)
        curveTo(7.23f, 16.7f, 4.5f, 13.97f, 4.5f, 10.6f)
        curveTo(4.5f, 7.23f, 7.23f, 4.5f, 10.6f, 4.5f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2.3f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15.3f, 15.3f)
        lineTo(19.7f, 19.7f)
      }
    }.build()
  }

  /** Settings, at rest: a soft-toothed gear in outline over a tonal fill, its hub solid. */
  val SettingsOutline: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(10.8f, 5.82f)
        lineTo(10.63f, 3.81f)
        lineTo(13.37f, 3.81f)
        lineTo(13.2f, 5.82f)
        lineTo(15.52f, 6.78f)
        lineTo(16.82f, 5.24f)
        lineTo(18.76f, 7.18f)
        lineTo(17.22f, 8.48f)
        lineTo(18.18f, 10.8f)
        lineTo(20.19f, 10.63f)
        lineTo(20.19f, 13.37f)
        lineTo(18.18f, 13.2f)
        lineTo(17.22f, 15.52f)
        lineTo(18.76f, 16.82f)
        lineTo(16.82f, 18.76f)
        lineTo(15.52f, 17.22f)
        lineTo(13.2f, 18.18f)
        lineTo(13.37f, 20.19f)
        lineTo(10.63f, 20.19f)
        lineTo(10.8f, 18.18f)
        lineTo(8.48f, 17.22f)
        lineTo(7.18f, 18.76f)
        lineTo(5.24f, 16.82f)
        lineTo(6.78f, 15.52f)
        lineTo(5.82f, 13.2f)
        lineTo(3.81f, 13.37f)
        lineTo(3.81f, 10.63f)
        lineTo(5.82f, 10.8f)
        lineTo(6.78f, 8.48f)
        lineTo(5.24f, 7.18f)
        lineTo(7.18f, 5.24f)
        lineTo(8.48f, 6.78f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.8f, 5.82f)
        lineTo(10.63f, 3.81f)
        lineTo(13.37f, 3.81f)
        lineTo(13.2f, 5.82f)
        lineTo(15.52f, 6.78f)
        lineTo(16.82f, 5.24f)
        lineTo(18.76f, 7.18f)
        lineTo(17.22f, 8.48f)
        lineTo(18.18f, 10.8f)
        lineTo(20.19f, 10.63f)
        lineTo(20.19f, 13.37f)
        lineTo(18.18f, 13.2f)
        lineTo(17.22f, 15.52f)
        lineTo(18.76f, 16.82f)
        lineTo(16.82f, 18.76f)
        lineTo(15.52f, 17.22f)
        lineTo(13.2f, 18.18f)
        lineTo(13.37f, 20.19f)
        lineTo(10.63f, 20.19f)
        lineTo(10.8f, 18.18f)
        lineTo(8.48f, 17.22f)
        lineTo(7.18f, 18.76f)
        lineTo(5.24f, 16.82f)
        lineTo(6.78f, 15.52f)
        lineTo(5.82f, 13.2f)
        lineTo(3.81f, 13.37f)
        lineTo(3.81f, 10.63f)
        lineTo(5.82f, 10.8f)
        lineTo(6.78f, 8.48f)
        lineTo(5.24f, 7.18f)
        lineTo(7.18f, 5.24f)
        lineTo(8.48f, 6.78f)
        close()
      }
      path(fill = Ink) {
        moveTo(12f, 9.3f)
        curveTo(13.49f, 9.3f, 14.7f, 10.51f, 14.7f, 12f)
        curveTo(14.7f, 13.49f, 13.49f, 14.7f, 12f, 14.7f)
        curveTo(10.51f, 14.7f, 9.3f, 13.49f, 9.3f, 12f)
        curveTo(9.3f, 10.51f, 10.51f, 9.3f, 12f, 9.3f)
        close()
      }
    }.build()
  }

  /** Settings, selected: the gear filled, with the hub cut out of it. */
  val SettingsFilled: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, pathFillType = PathFillType.EvenOdd) {
        moveTo(10.8f, 5.82f)
        lineTo(10.63f, 3.81f)
        lineTo(13.37f, 3.81f)
        lineTo(13.2f, 5.82f)
        lineTo(15.52f, 6.78f)
        lineTo(16.82f, 5.24f)
        lineTo(18.76f, 7.18f)
        lineTo(17.22f, 8.48f)
        lineTo(18.18f, 10.8f)
        lineTo(20.19f, 10.63f)
        lineTo(20.19f, 13.37f)
        lineTo(18.18f, 13.2f)
        lineTo(17.22f, 15.52f)
        lineTo(18.76f, 16.82f)
        lineTo(16.82f, 18.76f)
        lineTo(15.52f, 17.22f)
        lineTo(13.2f, 18.18f)
        lineTo(13.37f, 20.19f)
        lineTo(10.63f, 20.19f)
        lineTo(10.8f, 18.18f)
        lineTo(8.48f, 17.22f)
        lineTo(7.18f, 18.76f)
        lineTo(5.24f, 16.82f)
        lineTo(6.78f, 15.52f)
        lineTo(5.82f, 13.2f)
        lineTo(3.81f, 13.37f)
        lineTo(3.81f, 10.63f)
        lineTo(5.82f, 10.8f)
        lineTo(6.78f, 8.48f)
        lineTo(5.24f, 7.18f)
        lineTo(7.18f, 5.24f)
        lineTo(8.48f, 6.78f)
        close()
        moveTo(12f, 9.3f)
        curveTo(13.49f, 9.3f, 14.7f, 10.51f, 14.7f, 12f)
        curveTo(14.7f, 13.49f, 13.49f, 14.7f, 12f, 14.7f)
        curveTo(10.51f, 14.7f, 9.3f, 13.49f, 9.3f, 12f)
        curveTo(9.3f, 10.51f, 10.51f, 9.3f, 12f, 9.3f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.8f, 5.82f)
        lineTo(10.63f, 3.81f)
        lineTo(13.37f, 3.81f)
        lineTo(13.2f, 5.82f)
        lineTo(15.52f, 6.78f)
        lineTo(16.82f, 5.24f)
        lineTo(18.76f, 7.18f)
        lineTo(17.22f, 8.48f)
        lineTo(18.18f, 10.8f)
        lineTo(20.19f, 10.63f)
        lineTo(20.19f, 13.37f)
        lineTo(18.18f, 13.2f)
        lineTo(17.22f, 15.52f)
        lineTo(18.76f, 16.82f)
        lineTo(16.82f, 18.76f)
        lineTo(15.52f, 17.22f)
        lineTo(13.2f, 18.18f)
        lineTo(13.37f, 20.19f)
        lineTo(10.63f, 20.19f)
        lineTo(10.8f, 18.18f)
        lineTo(8.48f, 17.22f)
        lineTo(7.18f, 18.76f)
        lineTo(5.24f, 16.82f)
        lineTo(6.78f, 15.52f)
        lineTo(5.82f, 13.2f)
        lineTo(3.81f, 13.37f)
        lineTo(3.81f, 10.63f)
        lineTo(5.82f, 10.8f)
        lineTo(6.78f, 8.48f)
        lineTo(5.24f, 7.18f)
        lineTo(7.18f, 5.24f)
        lineTo(8.48f, 6.78f)
        close()
      }
    }.build()
  }

  /** Continue Watching, at rest: a ring over a tonal disc, with a soft-cornered play mark. */
  val ContinueOutline: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekContinueOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 3.45f)
        curveTo(16.722f, 3.45f, 20.55f, 7.278f, 20.55f, 12f)
        curveTo(20.55f, 16.722f, 16.722f, 20.55f, 12f, 20.55f)
        curveTo(7.278f, 20.55f, 3.45f, 16.722f, 3.45f, 12f)
        curveTo(3.45f, 7.278f, 7.278f, 3.45f, 12f, 3.45f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3.45f)
        curveTo(16.722f, 3.45f, 20.55f, 7.278f, 20.55f, 12f)
        curveTo(20.55f, 16.722f, 16.722f, 20.55f, 12f, 20.55f)
        curveTo(7.278f, 20.55f, 3.45f, 16.722f, 3.45f, 12f)
        curveTo(3.45f, 7.278f, 7.278f, 3.45f, 12f, 3.45f)
        close()
      }
      path(fill = Ink) {
        moveTo(9.9f, 9.55f)
        quadTo(9.9f, 8.3f, 11f, 8.93f)
        lineTo(15f, 11.35f)
        quadTo(16f, 12f, 15f, 12.65f)
        lineTo(11f, 15.07f)
        quadTo(9.9f, 15.7f, 9.9f, 14.45f)
        close()
      }
    }.build()
  }

  /** Continue Watching, selected: the disc filled, with the play mark cut out of it. */
  val ContinueFilled: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekContinueFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 3.45f)
        curveTo(16.722f, 3.45f, 20.55f, 7.278f, 20.55f, 12f)
        curveTo(20.55f, 16.722f, 16.722f, 20.55f, 12f, 20.55f)
        curveTo(7.278f, 20.55f, 3.45f, 16.722f, 3.45f, 12f)
        curveTo(3.45f, 7.278f, 7.278f, 3.45f, 12f, 3.45f)
        close()
        moveTo(9.9f, 9.55f)
        quadTo(9.9f, 8.3f, 11f, 8.93f)
        lineTo(15f, 11.35f)
        quadTo(16f, 12f, 15f, 12.65f)
        lineTo(11f, 15.07f)
        quadTo(9.9f, 15.7f, 9.9f, 14.45f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3.45f)
        curveTo(16.722f, 3.45f, 20.55f, 7.278f, 20.55f, 12f)
        curveTo(20.55f, 16.722f, 16.722f, 20.55f, 12f, 20.55f)
        curveTo(7.278f, 20.55f, 3.45f, 16.722f, 3.45f, 12f)
        curveTo(3.45f, 7.278f, 7.278f, 3.45f, 12f, 3.45f)
        close()
      }
    }.build()
  }

  /** Watchlist, at rest: a round-shouldered bookmark in outline over a tonal fill. */
  val WatchlistOutline: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekWatchlistOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.5f, 6.5f)
        quadTo(6.5f, 3.9f, 9.1f, 3.9f)
        lineTo(14.9f, 3.9f)
        quadTo(17.5f, 3.9f, 17.5f, 6.5f)
        lineTo(17.5f, 18.7f)
        quadTo(17.5f, 20.5f, 16f, 19.5f)
        lineTo(12.8f, 17.35f)
        quadTo(12f, 16.8f, 11.2f, 17.35f)
        lineTo(8f, 19.5f)
        quadTo(6.5f, 20.5f, 6.5f, 18.7f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.5f, 6.5f)
        quadTo(6.5f, 3.9f, 9.1f, 3.9f)
        lineTo(14.9f, 3.9f)
        quadTo(17.5f, 3.9f, 17.5f, 6.5f)
        lineTo(17.5f, 18.7f)
        quadTo(17.5f, 20.5f, 16f, 19.5f)
        lineTo(12.8f, 17.35f)
        quadTo(12f, 16.8f, 11.2f, 17.35f)
        lineTo(8f, 19.5f)
        quadTo(6.5f, 20.5f, 6.5f, 18.7f)
        close()
      }
    }.build()
  }

  /** Watchlist, selected: the same bookmark, filled. */
  val WatchlistFilled: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekWatchlistFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(6.5f, 6.5f)
        quadTo(6.5f, 3.9f, 9.1f, 3.9f)
        lineTo(14.9f, 3.9f)
        quadTo(17.5f, 3.9f, 17.5f, 6.5f)
        lineTo(17.5f, 18.7f)
        quadTo(17.5f, 20.5f, 16f, 19.5f)
        lineTo(12.8f, 17.35f)
        quadTo(12f, 16.8f, 11.2f, 17.35f)
        lineTo(8f, 19.5f)
        quadTo(6.5f, 20.5f, 6.5f, 18.7f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.5f, 6.5f)
        quadTo(6.5f, 3.9f, 9.1f, 3.9f)
        lineTo(14.9f, 3.9f)
        quadTo(17.5f, 3.9f, 17.5f, 6.5f)
        lineTo(17.5f, 18.7f)
        quadTo(17.5f, 20.5f, 16f, 19.5f)
        lineTo(12.8f, 17.35f)
        quadTo(12f, 16.8f, 11.2f, 17.35f)
        lineTo(8f, 19.5f)
        quadTo(6.5f, 20.5f, 6.5f, 18.7f)
        close()
      }
    }.build()
  }

  /** Library, selected: the tray filled, with the play mark cut out of it. */
  val LibraryFilled: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekLibraryFilled", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeAlpha = 0.45f, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8.9f, 3.5f)
        quadTo(9.1f, 2.95f, 9.9f, 2.95f)
        lineTo(14.1f, 2.95f)
        quadTo(14.9f, 2.95f, 15.1f, 3.5f)
      }
      path(stroke = Ink, strokeAlpha = 0.7f, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.5f, 6.6f)
        quadTo(6.7f, 6f, 7.6f, 6f)
        lineTo(16.4f, 6f)
        quadTo(17.3f, 6f, 17.5f, 6.6f)
      }
      path(fill = Ink, pathFillType = PathFillType.EvenOdd) {
        moveTo(6.6f, 9.3f)
        lineTo(17.4f, 9.3f)
        quadTo(20.6f, 9.3f, 20.3f, 12.1f)
        lineTo(19.7f, 17.5f)
        quadTo(19.4f, 20f, 16.8f, 20f)
        lineTo(7.2f, 20f)
        quadTo(4.6f, 20f, 4.3f, 17.5f)
        lineTo(3.7f, 12.1f)
        quadTo(3.4f, 9.3f, 6.6f, 9.3f)
        close()
        moveTo(10.3f, 12.75f)
        quadTo(10.3f, 11.6f, 11.3f, 12.17f)
        lineTo(14.55f, 14.05f)
        quadTo(15.4f, 14.6f, 14.55f, 15.15f)
        lineTo(11.3f, 17.03f)
        quadTo(10.3f, 17.6f, 10.3f, 16.45f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.6f, 9.3f)
        lineTo(17.4f, 9.3f)
        quadTo(20.6f, 9.3f, 20.3f, 12.1f)
        lineTo(19.7f, 17.5f)
        quadTo(19.4f, 20f, 16.8f, 20f)
        lineTo(7.2f, 20f)
        quadTo(4.6f, 20f, 4.3f, 17.5f)
        lineTo(3.7f, 12.1f)
        quadTo(3.4f, 9.3f, 6.6f, 9.3f)
        close()
      }
    }.build()
  }
}
