package net.streamdek.mobile.nativeapp

import androidx.annotation.StringRes
import net.streamdek.mobile.R

/**
 * How the floating navigation and the page headers respond to the viewer.
 *
 * # Two questions, four answers
 *
 * Each option answers two things: does the navigation collapse, and what triggers it; and do page
 * headers (title, search field, grid button, filters, counts, clear-all) stay put or make room while
 * scrolling. Only the fully fixed option keeps headers still. Every other option, including the
 * expanded one with scroll-aware headers, lets them condense, because a viewer who has chosen any
 * movement at all has chosen more room for content.
 *
 * # Storage, and why it is split
 *
 * `collapsible_navigation_enabled` predates this and travels with the account's cloud preferences,
 * where other StreamDek apps may read it. Its meaning is left exactly as it was — "the navigation
 * collapses" — and everything else is device-local: what triggers the collapse, and whether an
 * expanded navigation keeps fixed headers. A viewer changing either on their phone therefore changes
 * nothing anywhere else, and an older build reading the synced boolean still sees what it expects.
 */
enum class NavigationBehaviour(
  @StringRes val labelRes: Int,
  @StringRes val descriptionRes: Int,
) {
  ExpandedFixedHeaders(R.string.navigation_behaviour_always_expanded, R.string.navigation_behaviour_always_expanded_description),
  ExpandedScrollAwareHeaders(R.string.navigation_behaviour_expanded_scroll_aware, R.string.navigation_behaviour_expanded_scroll_aware_description),
  CollapseAfterDelay(R.string.navigation_behaviour_after_delay, R.string.navigation_behaviour_after_delay_description),
  CollapseWhileScrolling(R.string.navigation_behaviour_while_scrolling, R.string.navigation_behaviour_while_scrolling_description);

  /** The synced half. */
  val collapses: Boolean get() = this == CollapseAfterDelay || this == CollapseWhileScrolling

  /** Whether page headers condense while scrolling. Only the fully fixed option keeps them still. */
  val headersScrollAware: Boolean get() = this != ExpandedFixedHeaders

  companion object {
    /** The device-local half: what triggers the collapse when [collapses] is true. */
    internal const val TRIGGER_PREFERENCE = "navigation_collapse_trigger"
    internal const val TRIGGER_DELAY = "delay"
    internal const val TRIGGER_SCROLL = "scroll"

    /** Device-local: whether an expanded navigation keeps scroll-aware headers. Absent means fixed. */
    internal const val EXPANDED_HEADERS_PREFERENCE = "navigation_expanded_headers"
    internal const val EXPANDED_HEADERS_FIXED = "fixed"
    internal const val EXPANDED_HEADERS_SCROLL = "scroll"

    fun from(collapsible: Boolean, collapseOnScroll: Boolean, expandedHeadersScrollAware: Boolean = false): NavigationBehaviour = when {
      !collapsible -> if (expandedHeadersScrollAware) ExpandedScrollAwareHeaders else ExpandedFixedHeaders
      collapseOnScroll -> CollapseWhileScrolling
      else -> CollapseAfterDelay
    }
  }
}
