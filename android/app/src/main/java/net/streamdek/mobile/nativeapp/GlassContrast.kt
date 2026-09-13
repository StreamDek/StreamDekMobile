package net.streamdek.mobile.nativeapp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How bright, and how busy, the top and bottom of a piece of artwork are.
 *
 * Luminance is relative luminance (0 black, 1 white). Detail is how much neighbouring pixels differ,
 * 0 for a flat wash and approaching 1 for fine, high-contrast texture — the kind of background that
 * breaks up small type even when its average brightness would be fine.
 */
@Immutable
data class ArtworkBands(val topLuminance: Float, val bottomLuminance: Float, val detail: Float)

/**
 * Samples [ArtworkBands] from artwork, once per image.
 *
 * The same shape as `DominantColor`: a tiny copy of the image, read off the main thread, cached by
 * URL. Nothing here runs while scrolling — an image's answer never changes, so it is worked out when
 * the image arrives and then only looked up.
 */
internal object ArtworkBandsSampler {
  private const val SAMPLE_SIZE = 24

  private val cache = object : LinkedHashMap<String, ArtworkBands>(24, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkBands>?): Boolean = size > 64
  }

  @Synchronized
  private fun cached(url: String): ArtworkBands? = cache[url]

  @Synchronized
  private fun store(url: String, bands: ArtworkBands) {
    cache[url] = bands
  }

  suspend fun forArtwork(context: Context, url: String?): ArtworkBands? {
    val artwork = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    cached(artwork)?.let { return it }
    return withContext(Dispatchers.IO) {
      runCatching {
        val request = ImageRequest.Builder(context)
          .data(DominantColor.samplingUrl(artwork))
          .size(SAMPLE_SIZE, SAMPLE_SIZE)
          .allowHardware(false)
          .allowRgb565(false)
          .build()
        val drawable = context.imageLoader.execute(request).drawable ?: return@runCatching null
        val bitmap = drawable.toBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        bitmap.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
        bandsFrom(pixels, SAMPLE_SIZE, SAMPLE_SIZE)?.also { store(artwork, it) }
      }.getOrNull()
    }
  }

  /** The top and bottom thirds, averaged. Split from the bitmap so it can be tested as numbers. */
  internal fun bandsFrom(pixels: IntArray, width: Int, height: Int): ArtworkBands? {
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null
    val band = (height / 3).coerceAtLeast(1)
    fun luminanceAt(index: Int): Float {
      val pixel = pixels[index]
      return relativeLuminance((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)
    }
    fun averageRows(from: Int, until: Int): Float {
      var sum = 0f
      for (row in from until until) for (column in 0 until width) sum += luminanceAt(row * width + column)
      return sum / ((until - from) * width)
    }
    var differences = 0f
    var pairs = 0
    for (row in 0 until height) {
      for (column in 1 until width) {
        differences += abs(luminanceAt(row * width + column) - luminanceAt(row * width + column - 1))
        pairs += 1
      }
    }
    // A mean neighbour difference of 0.25 is already very busy texture at this sample size.
    val detail = if (pairs == 0) 0f else (differences / pairs / 0.25f).coerceIn(0f, 1f)
    return ArtworkBands(
      topLuminance = averageRows(0, band),
      bottomLuminance = averageRows(height - band, height),
      detail = detail,
    )
  }

  internal fun relativeLuminance(red: Int, green: Int, blue: Int): Float {
    fun linear(channel: Int): Float {
      val c = channel / 255f
      return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * linear(red) + 0.7152f * linear(green) + 0.0722f * linear(blue)
  }
}

/** The artwork bands for [url], or null until they have been worked out. */
@Composable
fun rememberArtworkBands(url: String?): ArtworkBands? {
  val context = LocalContext.current
  var bands by remember(url) { mutableStateOf<ArtworkBands?>(null) }
  LaunchedEffect(url) { bands = ArtworkBandsSampler.forArtwork(context, url) }
  return bands
}

/**
 * Where a glass surface sits relative to the page's own chrome.
 *
 * The protection a surface gets depends on what can pass underneath it: a page header has the list
 * scrolling up beneath it, the floating navigation has it arriving from below, and a card in the
 * middle of a page sits on the page and needs nothing extra.
 */
enum class GlassContrastZone { None, TopChrome, BottomChrome }

/**
 * How much extra scrim a chrome surface wants, from 0 (none) to 1 (as much as the surface allows).
 *
 * Plain logic, tested on its own. The inputs change a few times per page, not per frame.
 */
internal object GlassContrastPolicy {
  /**
   * How badly artwork of [luminance] and [detail] fights the theme's text.
   *
   * A dark theme's text is light, so bright artwork is the problem; a light theme's text is dark, so
   * dark artwork is. Busy texture adds to either, because it breaks up letterforms even at a
   * comfortable average brightness.
   */
  fun artworkNeed(luminance: Float, detail: Float, lightTheme: Boolean): Float {
    val brightnessNeed = if (lightTheme) (0.45f - luminance) / 0.35f else (luminance - 0.22f) / 0.40f
    return (brightnessNeed.coerceIn(0f, 1f) + detail * 0.25f).coerceIn(0f, 1f)
  }

  /**
   * @param scrolledUnder the page has scrolled away from its top, so arbitrary content — posters,
   *   stills, logos — is now moving under the chrome.
   */
  fun protection(
    zone: GlassContrastZone,
    backdrop: ArtworkBands?,
    scrolledUnder: Boolean,
    lightTheme: Boolean,
    highContrast: Boolean,
  ): Float {
    val base = when (zone) {
      GlassContrastZone.None -> 0f
      GlassContrastZone.TopChrome ->
        // At the top the artwork itself is behind the header; once scrolled, it has gone and what
        // passes underneath is unknowable, so a fixed, moderate lift stands in for it.
        if (scrolledUnder) SCROLLED_TOP else backdrop?.let { artworkNeed(it.topLuminance, it.detail, lightTheme) } ?: 0f
      GlassContrastZone.BottomChrome -> maxOf(
        if (scrolledUnder) SCROLLED_BOTTOM else 0f,
        if (scrolledUnder) 0f else backdrop?.let { artworkNeed(it.bottomLuminance, it.detail, lightTheme) } ?: 0f,
      )
    }
    return if (highContrast && zone != GlassContrastZone.None) maxOf(base, HIGH_CONTRAST_FLOOR) else base
  }

  const val SCROLLED_TOP = 0.45f
  const val SCROLLED_BOTTOM = 0.22f
  const val HIGH_CONTRAST_FLOOR = 0.75f
  /** Above this a surface also strengthens its icons and labels, not just the ground under them. */
  const val EMPHASIS_THRESHOLD = 0.5f
}

/**
 * The live protection for the app's chrome, shared by every glass surface in it.
 *
 * One pair of animated values rather than an animation per surface. They are read inside draw
 * lambdas, so a change redraws the scrim without recomposing anything underneath it.
 */
@Stable
class GlassContrastState internal constructor() {
  internal val top = Animatable(0f)
  internal val bottom = Animatable(0f)

  /**
   * How strongly the status bar is protected: 0 at the top of a page, 1 once content scrolls under it.
   *
   * Deliberately independent of the artwork. At rest a page's hero is meant to run up behind the
   * status bar untouched, exactly as it did before, and only content travelling under the clock
   * earns the scrim.
   */
  internal val statusBar = Animatable(0f)

  /** Artwork a page has reported as sitting behind its chrome, see [ReportGlassBackdrop]. */
  internal var backdrop by mutableStateOf<ArtworkBands?>(null)

  internal var bottomTarget by mutableStateOf(0f)

  fun protection(zone: GlassContrastZone): Float = when (zone) {
    GlassContrastZone.None -> 0f
    GlassContrastZone.TopChrome -> top.value
    GlassContrastZone.BottomChrome -> bottom.value
  }

  /** True when the navigation's icons should lean on stronger ink. Changes rarely; safe in composition. */
  val bottomNeedsEmphasis: Boolean by derivedStateOf { bottomTarget >= GlassContrastPolicy.EMPHASIS_THRESHOLD }
}

val LocalGlassContrast: ProvidableCompositionLocal<GlassContrastState?> = staticCompositionLocalOf { null }

@Composable
fun rememberGlassContrastState(chrome: ScrollChromeState?): GlassContrastState {
  val state = remember { GlassContrastState() }
  val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val effects = LocalVisualEffects.current
  val motion = LocalMotionSettings.current
  LaunchedEffect(state, chrome, lightTheme, effects.highContrast, motion) {
    snapshotFlow { state.backdrop to (chrome?.phase?.let { it != ScrollPhase.NearTop } ?: false) }
      .distinctUntilChanged()
      .collect { (backdrop, scrolledUnder) ->
        val topTarget = GlassContrastPolicy.protection(GlassContrastZone.TopChrome, backdrop, scrolledUnder, lightTheme, effects.highContrast)
        val bottomTarget = GlassContrastPolicy.protection(GlassContrastZone.BottomChrome, backdrop, scrolledUnder, lightTheme, effects.highContrast)
        state.bottomTarget = bottomTarget
        // The same duration as a crossfade: glass thickening under a header is light changing, and
        // should read as that rather than as a surface being swapped.
        val spec = tween<Float>(motion.crossfade(MotionDuration.crossfade * 2))
        launch { state.top.animateTo(topTarget, spec) }
        launch { state.bottom.animateTo(bottomTarget, spec) }
        launch { state.statusBar.animateTo(if (scrolledUnder) 1f else 0f, spec) }
      }
  }
  return state
}

/**
 * Tells the chrome what artwork is behind it at the top of this page.
 *
 * Only for full-strength hero artwork, where brightness genuinely varies from title to title. The
 * report is withdrawn when the page leaves.
 */
@Composable
fun ReportGlassBackdrop(artworkUrl: String?) {
  val contrast = LocalGlassContrast.current ?: return
  val bands = rememberArtworkBands(artworkUrl)
  DisposableEffect(contrast, bands) {
    contrast.backdrop = bands
    onDispose { if (contrast.backdrop == bands) contrast.backdrop = null }
  }
}

/**
 * Keeps the status bar's clock and icons readable once a floating header has slid away.
 *
 * Any page whose content runs up under the status bar — a floating Modern header that has tucked
 * away, a hero, a detail page — needs this once it scrolls. It fades in a soft gradient as content
 * passes under the bar and fades it out again at the top of the page, and costs a redraw of one
 * gradient rather than any recomposition.
 */
@Composable
fun ChromeStatusBarScrim(modifier: Modifier = Modifier) {
  val contrast = LocalGlassContrast.current ?: return
  val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
  val ground = if (lightTheme) MaterialTheme.colorScheme.background else Color.Black
  Box(
    modifier = modifier
      .fillMaxWidth()
      .drawBehind {
        val strength = contrast.statusBar.value
        if (strength <= 0.01f) return@drawBehind
        val barBottom = (size.height - 14.dp.toPx()).coerceAtLeast(1f)
        drawRect(
          Brush.verticalGradient(
            0f to ground.copy(alpha = 0.86f * strength),
            (barBottom / size.height) to ground.copy(alpha = 0.58f * strength),
            1f to Color.Transparent,
          ),
        )
      }
      // Feathered past the bar itself so the scrim has no edge of its own to notice. Drawn outside
      // both, so the gradient covers the bar and the feather together.
      .padding(bottom = 14.dp)
      .windowInsetsTopHeight(WindowInsets.statusBars),
  )
}
