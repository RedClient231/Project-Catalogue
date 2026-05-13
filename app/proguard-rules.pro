-keepattributes *Annotation*,Signature,Exceptions,InnerClasses
-keep class com.redclient.virtualspace.** { *; }
-keepclassmembers class com.redclient.virtualspace.** { *; }
-dontwarn com.redclient.virtualspace.**
-keep class androidx.** { *; }
-dontwarn androidx.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class com.google.gson.** { *; }
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
-keepclasseswithmembernames class * {
    native <methods>;
}
