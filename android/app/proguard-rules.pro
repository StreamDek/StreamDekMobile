# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# react-native-reanimated
-keep class com.swmansion.reanimated.** { *; }
-keep class com.facebook.react.turbomodule.** { *; }

# Add any project specific keep options here:
-keep class com.frostwire.jlibtorrent.** { *; }

# Suppress R8 warnings from Google Cast SDK (invalid stack map tables in precompiled bytecode)
-dontwarn com.google.android.gms.internal.cast.**

# Glide (used by expo-image) - annotation-processor generates this class at build time,
# R8 strips it because it sees no direct references in source
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}
-keep public class com.bumptech.glide.request.ThumbnailRequestCoordinator { *; }
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep public class com.bumptech.glide.integration.webp.WebpImage { *; }
-keep public class com.bumptech.glide.integration.webp.WebpFrame { *; }
-keep public class com.bumptech.glide.integration.webp.WebpBitmapFactory { *; }
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder
