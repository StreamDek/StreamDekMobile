package net.streamdek.mobile.nativeapp
import org.junit.Assert.*
import org.junit.Test
class AddonMediaReferenceTest {
  @Test fun `source identity survives navigation and opaque encoding`() {
    val ref = AddonMediaReference("addon-a", "other", "cnc:%2F:some title/作品")
    assertEquals(ref, AddonMediaReference.decode(ref.encode()))
    assertNotEquals(ref.encode(), ref.copy(addonId="addon-b").encode())
    for (id in listOf("tmdb:tv:1399", "cnc:opaque", "sd-addon:bad", "sd-addon:a:b:c")) assertNull(AddonMediaReference.decode(id))
  }
}
