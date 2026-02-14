# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.unpal.app.data.** { *; }
