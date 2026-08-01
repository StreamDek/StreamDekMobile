package net.streamdek.mobile.nativeapp

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight, purely local "rename" store for installed Add-ons and Plugin collections.
 *
 * Add-on manifests and Plugin repository manifests are owned by their remote publisher, so
 * renaming them isn't something that can (or should) be pushed back upstream. Instead we keep
 * a small on-device override table keyed by a stable identifier (addon id / repo url) that the
 * UI consults whenever it would otherwise show the publisher-provided name.
 */
object DisplayNameOverrides {
  private const val PREFS_NAME = "streamdek_display_name_overrides"
  private var prefs: SharedPreferences? = null

  fun initialize(context: Context) {
    if (prefs == null) {
      prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
  }

  /** Returns the user-chosen override for [key], or null if it hasn't been renamed. */
  fun get(key: String): String? = prefs?.getString(key, null)?.takeIf { it.isNotBlank() }

  /** Applies [override] to whatever [fallback] name would otherwise be shown. */
  fun resolve(key: String, fallback: String): String = get(key) ?: fallback

  fun set(key: String, override: String?) {
    val editor = prefs?.edit() ?: return
    if (override.isNullOrBlank()) editor.remove(key) else editor.putString(key, override.trim())
    editor.apply()
  }

  fun clear(key: String) = set(key, null)
}
