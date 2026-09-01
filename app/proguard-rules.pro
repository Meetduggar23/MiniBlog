# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard config.

# Keep data classes used for JSON parsing
-keep class com.example.miniblog.model.** { *; }

# Keep NetworkClient and NetworkUtils
-keep class com.example.miniblog.network.** { *; }

# Keep JsonParser
-keep class com.example.miniblog.data.JsonParser { *; }

# General Android rules
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
