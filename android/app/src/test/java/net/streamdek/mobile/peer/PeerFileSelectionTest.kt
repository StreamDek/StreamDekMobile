package net.streamdek.mobile.peer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PeerFileSelectionTest {
  @Test
  fun `season pack selects the requested episode rather than largest file`() {
    val selected = selectPeerVideoFile(
      listOf(
        PeerFileCandidate(0, "Show.S04E01.mkv", 900),
        PeerFileCandidate(1, "Show.S04E03.mkv", 800),
        PeerFileCandidate(2, "Show.S04E04.mkv", 1_200),
      ),
      null, "Show", 4, 3,
    )
    assertEquals(1, selected.index)
  }

  @Test
  fun `missing requested episode fails instead of playing another episode`() {
    assertThrows(IllegalStateException::class.java) {
      selectPeerVideoFile(
        listOf(PeerFileCandidate(0, "Show.S04E04.mkv", 1_200)),
        null, "Show", 4, 3,
      )
    }
  }

  @Test
  fun `similar series title is not accepted for the same episode`() {
    assertThrows(IllegalStateException::class.java) {
      selectPeerVideoFile(
        listOf(PeerFileCandidate(0, "Preacher.S04E05.mkv", 1_200)),
        null, "Reacher", 4, 5,
      )
    }
  }

  @Test
  fun `movie ignores sample and selects main video`() {
    val selected = selectPeerVideoFile(
      listOf(
        PeerFileCandidate(0, "sample.mkv", 100),
        PeerFileCandidate(1, "Movie.2026.mkv", 1_000),
        PeerFileCandidate(2, "poster.jpg", 5_000),
      ),
      null, "Movie", null, null,
    )
    assertEquals(1, selected.index)
  }
}
