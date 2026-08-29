package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStreamIdentityTest {
  @Test
  fun `similar series names are not the same identity`() {
    assertFalse(cloudStreamTitleMatches("Reacher", "Preacher"))
    assertFalse(cloudStreamTitleMatches("Preacher", "Reacher"))
  }

  @Test
  fun `formatting differences preserve exact title identity`() {
    assertTrue(cloudStreamTitleMatches("The Bear", "the-bear (2022)"))
  }

  @Test
  fun `episode identity requires exact season and episode`() {
    assertTrue(cloudStreamEpisodeMatches(4, 5, 4, 5))
    assertFalse(cloudStreamEpisodeMatches(4, 5, 4, 4))
    assertFalse(cloudStreamEpisodeMatches(4, 5, 3, 5))
    assertFalse(cloudStreamEpisodeMatches(4, 5, null, 5))
  }
}
