package net.streamdek.mobile.nativeapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
* The Jellyfin mark for StreamDek's navigation, and the combined mark shown when Plex and Jellyfin
* are both connected.
*
* Single-colour, like the Plex chevron beside it, so it takes the same tint and focus treatment as
* every other destination icon. The full-colour mark is `R.drawable.jellyfin_logo`, for places that
* name the service.
*
* Jellyfin logo geometry from jellyfin/jellyfin-ux (logos/SVG), (c) Jellyfin contributors, licensed
* CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/). Used unchanged; credited in Settings.
*/
internal object JellyfinIcons {
  private const val INNER = "M24.2116 49.1581C22.6599 46.0424 32.8378 27.5879 35.9999 27.5879C39.1666 27.5895 49.3228 46.0764 47.7882 49.1581C46.2536 52.2398 25.7632 52.2738 24.2116 49.1581Z"
  private const val OUTER = "M0.481861 64.9951C-4.19479 55.6047 26.4765 0 36 0C45.5328 0 76.153 55.713 71.5274 64.9951C66.9018 74.2773 5.15852 74.3856 0.481861 64.9951ZM12.7358 56.847C15.8005 62.9995 56.2536 62.9314 59.2843 56.847C62.3149 50.761 42.2515 14.2605 36.0093 14.2605C29.767 14.2605 9.67118 50.6944 12.7358 56.847Z"

  val Mark: ImageVector by lazy {
    ImageVector.Builder(name = "JellyfinMark", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 72f, viewportHeight = 72f)
      .addPath(addPathNodes(INNER), fill = SolidColor(Color.White))
      .addPath(addPathNodes(OUTER), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.White))
      .build()
  }

  /**
  * Both servers' marks, overlapped like a stack of avatars: the Plex chevron behind at half
  * strength, the Jellyfin mark in front. The tint keeps that difference in strength, so the two
  * still read as two marks in one colour.
  */
  val Stack: ImageVector by lazy {
    val jellyfinScale = 15f / 72f
    ImageVector.Builder(name = "PersonalMediaStack", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
      .addGroup(name = "plex", scaleX = 0.75f, scaleY = 0.75f, translationX = -3.2f, translationY = 3f)
      .addPath(addPathNodes(PLEX_CHEVRON), fill = SolidColor(Color.White), fillAlpha = 0.5f)
      .clearGroup()
      .addGroup(name = "jellyfin", scaleX = jellyfinScale, scaleY = jellyfinScale, translationX = 8.6f, translationY = 4.6f)
      .addPath(addPathNodes(INNER), fill = SolidColor(Color.White))
      .addPath(addPathNodes(OUTER), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.White))
      .clearGroup()
      .build()
  }

  /** Same geometry as PlexIcons.Chevron and res/drawable/ic_plex_chevron.xml. */
  private const val PLEX_CHEVRON = "M6.2,3.2L11.9,3.2Q12.4,3.2 12.7,3.6L18.6,11.4Q19.05,12 18.6,12.6L12.7,20.4Q12.4,20.8 11.9,20.8L6.2,20.8Q5.6,20.8 6,20.3L12.1,12L6,3.7Q5.6,3.2 6.2,3.2Z"
}
