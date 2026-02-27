# --- Gson ---
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * extends com.google.gson.TypeAdapterFactory
-keep class * extends com.google.gson.JsonSerializer
-keep class * extends com.google.gson.JsonDeserializer

# --- Core module data classes (serialized by Gson) ---
-keep class com.passwordmanager.vault.Vault { *; }
-keep class com.passwordmanager.vault.PasswordEntry { *; }
-keep class com.passwordmanager.vault.VaultLoadResult { *; }
-keep class com.passwordmanager.vault.VaultManager$CharArrayAdapter { *; }
-keep class com.passwordmanager.config.** { *; }
-keep class com.passwordmanager.crypto.EncryptedPayload { *; }

# --- AndroidX Security / Google Tink (EncryptedSharedPreferences) ---
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# --- Kotlin Coroutines ---
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- AndroidX Lifecycle ---
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# --- AndroidX Navigation ---
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# --- Jetpack Compose ---
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# --- JSch (SFTP sync) ---
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# --- Dagger / Hilt ---
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class **_HiltModules* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
