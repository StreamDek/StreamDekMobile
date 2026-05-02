package com.anonymous.streamdekmobile.mpv

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.RCTEventEmitter

class MpvPlayerViewManager(
    private val reactContext: ReactApplicationContext,
) : SimpleViewManager<MPVView>() {

    companion object {
        const val REACT_CLASS = "MpvPlayer"
        private const val TAG = "StreamDekMpvViewManager"
    }

    override fun getName(): String {
        Log.i(TAG, "getName called -> $REACT_CLASS")
        return REACT_CLASS
    }

    override fun createViewInstance(context: ThemedReactContext): MPVView {
        Log.i(TAG, "createViewInstance")
        val view = MPVView(context)

        view.onLoadCallback = { duration, width, height ->
            val event = Arguments.createMap().apply {
                putDouble("duration", duration)
                putInt("width", width)
                putInt("height", height)
            }
            sendEvent(context, view.id, "onLoad", event)
        }

        view.onProgressCallback = { position, duration ->
            val event = Arguments.createMap().apply {
                putDouble("currentTime", position)
                putDouble("duration", duration)
            }
            sendEvent(context, view.id, "onProgress", event)
        }

        view.onEndCallback = {
            sendEvent(context, view.id, "onEnd", Arguments.createMap())
        }

        view.onErrorCallback = { message ->
            val event = Arguments.createMap().apply {
                putString("error", message)
            }
            sendEvent(context, view.id, "onError", event)
        }

        view.onTracksChangedCallback = { audioTracks, subtitleTracks, selectedAudioTrackId, selectedSubtitleTrackId ->
            val event = Arguments.createMap().apply {
                putArray("audioTracks", tracksToWritableArray(audioTracks))
                putArray("subtitleTracks", tracksToWritableArray(subtitleTracks))
                if (selectedAudioTrackId != null) putInt("selectedAudioTrackId", selectedAudioTrackId) else putNull("selectedAudioTrackId")
                if (selectedSubtitleTrackId != null) putInt("selectedSubtitleTrackId", selectedSubtitleTrackId) else putNull("selectedSubtitleTrackId")
            }
            sendEvent(context, view.id, "onTracksChanged", event)
        }

        return view
    }

    override fun getCommandsMap(): Map<String, Int> {
        return mapOf(
            "seek" to 1,
            "setAudioTrack" to 2,
            "setSubtitleTrack" to 3,
            "disableSubtitleTrack" to 4,
            // External subtitle loading: pass a file:// URI or absolute path
            "addSubtitleFile" to 5,
            // Subtitle delay in seconds (positive = later, negative = earlier)
            "setSubtitleDelay" to 6,
            // Subtitle style
            "setSubtitleFontSize" to 7,
            "setSubtitleColor" to 8,
            "setSubtitlePosition" to 9,
        )
    }

    override fun receiveCommand(view: MPVView, commandId: Int, args: ReadableArray?) {
        when (commandId) {
            1 -> {
                val target = extractDoubleArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(int) seek -> $target")
                view.seekTo(target)
            }
            2 -> {
                val target = extractIntArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(int) setAudioTrack -> $target")
                view.setAudioTrack(target)
            }
            3 -> {
                val target = extractIntArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(int) setSubtitleTrack -> $target")
                view.setSubtitleTrack(target)
            }
            4 -> {
                Log.i(TAG, "receiveCommand(int) disableSubtitleTrack")
                view.disableSubtitleTrack()
            }
            5 -> {
                val path = args?.getString(0) ?: return
                Log.i(TAG, "receiveCommand(int) addSubtitleFile -> $path")
                view.addSubtitleFile(path)
            }
            6 -> {
                val delay = extractDoubleArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(int) setSubtitleDelay -> $delay")
                view.setSubtitleDelay(delay)
            }
            7 -> {
                val size = extractIntArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(int) setSubtitleFontSize -> $size")
                view.setSubtitleFontSize(size)
            }
            8 -> {
                val color = args?.getString(0) ?: return
                Log.i(TAG, "receiveCommand(int) setSubtitleColor -> $color")
                view.setSubtitleColor(color)
            }
            9 -> {
                val pos = extractIntArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(int) setSubtitlePosition -> $pos")
                view.setSubtitlePosition(pos)
            }
        }
    }

    override fun receiveCommand(view: MPVView, commandId: String?, args: ReadableArray?) {
        when (commandId) {
            "seek" -> {
                val target = extractDoubleArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(string) seek -> $target")
                view.seekTo(target)
            }
            "setAudioTrack" -> {
                val target = extractIntArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(string) setAudioTrack -> $target")
                view.setAudioTrack(target)
            }
            "setSubtitleTrack" -> {
                val target = extractIntArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(string) setSubtitleTrack -> $target")
                view.setSubtitleTrack(target)
            }
            "disableSubtitleTrack" -> {
                Log.i(TAG, "receiveCommand(string) disableSubtitleTrack")
                view.disableSubtitleTrack()
            }
            "addSubtitleFile" -> {
                val path = args?.getString(0) ?: return
                Log.i(TAG, "receiveCommand(string) addSubtitleFile -> $path")
                view.addSubtitleFile(path)
            }
            "setSubtitleDelay" -> {
                val delay = extractDoubleArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(string) setSubtitleDelay -> $delay")
                view.setSubtitleDelay(delay)
            }
            "setSubtitleFontSize" -> {
                val size = extractIntArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(string) setSubtitleFontSize -> $size")
                view.setSubtitleFontSize(size)
            }
            "setSubtitleColor" -> {
                val color = args?.getString(0) ?: return
                Log.i(TAG, "receiveCommand(string) setSubtitleColor -> $color")
                view.setSubtitleColor(color)
            }
            "setSubtitlePosition" -> {
                val pos = extractIntArg(args, 0) ?: return
                Log.i(TAG, "receiveCommand(string) setSubtitlePosition -> $pos")
                view.setSubtitlePosition(pos)
            }
        }
    }

    @ReactProp(name = "source")
    fun setSource(view: MPVView, source: String?) {
        Log.i(TAG, "setSource called (len=${source?.length ?: 0})")
        view.setSource(source)
    }

    @ReactProp(name = "uri")
    fun setUri(view: MPVView, uri: String?) {
        Log.i(TAG, "setUri called (len=${uri?.length ?: 0})")
        view.setSource(uri)
    }

    @ReactProp(name = "paused", defaultBoolean = false)
    fun setPaused(view: MPVView, paused: Boolean) {
        view.setPaused(paused)
    }

    @ReactProp(name = "volume", defaultFloat = 1f)
    fun setVolume(view: MPVView, volume: Float) {
        view.setVolume(volume.toDouble())
    }

    @ReactProp(name = "rate", defaultFloat = 1f)
    fun setRate(view: MPVView, rate: Float) {
        view.setSpeed(rate.toDouble())
    }

    @ReactProp(name = "resizeMode")
    fun setResizeMode(view: MPVView, resizeMode: String?) {
        view.setResizeMode(resizeMode)
    }

    @ReactProp(name = "headers")
    fun setHeaders(view: MPVView, headers: ReadableMap?) {
        Log.i(TAG, "setHeaders called (present=${headers != null})")
        if (headers == null) {
            view.setHeaders(null)
            return
        }
        val mapped = mutableMapOf<String, String>()
        val iterator = headers.keySetIterator()
        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            headers.getString(key)?.let { value ->
                mapped[key] = value
            }
        }
        view.setHeaders(mapped)
    }

    override fun getExportedCustomBubblingEventTypeConstants(): MutableMap<String, Any> {
        return MapBuilder.builder<String, Any>()
            .put("onLoad", MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", "onLoad")))
            .put("onProgress", MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", "onProgress")))
            .put("onEnd", MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", "onEnd")))
            .put("onError", MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", "onError")))
            .put("onTracksChanged", MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", "onTracksChanged")))
            .build()
            .toMutableMap()
    }

    private fun sendEvent(context: ThemedReactContext, viewId: Int, eventName: String, params: com.facebook.react.bridge.WritableMap) {
        context.getJSModule(RCTEventEmitter::class.java)
            .receiveEvent(viewId, eventName, params)
    }

    private fun extractDoubleArg(args: ReadableArray?, index: Int): Double? {
        if (args == null) return null
        if (index < 0 || index >= args.size()) return null
        return try {
            args.getDouble(index)
        } catch (_: Throwable) {
            val value = args.getString(index)
            value?.toDoubleOrNull()
        }
    }

    private fun extractIntArg(args: ReadableArray?, index: Int): Int? {
        val numeric = extractDoubleArg(args, index) ?: return null
        if (!numeric.isFinite()) return null
        return numeric.toInt()
    }

    private fun tracksToWritableArray(tracks: List<MpvTrackInfo>): com.facebook.react.bridge.WritableArray {
        val array = Arguments.createArray()
        tracks.forEach { track ->
            val item = Arguments.createMap().apply {
                putInt("id", track.id)
                putString("type", track.type)
                if (!track.title.isNullOrBlank()) putString("title", track.title) else putNull("title")
                if (!track.language.isNullOrBlank()) putString("language", track.language) else putNull("language")
                if (!track.codec.isNullOrBlank()) putString("codec", track.codec) else putNull("codec")
                putBoolean("selected", track.selected)
            }
            array.pushMap(item)
        }
        return array
    }
}
