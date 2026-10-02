package net.streamdek.mobile.nativeapp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SettingsSyncOwner(val accountId: String, val profileId: String, val generation: Long)

/** One network operation at a time, with identity captured before the first suspension. */
internal class SettingsSyncCoordinator(
  private val currentOwner: () -> SettingsSyncOwner?,
  private val pending: () -> PendingSettingsWrite,
  private val snapshot: () -> CloudPlaybackPreferences,
  private val upload: suspend (SettingsSyncOwner, CloudPlaybackPreferences) -> Result<Unit>,
  private val download: suspend (SettingsSyncOwner) -> Result<CloudPlaybackPreferences>,
  private val applyRemote: (CloudPlaybackPreferences) -> Unit,
  private val diagnostic: (String) -> Unit = ::settingsDiagnostic,
) {
  private val mutex = Mutex()

  suspend fun flush(owner: SettingsSyncOwner): Result<Unit> = mutex.withLock { flushLocked(owner) }

  private suspend fun flushLocked(owner: SettingsSyncOwner): Result<Unit> {
    while (currentOwner() == owner) {
      val sent = pending()
      if (sent.keys.isEmpty()) return Result.success(Unit)
      val result = upload(owner, SettingsSyncRegistry.select(snapshot(), sent.keys))
      result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
      if (result.isFailure) {
        diagnostic("upload_failed pending=${sent.keys.size} type=${result.exceptionOrNull()?.javaClass?.simpleName}")
        return result
      }
      sent.acknowledge()
      diagnostic("upload_acknowledged keys=${sent.keys.size}")
    }
    return Result.success(Unit)
  }

  suspend fun reconcile(owner: SettingsSyncOwner): Result<Unit> = mutex.withLock {
    if (currentOwner() != owner) return@withLock Result.success(Unit)
    val uploaded = flushLocked(owner)
    if (currentOwner() != owner) return@withLock Result.success(Unit)
    val fetched = download(owner)
    fetched.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    fetched.onSuccess { remote ->
      if (currentOwner() == owner) {
        val protected = SettingsSyncRegistry.protectedKeys(pending().keys)
        applyRemote(SettingsSyncRegistry.select(remote, SettingsSyncRegistry.syncedKeys - protected))
        diagnostic("cloud_hydrated protected=${protected.size}")
      } else diagnostic("stale_owner_response_ignored")
    }.onFailure { diagnostic("download_failed type=${it.javaClass.simpleName}") }
    if (uploaded.isFailure) uploaded else fetched.map { Unit }
  }
}
