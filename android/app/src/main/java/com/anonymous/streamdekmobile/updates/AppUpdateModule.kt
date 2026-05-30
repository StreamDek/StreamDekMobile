package com.anonymous.streamdekmobile.updates

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.anonymous.streamdekmobile.BuildConfig
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest

class AppUpdateModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext), ActivityEventListener {

    companion object {
        private const val INSTALL_REQUEST_CODE = 44123
        private const val DOWNLOAD_PROGRESS_EVENT = "appUpdateDownloadProgress"
    }

    private val client = OkHttpClient()
    private var installPromise: Promise? = null

    init {
        reactContext.addActivityEventListener(this)
    }

    override fun getName(): String = "AppUpdateModule"

    @ReactMethod
    fun getCurrentVersionInfo(promise: Promise) {
        val payload = Arguments.createMap().apply {
            putString("packageName", reactContext.packageName)
            putString("versionName", BuildConfig.VERSION_NAME)
            putInt("versionCode", getInstalledVersionCodeSafe())
        }
        promise.resolve(payload)
    }

    @ReactMethod
    fun canRequestPackageInstalls(promise: Promise) {
        promise.resolve(Build.VERSION.SDK_INT < Build.VERSION_CODES.O || reactContext.packageManager.canRequestPackageInstalls())
    }

    @ReactMethod
    fun openUnknownAppSourcesSettings(promise: Promise) {
        val packageUri = Uri.parse("package:${reactContext.packageName}")
        val primaryIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            reactContext.startActivity(primaryIntent)
            promise.resolve(null)
        } catch (_: ActivityNotFoundException) {
            try {
                reactContext.startActivity(fallbackIntent)
                promise.resolve(null)
            } catch (error: Exception) {
                promise.reject("UNKNOWN_APPS_SETTINGS_FAILED", error.message, error)
            }
        } catch (error: Exception) {
            promise.reject("UNKNOWN_APPS_SETTINGS_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun downloadApk(options: ReadableMap, promise: Promise) {
        try {
            val url = options.getString("url")?.trim().orEmpty()
            if (url.isBlank()) {
                promise.reject("DOWNLOAD_URL_MISSING", "Missing update download URL.")
                return
            }

            val fileName = options.getString("fileName")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "streamdek-update.apk"
            val expectedSha256 = options.getString("expectedSha256")?.trim()?.lowercase()
            val updatesDir = File(reactContext.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, fileName)

            Thread {
                try {
                    val result = performDownload(url, targetFile, expectedSha256)
                    promise.resolve(result)
                } catch (error: Exception) {
                    promise.reject(
                        mapDownloadErrorCode(error),
                        error.message ?: "Update download failed.",
                        error,
                    )
                }
            }.start()
        } catch (error: Exception) {
            promise.reject("DOWNLOAD_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun validateApk(filePath: String, expectedPackageName: String?, minimumVersionCode: Int, promise: Promise) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                promise.reject("APK_NOT_FOUND", "Downloaded APK was not found.")
                return
            }

            val info = readArchivePackageInfo(file)
            if (info == null) {
                promise.reject("APK_INVALID", "Downloaded file is not a valid Android package.")
                return
            }

            val actualPackageName = info.packageName.orEmpty()
            val actualVersionCode = packageVersionCode(info)
            val actualVersionName = info.versionName.orEmpty()
            val expectedPackage = expectedPackageName?.takeIf { it.isNotBlank() } ?: reactContext.packageName
            val packageMatches = actualPackageName == expectedPackage
            val versionMatches = actualVersionCode >= minimumVersionCode
            val signatureMatches = compareSigningCertificates(info)

            val payload = Arguments.createMap().apply {
                putBoolean("isValid", packageMatches && versionMatches && signatureMatches)
                putString("packageName", actualPackageName)
                putString("versionName", actualVersionName)
                putInt("versionCode", actualVersionCode)
                putBoolean("packageMatches", packageMatches)
                putBoolean("versionMatches", versionMatches)
                putBoolean("signatureMatches", signatureMatches)
            }
            promise.resolve(payload)
        } catch (error: Exception) {
            promise.reject("APK_VALIDATION_FAILED", error.message, error)
        }
    }

    @ReactMethod
    fun installApk(filePath: String, promise: Promise) {
        val activity = reactContext.currentActivity
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "Unable to launch installer without an active Android activity.")
            return
        }
        if (installPromise != null) {
            promise.reject("INSTALL_IN_PROGRESS", "Another install request is already in progress.")
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            promise.reject("APK_NOT_FOUND", "Downloaded APK was not found.")
            return
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                reactContext,
                "${reactContext.packageName}.fileprovider",
                file,
            )
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }

            installPromise = promise
            activity.startActivityForResult(installIntent, INSTALL_REQUEST_CODE)
        } catch (_: ActivityNotFoundException) {
            installPromise = null
            promise.reject("INSTALLER_UNAVAILABLE", "No Android package installer is available on this device.")
        } catch (error: Exception) {
            installPromise = null
            promise.reject("INSTALL_LAUNCH_FAILED", error.message, error)
        }
    }

    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != INSTALL_REQUEST_CODE) return
        val promise = installPromise ?: return
        installPromise = null

        val payload = Arguments.createMap().apply {
            when (resultCode) {
                Activity.RESULT_OK -> putString("status", "installed")
                Activity.RESULT_CANCELED -> putString("status", "cancelled")
                else -> putString("status", "failed")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                putInt("resultCode", resultCode)
            }
        }
        promise.resolve(payload)
    }

    override fun onNewIntent(intent: Intent) = Unit

    private fun performDownload(url: String, targetFile: File, expectedSha256: String?): WritableMap {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Update download failed with code ${response.code}.")
            }

            val body = response.body ?: throw IOException("Update response did not include an APK file.")
            val totalBytes = body.contentLength()
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        emitProgress(written, totalBytes)
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!expectedSha256.isNullOrBlank() && actualSha256 != expectedSha256) {
                targetFile.delete()
                throw IOException("Downloaded APK failed checksum validation.")
            }

            val payload = Arguments.createMap().apply {
                putString("filePath", targetFile.absolutePath)
                putString("sha256", actualSha256)
                if (totalBytes > 0) {
                    putDouble("fileSizeBytes", totalBytes.toDouble())
                }
            }
            return payload
        }
    }

    private fun emitProgress(downloadedBytes: Long, totalBytes: Long) {
        val payload = Arguments.createMap().apply {
            putDouble("downloadedBytes", downloadedBytes.toDouble())
            if (totalBytes > 0) {
                putDouble("totalBytes", totalBytes.toDouble())
                putInt("progressPercent", ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
            } else {
                putNull("totalBytes")
                putNull("progressPercent")
            }
        }
        reactContext
            .getJSModule(com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(DOWNLOAD_PROGRESS_EVENT, payload)
    }

    private fun getInstalledVersionCodeSafe(): Int {
        return try {
            val info = reactContext.packageManager.getPackageInfo(reactContext.packageName, 0)
            packageVersionCode(info)
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE
        }
    }

    private fun readArchivePackageInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return reactContext.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    private fun packageVersionCode(info: PackageInfo): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }

    private fun compareSigningCertificates(archiveInfo: PackageInfo): Boolean {
        return try {
            val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                reactContext.packageManager.getPackageInfo(
                    reactContext.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
            } else {
                @Suppress("DEPRECATION")
                reactContext.packageManager.getPackageInfo(
                    reactContext.packageName,
                    PackageManager.GET_SIGNATURES,
                )
            }

            val installed = signingDigests(installedInfo)
            val archive = signingDigests(archiveInfo)
            installed.isNotEmpty() && archive.isNotEmpty() && installed == archive
        } catch (_: Exception) {
            false
        }
    }

    private fun signingDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }

        return signatures.mapTo(linkedSetOf()) { bytes ->
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            digest.joinToString("") { "%02x".format(it) }
        }
    }

    private fun mapDownloadErrorCode(error: Exception): String {
        return when (error) {
            is UnknownHostException -> "DOWNLOAD_NO_INTERNET"
            is SocketTimeoutException -> "DOWNLOAD_TIMEOUT"
            is IOException -> {
                if (error.message?.contains("No space left on device", ignoreCase = true) == true) {
                    "DOWNLOAD_INSUFFICIENT_STORAGE"
                } else {
                    "DOWNLOAD_IO_FAILED"
                }
            }
            else -> "DOWNLOAD_FAILED"
        }
    }
}
