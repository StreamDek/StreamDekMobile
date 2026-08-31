package net.streamdek.mobile.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * StreamDek's one answer to "how much room is there?".
 *
 * Everything adaptive in the app reads from here rather than asking about the device. The
 * distinction matters: a tablet in split screen has a phone's worth of width and should get a
 * phone's layout, while a large phone unfolded has a tablet's. Deciding on the window means both
 * land correctly without either being special-cased, and it means the UI keeps up when the window
 * is resized underneath it rather than only at launch.
 *
 * The thresholds follow the platform's own: 600dp is where a second column starts to earn its
 * place, and 840dp is where two panes side by side stop feeling cramped.
 */
enum class WindowWidthClass {
  /** Phones upright, and anything squeezed into a narrow split-screen pane. */
  Compact,

  /** Large phones in landscape, unfolded foldables, small and portrait tablets. */
  Medium,

  /** Tablets in landscape, desktop-style windows, anything genuinely wide. */
  Expanded,
  ;

  val atLeastMedium: Boolean get() = this != Compact
  val isExpanded: Boolean get() = this == Expanded
}

/** Height matters far less than width, but a short landscape window cannot afford a tall hero. */
enum class WindowHeightClass { Compact, Medium, Expanded }

@Immutable
data class WindowSize(val widthDp: Dp, val heightDp: Dp) {
  val widthClass: WindowWidthClass = when {
    widthDp < 600.dp -> WindowWidthClass.Compact
    widthDp < 840.dp -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
  }
  val heightClass: WindowHeightClass = when {
    heightDp < 480.dp -> WindowHeightClass.Compact
    heightDp < 900.dp -> WindowHeightClass.Medium
    else -> WindowHeightClass.Expanded
  }

  /**
   * Whether two panes side by side are worth it.
   *
   * Width alone is not enough: a tall portrait tablet has the width for two panes but reads far
   * better as one column with more in it, the way a magazine page does.
   */
  val supportsTwoPanes: Boolean get() = widthClass.isExpanded
}

/**
 * Defaults to a compact phone so a composable rendered outside the app scaffold — a preview, a
 * dialog hosted in its own window — still lays out sensibly rather than crashing.
 */
val LocalWindowSize: ProvidableCompositionLocal<WindowSize> =
  compositionLocalOf { WindowSize(widthDp = 411.dp, heightDp = 891.dp) }

val LocalStreamDekSpacing: ProvidableCompositionLocal<StreamDekSpacing> =
  compositionLocalOf { StreamDekSpacing.forWidth(WindowWidthClass.Compact) }

/**
 * The gaps and margins a screen is built from, sized once for the current window.
 *
 * Having these in one place is what stops the tablet layouts drifting into a second design. A
 * screen asks for `spacing.pagePadding` rather than picking a number, so every screen widens by the
 * same amount at the same point and the result still looks like one application.
 */
@Immutable
data class StreamDekSpacing(
  /** Left and right margin for page content. */
  val pagePadding: Dp,
  /** Gap between cards in a grid or row. */
  val gridGap: Dp,
  /** Vertical gap between a page's major sections. */
  val sectionGap: Dp,
  /**
   * Widest a column of prose or form controls is allowed to get.
   *
   * Not a stylistic choice: a line of text stretched across a 12-inch screen is genuinely harder to
   * read, because the eye loses its place on the way back to the start of the next line.
   */
  val readableContentWidth: Dp,
) {
  companion object {
    fun forWidth(widthClass: WindowWidthClass): StreamDekSpacing = when (widthClass) {
      WindowWidthClass.Compact -> StreamDekSpacing(
        pagePadding = 16.dp,
        gridGap = 12.dp,
        sectionGap = 26.dp,
        readableContentWidth = Dp.Infinity,
      )
      WindowWidthClass.Medium -> StreamDekSpacing(
        pagePadding = 24.dp,
        gridGap = 14.dp,
        sectionGap = 30.dp,
        readableContentWidth = 760.dp,
      )
      WindowWidthClass.Expanded -> StreamDekSpacing(
        pagePadding = 32.dp,
        gridGap = 16.dp,
        sectionGap = 34.dp,
        readableContentWidth = 920.dp,
      )
    }
  }
}

/** How the app's primary navigation should be presented at the current width. */
enum class NavigationStyle {
  /** The phone's bottom bar, unchanged. */
  BottomBar,

  /** A vertical rail down the leading edge, icons above short labels. */
  Rail,
}

val WindowWidthClass.navigationStyle: NavigationStyle
  get() = if (this == WindowWidthClass.Compact) NavigationStyle.BottomBar else NavigationStyle.Rail

/**
 * Poster columns for the width available, derived rather than hardcoded.
 *
 * The rule is a minimum comfortable card width, not a table of device sizes: it produces 2–3 across
 * on a phone exactly as before, and 4, 6 or 8 on progressively larger screens, without anyone
 * having to decide in advance which number belongs to which tablet. [preferredCardWidth] is what
 * distinguishes a poster grid from a landscape-artwork grid.
 */
fun adaptiveColumnCount(
  availableWidth: Dp,
  preferredCardWidth: Dp,
  horizontalPadding: Dp,
  gap: Dp,
  minColumns: Int = 1,
  maxColumns: Int = 12,
): Int {
  val usable = availableWidth - horizontalPadding * 2
  if (usable <= 0.dp) return minColumns
  // Each column past the first also costs a gap, so the gap is folded into the column width and
  // added back once — otherwise wide grids come out one column too optimistic.
  val columns = ((usable + gap) / (preferredCardWidth + gap)).toInt()
  return columns.coerceIn(minColumns, maxColumns)
}

/**
 * Turns the grid density a viewer chose on a phone into a column count for the window they are
 * actually using.
 *
 * The existing 2-or-3 toggle is kept, but read as a statement about how big a card should be rather
 * than how many there are: "three across" on a phone means roughly hundred-dp posters, and on a
 * wide screen that same intent is six or eight of them, not three stretched to a third of a tablet
 * each. Compact windows are returned untouched, so no phone layout can shift by a single pixel.
 */
@Composable
fun adaptiveMediaColumns(compactColumns: Int, landscapeArtwork: Boolean = false): Int {
  val window = LocalWindowSize.current
  if (window.widthClass == WindowWidthClass.Compact) return compactColumns
  val spacing = LocalStreamDekSpacing.current
  val cardWidth = when {
    landscapeArtwork -> LandscapeCardWidth
    compactColumns <= 2 -> RoomyPosterCardWidth
    else -> PosterCardWidth
  }
  val maxColumns = when {
    landscapeArtwork -> 5
    compactColumns <= 2 -> 6
    else -> 8
  }
  return adaptiveColumnCount(
    availableWidth = window.widthDp,
    preferredCardWidth = cardWidth,
    horizontalPadding = spacing.pagePadding,
    gap = spacing.gridGap,
    minColumns = compactColumns,
    maxColumns = maxColumns,
  )
}

/**
 * Poster artwork is 2:3 and stays narrow, so a lot of it fits across a wide screen.
 *
 * These two widths are what "three across" and "two across" already come out as on a phone, which
 * is what keeps the density on a tablet recognisably the same choice rather than a new one.
 */
val PosterCardWidth: Dp = 100.dp
val RoomyPosterCardWidth: Dp = 150.dp

/** Backdrop and channel artwork is 16:9 and needs roughly twice the width to read. */
val LandscapeCardWidth: Dp = 240.dp

/**
 * Card width for the horizontally scrolling rows on Home.
 *
 * These rows need the least help of anything in the app: a wider window already reveals more of the
 * row without any change at all, which is exactly the behaviour wanted. The small step up stops
 * artwork looking undersized on a large screen without turning a browsing row into a showcase.
 */
@Composable
fun homeRowPosterWidth(): Dp = when (LocalWindowSize.current.widthClass) {
  WindowWidthClass.Compact -> 138.dp
  WindowWidthClass.Medium -> 150.dp
  WindowWidthClass.Expanded -> 164.dp
}

/**
 * Centres page content and stops it stretching past a readable width.
 *
 * Applied to prose, forms and settings — the places where more width makes things worse. Grids are
 * deliberately left out: for those, extra width means more artwork on screen, which is the point.
 */
@Composable
fun AdaptiveContentContainer(
  modifier: Modifier = Modifier,
  maxWidth: Dp = LocalStreamDekSpacing.current.readableContentWidth,
  alignment: Alignment = Alignment.TopCenter,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
    Box(
      modifier = if (maxWidth == Dp.Infinity) Modifier.fillMaxWidth() else Modifier.widthIn(max = maxWidth).fillMaxWidth(),
      content = content,
    )
  }
}

/**
 * Page padding for the current window, with the option to keep a screen's own bottom inset.
 *
 * Screens keep passing their own top and bottom values because those are about headers and
 * navigation bars rather than about width.
 */
@Composable
fun adaptivePagePadding(top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues {
  val spacing = LocalStreamDekSpacing.current
  return remember(spacing, top, bottom) {
    PaddingValues(start = spacing.pagePadding, end = spacing.pagePadding, top = top, bottom = bottom)
  }
}

/** Grid spacing that widens with the window, so density stays deliberate rather than accidental. */
@Composable
fun adaptiveGridArrangement(): Arrangement.HorizontalOrVertical =
  Arrangement.spacedBy(LocalStreamDekSpacing.current.gridGap)

/**
 * How wide a dialog should be.
 *
 * A dialog sized as a fraction of the window is right on a phone and absurd on a 12-inch tablet,
 * where it becomes a vast panel holding two buttons. Past the compact width it stops growing and
 * simply centres.
 */
@Composable
fun adaptiveDialogWidth(compactFraction: Float = 0.94f): Modifier {
  val window = LocalWindowSize.current
  val capped = window.widthDp * compactFraction
  return if (window.widthClass == WindowWidthClass.Compact) {
    Modifier.fillMaxWidth(compactFraction)
  } else {
    Modifier.widthIn(max = minOf(capped, MaxDialogWidth))
  }
}

/** Past this a dialog stops being a dialog and starts being a page. */
val MaxDialogWidth: Dp = 560.dp

/**
 * Width of the title/filter/search column on browsing pages in landscape.
 *
 * Wide enough for the search field and a couple of filter chips per line, narrow enough that the
 * results still get the great majority of the window — the controls are how you get to the content,
 * not the content itself.
 */
val BrowseSideHeaderWidth: Dp = 320.dp

/**
 * How wide the floating navigation bar is allowed to get.
 *
 * Roughly a large phone's width: enough that the five destinations keep the spacing they were
 * designed with, and not so much that they drift apart into a row of distant icons.
 */
val MaxFloatingNavigationWidth: Dp = 620.dp

/**
 * Sizing for the "Who's watching?" picker.
 *
 * The picker is the first thing the app shows, so it is the first impression of whether the app
 * belongs on the device. Built for a phone it puts small avatars in the middle of a large screen
 * with a band of scrim across most of it. Everything here is proportional to the window instead:
 * the artwork keeps a fixed share of the height, and the avatars grow enough to be worth aiming at
 * from tablet viewing distance.
 */
@Immutable
data class ProfilePickerMetrics(
  val heroHeight: Dp,
  val scrimHeight: Dp,
  val avatarSize: Dp,
  val avatarSpacing: Dp,
  val contentPadding: Dp,
  /** Multiplier for the "Who's watching?" headline, so it shrinks with the avatars it labels. */
  val headlineScale: Float = 1f,
)

@Composable
fun profilePickerMetrics(): ProfilePickerMetrics {
  val window = LocalWindowSize.current
  return when (window.widthClass) {
    // Unchanged from what the phone has always shown.
    WindowWidthClass.Compact -> ProfilePickerMetrics(
      // The artwork runs past the bottom of the glass pane rather than stopping level with it, so
      // the pane always has image behind it all the way down and the picker reads as one
      // photograph the controls sit on, not a photo with a grey band under it.
      //
      // Capped against the window rather than simply fixed, because the block underneath -
      // headline, avatars, Manage Profiles - is anchored to the bottom edge, so how far down the
      // screen its text begins depends on how tall the screen is. A fixed hero that clears the
      // headline on a 924dp phone would sit behind it on a short one. The reserve is that block's
      // height plus a margin, measured from the laid-out screen.
      heroHeight = minOf(620.dp, window.heightDp - 365.dp),
      scrimHeight = 430.dp,
      avatarSize = 92.dp,
      avatarSpacing = 34.dp,
      contentPadding = 22.dp,
    )
    WindowWidthClass.Medium -> ProfilePickerMetrics(
      heroHeight = minOf(window.heightDp * 0.58f, window.heightDp - 400.dp),
      scrimHeight = window.heightDp * 0.46f,
      avatarSize = 116.dp,
      avatarSpacing = 44.dp,
      contentPadding = 32.dp,
    )
    // A tablet held in landscape. The window is wide but short, and at that shape the avatars are
    // being read across a desk rather than at arm's length — so they are deliberately half the size
    // of the portrait ones, with the headline scaled to match. Portrait tablets keep the larger
    // treatment above, and no phone reaches this branch.
    WindowWidthClass.Expanded -> ProfilePickerMetrics(
      heroHeight = minOf(window.heightDp * 0.62f, window.heightDp - 300.dp),
      scrimHeight = window.heightDp * 0.50f,
      avatarSize = 76.dp,
      avatarSpacing = 34.dp,
      contentPadding = 40.dp,
      headlineScale = 0.575f,
    )
  }
}

/**
 * Whether a bottom sheet should become a centred dialog instead.
 *
 * A sheet anchored to the bottom edge of a large landscape screen puts its controls a long way from
 * where the eye and the hands already are.
 */
val WindowWidthClass.prefersCenteredModal: Boolean get() = this == WindowWidthClass.Expanded
