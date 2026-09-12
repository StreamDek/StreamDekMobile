package net.streamdek.mobile.nativeapp

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import net.streamdek.mobile.R

/**
 * How much of StreamDek's glass a device draws.
 *
 * # The rule this exists to enforce
 *
 * Blur is decoration, never contrast. Every glass surface has to be readable with the blur switched
 * off, because on a good share of phones it *is* switched off: Android draws no real-time blur before
 * 12, a low-memory phone cannot afford it mid-fling, and battery saver is a request to stop spending
 * power on exactly this. So the reduced treatment is not "the full one with a piece missing" — it is
 * its own recipe of tint, sheen and scrim (see `FrostedGlassSurface`), and it is what text is
 * designed against. The full treatment only ever adds blur on top.
 *
 * # One setting, device-local
 *
 * Stored beside the animation speed, for the same reason: it describes this phone, not the account.
 * Automatic is the default and almost everyone should leave it there; Full and Reduced exist for the
 * viewer who knows better than a heuristic about their own device.
 */
internal const val VISUAL_EFFECTS_PREFERENCE = "visual_effects"

enum class VisualEffectsMode(
  /** The persisted form. Stable across releases; the enum name is not the storage contract. */
  val key: String,
  @StringRes val labelRes: Int,
  @StringRes val descriptionRes: Int,
) {
  Automatic("automatic", R.string.visual_effects_automatic, R.string.visual_effects_automatic_description),
  Full("full", R.string.visual_effects_full, R.string.visual_effects_full_description),
  Reduced("reduced", R.string.visual_effects_reduced, R.string.visual_effects_reduced_description);

  companion object {
    val Default = Automatic

    fun fromKey(key: String?): VisualEffectsMode {
      val normalized = key?.trim()?.lowercase().orEmpty()
      return entries.firstOrNull { it.key == normalized || it.name.lowercase() == normalized } ?: Default
    }
  }
}

/** Why a device is drawing the reduced treatment, so Settings can say so rather than leave a mystery. */
enum class ReducedEffectsReason {
  /** The viewer picked Reduced. */
  Chosen,
  /** This version of Android draws no real-time blur, whatever the setting says. */
  Unsupported,
  BatterySaver,
  /** Automatic judged the hardware too constrained before anything was drawn. */
  Device,
  /** Automatic watched scrolling miss frames and stepped down for the rest of the session. */
  Performance,
}

@Immutable
data class VisualEffects(
  val mode: VisualEffectsMode = VisualEffectsMode.Default,
  /** Whether glass may blur what is behind it. Everything else about a surface is the same either way. */
  val liveBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
  val reducedReason: ReducedEffectsReason? = null,
  /** Android's High Contrast Text accessibility setting: glass leans on its scrim instead of the artwork. */
  val highContrast: Boolean = false,
)

val LocalVisualEffects: ProvidableCompositionLocal<VisualEffects> = staticCompositionLocalOf { VisualEffects() }

/** The decision, as plain logic so it can be tested without a device. */
internal object VisualEffectsPolicy {
  private const val CONSTRAINED_MEMORY_BYTES = 3_300L * 1024 * 1024

  fun resolve(
    mode: VisualEffectsMode,
    blurSupported: Boolean,
    deviceConstrained: Boolean,
    batterySaver: Boolean,
    performanceLimited: Boolean,
    highContrast: Boolean = false,
  ): VisualEffects {
    val reason = when {
      mode == VisualEffectsMode.Reduced -> ReducedEffectsReason.Chosen
      !blurSupported -> ReducedEffectsReason.Unsupported
      mode == VisualEffectsMode.Full -> null
      batterySaver -> ReducedEffectsReason.BatterySaver
      deviceConstrained -> ReducedEffectsReason.Device
      performanceLimited -> ReducedEffectsReason.Performance
      else -> null
    }
    return VisualEffects(mode = mode, liveBlur = reason == null, reducedReason = reason, highContrast = highContrast)
  }

  /**
   * A phone that will struggle with layered real-time blur, judged before drawing anything.
   *
   * Deliberately conservative. A false "constrained" costs a capable phone some glass, which the
   * viewer can take back with Full; a false "capable" costs smooth scrolling, which the frame monitor
   * then has to notice after the fact.
   */
  fun deviceConstrained(lowRamDevice: Boolean, totalMemoryBytes: Long, cpuCores: Int): Boolean =
    lowRamDevice ||
      (totalMemoryBytes in 1 until CONSTRAINED_MEMORY_BYTES) ||
      cpuCores in 1..4

  /**
   * Whether a window of frames drawn mid-scroll was janky enough to count against the glass.
   *
   * A frame is slow when it misses 60fps, not when it misses the panel's own refresh: a 120Hz phone
   * dropping to 90 during a fling is still smooth, and stepping its glass down for that would be
   * trading something visible for something nobody can see.
   */
  fun windowIsJanky(frames: Int, slowFrames: Int): Boolean = frames > 0 && slowFrames * 4 >= frames

  const val SLOW_FRAME_NANOS = 17_500_000L
  const val FRAMES_PER_WINDOW = 120
  const val JANKY_WINDOWS_TO_STEP_DOWN = 2
}

/**
 * The session's own verdict, kept outside composition so an activity recreate — which is how a
 * language change is applied — does not forget that this phone could not keep up and try again.
 */
internal object VisualEffectsSession {
  var performanceLimited by mutableStateOf(false)
}

/** Resolves [mode] for this device and keeps the answer current while the app runs. */
@Composable
fun rememberVisualEffects(mode: VisualEffectsMode): VisualEffects {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val constrained = remember(appContext) { assessDeviceConstrained(appContext) }
  var batterySaver by remember(appContext) { mutableStateOf(isBatterySaverOn(appContext)) }
  val highContrast = remember(context) { isHighContrastTextOn(appContext) }

  DisposableEffect(appContext) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        batterySaver = isBatterySaverOn(appContext)
      }
    }
    val registered = runCatching {
      appContext.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    }.isSuccess
    onDispose { if (registered) runCatching { appContext.unregisterReceiver(receiver) } }
  }

  val effects = VisualEffectsPolicy.resolve(
    mode = mode,
    blurSupported = blurSupported,
    deviceConstrained = constrained,
    batterySaver = batterySaver,
    performanceLimited = VisualEffectsSession.performanceLimited,
    highContrast = highContrast,
  )

  // Only worth watching while Automatic is actually drawing blur: there is nothing left to step
  // down from otherwise, and a listener on every frame is not free.
  val watchFrames = mode == VisualEffectsMode.Automatic && effects.liveBlur
  val activity = context.findHostActivity()
  DisposableEffect(watchFrames, activity) {
    val monitor = if (watchFrames && activity != null) {
      ScrollJankMonitor(activity.window) { VisualEffectsSession.performanceLimited = true }.also { it.start() }
    } else {
      null
    }
    onDispose { monitor?.stop() }
  }
  return effects
}

/**
 * Draws [state]'s blur source only when something will blur it.
 *
 * With blur off a haze source is pure overhead — every frame of the page recorded into a layer that
 * nothing reads — so the reduced treatment leaves it out entirely.
 */
@Composable
fun Modifier.glassSource(state: HazeState): Modifier =
  if (LocalVisualEffects.current.liveBlur) hazeSource(state) else this

private fun assessDeviceConstrained(context: Context): Boolean = runCatching {
  val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
  VisualEffectsPolicy.deviceConstrained(
    lowRamDevice = activityManager.isLowRamDevice,
    totalMemoryBytes = memory.totalMem,
    cpuCores = Runtime.getRuntime().availableProcessors(),
  )
}.getOrDefault(false)

private fun isBatterySaverOn(context: Context): Boolean = runCatching {
  (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
}.getOrDefault(false)

/** Not public API, but a long-standing secure setting that every Android version still writes. */
private fun isHighContrastTextOn(context: Context): Boolean = runCatching {
  Settings.Secure.getInt(context.contentResolver, "high_text_contrast_enabled", 0) == 1
}.getOrDefault(false)

private fun Context.findHostActivity(): Activity? {
  var current: Context? = this
  while (current is android.content.ContextWrapper) {
    if (current is Activity) return current
    current = current.baseContext
  }
  return null
}

/**
 * Counts slow frames drawn while the viewer scrolls, and says so once scrolling has been janky for
 * long enough to be the glass's fault rather than a hiccup.
 *
 * Frame metrics arrive on a thread of their own, so counting costs the UI thread nothing; only the
 * single verdict is posted back. Frames drawn while nothing scrolls are ignored — first composition
 * and image decoding are slow everywhere and say nothing about blur.
 */
private class ScrollJankMonitor(private val window: Window, private val onSustainedJank: () -> Unit) {
  private val thread = HandlerThread("StreamDekFrameMetrics").apply { start() }
  private val handler = Handler(thread.looper)
  private val mainHandler = Handler(Looper.getMainLooper())
  private var frames = 0
  private var slowFrames = 0
  private var jankyWindows = 0
  private var reported = false

  private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
    if (reported || !ScrollActivity.active) return@OnFrameMetricsAvailableListener
    frames += 1
    if (metrics.getMetric(FrameMetrics.TOTAL_DURATION) > VisualEffectsPolicy.SLOW_FRAME_NANOS) slowFrames += 1
    if (frames >= VisualEffectsPolicy.FRAMES_PER_WINDOW) {
      jankyWindows = if (VisualEffectsPolicy.windowIsJanky(frames, slowFrames)) jankyWindows + 1 else 0
      frames = 0
      slowFrames = 0
      if (jankyWindows >= VisualEffectsPolicy.JANKY_WINDOWS_TO_STEP_DOWN) {
        reported = true
        mainHandler.post(onSustainedJank)
      }
    }
  }

  fun start() {
    runCatching { window.addOnFrameMetricsAvailableListener(listener, handler) }
  }

  fun stop() {
    runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
    thread.quitSafely()
  }
}
