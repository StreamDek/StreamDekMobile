package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoHSettingsTest {
  @Test fun `predefined providers use HTTPS endpoints`() {
    StreamDekDoHProviders.filter { it.id != "custom" }.forEach { provider ->
      assertNull(provider.label, DoHSettings.validateEndpoint(provider.endpoint.orEmpty()))
    }
  }

  @Test fun `custom endpoint rejects invalid and non HTTPS URLs`() {
    assertEquals("Enter a valid URL.", DoHSettings.validateEndpoint("not a url"))
    assertEquals("DNS over HTTPS requires an HTTPS URL.", DoHSettings.validateEndpoint("http://resolver.example/dns-query"))
    assertNull(DoHSettings.validateEndpoint("https://resolver.example/dns-query"))
  }
}
