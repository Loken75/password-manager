package com.passwordmanager.crypto;

import com.passwordmanager.util.SecureWiper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM encryption service with DEK/KEK envelope encryption.
 *
 * Architecture:
 * - Master password -> PBKDF2 -> KEK (Key Encryption Key)
 * - Random DEK (Data Encryption Key) encrypted with KEK
 * - Vault data encrypted with DEK
 *
 * Benefits: saves are fast (no KDF), password change only re-encrypts DEK,
 * master password is not needed in memory after unlock.
 */
public class CryptoService implements EncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int DEK_LENGTH = 32;
    private static final int LEGACY_ITERATIONS = 100_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public VaultSession createSession(char[] masterPassword) throws VaultEncryptionException {
        byte[] rawDek = new byte[DEK_LENGTH];
        byte[] salt = null;
        SecretKey kek = null;
        try {
            RANDOM.nextBytes(rawDek);
            SecretKey dek = new SecretKeySpec(rawDek, "AES");

            salt = KeyDerivation.generateSalt();
            int iterations = KeyDerivation.getDefaultIterations();
            kek = KeyDerivation.deriveKey(masterPassword, salt, iterations);

            byte[] kekIv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(kekIv);
            EncryptedPayload encDek = doEncrypt(dek.getEncoded(), kek, kekIv);

            return new VaultSession(dek, salt, kekIv, encDek.getCiphertext(), iterations);
        } catch (VaultEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new VaultEncryptionException("Failed to create vault session", e);
        } finally {
            SecureWiper.wipe(rawDek);
            destroyKey(kek);
        }
    }

    @Override
    public VaultSession openSession(byte[] salt, byte[] kekIv, byte[] encryptedDek,
                                    int kdfIterations, char[] masterPassword) throws VaultDecryptionException {
        SecretKey kek = null;
        byte[] rawDek = null;
        try {
            kek = KeyDerivation.deriveKey(masterPassword, salt, kdfIterations);
            rawDek = doDecrypt(encryptedDek, kekIv, kek);
            SecretKey dek = new SecretKeySpec(rawDek, "AES");

            return new VaultSession(dek,
                Arrays.copyOf(salt, salt.length),
                Arrays.copyOf(kekIv, kekIv.length),
                Arrays.copyOf(encryptedDek, encryptedDek.length),
                kdfIterations);
        } catch (Exception e) {
            throw new VaultDecryptionException("Invalid master password or corrupted vault", e);
        } finally {
            destroyKey(kek);
            SecureWiper.wipe(rawDek);
        }
    }

    @Override
    public EncryptedPayload encryptData(byte[] plaintext, SecretKey dataKey) throws VaultEncryptionException {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            return doEncrypt(plaintext, dataKey, iv);
        } catch (Exception e) {
            throw new VaultEncryptionException("Data encryption failed", e);
        }
    }

    @Override
    public byte[] decryptData(byte[] iv, byte[] ciphertext, SecretKey dataKey) throws VaultDecryptionException {
        try {
            return doDecrypt(ciphertext, iv, dataKey);
        } catch (Exception e) {
            throw new VaultDecryptionException("Data decryption failed", e);
        }
    }

    @Override
    public VaultSession changePassword(VaultSession session, char[] newPassword) throws VaultEncryptionException {
        SecretKey newKek = null;
        try {
            byte[] newSalt = KeyDerivation.generateSalt();
            int iterations = KeyDerivation.getDefaultIterations();
            newKek = KeyDerivation.deriveKey(newPassword, newSalt, iterations);

            byte[] newKekIv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(newKekIv);
            EncryptedPayload encDek = doEncrypt(session.getDataKey().getEncoded(), newKek, newKekIv);

            session.updateEnvelope(newSalt, newKekIv, encDek.getCiphertext(), iterations);
            return session;
        } catch (VaultEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new VaultEncryptionException("Password change failed", e);
        } finally {
            destroyKey(newKek);
        }
    }

    @Override
    public byte[] decryptLegacy(byte[] salt, byte[] iv, byte[] ciphertext,
                                char[] masterPassword) throws VaultDecryptionException {
        SecretKey key = null;
        try {
            key = KeyDerivation.deriveKey(masterPassword, salt, LEGACY_ITERATIONS);
            return doDecrypt(ciphertext, iv, key);
        } catch (Exception e) {
            throw new VaultDecryptionException("Legacy vault decryption failed", e);
        } finally {
            destroyKey(key);
        }
    }

    private EncryptedPayload doEncrypt(byte[] plaintext, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] ciphertext = cipher.doFinal(plaintext);
        return new EncryptedPayload(Arrays.copyOf(iv, iv.length), ciphertext);
    }

    private byte[] doDecrypt(byte[] ciphertext, byte[] iv, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertext);
    }

    private static void destroyKey(SecretKey key) {
        if (key != null) {
            try { key.destroy(); } catch (Exception ignored) {}
        }
    }
}
