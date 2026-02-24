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
