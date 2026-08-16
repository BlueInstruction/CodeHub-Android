-keep class codehub.terminal.termux.** { *; }

-keep class com.termux.terminal.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
