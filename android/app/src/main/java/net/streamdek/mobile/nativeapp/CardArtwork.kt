package net.streamdek.mobile.nativeapp

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

/**
 * A card's picture, placed to suit what the picture turns out to be.
 *
 * Artwork that fits the card is cropped to fill it, exactly as cards always were. A picture of
 * another shape - a wide add-on card in a portrait row, a square logo - is shown whole instead of
 * losing its title art to a crop; opaque ones sit over a dimmed copy of themselves so the card is
 * still full, and transparent ones sit on the card's own surface so their alpha is respected.
 *
 * One decode serves both layers, and the choice is made from the decoded image before it is first
 * drawn, so nothing reloads, shifts or flashes. The card's size never depends on the image.
 */
@Composable
internal fun CardArtwork(
  model: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
) {
  val painter = rememberAsyncImagePainter(model = model, contentScale = ContentScale.Crop)
  BoxWithConstraints(modifier = modifier.clipToBounds()) {
    val loaded = (painter.state as? AsyncImagePainter.State.Success)
    val scaling = loaded?.let { success ->
      val size = success.painter.intrinsicSize
      val hasAlpha = (success.result.drawable as? BitmapDrawable)?.bitmap?.hasAlpha() == true
      chooseCardArtworkScaling(size.width, size.height, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), hasAlpha)
    } ?: CardArtworkScaling.Crop
    if (scaling == CardArtworkScaling.FitOverBackdrop) {
      // Blur is a no-op before Android 12, where the dimmed crop alone still fills the card.
      Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(18.dp).alpha(0.38f),
      )
    }
    Image(
      painter = painter,
      contentDescription = contentDescription,
      contentScale = if (scaling == CardArtworkScaling.Crop) ContentScale.Crop else ContentScale.Fit,
      modifier = Modifier
        .fillMaxSize()
        // A transparent logo gets a little air, as it would on the add-on's own card.
        .then(if (scaling == CardArtworkScaling.Fit) Modifier.padding(maxWidth * 0.08f) else Modifier),
    )
  }
}
