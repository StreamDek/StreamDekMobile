package com.anonymous.streamdekmobile.mpv

import android.os.Build
import android.util.Log
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

// NOTE: We intentionally do NOT implement ViewManagerOnDemandReactPackage here.
//
// With newArchEnabled=true and the New Architecture interop layer, on-demand
// view manager loading (ViewManagerOnDemandReactPackage) does not reliably resolve
// SimpleViewManager-based components, causing:
//   "Invariant Violation: View config not found for component `MpvPlayer`"
//
// Registering via createViewManagers() is fully supported by the interop layer
// and works correctly with both Old and New Architecture.

class MpvPackage : ReactPackage {
    companion object {
        private const val TAG = "StreamDekMpvPackage"
    }

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return emptyList()
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        // MpvPlayerViewManager requires Android O (API 26) — same guard as MainApplication
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i(TAG, "Registering MpvPlayerViewManager (sdk=${Build.VERSION.SDK_INT})")
            listOf(MpvPlayerViewManager(reactContext))
        } else {
            Log.w(TAG, "Skipping MpvPlayerViewManager: sdk=${Build.VERSION.SDK_INT} < 26")
            emptyList()
        }
    }
}
