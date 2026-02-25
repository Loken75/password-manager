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
-keep class com.passwordmanager.vault.VaultEntry { *; }
-keep class com.passwordmanager.vault.VaultLoadResult { *; }
-keep class com.passwordmanager.vault.VaultManager$CharArrayAdapter { *; }
-keep class com.passwordmanager.config.** { *; }
-keep class com.passwordmanager.crypto.EncryptedPayload { *; }

# --- AndroidX Security (EncryptedSharedPreferences) ---
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

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
