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
 * The Settings pages' own icons, drawn as one family with [StreamDekNavIcons] and
 * [StreamDekPlayerIcons], so the mark beside a setting reads as the same hand as the tab that
 * opened the page and the control it changes in the player.
 *
 * The same rules as those sets: a 24-unit grid with the artwork inside roughly 3 to 21, a 1.9
 * round-capped, round-joined outline, a tonal fill inside any closed shape, and one solid detail
 * where the shape has one (a plus, an eyelet, a small window).
 *
 * No colour is baked in. Every path is one ink, replaced by the caller's `Icon(tint = ...)`, and
 * the lighter parts are alpha on that ink, which a tint preserves. Settings tints each one with
 * its row's accent.
 *
 * Only drawings that Settings needs and the other two sets do not have live here. Where a row is
 * about something the app already draws - subtitles, audio, downloads, Home - it borrows that
 * drawing, so one idea keeps one picture across the app.
 */
internal object StreamDekSettingsIcons {
  private val Ink = SolidColor(Color.Black)

  /** Account: a head and shoulders, in outline over a tonal fill. */
  val Account: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsAccount", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(5f, 19.6f)
        curveTo(5f, 16f, 8f, 14.4f, 12f, 14.4f)
        curveTo(16f, 14.4f, 19f, 16f, 19f, 19.6f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 19.6f)
        curveTo(5f, 16f, 8f, 14.4f, 12f, 14.4f)
        curveTo(16f, 14.4f, 19f, 16f, 19f, 19.6f)
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 4.9f)
        curveTo(13.93f, 4.9f, 15.5f, 6.47f, 15.5f, 8.4f)
        curveTo(15.5f, 10.33f, 13.93f, 11.9f, 12f, 11.9f)
        curveTo(10.07f, 11.9f, 8.5f, 10.33f, 8.5f, 8.4f)
        curveTo(8.5f, 6.47f, 10.07f, 4.9f, 12f, 4.9f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 4.9f)
        curveTo(13.93f, 4.9f, 15.5f, 6.47f, 15.5f, 8.4f)
        curveTo(15.5f, 10.33f, 13.93f, 11.9f, 12f, 11.9f)
        curveTo(10.07f, 11.9f, 8.5f, 10.33f, 8.5f, 8.4f)
        curveTo(8.5f, 6.47f, 10.07f, 4.9f, 12f, 4.9f)
        close()
      }
    }.build()
  }

  /** Appearance: a painter's palette in outline over a tonal fill, its colours solid. */
  val Appearance: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsAppearance", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 3.8f)
        curveTo(16.7f, 3.8f, 20.3f, 7f, 20.3f, 11f)
        curveTo(20.3f, 13.6f, 18.4f, 15f, 16.2f, 15f)
        lineTo(14.6f, 15f)
        curveTo(13.4f, 15f, 12.7f, 16f, 13.1f, 17.1f)
        curveTo(13.6f, 18.6f, 13f, 20.2f, 11.4f, 20.2f)
        curveTo(7f, 20.2f, 3.7f, 16.6f, 3.7f, 12f)
        curveTo(3.7f, 7.4f, 7.4f, 3.8f, 12f, 3.8f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3.8f)
        curveTo(16.7f, 3.8f, 20.3f, 7f, 20.3f, 11f)
        curveTo(20.3f, 13.6f, 18.4f, 15f, 16.2f, 15f)
        lineTo(14.6f, 15f)
        curveTo(13.4f, 15f, 12.7f, 16f, 13.1f, 17.1f)
        curveTo(13.6f, 18.6f, 13f, 20.2f, 11.4f, 20.2f)
        curveTo(7f, 20.2f, 3.7f, 16.6f, 3.7f, 12f)
        curveTo(3.7f, 7.4f, 7.4f, 3.8f, 12f, 3.8f)
        close()
      }
      path(fill = Ink) {
        moveTo(8.1f, 10.15f)
        curveTo(8.79f, 10.15f, 9.35f, 10.71f, 9.35f, 11.4f)
        curveTo(9.35f, 12.09f, 8.79f, 12.65f, 8.1f, 12.65f)
        curveTo(7.41f, 12.65f, 6.85f, 12.09f, 6.85f, 11.4f)
        curveTo(6.85f, 10.71f, 7.41f, 10.15f, 8.1f, 10.15f)
        close()
      }
      path(fill = Ink) {
        moveTo(11.2f, 6.65f)
        curveTo(11.89f, 6.65f, 12.45f, 7.21f, 12.45f, 7.9f)
        curveTo(12.45f, 8.59f, 11.89f, 9.15f, 11.2f, 9.15f)
        curveTo(10.51f, 9.15f, 9.95f, 8.59f, 9.95f, 7.9f)
        curveTo(9.95f, 7.21f, 10.51f, 6.65f, 11.2f, 6.65f)
        close()
      }
      path(fill = Ink) {
        moveTo(15.6f, 7.95f)
        curveTo(16.29f, 7.95f, 16.85f, 8.51f, 16.85f, 9.2f)
        curveTo(16.85f, 9.89f, 16.29f, 10.45f, 15.6f, 10.45f)
        curveTo(14.91f, 10.45f, 14.35f, 9.89f, 14.35f, 9.2f)
        curveTo(14.35f, 8.51f, 14.91f, 7.95f, 15.6f, 7.95f)
        close()
      }
    }.build()
  }

  /** Streams and quality: three sliders, their knobs solid. */
  val Sliders: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsSliders", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeAlpha = 0.55f, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.5f, 7f)
        lineTo(19.5f, 7f)
        moveTo(4.5f, 12f)
        lineTo(19.5f, 12f)
        moveTo(4.5f, 17f)
        lineTo(19.5f, 17f)
      }
      path(fill = Ink) {
        moveTo(9f, 4.8f)
        curveTo(10.22f, 4.8f, 11.2f, 5.78f, 11.2f, 7f)
        curveTo(11.2f, 8.22f, 10.22f, 9.2f, 9f, 9.2f)
        curveTo(7.78f, 9.2f, 6.8f, 8.22f, 6.8f, 7f)
        curveTo(6.8f, 5.78f, 7.78f, 4.8f, 9f, 4.8f)
        close()
      }
      path(fill = Ink) {
        moveTo(15.2f, 9.8f)
        curveTo(16.42f, 9.8f, 17.4f, 10.78f, 17.4f, 12f)
        curveTo(17.4f, 13.22f, 16.42f, 14.2f, 15.2f, 14.2f)
        curveTo(13.98f, 14.2f, 13f, 13.22f, 13f, 12f)
        curveTo(13f, 10.78f, 13.98f, 9.8f, 15.2f, 9.8f)
        close()
      }
      path(fill = Ink) {
        moveTo(8f, 14.8f)
        curveTo(9.22f, 14.8f, 10.2f, 15.78f, 10.2f, 17f)
        curveTo(10.2f, 18.22f, 9.22f, 19.2f, 8f, 19.2f)
        curveTo(6.78f, 19.2f, 5.8f, 18.22f, 5.8f, 17f)
        curveTo(5.8f, 15.78f, 6.78f, 14.8f, 8f, 14.8f)
        close()
      }
    }.build()
  }

  /** Keys for content services: a key, its bow in outline over a tonal fill. */
  val Key: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsKey", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(8f, 8f)
        curveTo(10.21f, 8f, 12f, 9.79f, 12f, 12f)
        curveTo(12f, 14.21f, 10.21f, 16f, 8f, 16f)
        curveTo(5.79f, 16f, 4f, 14.21f, 4f, 12f)
        curveTo(4f, 9.79f, 5.79f, 8f, 8f, 8f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8f, 8f)
        curveTo(10.21f, 8f, 12f, 9.79f, 12f, 12f)
        curveTo(12f, 14.21f, 10.21f, 16f, 8f, 16f)
        curveTo(5.79f, 16f, 4f, 14.21f, 4f, 12f)
        curveTo(4f, 9.79f, 5.79f, 8f, 8f, 8f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 12f)
        lineTo(20.2f, 12f)
        moveTo(16.6f, 12f)
        lineTo(16.6f, 15.2f)
        moveTo(20.2f, 12f)
        lineTo(20.2f, 14.6f)
      }
    }.build()
  }

  /** Sync services: two arrows chasing each other round. */
  val Sync: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsSync", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 11.2f)
        curveTo(5.6f, 7.3f, 9.1f, 5f, 12.4f, 5f)
        curveTo(15f, 5f, 17.2f, 6.3f, 18.6f, 8.4f)
        moveTo(18.9f, 4.5f)
        lineTo(18.9f, 8.6f)
        lineTo(14.8f, 8.6f)
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(19f, 12.8f)
        curveTo(18.4f, 16.7f, 14.9f, 19f, 11.6f, 19f)
        curveTo(9f, 19f, 6.8f, 17.7f, 5.4f, 15.6f)
        moveTo(5.1f, 19.5f)
        lineTo(5.1f, 15.4f)
        lineTo(9.2f, 15.4f)
      }
    }.build()
  }

  /** Network: a globe in outline over a tonal fill. */
  val Network: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsNetwork", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 3.8f)
        curveTo(16.53f, 3.8f, 20.2f, 7.47f, 20.2f, 12f)
        curveTo(20.2f, 16.53f, 16.53f, 20.2f, 12f, 20.2f)
        curveTo(7.47f, 20.2f, 3.8f, 16.53f, 3.8f, 12f)
        curveTo(3.8f, 7.47f, 7.47f, 3.8f, 12f, 3.8f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3.8f)
        curveTo(16.53f, 3.8f, 20.2f, 7.47f, 20.2f, 12f)
        curveTo(20.2f, 16.53f, 16.53f, 20.2f, 12f, 20.2f)
        curveTo(7.47f, 20.2f, 3.8f, 16.53f, 3.8f, 12f)
        curveTo(3.8f, 7.47f, 7.47f, 3.8f, 12f, 3.8f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(3.8f, 12f)
        lineTo(20.2f, 12f)
        moveTo(12f, 3.8f)
        curveTo(8.2f, 7f, 8.2f, 17f, 12f, 20.2f)
        curveTo(15.8f, 17f, 15.8f, 7f, 12f, 3.8f)
        close()
      }
    }.build()
  }

  /** Shown: an eye in outline over a tonal fill, its pupil solid. */
  val Eye: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsEye", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(2.8f, 12f)
        curveTo(5.5f, 7.2f, 9f, 6f, 12f, 6f)
        curveTo(15f, 6f, 18.5f, 7.2f, 21.2f, 12f)
        curveTo(18.5f, 16.8f, 15f, 18f, 12f, 18f)
        curveTo(9f, 18f, 5.5f, 16.8f, 2.8f, 12f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(2.8f, 12f)
        curveTo(5.5f, 7.2f, 9f, 6f, 12f, 6f)
        curveTo(15f, 6f, 18.5f, 7.2f, 21.2f, 12f)
        curveTo(18.5f, 16.8f, 15f, 18f, 12f, 18f)
        curveTo(9f, 18f, 5.5f, 16.8f, 2.8f, 12f)
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

  /** Hidden: the same eye dimmed, with a stroke through it. */
  val EyeOff: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsEyeOff", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.12f) {
        moveTo(2.8f, 12f)
        curveTo(5.5f, 7.2f, 9f, 6f, 12f, 6f)
        curveTo(15f, 6f, 18.5f, 7.2f, 21.2f, 12f)
        curveTo(18.5f, 16.8f, 15f, 18f, 12f, 18f)
        curveTo(9f, 18f, 5.5f, 16.8f, 2.8f, 12f)
        close()
      }
      path(stroke = Ink, strokeAlpha = 0.55f, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(2.8f, 12f)
        curveTo(5.5f, 7.2f, 9f, 6f, 12f, 6f)
        curveTo(15f, 6f, 18.5f, 7.2f, 21.2f, 12f)
        curveTo(18.5f, 16.8f, 15f, 18f, 12f, 18f)
        curveTo(9f, 18f, 5.5f, 16.8f, 2.8f, 12f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 4.6f)
        lineTo(19f, 19.4f)
      }
    }.build()
  }

  /** Add-ons and plugins: a tile in outline over a tonal fill, its plus solid. */
  val Addon: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsAddon", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(8f, 4f)
        lineTo(16f, 4f)
        quadTo(20f, 4f, 20f, 8f)
        lineTo(20f, 16f)
        quadTo(20f, 20f, 16f, 20f)
        lineTo(8f, 20f)
        quadTo(4f, 20f, 4f, 16f)
        lineTo(4f, 8f)
        quadTo(4f, 4f, 8f, 4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(8f, 4f)
        lineTo(16f, 4f)
        quadTo(20f, 4f, 20f, 8f)
        lineTo(20f, 16f)
        quadTo(20f, 20f, 16f, 20f)
        lineTo(8f, 20f)
        quadTo(4f, 20f, 4f, 16f)
        lineTo(4f, 8f)
        quadTo(4f, 4f, 8f, 4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 8.4f)
        lineTo(12f, 15.6f)
        moveTo(8.4f, 12f)
        lineTo(15.6f, 12f)
      }
    }.build()
  }

  /** Premium services and backup: a cloud in outline over a tonal fill. */
  val Cloud: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsCloud", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(7.4f, 18.6f)
        curveTo(4.9f, 18.6f, 3.2f, 16.9f, 3.2f, 14.7f)
        curveTo(3.2f, 12.7f, 4.6f, 11.1f, 6.6f, 10.8f)
        curveTo(7f, 7.8f, 9.4f, 5.6f, 12.4f, 5.6f)
        curveTo(15.2f, 5.6f, 17.5f, 7.5f, 18.1f, 10.1f)
        curveTo(19.8f, 10.6f, 20.9f, 12.2f, 20.9f, 14.1f)
        curveTo(20.9f, 16.6f, 19f, 18.6f, 16.6f, 18.6f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7.4f, 18.6f)
        curveTo(4.9f, 18.6f, 3.2f, 16.9f, 3.2f, 14.7f)
        curveTo(3.2f, 12.7f, 4.6f, 11.1f, 6.6f, 10.8f)
        curveTo(7f, 7.8f, 9.4f, 5.6f, 12.4f, 5.6f)
        curveTo(15.2f, 5.6f, 17.5f, 7.5f, 18.1f, 10.1f)
        curveTo(19.8f, 10.6f, 20.9f, 12.2f, 20.9f, 14.1f)
        curveTo(20.9f, 16.6f, 19f, 18.6f, 16.6f, 18.6f)
        close()
      }
    }.build()
  }

  /** A link, a code or an address: two links of a chain. */
  val Link: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsLink", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.3f, 13.7f)
        curveTo(8.6f, 12f, 8.6f, 9.4f, 10.3f, 7.7f)
        lineTo(12.6f, 5.4f)
        curveTo(14.3f, 3.7f, 16.9f, 3.7f, 18.6f, 5.4f)
        curveTo(20.3f, 7.1f, 20.3f, 9.7f, 18.6f, 11.4f)
        lineTo(17.3f, 12.7f)
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(13.7f, 10.3f)
        curveTo(15.4f, 12f, 15.4f, 14.6f, 13.7f, 16.3f)
        lineTo(11.4f, 18.6f)
        curveTo(9.7f, 20.3f, 7.1f, 20.3f, 5.4f, 18.6f)
        curveTo(3.7f, 16.9f, 3.7f, 14.3f, 5.4f, 12.6f)
        lineTo(6.7f, 11.3f)
      }
    }.build()
  }

  /** The floating player: a screen in outline over a tonal fill, the small window solid. */
  val Pip: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsPip", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
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
      path(fill = Ink) {
        moveTo(12.8f, 11.2f)
        lineTo(17f, 11.2f)
        quadTo(18.2f, 11.2f, 18.2f, 12.4f)
        lineTo(18.2f, 15f)
        quadTo(18.2f, 16.2f, 17f, 16.2f)
        lineTo(12.8f, 16.2f)
        quadTo(11.6f, 16.2f, 11.6f, 15f)
        lineTo(11.6f, 12.4f)
        quadTo(11.6f, 11.2f, 12.8f, 11.2f)
        close()
      }
    }.build()
  }

  /** Next episode: play, against a bar. */
  val Next: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsNext", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(5f, 7.12f)
        quadTo(5f, 5.14f, 7.12f, 6.52f)
        lineTo(14.54f, 10.94f)
        quadTo(16.02f, 12f, 14.54f, 13.06f)
        lineTo(7.12f, 17.48f)
        quadTo(5f, 18.86f, 5f, 16.88f)
        close()
      }
      path(fill = Ink) {
        moveTo(17.85f, 5.6f)
        lineTo(17.85f, 5.6f)
        quadTo(19.2f, 5.6f, 19.2f, 6.95f)
        lineTo(19.2f, 17.05f)
        quadTo(19.2f, 18.4f, 17.85f, 18.4f)
        lineTo(17.85f, 18.4f)
        quadTo(16.5f, 18.4f, 16.5f, 17.05f)
        lineTo(16.5f, 6.95f)
        quadTo(16.5f, 5.6f, 17.85f, 5.6f)
        close()
      }
    }.build()
  }

  /** StreamDek Fuse and peer-to-peer: three sources joined at a solid hub. */
  val Hub: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsHub", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 9.6f)
        lineTo(12f, 7.6f)
        moveTo(14.51f, 13.95f)
        lineTo(16.24f, 14.95f)
        moveTo(9.49f, 13.95f)
        lineTo(7.76f, 14.95f)
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(12f, 3f)
        curveTo(13.22f, 3f, 14.2f, 3.98f, 14.2f, 5.2f)
        curveTo(14.2f, 6.42f, 13.22f, 7.4f, 12f, 7.4f)
        curveTo(10.78f, 7.4f, 9.8f, 6.42f, 9.8f, 5.2f)
        curveTo(9.8f, 3.98f, 10.78f, 3f, 12f, 3f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3f)
        curveTo(13.22f, 3f, 14.2f, 3.98f, 14.2f, 5.2f)
        curveTo(14.2f, 6.42f, 13.22f, 7.4f, 12f, 7.4f)
        curveTo(10.78f, 7.4f, 9.8f, 6.42f, 9.8f, 5.2f)
        curveTo(9.8f, 3.98f, 10.78f, 3f, 12f, 3f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(18.32f, 13.95f)
        curveTo(19.54f, 13.95f, 20.52f, 14.93f, 20.52f, 16.15f)
        curveTo(20.52f, 17.37f, 19.54f, 18.35f, 18.32f, 18.35f)
        curveTo(17.11f, 18.35f, 16.12f, 17.37f, 16.12f, 16.15f)
        curveTo(16.12f, 14.93f, 17.11f, 13.95f, 18.32f, 13.95f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(18.32f, 13.95f)
        curveTo(19.54f, 13.95f, 20.52f, 14.93f, 20.52f, 16.15f)
        curveTo(20.52f, 17.37f, 19.54f, 18.35f, 18.32f, 18.35f)
        curveTo(17.11f, 18.35f, 16.12f, 17.37f, 16.12f, 16.15f)
        curveTo(16.12f, 14.93f, 17.11f, 13.95f, 18.32f, 13.95f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(5.68f, 13.95f)
        curveTo(6.89f, 13.95f, 7.88f, 14.93f, 7.88f, 16.15f)
        curveTo(7.88f, 17.37f, 6.89f, 18.35f, 5.68f, 18.35f)
        curveTo(4.46f, 18.35f, 3.48f, 17.37f, 3.48f, 16.15f)
        curveTo(3.48f, 14.93f, 4.46f, 13.95f, 5.68f, 13.95f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5.68f, 13.95f)
        curveTo(6.89f, 13.95f, 7.88f, 14.93f, 7.88f, 16.15f)
        curveTo(7.88f, 17.37f, 6.89f, 18.35f, 5.68f, 18.35f)
        curveTo(4.46f, 18.35f, 3.48f, 17.37f, 3.48f, 16.15f)
        curveTo(3.48f, 14.93f, 4.46f, 13.95f, 5.68f, 13.95f)
        close()
      }
      path(fill = Ink) {
        moveTo(12f, 9.8f)
        curveTo(13.49f, 9.8f, 14.7f, 11.01f, 14.7f, 12.5f)
        curveTo(14.7f, 13.99f, 13.49f, 15.2f, 12f, 15.2f)
        curveTo(10.51f, 15.2f, 9.3f, 13.99f, 9.3f, 12.5f)
        curveTo(9.3f, 11.01f, 10.51f, 9.8f, 12f, 9.8f)
        close()
      }
    }.build()
  }

  /** Live TV and connecting to a television: a set with its aerial, its play mark solid. */
  val Tv: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsTv", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
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

  /** Text styling: a capital A on a baseline. */
  val Text: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsText", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.2f, 17f)
        lineTo(12f, 4.6f)
        lineTo(17.8f, 17f)
        moveTo(8.3f, 12.6f)
        lineTo(15.7f, 12.6f)
      }
      path(stroke = Ink, strokeAlpha = 0.55f, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 20.4f)
        lineTo(19f, 20.4f)
      }
    }.build()
  }

  /** Layouts and rows: four tiles, the first one solid. */
  val Grid: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsGrid", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink) {
        moveTo(5.9f, 4f)
        lineTo(8.7f, 4f)
        quadTo(10.6f, 4f, 10.6f, 5.9f)
        lineTo(10.6f, 8.7f)
        quadTo(10.6f, 10.6f, 8.7f, 10.6f)
        lineTo(5.9f, 10.6f)
        quadTo(4f, 10.6f, 4f, 8.7f)
        lineTo(4f, 5.9f)
        quadTo(4f, 4f, 5.9f, 4f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(15.3f, 4f)
        lineTo(18.1f, 4f)
        quadTo(20f, 4f, 20f, 5.9f)
        lineTo(20f, 8.7f)
        quadTo(20f, 10.6f, 18.1f, 10.6f)
        lineTo(15.3f, 10.6f)
        quadTo(13.4f, 10.6f, 13.4f, 8.7f)
        lineTo(13.4f, 5.9f)
        quadTo(13.4f, 4f, 15.3f, 4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15.3f, 4f)
        lineTo(18.1f, 4f)
        quadTo(20f, 4f, 20f, 5.9f)
        lineTo(20f, 8.7f)
        quadTo(20f, 10.6f, 18.1f, 10.6f)
        lineTo(15.3f, 10.6f)
        quadTo(13.4f, 10.6f, 13.4f, 8.7f)
        lineTo(13.4f, 5.9f)
        quadTo(13.4f, 4f, 15.3f, 4f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(5.9f, 13.4f)
        lineTo(8.7f, 13.4f)
        quadTo(10.6f, 13.4f, 10.6f, 15.3f)
        lineTo(10.6f, 18.1f)
        quadTo(10.6f, 20f, 8.7f, 20f)
        lineTo(5.9f, 20f)
        quadTo(4f, 20f, 4f, 18.1f)
        lineTo(4f, 15.3f)
        quadTo(4f, 13.4f, 5.9f, 13.4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5.9f, 13.4f)
        lineTo(8.7f, 13.4f)
        quadTo(10.6f, 13.4f, 10.6f, 15.3f)
        lineTo(10.6f, 18.1f)
        quadTo(10.6f, 20f, 8.7f, 20f)
        lineTo(5.9f, 20f)
        quadTo(4f, 20f, 4f, 18.1f)
        lineTo(4f, 15.3f)
        quadTo(4f, 13.4f, 5.9f, 13.4f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(15.3f, 13.4f)
        lineTo(18.1f, 13.4f)
        quadTo(20f, 13.4f, 20f, 15.3f)
        lineTo(20f, 18.1f)
        quadTo(20f, 20f, 18.1f, 20f)
        lineTo(15.3f, 20f)
        quadTo(13.4f, 20f, 13.4f, 18.1f)
        lineTo(13.4f, 15.3f)
        quadTo(13.4f, 13.4f, 15.3f, 13.4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(15.3f, 13.4f)
        lineTo(18.1f, 13.4f)
        quadTo(20f, 13.4f, 20f, 15.3f)
        lineTo(20f, 18.1f)
        quadTo(20f, 20f, 18.1f, 20f)
        lineTo(15.3f, 20f)
        quadTo(13.4f, 20f, 13.4f, 18.1f)
        lineTo(13.4f, 15.3f)
        quadTo(13.4f, 13.4f, 15.3f, 13.4f)
        close()
      }
    }.build()
  }

  /** Badges and labels: a tag in outline over a tonal fill, its eyelet solid. */
  val Tag: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsTag", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(4f, 6.6f)
        quadTo(4f, 4f, 6.6f, 4f)
        lineTo(11.6f, 4f)
        quadTo(12.7f, 4f, 13.5f, 4.8f)
        lineTo(19.6f, 10.9f)
        quadTo(21.2f, 12.5f, 19.6f, 14.1f)
        lineTo(14.1f, 19.6f)
        quadTo(12.5f, 21.2f, 10.9f, 19.6f)
        lineTo(4.8f, 13.5f)
        quadTo(4f, 12.7f, 4f, 11.6f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4f, 6.6f)
        quadTo(4f, 4f, 6.6f, 4f)
        lineTo(11.6f, 4f)
        quadTo(12.7f, 4f, 13.5f, 4.8f)
        lineTo(19.6f, 10.9f)
        quadTo(21.2f, 12.5f, 19.6f, 14.1f)
        lineTo(14.1f, 19.6f)
        quadTo(12.5f, 21.2f, 10.9f, 19.6f)
        lineTo(4.8f, 13.5f)
        quadTo(4f, 12.7f, 4f, 11.6f)
        close()
      }
      path(fill = Ink) {
        moveTo(8.6f, 7.1f)
        curveTo(9.43f, 7.1f, 10.1f, 7.77f, 10.1f, 8.6f)
        curveTo(10.1f, 9.43f, 9.43f, 10.1f, 8.6f, 10.1f)
        curveTo(7.77f, 10.1f, 7.1f, 9.43f, 7.1f, 8.6f)
        curveTo(7.1f, 7.77f, 7.77f, 7.1f, 8.6f, 7.1f)
        close()
      }
    }.build()
  }

  /** Opens a further page: a chevron, mirrored in right-to-left layouts. */
  val Forward: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsForward", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = true).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(9.4f, 5.6f)
        lineTo(15.8f, 12f)
        lineTo(9.4f, 18.4f)
      }
    }.build()
  }

  /** Opens what is folded below: a chevron. */
  val ChevronDown: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsChevronDown", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5.6f, 9.2f)
        lineTo(12f, 15.6f)
        lineTo(18.4f, 9.2f)
      }
    }.build()
  }

  /** Add something new: a plus. */
  val Add: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsAdd", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 5.2f)
        lineTo(12f, 18.8f)
        moveTo(5.2f, 12f)
        lineTo(18.8f, 12f)
      }
    }.build()
  }

  /** Remove for good: a bin in outline over a tonal fill, its lid lifted off as a line. */
  val Delete: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsDelete", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.2f, 8.2f)
        lineTo(7.1f, 18.2f)
        quadTo(7.3f, 20.4f, 9.5f, 20.4f)
        lineTo(14.5f, 20.4f)
        quadTo(16.7f, 20.4f, 16.9f, 18.2f)
        lineTo(17.8f, 8.2f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.2f, 8.2f)
        lineTo(7.1f, 18.2f)
        quadTo(7.3f, 20.4f, 9.5f, 20.4f)
        lineTo(14.5f, 20.4f)
        quadTo(16.7f, 20.4f, 16.9f, 18.2f)
        lineTo(17.8f, 8.2f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4.2f, 8.2f)
        lineTo(19.8f, 8.2f)
        moveTo(9.4f, 8.2f)
        lineTo(9.9f, 5.2f)
        lineTo(14.1f, 5.2f)
        lineTo(14.6f, 8.2f)
      }
      path(stroke = Ink, strokeAlpha = 0.7f, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(10.2f, 12f)
        lineTo(10.4f, 16.6f)
        moveTo(13.8f, 12f)
        lineTo(13.6f, 16.6f)
      }
    }.build()
  }

  /** Rename or change: a pencil in outline over a tonal fill, its tip solid. */
  val Edit: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsEdit", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(14.6f, 5.2f)
        quadTo(16f, 3.8f, 17.4f, 5.2f)
        lineTo(18.8f, 6.6f)
        quadTo(20.2f, 8f, 18.8f, 9.4f)
        lineTo(9f, 19.2f)
        lineTo(4.4f, 19.6f)
        lineTo(4.8f, 15f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(14.6f, 5.2f)
        quadTo(16f, 3.8f, 17.4f, 5.2f)
        lineTo(18.8f, 6.6f)
        quadTo(20.2f, 8f, 18.8f, 9.4f)
        lineTo(9f, 19.2f)
        lineTo(4.4f, 19.6f)
        lineTo(4.8f, 15f)
        close()
      }
      path(fill = Ink) {
        moveTo(4.8f, 15f)
        lineTo(9f, 19.2f)
        lineTo(4.4f, 19.6f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(13.2f, 6.6f)
        lineTo(17.4f, 10.8f)
      }
    }.build()
  }

  /** Scan a code: four corners of a viewfinder around a solid code mark. */
  val Scan: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsScan", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(4f, 8.6f)
        lineTo(4f, 6.4f)
        quadTo(4f, 4f, 6.4f, 4f)
        lineTo(8.6f, 4f)
        moveTo(15.4f, 4f)
        lineTo(17.6f, 4f)
        quadTo(20f, 4f, 20f, 6.4f)
        lineTo(20f, 8.6f)
        moveTo(20f, 15.4f)
        lineTo(20f, 17.6f)
        quadTo(20f, 20f, 17.6f, 20f)
        lineTo(15.4f, 20f)
        moveTo(8.6f, 20f)
        lineTo(6.4f, 20f)
        quadTo(4f, 20f, 4f, 17.6f)
        lineTo(4f, 15.4f)
      }
      path(fill = Ink) {
        moveTo(9f, 8.2f)
        lineTo(10.6f, 8.2f)
        quadTo(11.4f, 8.2f, 11.4f, 9f)
        lineTo(11.4f, 10.6f)
        quadTo(11.4f, 11.4f, 10.6f, 11.4f)
        lineTo(9f, 11.4f)
        quadTo(8.2f, 11.4f, 8.2f, 10.6f)
        lineTo(8.2f, 9f)
        quadTo(8.2f, 8.2f, 9f, 8.2f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.45f) {
        moveTo(13.4f, 8.2f)
        lineTo(15f, 8.2f)
        quadTo(15.8f, 8.2f, 15.8f, 9f)
        lineTo(15.8f, 10.6f)
        quadTo(15.8f, 11.4f, 15f, 11.4f)
        lineTo(13.4f, 11.4f)
        quadTo(12.6f, 11.4f, 12.6f, 10.6f)
        lineTo(12.6f, 9f)
        quadTo(12.6f, 8.2f, 13.4f, 8.2f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.45f) {
        moveTo(9f, 12.6f)
        lineTo(10.6f, 12.6f)
        quadTo(11.4f, 12.6f, 11.4f, 13.4f)
        lineTo(11.4f, 15f)
        quadTo(11.4f, 15.8f, 10.6f, 15.8f)
        lineTo(9f, 15.8f)
        quadTo(8.2f, 15.8f, 8.2f, 15f)
        lineTo(8.2f, 13.4f)
        quadTo(8.2f, 12.6f, 9f, 12.6f)
        close()
      }
      path(fill = Ink) {
        moveTo(13.4f, 12.6f)
        lineTo(15f, 12.6f)
        quadTo(15.8f, 12.6f, 15.8f, 13.4f)
        lineTo(15.8f, 15f)
        quadTo(15.8f, 15.8f, 15f, 15.8f)
        lineTo(13.4f, 15.8f)
        quadTo(12.6f, 15.8f, 12.6f, 15f)
        lineTo(12.6f, 13.4f)
        quadTo(12.6f, 12.6f, 13.4f, 12.6f)
        close()
      }
    }.build()
  }

  /** Hold to drag into a new order: two bars. */
  val DragHandle: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsDragHandle", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(stroke = Ink, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5.4f, 9.4f)
        lineTo(18.6f, 9.4f)
        moveTo(5.4f, 14.6f)
        lineTo(18.6f, 14.6f)
      }
    }.build()
  }

  /** Storage and file sizes: two drives stacked, their lights solid. */
  val Storage: ImageVector by lazy {
    ImageVector.Builder(name = "StreamDekSettingsStorage", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.4f, 4.4f)
        lineTo(17.6f, 4.4f)
        quadTo(20f, 4.4f, 20f, 6.8f)
        lineTo(20f, 8.2f)
        quadTo(20f, 10.6f, 17.6f, 10.6f)
        lineTo(6.4f, 10.6f)
        quadTo(4f, 10.6f, 4f, 8.2f)
        lineTo(4f, 6.8f)
        quadTo(4f, 4.4f, 6.4f, 4.4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.4f, 4.4f)
        lineTo(17.6f, 4.4f)
        quadTo(20f, 4.4f, 20f, 6.8f)
        lineTo(20f, 8.2f)
        quadTo(20f, 10.6f, 17.6f, 10.6f)
        lineTo(6.4f, 10.6f)
        quadTo(4f, 10.6f, 4f, 8.2f)
        lineTo(4f, 6.8f)
        quadTo(4f, 4.4f, 6.4f, 4.4f)
        close()
      }
      path(fill = Ink, fillAlpha = 0.2f) {
        moveTo(6.4f, 13.4f)
        lineTo(17.6f, 13.4f)
        quadTo(20f, 13.4f, 20f, 15.8f)
        lineTo(20f, 17.2f)
        quadTo(20f, 19.6f, 17.6f, 19.6f)
        lineTo(6.4f, 19.6f)
        quadTo(4f, 19.6f, 4f, 17.2f)
        lineTo(4f, 15.8f)
        quadTo(4f, 13.4f, 6.4f, 13.4f)
        close()
      }
      path(stroke = Ink, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6.4f, 13.4f)
        lineTo(17.6f, 13.4f)
        quadTo(20f, 13.4f, 20f, 15.8f)
        lineTo(20f, 17.2f)
        quadTo(20f, 19.6f, 17.6f, 19.6f)
        lineTo(6.4f, 19.6f)
        quadTo(4f, 19.6f, 4f, 17.2f)
        lineTo(4f, 15.8f)
        quadTo(4f, 13.4f, 6.4f, 13.4f)
        close()
      }
      path(fill = Ink) {
        moveTo(7.6f, 6.35f)
        curveTo(8.24f, 6.35f, 8.75f, 6.86f, 8.75f, 7.5f)
        curveTo(8.75f, 8.14f, 8.24f, 8.65f, 7.6f, 8.65f)
        curveTo(6.96f, 8.65f, 6.45f, 8.14f, 6.45f, 7.5f)
        curveTo(6.45f, 6.86f, 6.96f, 6.35f, 7.6f, 6.35f)
        close()
      }
      path(fill = Ink) {
        moveTo(7.6f, 15.35f)
        curveTo(8.24f, 15.35f, 8.75f, 15.86f, 8.75f, 16.5f)
        curveTo(8.75f, 17.14f, 8.24f, 17.65f, 7.6f, 17.65f)
        curveTo(6.96f, 17.65f, 6.45f, 17.14f, 6.45f, 16.5f)
        curveTo(6.45f, 15.86f, 6.96f, 15.35f, 7.6f, 15.35f)
        close()
      }
    }.build()
  }
}
