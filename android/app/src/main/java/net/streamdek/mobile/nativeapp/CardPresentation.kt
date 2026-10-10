package net.streamdek.mobile.nativeapp

import java.util.Locale

/**
 * The card shape a catalogue asks for, from Stremio's `posterShape`.
 *
 * Stremio defines it on every catalogue item (`MetaPreview.posterShape`): "poster" is the 2:3
 * default, "square" is 1:1 and "landscape" is a wide card. Add-ons that let the viewer pick a card
 * style - TopX among them - answer by changing this field and the artwork together: the item's
 * `poster` is itself drawn wide, and a separate wide `landscapePoster` is often supplied as well.
 * The field is per item, so it survives every catalogue, page and genre the add-on serves.
 *
 * Shared with the TV app, which reads the same field the same way; only card sizes differ.
 */
enum class PosterShape(val aspectRatio: Float) {
  Poster(2f / 3f),
  Square(1f),
  Landscape(16f / 9f),
  ;

  companion object {
    /** Null for anything absent, misspelt or not a string, which leaves the row on its default. */
    fun parse(raw: Any?): PosterShape? = when ((raw as? String)?.trim()?.lowercase(Locale.ROOT)) {
      "poster", "portrait", "regular", "vertical" -> Poster
      "square" -> Square
      "landscape", "wide", "horizontal" -> Landscape
      else -> null
    }
  }
}

/**
 * The shape most of a row's items declare, or null when none declares one.
 *
 * Decided per row rather than per card so a row keeps one height and one bottom edge: an add-on
 * that mixes shapes within a catalogue is rare, and one stray card would otherwise break the line.
 * A tie goes to the narrower shape, which loses the least when the other kind is fitted into it.
 */
fun dominantPosterShape(shapes: Iterable<PosterShape?>): PosterShape? {
  val counts = shapes.filterNotNull().groupingBy { it }.eachCount()
  val most = counts.values.maxOrNull() ?: return null
  return PosterShape.entries.first { counts[it] == most }
}

/**
 * The shape a row is drawn in. Highest wins:
 *
 * 1. [userOverride] - a choice the viewer made for this kind of row: the Continue Watching style,
 *    landscape live cards, landscape New Episodes. Those settings are explicit, so they stand.
 * 2. [declared] - what the row's add-on asks for, when it asks for something other than the
 *    default. "poster" is Stremio's default value, so declaring it says nothing an add-on that
 *    declares nothing does not, and it does not overrule the viewer's default card style.
 * 3. [defaultShape] - the viewer's default card style (portrait on the phone; the Home card style
 *    setting on TV).
 */
fun resolveRowPosterShape(userOverride: PosterShape?, declared: PosterShape?, defaultShape: PosterShape): PosterShape =
  userOverride ?: declared?.takeIf { it != PosterShape.Poster } ?: defaultShape

/**
 * Which picture fills a card of [shape].
 *
 * A wide card prefers a purpose-made wide image: `landscapePoster`, then the poster itself when the
 * add-on said its poster is wide, and only then the backdrop - a backdrop is scenery without the
 * title art the add-on drew onto its card. Portrait and square cards keep the poster.
 */
fun cardArtworkUrl(
  poster: String?,
  backdrop: String?,
  landscapePoster: String?,
  declared: PosterShape?,
  shape: PosterShape,
): String? {
  val posterArt = poster?.takeIf(String::isNotBlank)
  val backdropArt = backdrop?.takeIf(String::isNotBlank)
  val wideArt = landscapePoster?.takeIf(String::isNotBlank)
  return when (shape) {
    PosterShape.Landscape -> wideArt ?: posterArt.takeIf { declared == PosterShape.Landscape } ?: backdropArt ?: posterArt
    PosterShape.Square -> posterArt ?: wideArt ?: backdropArt
    PosterShape.Poster -> posterArt ?: backdropArt ?: wideArt
  }
}

/** How a loaded picture is placed in its card. */
enum class CardArtworkScaling {
  /** Fills the card, cropping a little; what every card did before. */
  Crop,

  /** Shown whole on the card's own surface: transparent artwork, whose empty areas must stay empty. */
  Fit,

  /** Shown whole over a dimmed copy of itself, so an opaque picture of the wrong shape still fills the card. */
  FitOverBackdrop,
}

/**
 * Crop when the picture already suits the card; otherwise show it whole.
 *
 * Cropping keeps `min(a, b) / max(a, b)` of the area, a and b being the two aspect ratios. Up to
 * [cropTolerance] of it lost reads as the same picture; beyond that a landscape image in a portrait
 * card loses its title art, so it is fitted instead. A picture with an alpha channel is fitted onto
 * the plain card rather than over a copy of itself: its transparent areas are part of the design,
 * and a logo would otherwise sit on a smeared, darkened duplicate. Unknown sizes crop.
 */
fun chooseCardArtworkScaling(
  imageWidth: Float,
  imageHeight: Float,
  frameWidth: Float,
  frameHeight: Float,
  hasAlpha: Boolean,
  cropTolerance: Float = CARD_ARTWORK_CROP_TOLERANCE,
): CardArtworkScaling {
  if (imageWidth <= 0f || imageHeight <= 0f || frameWidth <= 0f || frameHeight <= 0f) return CardArtworkScaling.Crop
  val imageAspect = imageWidth / imageHeight
  val frameAspect = frameWidth / frameHeight
  val croppedAway = 1f - minOf(imageAspect, frameAspect) / maxOf(imageAspect, frameAspect)
  return when {
    croppedAway <= cropTolerance -> CardArtworkScaling.Crop
    hasAlpha -> CardArtworkScaling.Fit
    else -> CardArtworkScaling.FitOverBackdrop
  }
}

/** Matches the poster tolerance the hero artwork uses; a 16:9 still in a 2:3 card loses 62%. */
const val CARD_ARTWORK_CROP_TOLERANCE = 0.30f
