# Keep Room generated code and Kotlin metadata.
-keep class dev.snapttl.data.expiry.** { *; }
-keep class dev.snapttl.data.search.** { *; }

# ML Kit reflection-based model loading.
-keep class com.google.mlkit.** { *; }

# Do not strip the Compose runtime's intrinsics used by the framework.
-dontwarn androidx.compose.**
