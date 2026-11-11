# UniFFI's Kotlin bindings reach the Rust library through JNA, which finds the bindings' native
# functions and structures by reflection: keep both.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class com.linpaintx.core.** { *; }
# the JNI functions the library exports are found by these names
-keep class com.linpaintx.canvas.NativeWindows { *; }
-dontwarn java.awt.**
