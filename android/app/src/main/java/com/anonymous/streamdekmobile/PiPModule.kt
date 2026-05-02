package com.anonymous.streamdekmobile

import android.app.PictureInPictureParams
import android.os.Build
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class PiPModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "PiPModule"

    @ReactMethod
    fun setEnabled(enabled: Boolean) {
        MainActivity.pipShouldEnter = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val activity = reactContext.currentActivity ?: return
            val params = PictureInPictureParams.Builder()
                .setAutoEnterEnabled(enabled)
                .build()
            activity.setPictureInPictureParams(params)
        }
    }

    @ReactMethod
    fun enterPiP() {
        if (!MainActivity.pipShouldEnter) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activity = reactContext.currentActivity ?: return
            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }
}
