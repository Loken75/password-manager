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
 *
 * Key material (DEK and KEK) is handled as raw {@code byte[]} and wiped after use;
 * the only residual exposure is the short-lived {@link SecretKeySpec} each cipher
 * operation must build, which the JCA copies and we cannot zero.
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
        byte[] kekBytes = null;
        try {
            RANDOM.nextBytes(rawDek);

            salt = KeyDerivation.generateSalt();
            int iterations = KeyDerivation.getDefaultIterations();
            kekBytes = KeyDerivation.deriveKeyBytes(masterPassword, salt, iterations);

            byte[] kekIv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(kekIv);
            EncryptedPayload encDek = doEncrypt(rawDek, new SecretKeySpec(kekBytes, "AES"), kekIv, null);

            return new VaultSession(rawDek, salt, kekIv, encDek.getCiphertext(), iterations);
        } catch (VaultEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new VaultEncryptionException("Failed to create vault session", e);
        } finally {
            SecureWiper.wipe(rawDek);
            SecureWiper.wipe(kekBytes);
        }
    }

    @Override
    public VaultSession openSession(byte[] salt, byte[] kekIv, byte[] encryptedDek,
                                    int kdfIterations, char[] masterPassword) throws VaultDecryptionException {
        byte[] kekBytes = null;
        byte[] rawDek = null;
        try {
            kekBytes = KeyDerivation.deriveKeyBytes(masterPassword, salt, kdfIterations);
            rawDek = doDecrypt(encryptedDek, kekIv, new SecretKeySpec(kekBytes, "AES"), null);

            return new VaultSession(rawDek,
                Arrays.copyOf(salt, salt.length),
                Arrays.copyOf(kekIv, kekIv.length),
                Arrays.copyOf(encryptedDek, encryptedDek.length),
                kdfIterations);
        } catch (Exception e) {
            throw new VaultDecryptionException("Invalid master password or corrupted vault", e);
        } finally {
            SecureWiper.wipe(kekBytes);
            SecureWiper.wipe(rawDek);
        }
    }

    @Override
    public EncryptedPayload encryptData(byte[] plaintext, SecretKey dataKey, byte[] aad) throws VaultEncryptionException {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            return doEncrypt(plaintext, dataKey, iv, aad);
        } catch (Exception e) {
            throw new VaultEncryptionException("Data encryption failed", e);
        }
    }

    @Override
    public byte[] decryptData(byte[] iv, byte[] ciphertext, SecretKey dataKey, byte[] aad) throws VaultDecryptionException {
        try {
            return doDecrypt(ciphertext, iv, dataKey, aad);
        } catch (Exception e) {
            throw new VaultDecryptionException("Data decryption failed", e);
        }
    }

    @Override
    public VaultSession changePassword(VaultSession session, char[] newPassword) throws VaultEncryptionException {
        byte[] newKekBytes = null;
        byte[] rawDek = null;
        try {
            byte[] newSalt = KeyDerivation.generateSalt();
            int iterations = KeyDerivation.getDefaultIterations();
            newKekBytes = KeyDerivation.deriveKeyBytes(newPassword, newSalt, iterations);

            byte[] newKekIv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(newKekIv);
            rawDek = session.getDataKey().getEncoded();
            EncryptedPayload encDek = doEncrypt(rawDek, new SecretKeySpec(newKekBytes, "AES"), newKekIv, null);

            session.updateEnvelope(newSalt, newKekIv, encDek.getCiphertext(), iterations);
            return session;
        } catch (VaultEncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new VaultEncryptionException("Password change failed", e);
        } finally {
            SecureWiper.wipe(rawDek);
            SecureWiper.wipe(newKekBytes);
        }
    }

    @Override
    public byte[] decryptLegacy(byte[] salt, byte[] iv, byte[] ciphertext,
                                char[] masterPassword) throws VaultDecryptionException {
        byte[] keyBytes = null;
        try {
            keyBytes = KeyDerivation.deriveKeyBytes(masterPassword, salt, LEGACY_ITERATIONS);
            return doDecrypt(ciphertext, iv, new SecretKeySpec(keyBytes, "AES"), null);
        } catch (Exception e) {
            throw new VaultDecryptionException("Legacy vault decryption failed", e);
        } finally {
            SecureWiper.wipe(keyBytes);
        }
    }

    private EncryptedPayload doEncrypt(byte[] plaintext, SecretKey key, byte[] iv, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        if (aad != null) cipher.updateAAD(aad);
        byte[] ciphertext = cipher.doFinal(plaintext);
        return new EncryptedPayload(Arrays.copyOf(iv, iv.length), ciphertext);
    }

    private byte[] doDecrypt(byte[] ciphertext, byte[] iv, SecretKey key, byte[] aad) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        if (aad != null) cipher.updateAAD(aad);
        return cipher.doFinal(ciphertext);
    }
}
