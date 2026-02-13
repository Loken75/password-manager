package com.passwordmanager.crypto;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption and decryption service.
 */
public class CryptoService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Encrypts plaintext with a master password.
     * Returns a JSON string containing version, salt, iv, and encrypted_data (all Base64).
     */
    public String encrypt(String plaintext, char[] masterPassword) throws Exception {
        byte[] salt = KeyDerivation.generateSalt();
        SecretKey key = KeyDerivation.deriveKey(masterPassword, salt);

        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        JsonObject json = new JsonObject();
        json.addProperty("version", "1.0");
        json.addProperty("salt", Base64.getEncoder().encodeToString(salt));
        json.addProperty("iv", Base64.getEncoder().encodeToString(iv));
        json.addProperty("encrypted_data", Base64.getEncoder().encodeToString(ciphertext));
        return json.toString();
    }

    /**
     * Decrypts an encrypted JSON string with the master password.
     */
    public String decrypt(String encryptedJson, char[] masterPassword) throws Exception {
        JsonObject json = JsonParser.parseString(encryptedJson).getAsJsonObject();

        byte[] salt = Base64.getDecoder().decode(json.get("salt").getAsString());
        byte[] iv = Base64.getDecoder().decode(json.get("iv").getAsString());
        byte[] ciphertext = Base64.getDecoder().decode(json.get("encrypted_data").getAsString());

        SecretKey key = KeyDerivation.deriveKey(masterPassword, salt);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * Re-encrypts data with a new password.
     */
    public String reEncrypt(String encryptedJson, char[] oldPassword, char[] newPassword) throws Exception {
        String plaintext = decrypt(encryptedJson, oldPassword);
        return encrypt(plaintext, newPassword);
    }
}
