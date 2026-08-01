package net.streamdek.mobile

import android.graphics.Color
import android.app.PictureInPictureParams
import android.content.Intent
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

class MainActivity : ComponentActivity() {
  private val pendingAddonManifestUrl = mutableStateOf<String?>(null)

  companion object {
    @JvmStatic
    var pipShouldEnter: Boolean = false
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (pipShouldEnter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      enterPictureInPictureMode(PictureInPictureParams.Builder().build())
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
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
