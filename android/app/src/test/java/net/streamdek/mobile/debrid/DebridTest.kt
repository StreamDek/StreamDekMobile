package net.streamdek.mobile.debrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision-making parts of on-device debrid: which file to play, whether two release names
 * describe the same thing, and what to tell the viewer when a provider says no.
 *
 * These are the pieces that decide what a viewer actually gets, and the ones a wrong answer is
 * silent in — a mis-picked file plays the wrong episode rather than failing, and a mis-read error
 * tells someone to renew a subscription that is perfectly fine.
 */
class DebridTest {

  // -- picking the file out of a torrent -------------------------------------------------------

  @Test
  fun `an exact filename match wins over a larger file`() {
    val links = listOf(
      DebridStreamLink("url-big", "Show.S01E02.1080p.mkv", filesize = 5_000_000_000),
      DebridStreamLink("url-wanted", "Show.S01E01.1080p.mkv", filesize = 1_000_000_000),
    )
    assertEquals("url-wanted", pickBestLink(links, "Show.S01E01.1080p.mkv").url)
  }

  @Test
  fun `a filename matches when only the extension differs`() {
    val links = listOf(
      DebridStreamLink("url-other", "Show.S01E09.mkv", filesize = 900),
      DebridStreamLink("url-wanted", "Show.S01E01.1080p.mkv", filesize = 100),
    )
    assertEquals("url-wanted", pickBestLink(links, "Show.S01E01.1080p.mp4").url)
  }

  @Test
  fun `with no filename to go on, the largest video wins`() {
    val links = listOf(
      DebridStreamLink("url-small", "sample.mp4", filesize = 20_000_000),
      DebridStreamLink("url-feature", "feature.mkv", filesize = 8_000_000_000),
    )
    assertEquals("url-feature", pickBestLink(links, null).url)
  }

  @Test
  fun `a sample is not chosen over the feature it sits beside`() {
    // Deepbrid packs return exactly this pair, and the sample is first in the list.
    val links = listOf(
      DebridStreamLink("url-sample", "sample.mp4", filesize = 30_000_000),
      DebridStreamLink("url-movie", "Mortal.Kombat.II.2026.2160p.mkv", filesize = 60_000_000_000),
    )
    assertEquals("url-movie", pickBestLink(links, "Mortal.Kombat.II.2026.2160p.mkv").url)
  }

  @Test
  fun `a torrent of non-video files still yields something rather than nothing`() {
    val links = listOf(DebridStreamLink("url-only", "readme.txt", filesize = 10))
    assertEquals("url-only", pickBestLink(links, null).url)
  }

  @Test
  fun `a short filename stem is not used for matching`() {
    // A stem of four characters or fewer matches far too much; the size rule is the safer answer.
    val links = listOf(
      DebridStreamLink("url-big", "Up.and.Away.2020.1080p.mkv", filesize = 9_000),
      DebridStreamLink("url-small", "Up.mkv", filesize = 10),
    )
    assertEquals("url-big", pickBestLink(links, "Up.mp4").url)
  }

  // -- release name matching -------------------------------------------------------------------

  @Test
  fun `punctuation differences do not stop two names matching`() {
    assertTrue(releaseNamesMatch("Some.Film.2026.2160p.WEB-DL", "Some Film 2026 2160p WEB DL"))
    assertTrue(releaseNamesMatch("Some_Film_2026_2160p", "Some.Film.2026.2160p"))
  }

  @Test
  fun `an extension does not stop a name matching`() {
    assertTrue(releaseNamesMatch("Some.Film.2026.2160p.WEB.mkv", "Some.Film.2026.2160p.WEB"))
  }

  @Test
  fun `an episode matches the pack that holds it`() {
    assertTrue(releaseNamesMatch("Some.Show.S01.COMPLETE.1080p.WEB", "Some.Show.S01.COMPLETE.1080p"))
  }

  @Test
  fun `unrelated releases do not match`() {
    assertFalse(releaseNamesMatch("Some.Film.2026.2160p", "Another.Film.2019.1080p"))
  }

  @Test
  fun `short names are never matched by containment`() {
    // "Up" appearing inside "Up and Away" would otherwise mark half a library as cached.
    assertFalse(releaseNamesMatch("Up", "Up.and.Away.2020.1080p.WEB"))
  }

  @Test
  fun `an empty name matches nothing`() {
    assertFalse(releaseNamesMatch("", "Some.Film.2026"))
    assertFalse(releaseNamesMatch("Some.Film.2026", "   "))
  }

  // -- magnet and URL parsing ------------------------------------------------------------------

  @Test
  fun `a magnet display name is decoded`() {
    assertEquals(
      "Some Film 2026 2160p",
      magnetDisplayName("magnet:?xt=urn:btih:abc123&dn=Some%20Film%202026%202160p"),
    )
  }

  @Test
  fun `a magnet with no display name yields nothing to match on`() {
    assertNull(magnetDisplayName("magnet:?xt=urn:btih:abc123"))
  }

  @Test
  fun `a delivered filename is read from the redirect target`() {
    assertEquals(
      "mortal.kombat.ii.2026.2160p.web.h265-TRB.mkv",
      filenameFromUrl("https://byron.myfast.link/rd/dl/torrent/e50923c6dd/mortal.kombat.ii.2026.2160p.web.h265-TRB.mkv"),
    )
  }

  @Test
  fun `plus signs in a delivered filename become spaces`() {
    assertEquals(
      "Mortal Kombat II 2026 2160p.mkv",
      filenameFromUrl("https://n.myfast.link/n/dl/torrent/6afb/Mortal+Kombat+II+2026+2160p.mkv"),
    )
  }

  // -- archives and video files ----------------------------------------------------------------

  @Test
  fun `an archive is not treated as playable`() {
    assertTrue(isArchiveFile("Sintel.rar"))
    assertTrue(isArchiveFile("Pack.7z"))
    assertFalse(isArchiveFile("Sintel.mkv"))
  }

  @Test
  fun `video files are recognised by extension`() {
    assertTrue(isVideoFile("Show.S01E01.mkv"))
    assertTrue(isVideoFile("movie.MP4"))
    assertFalse(isVideoFile("readme.txt"))
    // A Deepbrid listing names single-file torrents after the torrent, with no extension at all.
    assertFalse(isVideoFile("Some.Film.2026.2160p.WEB-DL"))
  }

  // -- failure classification ------------------------------------------------------------------

  @Test
  fun `a premium-only refusal is reported as needing a subscription`() {
    val failure = debridFailureFor("deepbrid", IllegalStateException("Deepbrid: this action requires a premium subscription"))
    assertEquals(DebridFailureCode.SubscriptionRequired, failure.code)
  }

  @Test
  fun `a rate limit is recognised from the status and from the message`() {
    assertEquals(
      DebridFailureCode.RateLimited,
      debridFailureFor("torbox", DebridHttpException(429, "Too Many Requests")).code,
    )
    // Deepbrid answers HTTP 200 and puts the refusal in the body, so the text has to carry it.
    assertEquals(
      DebridFailureCode.RateLimited,
      debridFailureFor("deepbrid", IllegalStateException("Deepbrid: rate limited — too many requests")).code,
    )
  }

  @Test
  fun `an unauthorised key is reported as access denied rather than a dead subscription`() {
    assertEquals(
      DebridFailureCode.AccessDenied,
      debridFailureFor("real-debrid", DebridHttpException(401, "Unauthorized")).code,
    )
  }

  @Test
  fun `an unsupported hoster is classified as such`() {
    assertEquals(
      DebridFailureCode.UnsupportedHost,
      debridFailureFor("deepbrid", IllegalStateException("Deepbrid: Filehoster not supported")).code,
    )
  }

  @Test
  fun `a provider outage is an upstream error`() {
    assertEquals(
      DebridFailureCode.UpstreamError,
      debridFailureFor("premiumize", DebridHttpException(503, "Service Unavailable")).code,
    )
  }

  @Test
  fun `anything unrecognised keeps its message rather than being dressed up`() {
    val failure = debridFailureFor("alldebrid", IllegalStateException("something odd happened"))
    assertEquals(DebridFailureCode.Unknown, failure.code)
    assertEquals("something odd happened", failure.message)
  }

  // -- size parsing ----------------------------------------------------------------------------

  @Test
  fun `human readable sizes become bytes`() {
    assertEquals(1_610_612_736L, parseSizeToBytes("1.50 GB"))
    assertEquals(1024L, parseSizeToBytes("1 KB"))
    assertEquals(0L, parseSizeToBytes("not a size"))
  }

  // -- Premiumize PKCE ---------------------------------------------------------------------

  @Test
  fun `the S256 challenge matches the published PKCE test vector`() {
    // RFC 7636 appendix B. If this drifts, every authorization is rejected at the token
    // exchange with nothing on screen to explain why.
    val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    val challenge = PremiumizeOAuth.challengeFor(verifier)
    assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
  }

  @Test
  fun `a fresh verifier is the length and alphabet the spec allows`() {
    val challenge = PremiumizeOAuth.newChallenge()
    assertTrue(challenge.verifier.length in 43..128)
    assertTrue(challenge.verifier.all { it.isLetterOrDigit() || it in "-._~" })
    // Padding would be rejected: the challenge travels in a URL.
    assertFalse(challenge.challenge.contains("="))
    assertTrue(challenge.state.isNotBlank())
  }

  @Test
  fun `two attempts never share a verifier`() {
    assertTrue(PremiumizeOAuth.newChallenge().verifier != PremiumizeOAuth.newChallenge().verifier)
  }

  @Test
  fun `the authorize url carries the challenge and never the verifier`() {
    val challenge = PremiumizeOAuth.newChallenge()
    val url = PremiumizeOAuth.authorizeUrl("47770900", challenge)
    assertTrue(url.startsWith("https://www.premiumize.me/authorize"))
    assertTrue(url.contains("code_challenge_method=S256"))
    assertTrue(url.contains("client_id=47770900"))
    // The point of PKCE: only the hash travels on the first redirect.
    assertFalse(url.contains(challenge.verifier))
  }

  @Test
  fun `a redirect with the wrong state is refused`() {
    val redirect = "streamdek://premiumize/callback?code=abc123&state=somebody-elses"
    assertNull(PremiumizeOAuth.codeFromRedirect(redirect, "ours"))
  }

  @Test
  fun `a matching redirect yields the code`() {
    val redirect = "streamdek://premiumize/callback?code=abc123&state=ours"
    assertEquals("abc123", PremiumizeOAuth.codeFromRedirect(redirect, "ours"))
  }

  @Test
  fun `a redirect carrying an error rather than a code yields nothing`() {
    val redirect = "streamdek://premiumize/callback?error=access_denied&state=ours"
    assertNull(PremiumizeOAuth.codeFromRedirect(redirect, "ours"))
  }
}
