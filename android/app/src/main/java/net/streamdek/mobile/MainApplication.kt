package net.streamdek.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import net.streamdek.mobile.nativeapp.EpisodeNotificationSystem

/**
 * The app, and the one image loader everything in it draws through.
 *
 * Without an [ImageLoaderFactory] Coil builds a default loader on first use, and two of those
 * defaults are wrong for a screen that is mostly artwork:
 *
 * The default OkHttp dispatcher allows **five** concurrent requests per host. Every poster, every
 * backdrop and every episode still comes from `image.tmdb.org` — one host — so the whole app was
 * loading artwork five images at a time no matter how many rows were on screen. A home screen
 * asking for a hundred posters queued ninety-five of them behind the first five, which is why a
 * row often only filled in as it was scrolled to: scrolling did not start those requests, it just
 * gave the queue time to reach them.
 *
 * And the default respects cache headers. TMDB artwork at a given path never changes — the size is
 * in the URL — so revalidating it costs a round trip to be told nothing happened.
 */
class MainApplication : Application(), ImageLoaderFactory {
  override fun onCreate() {
    net.streamdek.mobile.nativeapp.Perf.startupMark("application.onCreate")
    super.onCreate()
    // First thing the process does, because what it affects is the *next* launch: the system builds
    // the splash from the manifest theme before any of this runs, and this is what tells it which
    // night mode to build it in.
    net.streamdek.mobile.nativeapp.applyAppNightMode(this)
    EpisodeNotificationSystem.ensureBackgroundWork(this)
  }

  override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
    .okHttpClient {
      OkHttpClient.Builder()
        .dispatcher(
          Dispatcher().apply {
            maxRequests = 64
            // The number that matters. Artwork is one host, so this is effectively the app's
            // whole artwork concurrency; five made a grid load in visible waves.
            maxRequestsPerHost = 24
          },
        )
        // Deep enough that those parallel requests reuse connections instead of paying a TLS
        // handshake each, and idle long enough to still be warm when the next row scrolls in.
        .connectionPool(okhttp3.ConnectionPool(32, 5, TimeUnit.MINUTES))
        .build()
    }
    .memoryCache {
      // The manifest asks for a large heap, and artwork is what it is for: scrolling back up a
      // home screen should not re-decode a poster that was on screen four rows ago.
      MemoryCache.Builder(this).maxSizePercent(0.25).build()
    }
    .diskCache {
      DiskCache.Builder()
        .directory(cacheDir.resolve("image_cache"))
        .maxSizeBytes(256L * 1024 * 1024)
        .build()
    }
    .respectCacheHeaders(false)
    // Artwork used to appear by popping from empty to opaque in a single frame, which is the
    // harshest thing a screen made almost entirely of posters can do.
    //
    // This is cheaper than it looks. Coil's crossfade transition sits out any result that came
    // from the memory cache, so scrolling back over a row that is already decoded still snaps
    // instantly - the fade is only paid on an image the user has genuinely not seen yet. Two call
    // sites still pass `crossfade(false)` and should keep it: the profile hero already cross-fades
    // itself through an AnimatedContent, and a 24dp rating badge is too small for a fade to read
    // as anything but a flicker.
    .crossfade(if (animationsDisabled()) 0 else 180)
    .build()

  /**
   * Whether the device has been asked to stop animating.
   *
   * The same animator duration scale the UI reads through `LocalReducedMotion`. The image loader
   * is built before any of the Compose tree exists, so it has to ask the setting directly rather
   * than through a composition local.
   */
  private fun animationsDisabled(): Boolean = runCatching {
    android.provider.Settings.Global.getFloat(
      contentResolver,
      android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
      1f,
    )
  }.getOrDefault(1f) == 0f
}
