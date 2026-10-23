# Smart Search runtimes call back into their Java/Kotlin classes from native code (JNI), by name.
-keep class ai.onnxruntime.** { *; }
-keep class com.google.ai.edge.litert.** { *; }

# Only LiteRT's AiPackModelProvider (Play's AI-pack model delivery) uses these; the Play library
# is excluded from the build and Lumen never ships models through Play.
-dontwarn com.google.android.play.core.aipacks.**
-dontwarn com.google.android.gms.tasks.**
