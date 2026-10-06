package net.streamdek.mobile.nativeapp

import org.junit.Assert.*
import org.junit.Test

class ProviderPlaybackProbeTest {
  @Test fun requiresRenderingAudioAndResumedSeek() {
    val events = mutableListOf<Boolean>()
    val p = ProviderPlaybackProbe { ok, _ -> events.add(ok) }
    p.reset(); p.frame(); p.audio()
    for (i in 0..12) p.tick(i * 1000L, true)
    assertTrue(events.isEmpty())
    p.seek(30000)
    for (i in 30..34) p.tick(i * 1000L, true)
    assertEquals(listOf(true), events)
    p.tick(35000, true)
    assertEquals(1, events.size)
    p.fail(); p.fail()
    assertEquals(listOf(true, false), events)
  }
  @Test fun initialResumeMissingAudioAndStallsDoNotVerify() {
    val events = mutableListOf<Boolean>()
    val p = ProviderPlaybackProbe { ok, _ -> events.add(ok) }
    p.reset(); p.seek(30000); p.frame()
    for (i in 30..60) p.tick(i * 1000L, true)
    p.seek(100000)
    for (i in 100..110) p.tick(i * 1000L, true)
    assertTrue(events.isEmpty())
    p.reset(); p.frame(); p.audio()
    for (i in 0..12) p.tick(i * 1000L, true)
    p.seek(30000)
    repeat(10) { p.tick(30000, true) }
    assertTrue(events.isEmpty())
    p.reset()
    for (i in 30..40) p.tick(i * 1000L, true)
    assertTrue(events.isEmpty())
  }
  @Test fun seekNeedsFreshAudioAndVideo() {
    val events = mutableListOf<Boolean>()
    val p = ProviderPlaybackProbe { ok, _ -> events.add(ok) }
    p.reset(); p.frame(); p.audio()
    for (i in 0..12) p.tick(i * 1000L, true)
    p.seek(30000)
    for (i in 30..34) p.tick(i * 1000L, true)
    assertTrue(events.isEmpty())
    p.frame(); p.tick(35000, true)
    assertTrue(events.isEmpty())
    p.audio(); p.tick(36000, true)
    assertEquals(listOf(true), events)
  }
}
