# Room / Kotlin metadata is handled by AGP defaults.
# Keep VpnService + AccessibilityService entry points referenced only from the manifest.
-keep class com.sawn.app.vpn.SawnVpnService { *; }
-keep class com.sawn.app.accessibility.AppBlockAccessibilityService { *; }
-keep class com.sawn.app.accessibility.BrowserUrlMonitor { *; }
-keep class com.sawn.app.accessibility.InterceptActivityBridge { *; }
-keep class com.sawn.app.system.BootReceiver { *; }
-keep class com.sawn.app.system.KeepAliveWorker { *; }
-keep class com.sawn.app.system.GuardHealthWorker { *; }
-keep class com.sawn.app.system.NightGuardWorker { *; }
-keep class com.sawn.app.system.NudgesWorker { *; }
-keep class com.sawn.app.system.FocusEndWorker { *; }
-keep class com.sawn.app.system.SawnTile { *; }
-keep class com.sawn.app.system.SawnNotificationListener { *; }
-keep class com.sawn.app.system.SawnDeviceAdminReceiver { *; }
-keep class com.sawn.app.widget.SawnWidgetProvider { *; }
-keep class com.sawn.app.ui.intercept.InterceptActivity { *; }

# TF Lite Task Vision: AutoValue-generated classes reference the annotation at compile
# time only; R8 needs the (unshipped) dependency declared as don't-warn.
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue

# Strip all logging from release builds (defense against accidental sensitive logging).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
