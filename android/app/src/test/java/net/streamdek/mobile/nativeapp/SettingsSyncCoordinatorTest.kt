package net.streamdek.mobile.nativeapp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SettingsSyncCoordinatorTest {
  private val first = SettingsSyncOwner("account", "first", 1)
  private val second = SettingsSyncOwner("account", "second", 2)
  private var owner: SettingsSyncOwner? = first
  private val disk = SettingsTestPreferences()
  private val file = DurableSettingsPreferences(disk, SettingsSyncRegistry.syncedKeys) {}
  private fun snapshot() = CloudPlaybackPreferences(pictureInPictureEnabled = file.getBoolean("pip_enabled", true), mediaHubEnabled = file.getBoolean("media_hub_enabled", false))
  private fun coordinator(
    upload: suspend (SettingsSyncOwner, CloudPlaybackPreferences) -> Result<Unit> = { _, _ -> Result.success(Unit) },
    download: suspend (SettingsSyncOwner) -> Result<CloudPlaybackPreferences> = { Result.success(CloudPlaybackPreferences()) },
    apply: (CloudPlaybackPreferences) -> Unit = {},
  ) = SettingsSyncCoordinator({ owner }, { PendingSettingsWrite(listOf(file to file.pending())) }, ::snapshot, upload, download, apply) {}

  @Test fun `slow upload sends newer edit afterwards and never loses it`() = runBlocking {
    file.edit().putBoolean("pip_enabled", false).apply()
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val writes = mutableListOf<Boolean?>()
    val sync = coordinator(upload = { _, preferences ->
      writes += preferences.pictureInPictureEnabled
      if (writes.size == 1) { entered.complete(Unit); release.await() }
      Result.success(Unit)
    })
    val job = launch { sync.flush(first) }
    entered.await()
    file.edit().putBoolean("pip_enabled", true).apply()
    release.complete(Unit)
    job.join()
    assertEquals(listOf(false, true), writes)
    assertTrue(file.pending().isEmpty())
  }

  @Test fun `failed upload keeps edit across restart and stale cloud cannot overwrite it`() = runBlocking {
    file.edit().putBoolean("pip_enabled", false).apply()
    var applied: CloudPlaybackPreferences? = null
    val sync = coordinator(upload = { _, _ -> Result.failure(java.io.IOException("offline")) },
      download = { Result.success(CloudPlaybackPreferences(pictureInPictureEnabled = true, mediaHubEnabled = true)) }, apply = { applied = it })
    assertTrue(sync.reconcile(first).isFailure)
    assertNull(applied!!.pictureInPictureEnabled)
    assertEquals(true, applied!!.mediaHubEnabled)
    val restarted = DurableSettingsPreferences(disk.restart(), SettingsSyncRegistry.syncedKeys) {}
    assertFalse(restarted.getBoolean("pip_enabled", true))
    assertEquals(setOf("pip_enabled"), restarted.pending().keys)
  }

  @Test fun `edit during slow download is protected even though fetch started clean`() = runBlocking {
    val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
    var applied: CloudPlaybackPreferences? = null
    val sync = coordinator(download = {
      entered.complete(Unit); release.await()
      Result.success(CloudPlaybackPreferences(mediaHubEnabled = false))
    }, apply = { applied = it })
    val job = launch { sync.reconcile(first) }
    entered.await()
    file.edit().putBoolean("media_hub_enabled", true).apply()
    release.complete(Unit); job.join()
    assertNull(applied!!.mediaHubEnabled)
    assertTrue(file.getBoolean("media_hub_enabled", false))
  }

  @Test fun `logout or switch away and back rejects old response by generation`() = runBlocking {
    val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
    var applications = 0
    val sync = coordinator(download = {
      entered.complete(Unit); release.await(); Result.success(CloudPlaybackPreferences(mediaHubEnabled = true))
    }, apply = { applications++ })
    val job = launch { sync.reconcile(first) }
    entered.await()
    owner = null
    owner = second
    owner = first.copy(generation = 3)
    release.complete(Unit); job.join()
    assertEquals(0, applications)
  }

  @Test fun `concurrent foreground and setter requests serialize their network operations`() = runBlocking {
    file.edit().putBoolean("pip_enabled", false).apply()
    val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
    val events = mutableListOf<String>()
    val sync = coordinator(upload = { _, _ ->
      events += "upload-start"; entered.complete(Unit); release.await(); events += "upload-end"; Result.success(Unit)
    }, download = { events += "download"; Result.success(CloudPlaybackPreferences()) })
    val writer = launch { sync.flush(first) }
    entered.await()
    val reader = launch { sync.reconcile(first) }
    release.complete(Unit); writer.join(); reader.join()
    assertEquals(listOf("upload-start", "upload-end", "download"), events)
  }
}
