package net.streamdek.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
    .crossfade(false)
    .build()
}
