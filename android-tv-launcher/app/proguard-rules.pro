# kotlinx.serialization keeps its serializers in generated companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.couchtv.launcher.** {
    *** Companion;
}
-keepclasseswithmembers class dev.couchtv.launcher.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.couchtv.launcher.**$$serializer { *; }

# Java-WebSocket talks to the remote apps in a stable shape.
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**
