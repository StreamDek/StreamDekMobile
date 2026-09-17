package net.streamdek.mobile.nativeapp

import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.runBlocking

/** Runs against Android storage and real episode metadata, without touching a viewer profile. */
class NextUpDeviceInstrumentation : Instrumentation() {
  override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
  override fun onStart() {
    val result = Bundle()
    try {
      testHistorySurvivesRecreationAndHonoursProfileAndClear()
      testRealMetadataResolvesImmediateAiredEpisode()
      result.putString("stream", "NextUp device tests: 2 passed (profile storage, real episode metadata).\n")
      finish(Activity.RESULT_OK, result)
    } catch (error: Throwable) {
      result.putString("stream", "NextUp device tests FAILED: ${error.javaClass.simpleName}: ${error.message}\n")
      finish(Activity.RESULT_CANCELED, result)
    }
  }
  private fun assertTrue(value: Boolean) = check(value)
  private fun assertEquals(expected: Any?, actual: Any?) = check(expected == actual) { "$expected != $actual" }
  private fun assertNotNull(message: String, value: Any?) = check(value != null) { message }

  fun testHistorySurvivesRecreationAndHonoursProfileAndClear() {
    val context = targetContext
    val owner = "next-up-instrumentation-${System.nanoTime()}"
    val event = PlaybackProgressRecord("tv", "1399", "s01e01", 1, 1, "Test", null, null, null,
      100.0, 100.0, 100.0, true, updatedAt = System.currentTimeMillis())
    val store = NextUpHistory(context)
    try {
      assertEquals(1, store.merge(owner, listOf(event)).size)
      assertEquals(1, NextUpHistory(context).merge(owner, emptyList()).size)
      assertTrue(store.merge("$owner-other", emptyList()).isEmpty())
      store.clear(owner)
      assertTrue(store.merge(owner, listOf(event)).isEmpty())
    } finally {
      context.getSharedPreferences("streamdek_next_up_history", 0).edit().remove(owner).remove("cleared:$owner").remove("$owner-other").apply()
    }
  }

  fun testRealMetadataResolvesImmediateAiredEpisode() = runBlocking {
    val anchor = PlaybackProgressRecord("tv", "1399", "s01e01", 1, 1, "Game of Thrones", null, null, null,
      100.0, 100.0, 100.0, true, updatedAt = System.currentTimeMillis())
    val result = NextUpResolver(StreamDekApiClient(targetContext)).resolve(anchor)
    assertNotNull("Real metadata must resolve the next episode", result)
    assertEquals(1, result!!.second.seasonNumber)
    assertEquals(2, result.second.episodeNumber)
    assertTrue(nextUpHasReleased(result.second.airDate))
  }
}
