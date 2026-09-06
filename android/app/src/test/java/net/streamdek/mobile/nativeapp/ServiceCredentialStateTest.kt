package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the content-service credential model that do not need a device.
 *
 * The vault itself is Android keystore-backed and belongs in an instrumented test; what is worth
 * pinning here is the reasoning laid on top of it — what a viewer is told, and what a masked key
 * is allowed to contain.
 */
class ServiceCredentialStateTest {

  @Test
  fun `a masked key reveals four characters and no more`() {
    val masked = maskServiceKey("abcdef0123456789")
    assertEquals("••••••••6789", masked)
    assertFalse("the key itself must never survive masking", masked.contains("abcdef"))
  }

  @Test
  fun `masking a short key still reveals nothing extra`() {
    assertEquals("••••••••abcd", maskServiceKey("abcd"))
  }

  @Test
  fun `service ids round-trip, and anything else is not a service`() {
    assertEquals(ContentService.Tmdb, ContentService.fromId("tmdb"))
    assertEquals(ContentService.Mdblist, ContentService.fromId("MDBList"))
    assertNull(ContentService.fromId("trakt"))
    assertNull(ContentService.fromId(null))
  }

  @Test
  fun `every service explains itself well enough to be worth setting up`() {
    ContentService.values().forEach { service ->
      // Resource ids rather than text: the wording is whichever language the viewer chose, so what
      // is checked here is that every service was given one at all. A missing id is 0.
      assertTrue("${service.label} needs a tagline", service.taglineRes != 0)
      assertTrue("${service.label} needs a blurb", service.blurbRes != 0)
      assertTrue("${service.label} needs at least one stated use", service.usesRes.isNotEmpty())
      assertTrue("${service.label} needs steps for getting a key", service.howToGetRes.isNotEmpty())
      assertTrue("${service.label} needs a hint for its key field", service.keyHintRes != 0)
      assertTrue("${service.label} needs somewhere to get one", service.keyUrl.startsWith("https://"))
    }
  }

  @Test
  fun `a configured service is one that is connected or needs attention`() {
    val connected = ContentServiceState(ContentService.Tmdb, status = CredentialStatus.Connected)
    val stale = ContentServiceState(ContentService.Tmdb, status = CredentialStatus.NeedsAttention)
    val absent = ContentServiceState(ContentService.Tmdb, status = CredentialStatus.NotConfigured)

    assertTrue(connected.configured)
    // A key that has stopped working is still a key the viewer configured: reporting it as
    // "not configured" would offer them Add rather than Update, and lose the explanation.
    assertTrue(stale.configured)
    assertFalse(absent.configured)
  }

  @Test
  fun `the two services are tracked independently`() {
    val state = ContentServicesState()
      .with(ContentServiceState(ContentService.Tmdb, status = CredentialStatus.Connected))

    assertTrue(state.tmdb.configured)
    assertFalse("configuring TMDB must not imply MDBList", state.mdblist.configured)
    assertTrue(state.anyConfigured)
    assertFalse(state.allConfigured)
  }

  @Test
  fun `services needing attention are reported so a device can offer to update them`() {
    val state = ContentServicesState()
      .with(ContentServiceState(ContentService.Mdblist, status = CredentialStatus.NeedsAttention))
    assertEquals(listOf(ContentService.Mdblist), state.needsAttention.map { it.service })
  }

  @Test
  fun `the settings summary says what is actually true`() {
    val nothing = ContentServicesState()
    assertEquals("Use your own TMDB and MDBList keys", contentServicesSubtitle(nothing))

    val onlyTmdb = nothing.with(ContentServiceState(ContentService.Tmdb, status = CredentialStatus.Connected))
    assertTrue(contentServicesSubtitle(onlyTmdb).contains("MDBList"))

    val both = onlyTmdb.with(ContentServiceState(ContentService.Mdblist, status = CredentialStatus.Connected))
    assertEquals("TMDB and MDBList connected", contentServicesSubtitle(both))

    // A broken key outranks a count of working ones: it is the thing the viewer has to act on.
    val broken = both.with(ContentServiceState(ContentService.Tmdb, status = CredentialStatus.NeedsAttention))
    assertTrue(contentServicesSubtitle(broken).contains("needs attention"))
  }

  @Test
  fun `each storage mode carries its own wording`() {
    // The sentences moved into string resources, so this can no longer read them: a plain JVM
    // test has no resources to resolve against, and asserting on English would be asserting on
    // one locale of eight. What is still worth pinning down is that the two modes are
    // distinguishable and neither is left without a label or an explanation; that the sentences
    // exist in every language is checked by scripts/check-translations.mjs.
    for (storage in CredentialStorage.entries) {
      assertNotEquals(0, storage.labelRes)
      assertNotEquals(0, storage.detailRes)
    }
    assertNotEquals(CredentialStorage.Account.labelRes, CredentialStorage.Device.labelRes)
    assertNotEquals(CredentialStorage.Account.detailRes, CredentialStorage.Device.detailRes)
  }

  @Test
  fun `a refused key and an unreachable service are never the same message`() {
    assertEquals(CredentialFailure.InvalidKey, CredentialFailure.fromId("invalid_key"))
    assertEquals(CredentialFailure.Malformed, CredentialFailure.fromId("malformed"))
    assertEquals(CredentialFailure.ServiceUnavailable, CredentialFailure.fromId("service_unavailable"))

    // The distinction matters: one asks the viewer to fix their key, the other asks them to wait.
    assertTrue(CredentialFailure.ServiceUnavailable.message.contains("hasn't been checked"))
    assertFalse(CredentialFailure.ServiceUnavailable.message.contains("copied"))
  }

  @Test
  fun `no failure message leaks an HTTP status at a viewer`() {
    CredentialFailure.values().forEach { failure ->
      assertFalse(failure.name, failure.message.contains("HTTP"))
      assertFalse(failure.name, Regex("\\b[45]\\d\\d\\b").containsMatchIn(failure.message))
    }
  }
}
