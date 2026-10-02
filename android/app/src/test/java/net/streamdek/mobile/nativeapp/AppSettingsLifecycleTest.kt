package net.streamdek.mobile.nativeapp

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

/** Accesses the actual private UI/store classes without exposing UI implementation types in production. */
class AppSettingsLifecycleTest {
  private val files = linkedMapOf<String, SettingsTestPreferences>()
  private val storeType = Class.forName("net.streamdek.mobile.nativeapp.AppSettingsStore")
  private val stateType = Class.forName("net.streamdek.mobile.nativeapp.AppUiState")
  private fun store(owner: String = "account:profile"): Any {
    val factory: (String) -> SharedPreferences = { files.getOrPut(it) { SettingsTestPreferences() } }
    val log: (String) -> Unit = {}
    val constructor = storeType.declaredConstructors.first { it.parameterCount == 2 }
    return constructor.apply { isAccessible = true }.newInstance(factory, log).also { call(it, "selectProfileStorage", owner) }
  }
  private fun call(target: Any, name: String, vararg args: Any?): Any? = target.javaClass.declaredMethods
    .first { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(target, *args)
  private fun state(store: Any): Any = call(store, "applyTo", stateType.getDeclaredConstructor().apply { isAccessible = true }.newInstance())!!
  private fun value(state: Any, name: String): Any? = stateType.getDeclaredField(name).apply { isAccessible = true }.get(state)
  private fun restart() { files.replaceAll { _, file -> file.restart() } }

  @Test fun `reported preferences survive termination and offline relaunch in actual settings store`() {
    val first = store()
    call(first, "savePictureInPictureEnabled", false)
    call(first, "saveMediaHubEnabled", true)
    call(first, "saveDoubleTapSeekSeconds", 15)
    call(first, "saveLiveProgressBarEnabled", true)
    restart()
    val restored = state(store())
    assertEquals(false, value(restored, "pictureInPictureEnabled"))
    assertEquals(true, value(restored, "mediaHubEnabled"))
    assertEquals(15, value(restored, "doubleTapSeekSeconds"))
    assertEquals(true, value(restored, "liveProgressBarEnabled"))
  }

  @Test fun `profiles accounts and anonymous settings remain isolated across relaunch`() {
    val settings = store("first:a")
    call(settings, "saveSubtitleBold", true)
    call(settings, "savePictureInPictureEnabled", false)
    call(settings, "selectProfileStorage", "first:b")
    assertEquals(false, value(state(settings), "subtitleBold"))
    assertEquals(false, value(state(settings), "pictureInPictureEnabled"))
    call(settings, "selectProfileStorage", "guest")
    call(settings, "saveSubtitleBold", false)
    call(settings, "savePictureInPictureEnabled", true)
    call(settings, "selectProfileStorage", "second:a")
    assertEquals(true, value(state(settings), "pictureInPictureEnabled"))
    restart()
    val restored = state(store("first:a"))
    assertEquals(true, value(restored, "subtitleBold"))
    assertEquals(false, value(restored, "pictureInPictureEnabled"))
  }

  @Test fun `upgrade repairs incomplete profile migration without resetting stored choices`() {
    files[APP_SETTINGS_PREFERENCES] = SettingsTestPreferences().apply {
      edit().putBoolean("show_new_episodes_row", true).putString("episode_layout", "Stack").putBoolean("pip_enabled", false).commit()
    }
    files[profileSettingsStorageName("account:profile")] = SettingsTestPreferences().apply {
      edit().putBoolean("subtitle_bold", true).putString("episode_layout", "Strip").commit()
    }
    val restored = state(store())
    assertEquals(true, value(restored, "showNewEpisodesRow"))
    assertEquals("Strip", value(restored, "episodeLayout").toString())
    assertEquals(false, value(restored, "pictureInPictureEnabled"))
    assertEquals(true, value(restored, "subtitleBold"))
  }

  @Test fun `every scalar settings setter persists through a new process`() {
    val first = store()
    var count = 0
    storeType.declaredMethods.filter { it.name.startsWith("save") && it.parameterCount == 1 }.forEach { method ->
      val type = method.parameterTypes.single()
      val argument: Any = when {
        type == Boolean::class.javaPrimitiveType -> false
        type == Int::class.javaPrimitiveType -> 15
        type == Float::class.javaPrimitiveType -> 1.5f
        type == String::class.java -> when (method.name) {
          "saveEnabledRatingProviders", "saveHomeCatalogRows" -> "[]"
          "savePreferredAudioLanguage", "savePreferredSubtitleLanguage" -> "fr"
          else -> "test"
        }
        type.isEnum -> type.enumConstants.last()
        else -> return@forEach
      }
      method.isAccessible = true
      method.invoke(first, argument)
      count++
    }
    assertTrue("Expected a broad settings audit, got $count setters", count > 85)
    val before = state(first)
    restart()
    val after = state(store())
    val fields = SettingsSyncRegistry.keysByField.keys - setOf("homeCatalogRowsJson", "homeRowMode", "homeRowSourceOrder", "visualEffects", "navigationBehaviour", "mdblistApiKey")
    fields.forEach { name -> assertEquals(name, value(before, name), value(after, name)) }
  }

  @Test fun `empty rating selection survives store reload`() {
    val settings = store()
    call(settings, "saveEnabledRatingProviders", emptySet<String>())
    restart()
    assertEquals(emptySet<String>(), value(state(store()), "enabledRatingProviders"))
  }

  @Test fun `profile audio language is adopted once without an upload and never replaces a local choice`() {
    val settings = store()
    call(settings, "seedPreferredAudioLanguage", "fr")
    assertEquals("fr", value(state(settings), "preferredAudioLanguage"))
    assertFalse("preferred_audio_language" in (call(settings, "pendingWrite") as PendingSettingsWrite).keys)
    call(settings, "savePreferredAudioLanguage", "de")
    assertTrue("preferred_audio_language" in (call(settings, "pendingWrite") as PendingSettingsWrite).keys)
    call(settings, "seedPreferredAudioLanguage", "fr")
    restart()
    val relaunched = store()
    assertEquals("de", value(state(relaunched), "preferredAudioLanguage"))
    assertTrue("preferred_audio_language" in (call(relaunched, "pendingWrite") as PendingSettingsWrite).keys)
  }
  @Test fun `legacy installation preferences are never migrated into a different account`() {
    files[APP_SETTINGS_PREFERENCES] = SettingsTestPreferences().apply {
      edit().putBoolean("subtitle_bold", true).putString("preferred_audio_language", "fr").commit()
    }
    val first = store("first:a")
    assertEquals(true, value(state(first), "subtitleBold"))
    call(first, "selectProfileStorage", "second:a")
    assertEquals(false, value(state(first), "subtitleBold"))
    assertFalse("subtitle_bold" in (call(first, "pendingWrite") as PendingSettingsWrite).keys)
    restart()
    assertEquals(true, value(state(store("first:a")), "subtitleBold"))
  }

}
