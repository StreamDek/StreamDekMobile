package net.streamdek.mobile.nativeapp

/**
 * The Home hero as an endless loop: swiping on from the last title arrives at the first, and back
 * from the first arrives at the last.
 *
 * The pager is given far more pages than there are titles and each page shows the title at
 * `page mod count`, starting in the middle so there is room to go either way. Nobody swipes fifty
 * thousand times in one direction, so the ends are never reached. This is simpler and steadier
 * than snapping back to a real index after each turn: a snap mid-gesture would jump the backdrop
 * and restart its image request, and the pager's own fling physics stay untouched.
 *
 * With one title (or none) there is nothing to loop through, so the pager keeps its real count.
 */
internal const val HOME_HERO_LOOP_PAGES = 100_000

/** How many pages the hero pager exposes for [itemCount] titles. */
internal fun homeHeroPageCount(itemCount: Int): Int = if (itemCount > 1) HOME_HERO_LOOP_PAGES else itemCount

/** The page the loop starts on: the middle of the range, lined up so it shows the first title. */
internal fun homeHeroLoopStart(itemCount: Int): Int {
  if (itemCount <= 1) return 0
  val middle = HOME_HERO_LOOP_PAGES / 2
  return middle - middle % itemCount
}

/** Which title a pager page shows. */
internal fun homeHeroItemIndex(page: Int, itemCount: Int): Int = if (itemCount <= 0) 0 else page.mod(itemCount)

/**
 * The page to animate to for a dot, taking the short way round.
 *
 * Going to the literal page for that title would, in a loop, sometimes travel most of the way
 * round in the wrong direction: from the last title, tapping the first dot should step forward
 * once, not rewind through everything in between.
 */
internal fun homeHeroPageForItem(currentPage: Int, itemIndex: Int, itemCount: Int): Int {
  if (itemCount <= 1) return itemIndex.coerceAtLeast(0)
  var delta = (itemIndex - homeHeroItemIndex(currentPage, itemCount)).mod(itemCount)
  if (delta > itemCount / 2) delta -= itemCount
  return currentPage + delta
}

/**
 * The page nearest [position] that shows [itemIndex], for working out how lit that title's dot is.
 *
 * [position] is the pager's continuous position (current page plus offset fraction). Mid-swipe from
 * the last title to the first, the first title's nearest page is one ahead rather than a whole
 * lap behind, so its dot fills as the swipe progresses instead of staying dark.
 */
internal fun homeHeroNearestPageForItem(position: Float, itemIndex: Int, itemCount: Int): Int {
  if (itemCount <= 1) return itemIndex
  val anchor = kotlin.math.floor(position).toInt()
  val base = anchor - homeHeroItemIndex(anchor, itemCount) + itemIndex
  return listOf(base - itemCount, base, base + itemCount).minBy { kotlin.math.abs(it - position) }
}
