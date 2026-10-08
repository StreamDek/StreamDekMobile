package net.streamdek.mobile.nativeapp.mediaserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaServerSearchMatchTest {
  @Test
  fun `related titles a server adds are not matches`() {
    assertFalse(titleMatchesSearch("euphoria", "Tracker"))
    assertFalse(titleMatchesSearch("euphoria", "Supergirl"))
    assertFalse(titleMatchesSearch("euphoria", "Hoppers"))
    assertTrue(titleMatchesSearch("euphoria", "Euphoria"))
    assertTrue(titleMatchesSearch("Euphoria", "Euphoria (US)"))
  }

  @Test
  fun `case accents punctuation and word order do not matter`() {
    assertTrue(titleMatchesSearch("amelie", "Amélie"))
    assertTrue(titleMatchesSearch("spiderman", "Spider-Man: No Way Home"))
    assertTrue(titleMatchesSearch("spider man", "Spider-Man"))
    assertTrue(titleMatchesSearch("office", "The Office"))
    assertTrue(titleMatchesSearch("bad breaking", "Breaking Bad"))
    assertTrue(titleMatchesSearch("star", "Star Wars"))
    assertFalse(titleMatchesSearch("star trek", "Star Wars"))
  }

  @Test
  fun `one slip in a longer word is forgiven and the original title counts`() {
    assertTrue(titleMatchesSearch("euphria", "Euphoria"))
    assertTrue(titleMatchesSearch("euphoriq", "Euphoria"))
    assertFalse(titleMatchesSearch("eup", "Hoppers"))
    // A short word must be typed as it is: "cat" is not "bat".
    assertFalse(titleMatchesSearch("cat", "Batman"))
    assertTrue(titleMatchesSearch("parasite", "기생충", "Parasite"))
    assertFalse(titleMatchesSearch("parasite", null, ""))
    assertTrue(titleMatchesSearch("  ", "Anything"))
  }

  @Test
  fun `one edit apart`() {
    assertTrue(withinOneEdit("euphoria", "euphoria"))
    assertTrue(withinOneEdit("euphria", "euphoria"))
    assertTrue(withinOneEdit("euphoriq", "euphoria"))
    assertTrue(withinOneEdit("euphorias", "euphoria"))
    assertFalse(withinOneEdit("euphoira", "euphoria"))
    assertFalse(withinOneEdit("supergirl", "euphoria"))
  }
}
