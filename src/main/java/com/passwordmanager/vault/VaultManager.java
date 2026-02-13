package com.passwordmanager.vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.passwordmanager.crypto.CryptoService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages vault persistence: load/save encrypted vault files, import/export.
 */
public class VaultManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final CryptoService cryptoService = new CryptoService();
    private String vaultDirectory;

    public VaultManager(String vaultDirectory) {
        this.vaultDirectory = vaultDirectory;
        File dir = new File(vaultDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public String getVaultDirectory() { return vaultDirectory; }

    public String getVaultPath(String username) {
        return vaultDirectory + File.separator + "vault_" + username + ".enc";
    }

    public boolean vaultExists(String username) {
        return new File(getVaultPath(username)).exists();
    }

    public Vault createVault(String username, char[] masterPassword) throws Exception {
        Vault vault = new Vault(username);
        saveVault(vault, username, masterPassword);
        return vault;
    }

    public Vault loadVault(String username, char[] masterPassword) throws Exception {
        String path = getVaultPath(username);
        byte[] fileBytes = Files.readAllBytes(Paths.get(path));
        String encryptedJson = new String(fileBytes, StandardCharsets.UTF_8);
        String decryptedJson = cryptoService.decrypt(encryptedJson, masterPassword);
        return gson.fromJson(decryptedJson, Vault.class);
    }

    public void saveVault(Vault vault, String username, char[] masterPassword) throws Exception {
        String json = gson.toJson(vault);
        String encryptedJson = cryptoService.encrypt(json, masterPassword);
        String path = getVaultPath(username);
        Files.write(Paths.get(path), encryptedJson.getBytes(StandardCharsets.UTF_8));
    }

    public String[] listUsers() {
        File dir = new File(vaultDirectory);
        File[] files = dir.listFiles(new FilenameFilter() {
            public boolean accept(File d, String name) {
                return name.startsWith("vault_") && name.endsWith(".enc");
            }
        });
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
        File file = new File(getVaultPath(username));
        return file.exists() && file.delete();
    }

    public void changeMasterPassword(String username, char[] oldPassword, char[] newPassword) throws Exception {
        Vault vault = loadVault(username, oldPassword);
        saveVault(vault, username, newPassword);
    }

    public void exportBackup(String username, char[] masterPassword, String exportPath) throws Exception {
        String sourcePath = getVaultPath(username);
        byte[] data = Files.readAllBytes(Paths.get(sourcePath));
        Files.write(Paths.get(exportPath), data);
    }

    public String exportAsJson(String username, char[] masterPassword) throws Exception {
        Vault vault = loadVault(username, masterPassword);
        return gson.toJson(vault);
    }

    public String exportAsCsv(String username, char[] masterPassword) throws Exception {
        Vault vault = loadVault(username, masterPassword);
        StringBuilder sb = new StringBuilder();
        sb.append("title,username,password,url,notes,category,tags\n");
        for (VaultEntry e : vault.getEntries()) {
            sb.append(csvEscape(e.getTitle())).append(",");
            sb.append(csvEscape(e.getUsername())).append(",");
            sb.append(csvEscape(e.getPassword())).append(",");
            sb.append(csvEscape(e.getUrl())).append(",");
            sb.append(csvEscape(e.getNotes())).append(",");
            sb.append(csvEscape(e.getCategory())).append(",");
            sb.append(csvEscape(e.getTags() != null ? joinStrings(e.getTags(), ";") : "")).append("\n");
        }
        return sb.toString();
    }

    public int importFromCsv(Vault vault, String csvContent) {
        String[] lines = csvContent.split("\n");
        if (lines.length < 2) return 0;

        String headerLine = lines[0].trim();

        // Detect separator: if more ';' than ',' in header, use ';'
        int semicolons = 0;
        int commas = 0;
        for (int i = 0; i < headerLine.length(); i++) {
            if (headerLine.charAt(i) == ';') semicolons++;
            else if (headerLine.charAt(i) == ',') commas++;
        }
        char separator = semicolons > commas ? ';' : ',';

        // Parse header and build column mapping
        String[] headers = parseCsvLine(headerLine, separator);
        Map<String, String> aliasMap = buildAliasMap();
        int titleIdx = -1, usernameIdx = -1, passwordIdx = -1, urlIdx = -1;
        int notesIdx = -1, categoryIdx = -1, tagsIdx = -1;
        boolean headerRecognized = false;

        for (int i = 0; i < headers.length; i++) {
            String normalized = stripAccents(headers[i].trim().toLowerCase());
            String field = aliasMap.get(normalized);
            if (field != null) {
                headerRecognized = true;
                if ("title".equals(field)) titleIdx = i;
                else if ("username".equals(field)) usernameIdx = i;
                else if ("password".equals(field)) passwordIdx = i;
                else if ("url".equals(field)) urlIdx = i;
                else if ("notes".equals(field)) notesIdx = i;
                else if ("category".equals(field)) categoryIdx = i;
                else if ("tags".equals(field)) tagsIdx = i;
            }
        }

        // Fallback: if no header recognized, use fixed positional order
        if (!headerRecognized) {
            titleIdx = 0; usernameIdx = 1; passwordIdx = 2; urlIdx = 3;
            notesIdx = 4; categoryIdx = 5; tagsIdx = 6;
        }

        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = parseCsvLine(line, separator);

            VaultEntry entry = new VaultEntry();
            entry.setTitle(getField(parts, titleIdx));
            entry.setUsername(getField(parts, usernameIdx));
            entry.setPassword(getField(parts, passwordIdx));
            entry.setUrl(getField(parts, urlIdx));
            entry.setNotes(getField(parts, notesIdx));
            String cat = getField(parts, categoryIdx);
            entry.setCategory(cat.isEmpty() ? "Autre" : cat);
            String tagsVal = getField(parts, tagsIdx);
            if (!tagsVal.isEmpty()) {
                entry.setTags(Arrays.asList(tagsVal.split(";")));
            }

            // Only add if we have at least a title or username or password
            if (!entry.getTitle().isEmpty() || !entry.getUsername().isEmpty() || !entry.getPassword().isEmpty()) {
                vault.getEntries().add(entry);
                count++;
            }
        }
        return count;
    }

    private static String getField(String[] parts, int index) {
        if (index >= 0 && index < parts.length) {
            return parts[index];
        }
        return "";
    }

    private static String stripAccents(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private static Map<String, String> buildAliasMap() {
        Map<String, String> map = new HashMap<String, String>();
        // title aliases
        for (String alias : new String[]{"title", "organisme", "name", "nom", "titre"}) {
            map.put(alias, "title");
        }
        // username aliases
        for (String alias : new String[]{"username", "identifiant", "email", "adresse mail",
                "login", "adresse mail / identifiant"}) {
            map.put(alias, "username");
        }
        // password aliases
        for (String alias : new String[]{"password", "mdp", "mot de passe", "pass"}) {
            map.put(alias, "password");
        }
        // url aliases
        for (String alias : new String[]{"url", "site", "website", "lien"}) {
            map.put(alias, "url");
        }
        // notes aliases
        for (String alias : new String[]{"notes", "description", "commentaire"}) {
            map.put(alias, "notes");
        }
        // category aliases
        for (String alias : new String[]{"category", "categorie", "type"}) {
            map.put(alias, "category");
        }
        // tags aliases
        for (String alias : new String[]{"tags", "etiquettes"}) {
            map.put(alias, "tags");
        }
        return map;
    }

    public int importFromJson(Vault vault, String jsonContent) {
        Vault imported = gson.fromJson(jsonContent, Vault.class);
        if (imported != null && imported.getEntries() != null) {
            vault.getEntries().addAll(imported.getEntries());
            return imported.getEntries().size();
        }
        return 0;
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String[] parseCsvLine(String line, char separator) {
        List<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == separator) {
                    fields.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private static String joinStrings(List<String> list, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
