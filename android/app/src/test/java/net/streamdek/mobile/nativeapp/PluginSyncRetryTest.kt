package net.streamdek.mobile.nativeapp

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class PluginSyncRetryTest {
  @Test fun `only transient failures retry within a finite exponential budget`() {
    assertTrue(PluginSyncRetry.retryable(IOException("connection reset")))
    for (status in listOf(408, 429, 500, 502, 503, 504)) assertTrue(PluginSyncRetry.retryable(PluginSyncHttpFailure(status)))
    for (status in listOf(400, 401, 403, 404, 409, 413)) assertFalse(PluginSyncRetry.retryable(PluginSyncHttpFailure(status)))
    assertFalse(PluginSyncRetry.retryable(CancellationException()))
    assertFalse(PluginSyncRetry.retryable(IllegalArgumentException()))
    assertEquals(3, PluginSyncRetry.attempts)
    assertEquals(500L, PluginSyncRetry.delayMs(0, 0))
    assertEquals(1250L, PluginSyncRetry.delayMs(1, 250))
    assertEquals(2500L, PluginSyncRetry.delayMs(2, 900))
  }
}
