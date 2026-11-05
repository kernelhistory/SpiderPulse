# SpiderPulse OSINT Release R8 / ProGuard Configuration
-optimizationpasses 5
-allowaccessmodification

# Keep model classes and enums used in state & JSON serialization
-keep class com.spiderpulse.osint.model.** { *; }

# Strip verbose/debug Android log calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep Kotlin Coroutines & Compose runtime metadata
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-dontwarn kotlinx.coroutines.**
