package net.streamdek.mobile.nativeapp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colour a page takes from its artwork.
 *
 * Not the most common colour in the image — that is usually a near-black shadow or a blown-out sky,
 * and a page painted either of those looks broken rather than matched. What reads as "the colour of
 * this poster" to a person is the most *characteristic* one: reasonably saturated, mid-brightness,
 * and covering enough of the frame to be noticed. Colours are bucketed, scored on that basis, and
 * the winner is then settled into something a page of text can sit on.
 */
object DominantColor {
  /** Small enough to read in a few milliseconds, large enough to keep the colour proportions. */
  private const val SAMPLE_SIZE = 48

  /** Cache keyed on artwork URL: the answer for an image never changes. */
  private val cache = object : LinkedHashMap<String, Color>(24, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?): Boolean = size > 64
  }

  @Synchronized
  private fun cached(url: String): Color? = cache[url]

  @Synchronized
  private fun remember(url: String, color: Color) {
    cache[url] = color
  }

  /**
   * The background colour for [url], or null when it cannot be worked out.
   *
   * Null rather than a guess: a page that falls back to the theme background looks deliberate,
   * whereas one painted an arbitrary colour looks broken.
   */
  suspend fun forArtwork(context: Context, url: String?, lightTheme: Boolean): Color? {
    val artwork = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // The theme is part of the key: the same artwork settles to a deep colour in a dark theme and a
    // pale one in a light theme, so a cached answer from the other polarity is the wrong answer.
    val key = if (lightTheme) "light:$artwork" else "dark:$artwork"
    cached(key)?.let { return it }
    return withContext(Dispatchers.IO) {
      runCatching {
        val request = ImageRequest.Builder(context)
          .data(samplingUrl(artwork))
          .size(SAMPLE_SIZE, SAMPLE_SIZE)
          // A hardware bitmap has no pixels this side of the GPU, and reading them is the point.
          .allowHardware(false)
          .allowRgb565(false)
          .build()
        // The app's shared loader, not a new one per call. Building an ImageLoader stands up a
        // fresh OkHttp client, thread pools and cache handles, and the result shares nothing with
        // the loader every AsyncImage draws through — so sampling a poster re-downloaded artwork
        // that was already in the cache, and did it on connections nothing else could reuse.
        val drawable = context.imageLoader.execute(request).drawable ?: return@runCatching null
        val bitmap = drawable.toBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
        pageColorFrom(bitmap, lightTheme)?.also { remember(key, it) }
      }.getOrNull()
    }
  }

  /**
   * A cheap version of the same image to take the colour from.
   *
   * The page background used to arrive at the same moment as the hero artwork, because sampling
   * downloaded that same multi-megabyte original just to look at 48x48 pixels of it. The metadata
   * service publishes the identical frame at every width, and a ~20KB copy has the same colours in
   * it, so the colour now lands long before the artwork it was taken from.
   *
   * Any other host is left alone — the full URL is all there is to ask for.
   */
  internal fun samplingUrl(url: String): String =
    if (url.contains("image.tmdb.org")) {
      Regex("/t/p/(original|w\\d{3,4}|h\\d{3,4})/").replace(url, "/t/p/w185/")
    } else {
      url
    }

  /** The dominant colour of a sampled bitmap, already settled for use behind text. */
  internal fun pageColorFrom(bitmap: Bitmap, lightTheme: Boolean): Color? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return pageColorFrom(pixels, lightTheme)
  }

  /**
   * Buckets the pixels by coarse hue and lightness, scores each bucket, and settles the winner.
   *
   * Split out from the bitmap so the scoring can be tested without an Android image.
   */
  internal fun pageColorFrom(pixels: IntArray, lightTheme: Boolean): Color? {
    if (pixels.isEmpty()) return null
    val weights = HashMap<Int, Double>()
    val sums = HashMap<Int, Triple<Long, Long, Long>>()

    fun bucketOf(pixel: Int): Int? {
      val red = (pixel shr 16) and 0xFF
      val green = (pixel shr 8) and 0xFF
      val blue = pixel and 0xFF
      val (hue, saturation, value) = rgbToHsv(red, green, blue)
      if (value < 0.12f || value > 0.96f) return null
      if (saturation < 0.12f) return null
      return ((hue / 15f).toInt() shl 8) or ((saturation * 4).toInt() shl 4) or (value * 4).toInt()
    }

    pixels.forEach { pixel ->
      val alpha = (pixel ushr 24) and 0xFF
      if (alpha < 128) return@forEach
      val red = (pixel shr 16) and 0xFF
      val green = (pixel shr 8) and 0xFF
      val blue = pixel and 0xFF
      val (_, saturation, value) = rgbToHsv(red, green, blue)
      // Ignore what cannot carry a colour: shadows, blown highlights and washed-out greys. Without
      // this the winner is almost always the black bar at the edge of a poster. Coarse buckets so
      // shades of one colour reinforce each other rather than splitting the vote.
      val key = bucketOf(pixel) ?: return@forEach
      // Saturated, mid-bright pixels are the ones a person would name as the image's colour.
      val brightnessFit = 1.0 - kotlin.math.abs(value - 0.62f)
      weights[key] = (weights[key] ?: 0.0) + saturation * brightnessFit
      val running = sums[key] ?: Triple(0L, 0L, 0L)
      sums[key] = Triple(running.first + red, running.second + green, running.third + blue)
    }

    val winner = weights.maxByOrNull { it.value }?.key ?: return null
    val total = pixels.count { pixel ->
      ((pixel ushr 24) and 0xFF) >= 128 && bucketOf(pixel) == winner
    }.coerceAtLeast(1)
    val (redSum, greenSum, blueSum) = sums[winner] ?: return null
    return settleForBackground(
      (redSum / total).toInt().coerceIn(0, 255),
      (greenSum / total).toInt().coerceIn(0, 255),
      (blueSum / total).toInt().coerceIn(0, 255),
      lightTheme,
    )
  }

  /**
   * Turns a colour taken from artwork into one a page can be painted with.
   *
   * The theme's polarity decides the answer. A dark theme wants a deep version of the artwork's
   * colour; a light theme wants a pale wash of it. This has to follow the theme because the text on
   * top does not move: painting a light-themed page a deep navy leaves dark type on a dark ground,
   * which is what made titles unreadable in light mode.
   *
   * Either way the hue is kept exactly and the lightness is pulled into a narrow band, so every
   * title gives a page of comparable weight and the reading experience does not change with it.
   */
  internal fun settleForBackground(red: Int, green: Int, blue: Int, lightTheme: Boolean): Color {
    val (hue, saturation, value) = rgbToHsv(red, green, blue)
    return if (lightTheme) {
      hsvToColor(hue, (saturation * 0.34f).coerceAtMost(0.18f), (value + 0.55f).coerceIn(0.90f, 0.97f))
    } else {
      hsvToColor(hue, (saturation * 0.72f).coerceAtMost(0.55f), (value * 0.42f).coerceIn(0.16f, 0.30f))
    }
  }

  /** Hue in degrees, saturation and value in 0..1. Kept here so the scoring needs no Android types. */
  internal fun rgbToHsv(red: Int, green: Int, blue: Int): Triple<Float, Float, Float> {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
      delta == 0f -> 0f
      max == r -> 60f * (((g - b) / delta) % 6f)
      max == g -> 60f * (((b - r) / delta) + 2f)
      else -> 60f * (((r - g) / delta) + 4f)
    }
    return Triple(if (hue < 0f) hue + 360f else hue, if (max == 0f) 0f else delta / max, max)
  }

  /** Internal so the ambient accent can be worked out with the same maths, off an Android device. */
  internal fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs(((hue / 60f) % 2f) - 1f))
    val m = value - c
    val (r, g, b) = when {
      hue < 60f -> Triple(c, x, 0f)
      hue < 120f -> Triple(x, c, 0f)
      hue < 180f -> Triple(0f, c, x)
      hue < 240f -> Triple(0f, x, c)
      hue < 300f -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
  }
}
