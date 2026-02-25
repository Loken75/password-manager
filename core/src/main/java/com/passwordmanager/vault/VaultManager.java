package com.passwordmanager.vault;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.passwordmanager.crypto.*;
import com.passwordmanager.util.DateUtils;
import com.passwordmanager.util.FileSecurityUtils;
import com.passwordmanager.util.SecureWiper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Manages vault persistence: load/save encrypted vault files with DEK/KEK envelope encryption.
 * Import/export is delegated to VaultImporter/VaultExporter.
 */
public class VaultManager {
    private static final String VAULT_VERSION = "2.0";
    private final Gson gson;
    private final EncryptionService cryptoService;
    private final VaultImporter importer;
    private final VaultExporter exporter;
    private final String vaultDirectory;

    public VaultManager(String vaultDirectory) {
        this(vaultDirectory, new CryptoService());
    }

    public VaultManager(String vaultDirectory, EncryptionService cryptoService) {
        this.vaultDirectory = vaultDirectory;
        this.cryptoService = cryptoService;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(char[].class, new CharArrayAdapter())
            .create();
        this.importer = new VaultImporter(gson);
        this.exporter = new VaultExporter(gson);
        File dir = new File(vaultDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            FileSecurityUtils.setOwnerOnlyPermissions(dir.toPath());
        }
    }

    public String getVaultDirectory() { return vaultDirectory; }
    public VaultImporter getImporter() { return importer; }
    public VaultExporter getExporter() { return exporter; }

    public String getVaultPath(String username) {
        validateUsername(username);
        return vaultDirectory + File.separator + "vault_" + username + ".enc";
    }

    /**
     * Validates the username to prevent path traversal at the VaultManager level.
     * Only alphanumeric characters and underscores are allowed.
     */
    private static void validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Username contains invalid characters");
        }
    }

    public boolean vaultExists(String username) {
        return new File(getVaultPath(username)).exists();
    }

    /**
     * Creates a new vault with DEK/KEK envelope encryption (English default categories).
     */
    public VaultLoadResult createVault(String username, char[] masterPassword)
            throws VaultEncryptionException, IOException {
        return createVault(username, masterPassword, null);
    }

    /**
     * Creates a new vault with DEK/KEK envelope encryption.
     * @param defaultCategories localized categories, or null for English defaults
     */
    public VaultLoadResult createVault(String username, char[] masterPassword,
                                       List<String> defaultCategories)
            throws VaultEncryptionException, IOException {
        Vault vault = (defaultCategories != null)
                ? new Vault(username, defaultCategories)
                : new Vault(username);
        VaultSession session = cryptoService.createSession(masterPassword);
        saveVault(vault, username, session);
        return new VaultLoadResult(vault, session);
    }

    /**
     * Loads a vault, auto-migrating v1.0 format to v2.0 DEK/KEK format.
     */
    /** Maximum vault file size: 50 MB. */
    private static final long MAX_VAULT_FILE_SIZE = 50L * 1024 * 1024;

    public VaultLoadResult loadVault(String username, char[] masterPassword)
            throws VaultDecryptionException, VaultEncryptionException, IOException {
        String path = getVaultPath(username);
        Path filePath = Paths.get(path);
        long fileSize = Files.size(filePath);
        if (fileSize > MAX_VAULT_FILE_SIZE) {
            throw new IOException("Vault file exceeds maximum size (" + MAX_VAULT_FILE_SIZE / (1024 * 1024) + " MB)");
        }
        byte[] fileBytes = Files.readAllBytes(filePath);
        String fileContent;
        try {
            fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        } finally {
            SecureWiper.wipe(fileBytes);
        }
        JsonObject json = JsonParser.parseString(fileContent).getAsJsonObject();
        String version = json.has("version") ? json.get("version").getAsString() : "1.0";

        VaultSession session;
        byte[] vaultJsonBytes;

        if ("2.0".equals(version)) {
            byte[] salt = Base64.getDecoder().decode(json.get("salt").getAsString());
            byte[] kekIv = Base64.getDecoder().decode(json.get("kek_iv").getAsString());
            byte[] encryptedDek = Base64.getDecoder().decode(json.get("encrypted_dek").getAsString());
            int iterations = json.get("kdf_iterations").getAsInt();
            byte[] dataIv = Base64.getDecoder().decode(json.get("data_iv").getAsString());
            byte[] ciphertext = Base64.getDecoder().decode(json.get("encrypted_data").getAsString());

            session = cryptoService.openSession(salt, kekIv, encryptedDek, iterations, masterPassword);
            vaultJsonBytes = cryptoService.decryptData(dataIv, ciphertext, session.getDataKey());
        } else {
            // Legacy v1.0: password directly derives the data key
            byte[] salt = Base64.getDecoder().decode(json.get("salt").getAsString());
            byte[] iv = Base64.getDecoder().decode(json.get("iv").getAsString());
            byte[] ciphertext = Base64.getDecoder().decode(json.get("encrypted_data").getAsString());

            vaultJsonBytes = cryptoService.decryptLegacy(salt, iv, ciphertext, masterPassword);
            // Auto-migrate: create new DEK/KEK session
            session = cryptoService.createSession(masterPassword);
        }

        Vault vault;
        try {
            vault = gson.fromJson(new String(vaultJsonBytes, StandardCharsets.UTF_8), Vault.class);
        } finally {
            SecureWiper.wipe(vaultJsonBytes);
        }

        // If migrated from v1.0, save in v2.0 format immediately
        if (!"2.0".equals(version)) {
            saveVault(vault, username, session);
        }

        return new VaultLoadResult(vault, session);
    }

    /**
     * Saves vault with backup and atomic write for data safety.
     * Uses the session's DEK for encryption (no KDF needed -- fast).
     */
    public void saveVault(Vault vault, String username, VaultSession session)
            throws VaultEncryptionException, IOException {
        vault.setUpdatedAt(DateUtils.getCurrentTimestamp());

        // Serialize vault to bytes and wipe after encryption
        byte[] vaultBytes = gson.toJson(vault).getBytes(StandardCharsets.UTF_8);
        String envelopeJson;
        try {
            EncryptedPayload encData = cryptoService.encryptData(vaultBytes, session.getDataKey());

            JsonObject envelope = new JsonObject();
            envelope.addProperty("version", VAULT_VERSION);
            envelope.addProperty("kdf", "PBKDF2WithHmacSHA256");
            envelope.addProperty("kdf_iterations", session.getKdfIterations());
            envelope.addProperty("salt", Base64.getEncoder().encodeToString(session.getSalt()));
            envelope.addProperty("kek_iv", Base64.getEncoder().encodeToString(session.getKekIv()));
            envelope.addProperty("encrypted_dek", Base64.getEncoder().encodeToString(session.getEncryptedDek()));
            envelope.addProperty("data_iv", Base64.getEncoder().encodeToString(encData.getIv()));
            envelope.addProperty("encrypted_data", Base64.getEncoder().encodeToString(encData.getCiphertext()));
            envelopeJson = envelope.toString();
        } finally {
            SecureWiper.wipe(vaultBytes);
        }

        Path path = Paths.get(getVaultPath(username));

        // Backup existing file before overwriting (single rolling .bak)
        if (Files.exists(path)) {
            Path backupPath = Paths.get(getVaultPath(username) + ".bak");
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
            FileSecurityUtils.setOwnerOnlyPermissions(backupPath);
            cleanupOldBackups(username);
        }

        // Atomic write: write to temp file, set permissions, then rename
        Path tempPath = Paths.get(getVaultPath(username) + ".tmp");
        Files.write(tempPath, envelopeJson.getBytes(StandardCharsets.UTF_8));
        FileSecurityUtils.setOwnerOnlyPermissions(tempPath);
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempPath);
        }

        FileSecurityUtils.setOwnerOnlyPermissions(path);
    }

    public String[] listUsers() {
        File dir = new File(vaultDirectory);
        File[] files = dir.listFiles((d, name) -> name.startsWith("vault_") && name.endsWith(".enc"));
        if (files == null) return new String[0];
        String[] users = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            users[i] = name.substring(6, name.length() - 4);
        }
        Arrays.sort(users);
        return users;
    }

    public boolean deleteVault(String username) {
        validateUsername(username);
        File file = new File(getVaultPath(username));
        boolean deleted = file.exists() && file.delete();

        // Also delete all backup files for this user
        File dir = new File(vaultDirectory);
        String prefix = "vault_" + username;
        File[] backups = dir.listFiles((d, name) ->
            name.startsWith(prefix) && name.endsWith(".bak"));
        if (backups != null) {
            for (File backup : backups) {
                backup.delete();
            }
        }
        return deleted;
    }

    /**
     * Changes the master password: re-encrypts the DEK only (fast, no full vault re-encryption).
     */
    public VaultSession changeMasterPassword(String username, Vault vault,
                                              VaultSession currentSession, char[] newPassword)
            throws VaultEncryptionException, IOException {
        VaultSession updatedSession = cryptoService.changePassword(currentSession, newPassword);
        saveVault(vault, username, updatedSession);
        return updatedSession;
    }

    /**
     * Reloads a vault from disk using an existing session's DEK.
     * Used after sync downloads a remote vault file (same DEK, no need for master password).
     */
    public Vault reloadVault(String username, VaultSession session)
            throws VaultDecryptionException, IOException {
        String path = getVaultPath(username);
        byte[] fileBytes = Files.readAllBytes(Paths.get(path));
        String fileContent;
        try {
            fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        } finally {
            SecureWiper.wipe(fileBytes);
        }
        JsonObject json = JsonParser.parseString(fileContent).getAsJsonObject();

        byte[] dataIv = Base64.getDecoder().decode(json.get("data_iv").getAsString());
        byte[] ciphertext = Base64.getDecoder().decode(json.get("encrypted_data").getAsString());

        byte[] vaultJsonBytes = cryptoService.decryptData(dataIv, ciphertext, session.getDataKey());
        try {
            return gson.fromJson(new String(vaultJsonBytes, StandardCharsets.UTF_8), Vault.class);
        } finally {
            SecureWiper.wipe(vaultJsonBytes);
        }
    }

    /**
     * Exports an encrypted backup. Verifies the vault can be loaded first.
     */
    public void exportBackup(String username, VaultSession session, String exportPath) throws IOException {
        String sourcePath = getVaultPath(username);
        byte[] data = Files.readAllBytes(Paths.get(sourcePath));
        Path target = Paths.get(exportPath);
        Files.write(target, data);
        FileSecurityUtils.setOwnerOnlyPermissions(target);
    }

    public char[] exportAsJson(Vault vault) {
        return exporter.exportAsJson(vault);
    }

    public char[] exportAsCsv(Vault vault) {
        return exporter.exportAsCsv(vault);
    }

    public int importFromCsv(Vault vault, String csvContent) {
        return importer.importFromCsv(vault, csvContent);
    }

    public int importFromJson(Vault vault, String jsonContent) {
        return importer.importFromJson(vault, jsonContent);
    }

    /**
     * Retains only the most recent backup files per user, deleting older ones.
     */
    private void cleanupOldBackups(String username) {
        File dir = new File(vaultDirectory);
        String prefix = "vault_" + username;
        File[] backups = dir.listFiles((d, name) ->
            name.startsWith(prefix) && name.endsWith(".bak"));
        if (backups == null || backups.length <= 3) return;
        java.util.Arrays.sort(backups, java.util.Comparator.comparingLong(File::lastModified).reversed());
        for (int i = 3; i < backups.length; i++) {
            backups[i].delete();
        }
    }

    /**
     * Gson TypeAdapter that serializes char[] as a JSON string (not a JSON array).
     * This ensures backward-compatible JSON format while storing passwords as char[].
     */
    static class CharArrayAdapter extends com.google.gson.TypeAdapter<char[]> {
        @Override
        public void write(JsonWriter out, char[] value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(new String(value));
            }
        }

        @Override
        public char[] read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return in.nextString().toCharArray();
        }
    }
}
