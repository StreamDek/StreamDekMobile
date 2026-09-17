package net.streamdek.mobile.nativeapp

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import net.streamdek.mobile.R

/**
 * StreamDek's curated colour themes.
 *
 * # A collection, not a colour picker
 *
 * Eight themes, each designed for a streaming app whose content is the hero: warm gold, deep
 * cinematic blue, rich red, electric violet, jewel green, luminous teal, raspberry rose and an
 * intentional black/graphite/silver system. White was dropped: it duplicated Monochrome, and a pale
 * accent on its own is not a theme. Hues are kept well apart (no two within ~20°) so each one reads as
 * a different personality rather than a variation of its neighbour.
 *
 * The declaration order is the order the picker shows them in.
 */
internal enum class AppThemePreset(@StringRes val labelRes: Int, @StringRes val taglineRes: Int) {
  Amber(R.string.theme_amber, R.string.theme_tagline_amber),
  Ocean(R.string.theme_ocean, R.string.theme_tagline_ocean),
  Crimson(R.string.theme_crimson, R.string.theme_tagline_crimson),
  Violet(R.string.theme_violet, R.string.theme_tagline_violet),
  Emerald(R.string.theme_emerald, R.string.theme_tagline_emerald),
  Aurora(R.string.theme_aurora, R.string.theme_tagline_aurora),
  Rose(R.string.theme_rose, R.string.theme_tagline_rose),
  Monochrome(R.string.theme_monochrome, R.string.theme_tagline_monochrome);

  companion object {
    /** A fresh install, and anything unreadable. The neutral system, so artwork sets the mood. */
    val Default = Monochrome

    /**
     * Reads a stored or synced theme name. White no longer exists: it becomes Monochrome, which is
     * what it was trying to be. An unknown name (from a newer build) is null, so the caller keeps
     * what it has rather than jumping to the default.
     */
    fun fromName(name: String?): AppThemePreset? = when (name) {
      null -> null
      "White" -> Monochrome
      else -> entries.firstOrNull { it.name == name }
    }
  }
}

/**
 * One theme, in one appearance, as the roles the interface actually paints.
 *
 * A theme is not a single accent. Controls, selection, progress and focus each want a related but
 * different tone: a deeper shade for a selected surface, a brighter one for a progress bar or a focus
 * ring, a translucent tint for a chip. All of them come from three hand-picked tones per appearance
 * ([ThemeTones]) so they stay in the same family, and every foreground is chosen for contrast rather
 * than assumed.
 *
 * Large surfaces are deliberately absent: backgrounds and cards stay StreamDek's neutral greys in
 * every theme. A theme communicates selection, focus, progress and interaction — it does not turn the
 * app amber or purple. The translucent roles are translucent on purpose, so they sit on glass and
 * ambient artwork backgrounds instead of pasting an opaque slab over them.
 */
@Immutable
internal data class StreamDekThemeColors(
  /** Buttons, active controls, sliders, links: the theme's voice. */
  val accent: Color,
  /** Text and icons on [accent]. */
  val onAccent: Color,
  /** A second, related tone: secondary emphasis, secondary buttons, Monochrome's silver/grey states. */
  val secondary: Color,
  val onSecondary: Color,
  /** The brighter companion (darker in light mode) for moments that must catch the eye. */
  val highlight: Color,
  /** Progress bars and indicators. */
  val progress: Color,
  /** The unfilled part of a progress bar: neutral, so only the progress itself carries the theme. */
  val progressTrack: Color,
  /** A selected row, card or option: a tint, not a fill. */
  val selectedContainer: Color,
  /** Text and icons on [selectedContainer]. */
  val onSelectedContainer: Color,
  /** The edge of a selected card. */
  val selectedBorder: Color,
  /** An accent control while it is being pressed. */
  val pressed: Color,
  /** Focus borders. */
  val focusRing: Color,
  /** Soft glow behind a focused or featured element. */
  val glow: Color,
  /** A selected filter chip. */
  val chipSelected: Color,
  val onChipSelected: Color,
  /** A switched-on toggle. */
  val toggleTrack: Color,
  val toggleThumb: Color,
  /**
   * The navigation's edge while it is drawing attention (an update downloading). The current
   * destination itself keeps its neutral pill; only its icon takes [onSelectedContainer].
   */
  val navSelectionBorder: Color,
  /** A barely-there tint for a surface that belongs to the theme (a promo, a hint card). */
  val tintedSurface: Color,
  val darkMode: Boolean,
)

/**
 * The three tones a theme is designed from, for one appearance.
 *
 * [accent] carries text at 4.5:1 or better against that appearance's background and surfaces;
 * [highlight] is the more luminous companion for progress and focus (brighter on dark, a clearer
 * mid-tone on light); [deep] is the richer shade for secondary states and, in light mode, the text on
 * tinted selections. The values are checked by `ThemePaletteTest`.
 */
@Immutable
internal data class ThemeTones(val accent: Color, val highlight: Color, val deep: Color)

internal fun themeTones(theme: AppThemePreset, darkMode: Boolean): ThemeTones = if (darkMode) {
  when (theme) {
    // Gold rather than orange or yellow: warm, not a warning.
    AppThemePreset.Amber -> ThemeTones(Color(0xFFF4B740), Color(0xFFFFD47A), Color(0xFFC98A1B))
    // A deep cinematic blue with a cyan-leaning highlight for progress and focus.
    AppThemePreset.Ocean -> ThemeTones(Color(0xFF4C9BFF), Color(0xFF6FD0FF), Color(0xFF2F6FD6))
    // A film-poster red, pulled away from the error red Material uses for mistakes.
    AppThemePreset.Crimson -> ThemeTones(Color(0xFFEF4A64), Color(0xFFFF7F90), Color(0xFFC62C4E))
    // Electric but controlled: a luminous highlight over a deep violet, never candy purple.
    AppThemePreset.Violet -> ThemeTones(Color(0xFF9F82FF), Color(0xFFC3B2FF), Color(0xFF6C4BE3))
    // Jewel green with blue in it, clear of Android's system green.
    AppThemePreset.Emerald -> ThemeTones(Color(0xFF2DBE78), Color(0xFF72E3A6), Color(0xFF16925A))
    // Teal-cyan with a pale glacial highlight: luminous on glass without going neon.
    AppThemePreset.Aurora -> ThemeTones(Color(0xFF34CEDA), Color(0xFF93EEF5), Color(0xFF1C9AAA))
    // Raspberry, well toward magenta, so it is never mistaken for Crimson or for a generic pink.
    AppThemePreset.Rose -> ThemeTones(Color(0xFFE0559E), Color(0xFFFF94CB), Color(0xFFC13A82))
    // Near-white accent, pure white highlight, silver secondary states.
    AppThemePreset.Monochrome -> ThemeTones(Color(0xFFEDEDEF), Color(0xFFFFFFFF), Color(0xFFA3A8B0))
  }
} else {
  when (theme) {
    AppThemePreset.Amber -> ThemeTones(Color(0xFF9A5A06), Color(0xFFB86E0A), Color(0xFF6F4104))
    AppThemePreset.Ocean -> ThemeTones(Color(0xFF1A5DCC), Color(0xFF0A78B8), Color(0xFF12408F))
    AppThemePreset.Crimson -> ThemeTones(Color(0xFFB21F3A), Color(0xFFCC3350), Color(0xFF861528))
    AppThemePreset.Violet -> ThemeTones(Color(0xFF6639D6), Color(0xFF7A4FEA), Color(0xFF4B28A6))
    AppThemePreset.Emerald -> ThemeTones(Color(0xFF067A45), Color(0xFF0A9152), Color(0xFF055A34))
    AppThemePreset.Aurora -> ThemeTones(Color(0xFF07707F), Color(0xFF0A8797), Color(0xFF05525D))
    AppThemePreset.Rose -> ThemeTones(Color(0xFFAD1F68), Color(0xFFC8337F), Color(0xFF80164C))
    // Graphite accent, charcoal highlight, dark grey secondary states on light neutrals.
    AppThemePreset.Monochrome -> ThemeTones(Color(0xFF1B1C20), Color(0xFF3A3D44), Color(0xFF545962))
  }
}

/** The neutral grounds every theme is designed against. Shared with [appColorScheme]. */
internal object StreamDekNeutrals {
  val darkBackground = Color.Black
  val darkSurface = Color(0xFF111111)
  val darkSurfaceVariant = Color(0xFF1D1D1D)
  val darkOnSurface = Color(0xFFF5F7FB)
  val lightBackground = Color(0xFFF5F4F0)
  val lightSurface = Color.White
  val lightOnSurface = Color(0xFF0F172A)
  /** The two inks an on-colour is chosen between. Near-black rather than black keeps it from buzzing. */
  val inkDark = Color(0xFF0B0B0F)
  val inkLight = Color.White
}

/**
 * Whichever of the theme's two inks reads better on [background], measured with [contrastRatio].
 * Near-black rather than [readableOn]'s pure black, which buzzes against saturated accents.
 */
internal fun themeInkOn(background: Color): Color =
  if (contrastRatio(background, StreamDekNeutrals.inkDark) >= contrastRatio(background, StreamDekNeutrals.inkLight)) {
    StreamDekNeutrals.inkDark
  } else {
    StreamDekNeutrals.inkLight
  }

private fun Color.mixedWith(other: Color, amount: Float): Color = Color(
  red = red + (other.red - red) * amount,
  green = green + (other.green - green) * amount,
  blue = blue + (other.blue - blue) * amount,
  alpha = alpha,
)

internal fun streamDekThemeColors(theme: AppThemePreset, darkMode: Boolean): StreamDekThemeColors {
  val tones = themeTones(theme, darkMode)
  val mono = theme == AppThemePreset.Monochrome
  return if (darkMode) {
    val surface = StreamDekNeutrals.darkSurface
    val chip = tones.accent.copy(alpha = if (mono) 0.18f else 0.22f)
    StreamDekThemeColors(
      accent = tones.accent,
      onAccent = themeInkOn(tones.accent),
      secondary = tones.deep,
      onSecondary = themeInkOn(tones.deep),
      highlight = tones.highlight,
      progress = tones.highlight,
      progressTrack = StreamDekNeutrals.darkOnSurface.copy(alpha = 0.16f),
      selectedContainer = tones.accent.copy(alpha = if (mono) 0.12f else 0.16f),
      onSelectedContainer = tones.highlight,
      selectedBorder = tones.accent.copy(alpha = 0.64f),
      pressed = tones.accent.mixedWith(Color.Black, 0.18f),
      focusRing = tones.highlight,
      glow = tones.highlight.copy(alpha = if (mono) 0.22f else 0.34f),
      chipSelected = chip,
      onChipSelected = if (contrastRatio(tones.highlight, chip.compositeOver(surface)) >= 4.5f) tones.highlight else StreamDekNeutrals.darkOnSurface,
      toggleTrack = tones.accent,
      toggleThumb = themeInkOn(tones.accent),
      navSelectionBorder = tones.accent.copy(alpha = 0.72f),
      tintedSurface = tones.accent.copy(alpha = 0.06f),
      darkMode = true,
    )
  } else {
    val surface = StreamDekNeutrals.lightSurface
    val chip = tones.accent.copy(alpha = 0.14f)
    StreamDekThemeColors(
      accent = tones.accent,
      onAccent = themeInkOn(tones.accent),
      secondary = tones.deep,
      onSecondary = themeInkOn(tones.deep),
      highlight = tones.highlight,
      progress = tones.highlight,
      progressTrack = StreamDekNeutrals.lightOnSurface.copy(alpha = 0.12f),
      selectedContainer = tones.accent.copy(alpha = 0.10f),
      onSelectedContainer = tones.deep,
      selectedBorder = tones.accent.copy(alpha = 0.55f),
      pressed = tones.accent.mixedWith(Color.Black, 0.15f),
      focusRing = tones.accent,
      glow = tones.accent.copy(alpha = 0.20f),
      chipSelected = chip,
      onChipSelected = if (contrastRatio(tones.deep, chip.compositeOver(surface)) >= 4.5f) tones.deep else StreamDekNeutrals.lightOnSurface,
      toggleTrack = tones.accent,
      toggleThumb = themeInkOn(tones.accent),
      navSelectionBorder = tones.accent.copy(alpha = 0.60f),
      tintedSurface = tones.accent.copy(alpha = 0.05f),
      darkMode = false,
    )
  }
}

/** The current theme's roles. Provided alongside `MaterialTheme` by the app shell. */
internal val LocalStreamDekThemeColors = staticCompositionLocalOf { streamDekThemeColors(AppThemePreset.Default, darkMode = true) }

/**
 * The Material colour scheme for a theme.
 *
 * Material's own components (buttons, sliders, text fields, spinners) read these roles, so mapping
 * them from [StreamDekThemeColors] is what keeps a stock `Button` in step with StreamDek's own
 * controls. Backgrounds and surfaces are the same neutrals in every theme.
 */
internal fun appColorScheme(theme: AppThemePreset, darkMode: Boolean): ColorScheme {
  val colors = streamDekThemeColors(theme, darkMode)
  return if (darkMode) {
    val surface = StreamDekNeutrals.darkSurface
    val container = colors.selectedContainer.compositeOver(surface)
    val chip = colors.chipSelected.compositeOver(surface)
    darkColorScheme(
      primary = colors.accent,
      onPrimary = colors.onAccent,
      primaryContainer = container,
      onPrimaryContainer = colors.onSelectedContainer,
      secondary = colors.highlight,
      onSecondary = themeInkOn(colors.highlight),
      secondaryContainer = chip,
      onSecondaryContainer = colors.onChipSelected,
      tertiary = colors.secondary,
      onTertiary = colors.onSecondary,
      inversePrimary = themeTones(theme, darkMode = false).accent,
      background = StreamDekNeutrals.darkBackground,
      surface = surface,
      surfaceVariant = StreamDekNeutrals.darkSurfaceVariant,
      onBackground = StreamDekNeutrals.darkOnSurface,
      onSurface = StreamDekNeutrals.darkOnSurface,
    )
  } else {
    val surface = StreamDekNeutrals.lightSurface
    lightColorScheme(
      primary = colors.accent,
      onPrimary = colors.onAccent,
      primaryContainer = colors.selectedContainer.compositeOver(surface),
      onPrimaryContainer = colors.onSelectedContainer,
      secondary = colors.highlight,
      onSecondary = themeInkOn(colors.highlight),
      secondaryContainer = colors.chipSelected.compositeOver(surface),
      onSecondaryContainer = colors.onChipSelected,
      tertiary = colors.secondary,
      onTertiary = colors.onSecondary,
      inversePrimary = themeTones(theme, darkMode = true).accent,
      background = StreamDekNeutrals.lightBackground,
      surface = surface,
      // A near-neutral slate: Material paints an unselected chip and a text field's container with
      // this, and a warm tint there made those controls read as coloured slabs.
      surfaceVariant = Color(0xFFE7E9EE),
      onSurfaceVariant = Color(0xFF334155),
      outline = Color(0xFF94A3B8),
      outlineVariant = Color(0xFFCBD5E1),
      onBackground = StreamDekNeutrals.lightOnSurface,
      onSurface = StreamDekNeutrals.lightOnSurface,
    )
  }
}
