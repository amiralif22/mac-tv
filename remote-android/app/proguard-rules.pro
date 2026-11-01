# The protocol is plain JSON built by hand, so nothing needs keeping except the WebSocket library's
# optional reflection hooks, which R8 cannot see through.
-keep class org.java_websocket.** { *; }
-dontwarn org.slf4j.**
