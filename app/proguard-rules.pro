# Smart Search runtimes call back into their Java/Kotlin classes from native code (JNI), by name.
-keep class ai.onnxruntime.** { *; }
-keep class com.google.ai.edge.litert.** { *; }

# Only LiteRT's AiPackModelProvider (Play's AI-pack model delivery) uses these; the Play library
# is excluded from the build and Lumen never ships models through Play.
-dontwarn com.google.android.play.core.aipacks.**
-dontwarn com.google.android.gms.tasks.**

# Details → All Metadata lists EXIF tags by reading ExifInterface's TAG_ constants by reflection
# (AllMetadata.ExifTags). Minified, R8 removed them and every photo said "Nothing readable".
-keepclassmembers class androidx.exifinterface.media.ExifInterface {
    public static final java.lang.String TAG_*;
}
