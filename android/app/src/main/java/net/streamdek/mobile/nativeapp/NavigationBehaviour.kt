package net.streamdek.mobile.nativeapp

import androidx.annotation.StringRes
import net.streamdek.mobile.R

/**
 * When the floating navigation tucks away into the profile button.
 *
 * # Named for what makes it collapse
 *
 * The three options are the same bar; the only thing that differs is what sends it into its
 * collapsed state — nothing, a timer, or scrolling. So that trigger is the name. "Floating",
 * "Adaptive" and "Dynamic" were considered and rejected: the bar floats in all three, and the other
 * two describe a quality rather than a behaviour, which leaves a viewer guessing which one does what.
 *
 * # Storage, and why it is split in two
 *
 * `collapsible_navigation_enabled` predates this and travels with the account's cloud preferences,
 * where other StreamDek apps may read it. Its meaning is left exactly as it was — "the navigation
 * collapses" — and what triggers the collapse is a second, device-local key. A viewer choosing
 * Collapse While Scrolling on their phone therefore changes nothing anywhere else, and an older build
 * reading the synced boolean still sees a navigation that collapses.
 */
enum class NavigationBehaviour(
  @StringRes val labelRes: Int,
  @StringRes val descriptionRes: Int,
) {
  AlwaysExpanded(R.string.navigation_behaviour_always_expanded, R.string.navigation_behaviour_always_expanded_description),
  CollapseAfterDelay(R.string.navigation_behaviour_after_delay, R.string.navigation_behaviour_after_delay_description),
  CollapseWhileScrolling(R.string.navigation_behaviour_while_scrolling, R.string.navigation_behaviour_while_scrolling_description);

  /** The synced half. */
  val collapses: Boolean get() = this != AlwaysExpanded

  companion object {
    /** The device-local half: what triggers the collapse when [collapses] is true. */
    internal const val TRIGGER_PREFERENCE = "navigation_collapse_trigger"
    internal const val TRIGGER_DELAY = "delay"
    internal const val TRIGGER_SCROLL = "scroll"

    fun from(collapsible: Boolean, collapseOnScroll: Boolean): NavigationBehaviour = when {
      !collapsible -> AlwaysExpanded
      collapseOnScroll -> CollapseWhileScrolling
      else -> CollapseAfterDelay
    }
  }
}
