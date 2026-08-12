package net.streamdek.mobile

import android.graphics.Color
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.streamdek.mobile.nativeapp.StreamDekNativeApp
import net.streamdek.mobile.nativeapp.normalizeAddonManifestUrl
import net.streamdek.mobile.nativeapp.localizedAppContext

class MainActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(localizedAppContext(newBase))
  }

  private val pendingAddonManifestUrl = mutableStateOf<String?>(null)

  companion object {
    @JvmStatic
    var pipShouldEnter: Boolean = false

    /**
     * Set while the video player has taken orientation for itself.
     *
     * The player turns the activity landscape and restores it on the way out. Without this flag the
     * rotation it asks for would arrive back here as a configuration change and be immediately
     * undone, leaving a phone stuck in portrait the moment playback started.
     */
    @JvmStatic
    var playerOwnsOrientation: Boolean = false

    /** The platform's own line between a phone and something larger. */
    private const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (pipShouldEnter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      enterPictureInPictureMode(PictureInPictureParams.Builder().build())
    }
  }

  /**
   * Who is allowed to rotate.
   *
   * The manifest no longer pins the activity to portrait, because doing so is what made StreamDek
   * feel like a phone app propped up on a tablet. The restriction still applies to phones, though:
   * their layouts are built for one column held upright, and letting a phone rotate would be a
   * change to the existing experience rather than an improvement to it.
   *
   * The test is the smallest width the window can have, not the current one — that value does not
   * change as the device turns, so a tablet cannot lock itself to portrait by being in portrait
   * when this runs.
   */
  private fun applyOrientationPolicy() {
    if (playerOwnsOrientation) return
    val smallestWidthDp = resources.configuration.smallestScreenWidthDp
    requestedOrientation = if (smallestWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP) {
      ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    // A foldable opening, or the app being dragged between windowing modes, can move the device
    // across the threshold while it is running.
    applyOrientationPolicy()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    applyOrientationPolicy()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.BLACK
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    handleDeepLink(intent)
    setContent {
      StreamDekNativeApp(
        pendingAddonManifestUrl = pendingAddonManifestUrl.value,
        onAddonManifestConsumed = { pendingAddonManifestUrl.value = null },
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLink(intent)
  }

  private fun handleDeepLink(intent: Intent?) {
    if (intent?.action != Intent.ACTION_VIEW) return
    pendingAddonManifestUrl.value = normalizeAddonManifestUrl(intent.dataString ?: return)
  }
}
