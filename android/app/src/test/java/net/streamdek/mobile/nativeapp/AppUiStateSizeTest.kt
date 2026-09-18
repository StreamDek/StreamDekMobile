package net.streamdek.mobile.nativeapp

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A guard on how many properties the app state may hold.
 *
 * Kotlin generates `copy$default` for a data class taking every property at once, plus a bitmask
 * int per 32 of them. A dex method can be handed at most **255 argument registers** (a wide
 * property - Long or Double - takes two), and the app state sits a handful of properties below that
 * ceiling.
 *
 * Going past it does not fail the build. It compiles, it dexes, and then every method that calls
 * `uiState.copy(...)` fails verification at runtime:
 *
 *     java.lang.VerifyError: Verifier rejected class …NativeAppViewModel:
 *       [0x482] Rejecting invocation, expected 1 argument registers, method signature has 2 or more
 *
 * which is the whole app refusing to start, with nothing in the message that points at the cause.
 * This test is here so that arrives as a red test naming the ceiling instead.
 *
 * If it fails, do not raise the limit. Group the related new properties into one `@Immutable` data
 * class held as a single property - [HomeRowArrangement] and [GuestSetupTransfer] are two examples -
 * which costs one register however many fields it carries.
 */
class AppUiStateSizeTest {

  private companion object {
    /** The dex ceiling on argument registers for one invocation. */
    const val DEX_ARGUMENT_REGISTER_LIMIT = 255
  }

  @Test
  fun `the app state still fits in a dex method invocation`() {
    // Found by name: the class is file-private in Kotlin, which is package-private on the JVM, and
    // this test is in that package.
    val stateClass = Class.forName("net.streamdek.mobile.nativeapp.AppUiState")
    val properties = stateClass.declaredFields.filterNot { it.isSynthetic || it.name == "\$stable" }
    val wide = properties.count { it.type == java.lang.Long.TYPE || it.type == java.lang.Double.TYPE }
    val masks = (properties.size + 31) / 32
    // properties (wide ones twice) + one bitmask int per 32 + the instance + the null marker.
    val registers = properties.size + wide + masks + 2

    assertTrue(
      "AppUiState needs $registers argument registers to copy, over the dex limit of " +
        "$DEX_ARGUMENT_REGISTER_LIMIT (${properties.size} properties). Group related state into an " +
        "@Immutable holder rather than adding another top-level property - see this test's docs.",
      registers <= DEX_ARGUMENT_REGISTER_LIMIT,
    )
  }
}
