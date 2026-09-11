package com.lagradost.cloudstream3.ui.settings

/**
 * The part of CloudStream's `ui.settings.Globals` that extensions call into.
 *
 * It lives in the CloudStream app rather than its provider API, so the trimmed runtime StreamDek
 * ships (libs/cloudstream-provider-runtime.jar) does not have it. Extensions that ask which layout
 * they are running under — CNCVerse's Cloudflare solver among them — failed with a missing class
 * the moment they did. Same package, same name and same members, so the call resolves here.
 *
 * StreamDek Mobile is always the phone layout.
 */
object Globals {
  const val PHONE: Int = 0b001
  const val TV: Int = 0b010
  const val EMULATOR: Int = 0b100

  private const val LAYOUT: Int = PHONE

  /** True when any of [flags] names the layout this app is running under. */
  fun isLayout(flags: Int): Boolean = (LAYOUT and flags) != 0
}
