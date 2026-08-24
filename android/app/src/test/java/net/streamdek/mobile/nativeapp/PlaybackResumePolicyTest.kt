package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumePolicyTest {
  @Test fun `exact SyncDek seconds win over a percentage`() {
    assertEquals(1122.0, playerResumePosition(3000.0, 1122.0, 5.0), 0.0)
  }

  @Test fun `old percentage-only entries still resume`() {
    assertEquals(300.0, playerResumePosition(1200.0, 0.0, 25.0), 0.0)
  }

  @Test fun `resume is clamped when a replacement source is shorter`() {
    assertEquals(895.0, playerResumePosition(900.0, 1122.0, 0.0), 0.0)
  }
}
