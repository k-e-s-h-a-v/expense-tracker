# Project-specific ProGuard / R8 rules for Expense Tracker.
# Applied alongside proguard-android-optimize.txt (see app/build.gradle.kts).

# Preserve line numbers for readable release crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep manifest-declared components and their constructors.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ViewModels are often instantiated by name via reflection.
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# App model types used in Compose state and SharedPreferences serialization.
-keep class com.keshav.expensetracker.model.** { *; }

# Kotlin metadata used by coroutines and default interface methods.
-keepattributes InnerClasses,EnclosingMethod,Signature,*Annotation*
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Compose runtime keeps (library consumer rules cover most cases; these are safety nets).
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-dontwarn com.google.errorprone.annotations.**
