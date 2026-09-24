package net.streamdek.mobile.nativeapp

import java.io.IOException
import java.util.concurrent.CancellationException

internal class PluginSyncHttpFailure(val status: Int) : Exception("Plugin sync HTTP $status")

internal object PluginSyncRetry {
  const val attempts = 3
  fun retryable(error: Throwable): Boolean = when (error) {
    is CancellationException -> false
    is PluginSyncHttpFailure -> error.status == 408 || error.status == 429 || error.status in 500..599
    is IOException -> true
    else -> false
  }
  fun delayMs(attempt: Int, jitter: Long): Long = (500L shl attempt.coerceIn(0, 4)) + jitter.coerceIn(0, 500)
}
