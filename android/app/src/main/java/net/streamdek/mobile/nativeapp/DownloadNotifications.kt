package net.streamdek.mobile.nativeapp

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import net.streamdek.mobile.R

/**
 * Everything a download says in the notification shade apart from the ongoing progress itself.
 *
 * Media3's [DownloadService] draws one notification, and only while something is running: the
 * moment the last download finishes, fails or is paused, the service leaves the foreground and the
 * notification goes with it. So a download that finished while the phone was in a pocket used to
 * leave no trace, and a paused one could only be resumed from inside the app.
 *
 * This listens to the manager directly and posts one quiet, dismissible notification per download
 * for the states worth knowing about afterwards - done, failed, paused - and takes it away again
 * when the download is resumed, retried or removed. Nothing here is ongoing: when no download is
 * running, nothing is pinned to the shade.
 */
@OptIn(UnstableApi::class)
internal object DownloadNotifications {
  /** The stop reason a pause from StreamDek sets. Any non-zero value stops a download; this is ours. */
  const val STOP_REASON_PAUSED = 1

  /** The ongoing notification's channel, shared with [StreamDekDownloadService]. */
  const val CHANNEL_ID = "streamdek_downloads"

  /** Every per-download notification shares this id and is told apart by its tag. */
  private const val STATE_NOTIFICATION_ID = 21002
  private const val TAG_PREFIX = "download:"

  private enum class Action { Pause, Resume, Cancel, Retry }

  /** Whether the shade can show StreamDek's download notifications at all. */
  fun canNotify(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
  }

  /** Listens for the states [DownloadService] leaves unsaid. Registered once, beside the manager. */
  fun listener(context: Context): DownloadManager.Listener {
    val appContext = context.applicationContext
    return object : DownloadManager.Listener {
      override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
        onStateChanged(appContext, download)
      }

      override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
        cancel(appContext, download.request.id)
      }
    }
  }

  private fun onStateChanged(context: Context, download: Download) {
    val id = download.request.id
    when (download.state) {
      Download.STATE_COMPLETED -> post(context, id, completed(context, download))
      Download.STATE_FAILED -> post(context, id, failed(context, download))
      Download.STATE_STOPPED -> post(context, id, paused(context, download))
      // Running again, or on its way out: the ongoing notification or nothing at all says so now.
      else -> cancel(context, id)
    }
  }

  private fun post(context: Context, id: String, builder: NotificationCompat.Builder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    runCatching { NotificationManagerCompat.from(context).notify(TAG_PREFIX + id, STATE_NOTIFICATION_ID, builder.build()) }
  }

  private fun cancel(context: Context, id: String) {
    NotificationManagerCompat.from(context).cancel(TAG_PREFIX + id, STATE_NOTIFICATION_ID)
  }

  private fun base(context: Context, download: Download): NotificationCompat.Builder {
    val localized = localizedAppContext(context)
    val entry = StreamDekDownloads.entryOf(download)
    return NotificationCompat.Builder(localized, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_streamdek)
      .setContentTitle(DownloadProgressText.title(localized, entry.media))
      .setContentIntent(openAppIntent(context))
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
  }

  private fun completed(context: Context, download: Download): NotificationCompat.Builder {
    val localized = localizedAppContext(context)
    val size = DownloadProgressText.completedSize(localized, StreamDekDownloads.entryOf(download))
    return base(context, download)
      .setContentText(
        size?.let { localized.getString(R.string.download_notification_completed_size, it) }
          ?: localized.getString(R.string.download_notification_completed),
      )
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setAutoCancel(true)
  }

  private fun failed(context: Context, download: Download): NotificationCompat.Builder {
    val localized = localizedAppContext(context)
    return base(context, download)
      .setContentText(localized.getString(R.string.download_notification_failed))
      .setCategory(NotificationCompat.CATEGORY_ERROR)
      .setAutoCancel(true)
      .addAction(0, localized.getString(R.string.action_retry), serviceIntent(context, download, Action.Retry))
      .addAction(0, localized.getString(R.string.action_cancel), serviceIntent(context, download, Action.Cancel))
  }

  private fun paused(context: Context, download: Download): NotificationCompat.Builder {
    val localized = localizedAppContext(context)
    val entry = StreamDekDownloads.entryOf(download)
    val status = localized.getString(R.string.download_notification_paused_percent, entry.wholePercent)
    return base(context, download)
      .setContentText(status)
      .setProgress(100, entry.wholePercent, false)
      .addAction(0, localized.getString(R.string.action_resume), serviceIntent(context, download, Action.Resume))
      .addAction(0, localized.getString(R.string.action_cancel), serviceIntent(context, download, Action.Cancel))
  }

  /** Pause and Cancel for one running download, for the ongoing notification. */
  fun runningActions(context: Context, entry: DownloadEntry): List<NotificationCompat.Action> = listOf(
    NotificationCompat.Action(0, context.getString(R.string.action_pause), serviceIntent(context, entry.id, Action.Pause)),
    NotificationCompat.Action(0, context.getString(R.string.action_cancel), serviceIntent(context, entry.id, Action.Cancel)),
  )

  /** Pause for everything at once, when several are running and there is no room for one each. */
  fun pauseAllAction(context: Context): NotificationCompat.Action =
    NotificationCompat.Action(0, context.getString(R.string.download_action_pause_all), serviceIntent(context, null, Action.Pause))

  private fun serviceIntent(context: Context, download: Download, action: Action): PendingIntent =
    serviceIntent(context, download.request.id, action, download)

  /**
   * The service call behind a notification button.
   *
   * Pause and Cancel need no more than an ordinary service start: Android lets an app that is
   * executing a notification's intent start a service from the background. Resume and Retry start
   * a download again, which the service can only run in the foreground, so they start it that way -
   * which a tap on a notification is also allowed to do.
   */
  private fun serviceIntent(context: Context, id: String?, action: Action, download: Download? = null): PendingIntent {
    val service = StreamDekDownloadService::class.java
    val foreground = action == Action.Resume || action == Action.Retry
    val intent: Intent = when (action) {
      Action.Pause -> DownloadService.buildSetStopReasonIntent(context, service, id, STOP_REASON_PAUSED, foreground)
      Action.Resume -> DownloadService.buildSetStopReasonIntent(context, service, id, Download.STOP_REASON_NONE, foreground)
      Action.Cancel -> DownloadService.buildRemoveDownloadIntent(context, service, id.orEmpty(), foreground)
      // Adding a failed download's request again starts it over; Media3 merges it with the record.
      Action.Retry -> DownloadService.buildAddDownloadIntent(context, service, download!!.request, foreground)
    }
    val requestCode = "${action.name}:${id ?: "*"}".hashCode()
    val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    return if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      PendingIntent.getForegroundService(context, requestCode, intent, flags)
    } else {
      PendingIntent.getService(context, requestCode, intent, flags)
    }
  }

  fun openAppIntent(context: Context): PendingIntent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
      PendingIntent.getActivity(context, STATE_NOTIFICATION_ID, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * Asks for Android 13's notification permission, if it has not been given, at the moment it is
 * first worth having: a download starting.
 *
 * The only place the app used to ask was the new-episode reminders switch, so a phone that never
 * turned reminders on - or one where the app had been reinstalled - ran every download with its
 * notification silently suppressed. The download itself does not wait on the answer.
 */
@Composable
internal fun rememberNotificationPermissionRequest(): () -> Unit {
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  return remember(context, launcher) {
    {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
      ) {
        runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
      }
    }
  }
}

/**
 * Says, on the Downloads page, that progress will not show in the notification shade - and offers
 * the one place it can be fixed.
 *
 * Shown only while there is something downloading to miss, and re-checked whenever the page comes
 * back into view, since the fix happens in the system's settings rather than here.
 */
@Composable
internal fun DownloadNotificationsOffNotice(visible: Boolean) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var canNotify by remember { mutableStateOf(DownloadNotifications.canNotify(context)) }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) canNotify = DownloadNotifications.canNotify(context)
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  if (!visible || canNotify) return
  Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = StreamDekRadius.thumbShape) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.downloads_notifications_off_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
          stringResource(R.string.downloads_notifications_off_detail),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
      }
      TextButton(onClick = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
      }) { Text(stringResource(R.string.downloads_notifications_turn_on)) }
    }
  }
}
