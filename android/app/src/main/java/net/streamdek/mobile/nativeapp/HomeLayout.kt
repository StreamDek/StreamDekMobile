package net.streamdek.mobile.nativeapp

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * The device-local key the selection is stored under, in [APP_SETTINGS_PREFERENCES].
 *
 * Not in the cloud-preference payload and not profile-scoped, which is what keeps a phone set to
 * Compact from packing the television's home screen too.
 */
internal const val HOME_DENSITY_PREFERENCE = "home_density"

/** Which caption is drawn below ordinary poster cards on Home. */
enum class HomeCardTextMode(
  val key: String,
  val label: String,
) {
  ShowFull("show_full", "Show Full"),
  ShowYearOnly("show_year_only", "Show Year Only"),
  Off("off", "Off");

  companion object {
    val Default = ShowFull

    fun fromKey(key: String?): HomeCardTextMode {
      val normalized = key?.trim()?.lowercase().orEmpty()
      if (normalized.isEmpty()) return Default
      return entries.firstOrNull {
        it.key == normalized || it.name.lowercase() == normalized || it.label.lowercase() == normalized
      } ?: Default
    }
  }
}

/**
 * How much of Home fits on the screen at once.
 *
 * A *layout mode*, not a pile of flags. Everything on Home that changes between the two - the gap
 * between rows, the bar under a row header, how big a card is and how its caption is spaced - is a
 * field of [HomeLayoutMetrics], read from [LocalHomeLayout]. No composable on Home asks which mode
 * it is in; it asks what the measurement is. That is what stops the two modes drifting into two
 * home screens, and it is why [HomeDensity.Relaxed]'s numbers below are the numbers the screen was
 * already built from rather than a fresh set that happens to look similar.
 *
 * The selection is a property of this installation, like [AnimationSpeed]: it says how close a
 * particular screen is held, so it is stored device-locally and never travels through cloud
 * preferences or the profile document to the television.
 */
enum class HomeDensity(
  /** The persisted form. Stable across releases; the enum name is not the storage contract. */
  val key: String,
  val label: String,
  val description: String,
) {
  Relaxed(
    key = "relaxed",
    label = "Relaxed",
    description = "Full-size artwork with room between rows.",
  ),
  Compact(
    key = "compact",
    label = "Compact",
    description = "Smaller cards and tighter rows, so more fits on screen.",
  );

  companion object {
    /** The existing home screen, so nobody's Home changes because this setting arrived. */
    val Default = Relaxed

    /** Accepts the stored key and the enum name; anything else is the default. */
    fun fromKey(key: String?): HomeDensity {
      val normalized = key?.trim()?.lowercase().orEmpty()
      if (normalized.isEmpty()) return Default
      return entries.firstOrNull { it.key == normalized || it.name.lowercase() == normalized } ?: Default
    }
  }
}

/**
 * Every measurement Home's rows and cards are built from, for the selected [HomeDensity].
 *
 * Card *dimensions* go through [card], a single multiplier applied to width and height alike, so
 * artwork keeps its aspect ratio exactly rather than each card being re-proportioned by hand.
 * Typography and the spacing around it do not: shrinking a caption by the same 20% as the poster it
 * labels makes it hard to read, so those are named separately and rebalanced. The point of Compact
 * is to fit more titles on the screen, not to make the ones on it smaller to look at.
 */
@Immutable
data class HomeLayoutMetrics(
  val density: HomeDensity,
  /** Multiplier for every card dimension. Width and height both take it. */
  val cardScale: Float,
  /** Multiplier for type set inside a card. Deliberately gentler than [cardScale]. */
  val textScale: Float,
  /** Vertical gap between one row and the next. */
  val rowGap: Dp,
  /** Vertical gap between the spotlight and the first row, which is tighter than [rowGap]. */
  val heroToRowGap: Dp,
  /** Gap between a row's header block and its cards. */
  val rowHeaderGap: Dp,
  /** Gap inside the header, between the title line and the accent bar under it. */
  val rowHeaderTitleGap: Dp,
  /** Whether the accent bar under a row header is drawn at all. */
  val showRowAccentBar: Boolean,
  /** The row's own left and right inset, which both the header and the cards line up against. */
  val rowSideInset: Dp,
  /** Horizontal gap between neighbouring cards. */
  val cardGap: Dp,
  val rowTitleSize: TextUnit,
  /** Add-on rows title one step smaller than StreamDek's own, as they always have. */
  val addonRowTitleSize: TextUnit,
  /** Gap between a poster and the caption under it. */
  val cardMetaGap: Dp,
  /** Height reserved for the caption, so every card in a row keeps a common bottom edge. */
  val cardMetaHeight: Dp,
) {
  /** A card dimension at this density. */
  fun card(size: Dp): Dp = if (cardScale == 1f) size else size * cardScale

  /** A type size inside a card at this density. */
  fun text(size: TextUnit): TextUnit = scaled(size, textScale)

  /**
   * A whole text style at this density.
   *
   * Returned untouched at [HomeDensity.Relaxed] - the same instance, not a copy with the same
   * numbers - so a card styled from the Material scale is byte-for-byte what it was before this
   * file existed.
   */
  fun text(style: TextStyle): TextStyle = scaled(style, textScale)

  /**
   * Type inside a card whose *height* is fixed, rather than one that grows to fit its caption.
   *
   * Those cards - the landscape Continue Watching shapes - already lay out a two-line title against
   * a height that only just holds it, and the line below is measured against whatever is left. Type
   * that came down gently while the box came down by a fifth would leave less room in the Compact
   * card than the Relaxed one gives, so the same title would clip harder at the smaller size. Here
   * the type follows the box exactly, which keeps the card proportionally identical.
   */
  fun cardText(style: TextStyle): TextStyle = scaled(style, cardScale)

  private fun scaled(size: TextUnit, scale: Float): TextUnit = if (scale == 1f) size else size * scale

  private fun scaled(style: TextStyle, scale: Float): TextStyle = if (scale == 1f) {
    style
  } else {
    style.copy(
      fontSize = if (style.fontSize.isSpecified) style.fontSize * scale else style.fontSize,
      lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
    )
  }

  companion object {
    fun forDensity(density: HomeDensity): HomeLayoutMetrics = when (density) {
      // Exactly what Home already measured. Nothing here is a new decision.
      HomeDensity.Relaxed -> HomeLayoutMetrics(
        density = HomeDensity.Relaxed,
        cardScale = 1f,
        textScale = 1f,
        rowGap = 26.dp,
        heroToRowGap = 12.dp,
        rowHeaderGap = 14.dp,
        rowHeaderTitleGap = 6.dp,
        showRowAccentBar = true,
        rowSideInset = 16.dp,
        cardGap = 14.dp,
        rowTitleSize = 22.5.sp,
        addonRowTitleSize = 20.sp,
        cardMetaGap = 8.dp,
        cardMetaHeight = 56.dp,
      )
      // Cards a fifth smaller, and the space around them brought in to match. The accent bar goes
      // rather than shrinking: at this row height a title with no rule under it already reads as a
      // heading, and keeping a seven-pixel bar between every row is most of what made the tighter
      // spacing feel crowded rather than dense.
      //
      // The caption block is 48dp rather than the 44.8dp a straight 0.8 would give, because the two
      // lines inside it are only shrunk by 8%: type that follows the artwork down 20% stops being
      // comfortably readable at arm's length, which is the opposite of what fitting more on screen
      // is for.
      HomeDensity.Compact -> HomeLayoutMetrics(
        density = HomeDensity.Compact,
        cardScale = 0.8f,
        textScale = 0.92f,
        rowGap = 16.dp,
        heroToRowGap = 8.dp,
        rowHeaderGap = 9.dp,
        rowHeaderTitleGap = 0.dp,
        showRowAccentBar = false,
        rowSideInset = 16.dp,
        cardGap = 10.dp,
        rowTitleSize = 18.5.sp,
        addonRowTitleSize = 17.sp,
        cardMetaGap = 6.dp,
        cardMetaHeight = 48.dp,
      )
    }
  }
}

/**
 * The measurements Home is currently being drawn at.
 *
 * Defaults to Relaxed, and is only provided around Home itself. The media cards are shared with
 * Browse, Search and the View All pages, so the default is what keeps those pages exactly as they
 * were no matter which density Home is set to.
 */
val LocalHomeLayout: ProvidableCompositionLocal<HomeLayoutMetrics> =
  staticCompositionLocalOf { HomeLayoutMetrics.forDensity(HomeDensity.Default) }
