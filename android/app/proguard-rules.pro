# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:
-keep class com.frostwire.jlibtorrent.** { *; }

# Suppress R8 warnings from Google Cast SDK (invalid stack map tables in precompiled bytecode)
-dontwarn com.google.android.gms.internal.cast.**

# --- CloudStream (.cs3) provider runtime ---
# Loaded .cs3 plugins resolve their superclasses and call into this API by its original names at
# runtime, so none of it may be renamed, shrunk or repackaged. Same for NiceHttp, which the
# runtime's global `app` client is built from.
-keep class com.lagradost.** { *; }
-keepclassmembers class com.lagradost.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,KotlinMetadata

# kotlinx.serialization: the runtime's models are pre-compiled with generated $$serializer classes
# that are only ever reached reflectively from the companion.
-keepclassmembers class com.lagradost.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lagradost.**$$serializer { *; }

# A .cs3 is dex compiled ahead of time against the original names of everything in its API
# surface, so any type that appears in a signature a plugin calls has to survive R8 unrenamed and
# unshrunk — not just com.lagradost itself. (Upstream CloudStream sidesteps this by shipping with
# minification switched off entirely; keeping the surface explicitly lets StreamDek's own code
# stay minified.) The list below is derived from the types actually referenced by the extensions
# in the configured repositories.
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.jsoup.** { *; }
-keep class org.json.** { *; }
-keep class org.xmlpull.** { *; }
-keep class org.jetbrains.annotations.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.fleeksoft.ksoup.** { *; }
-keep class io.ktor.** { *; }
-keep class dev.whyoleg.cryptography.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class androidx.appcompat.app.** { *; }
-keep class androidx.fragment.app.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.recyclerview.widget.** { *; }
-keep class androidx.preference.** { *; }
-dontwarn com.fasterxml.jackson.**

# The trimmed runtime keeps method-body references to parts of the CloudStream app that StreamDek
# deliberately does not ship (its UI, downloader, cast and NewPipe-backed YouTube extraction).
# Those code paths are unreachable from the provider API, so the dangling references are expected.
-dontwarn com.lagradost.**
-dontwarn org.schabi.newpipe.**
-dontwarn org.conscrypt.**
-dontwarn org.chromium.net.**
-dontwarn com.google.android.material.**
-dontwarn com.google.android.gms.cast.**
-dontwarn androidx.navigation.**
-dontwarn androidx.recyclerview.**
-dontwarn androidx.viewbinding.**
-dontwarn androidx.viewpager2.**
-dontwarn androidx.palette.**
-dontwarn androidx.tvprovider.**
-dontwarn androidx.work.**
-dontwarn androidx.biometric.**
-dontwarn coil3.**
-dontwarn io.ktor.**
-dontwarn kotlinx.datetime.**
-dontwarn kotlinx.io.**
-dontwarn com.fleeksoft.ksoup.**
-dontwarn dev.whyoleg.cryptography.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.universalchardet.**
-dontwarn com.discord.panels.**
-dontwarn com.facebook.shimmer.**
-dontwarn com.jaredrummler.**
-dontwarn com.github.rubensousa.**
-dontwarn io.github.anilbeesetti.**
-dontwarn go.Seq
-dontwarn torrServer.**
# jsoup 1.22 (pulled in by NiceHttp) has optional re2j-backed regex support that is not bundled.
-dontwarn com.google.re2j.**
