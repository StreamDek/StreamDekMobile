package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CompletedEpisodeWatchedIdsTest {
  @Test
  fun addsCompletedEpisodeOnce() {
    assertEquals(listOf("episode:show:1:1", "episode:show:1:2"), completedEpisodeWatchedIds(listOf("episode:show:1:1"), "episode:show:1:2"))
  }

  @Test
  fun preservesExistingListWhenAlreadyWatched() {
    val existing = listOf("episode:show:1:1")
    assertSame(existing, completedEpisodeWatchedIds(existing, "episode:show:1:1"))
  }
}
