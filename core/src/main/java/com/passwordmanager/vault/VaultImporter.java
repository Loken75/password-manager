package com.passwordmanager.vault;

import com.google.gson.Gson;
import com.passwordmanager.util.DateUtils;
import com.passwordmanager.util.SecureWiper;

import java.text.Normalizer;
import java.util.*;

/**
 * Handles importing vault entries from CSV and JSON formats.
 */
public class VaultImporter {
    private final Gson gson;
    private String defaultImportCategory = "Other";

    public VaultImporter(Gson gson) {
        this.gson = gson;
    }

    /** Sets the localized default category for imported entries without a category. */
    public void setDefaultImportCategory(String category) {
        this.defaultImportCategory = (category != null && !category.isEmpty()) ? category : "Other";
    }

    public int importFromCsv(Vault vault, String csvContent) {
        String[] lines = csvContent.split("\\r?\\n");
        if (lines.length < 2) return 0;

        String headerLine = lines[0].trim();
        // Strip UTF-8 BOM (common in Excel exports on Windows)
        if (headerLine.startsWith("\uFEFF")) {
            headerLine = headerLine.substring(1);
        }

        int semicolons = 0, commas = 0;
        for (int i = 0; i < headerLine.length(); i++) {
            if (headerLine.charAt(i) == ';') semicolons++;
            else if (headerLine.charAt(i) == ',') commas++;
        }
        char separator = semicolons > commas ? ';' : ',';

        String[] headers = parseCsvLine(headerLine, separator);
        Map<String, String> aliasMap = buildAliasMap();
        int titleIdx = -1, usernameIdx = -1, emailIdx = -1;
        int passwordIdx = -1, urlIdx = -1;
        int notesIdx = -1, categoryIdx = -1, tagsIdx = -1, favoriteIdx = -1;
        boolean headerRecognized = false;

        for (int i = 0; i < headers.length; i++) {
            String normalized = stripAccents(headers[i].trim().toLowerCase());
            String field = aliasMap.get(normalized);
            if (field != null) {
                headerRecognized = true;
                switch (field) {
                    case "title": titleIdx = i; break;
                    case "username": usernameIdx = i; break;
                    case "email": emailIdx = i; break;
                    case "pseudo": /* legacy: map to username if username not already found */
                        if (usernameIdx == -1) usernameIdx = i; break;
                    case "password": passwordIdx = i; break;
                    case "url": urlIdx = i; break;
                    case "notes": notesIdx = i; break;
                    case "category": categoryIdx = i; break;
                    case "tags": tagsIdx = i; break;
                    case "favorite": favoriteIdx = i; break;
                }
            }
        }

        if (!headerRecognized) {
            titleIdx = 0; usernameIdx = 1; passwordIdx = 2; urlIdx = 3;
            notesIdx = 4; categoryIdx = 5; tagsIdx = 6;
        }

        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            if (count >= MAX_IMPORT_ENTRIES) break;
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = parseCsvLine(line, separator);

            VaultEntry entry = new VaultEntry();
            entry.setTitle(sanitizeField(getField(parts, titleIdx)));
            entry.setUsername(sanitizeField(getField(parts, usernameIdx)));
            entry.setEmail(sanitizeField(getField(parts, emailIdx)));
            String pwd = sanitizeField(getField(parts, passwordIdx));
            if (!pwd.isEmpty()) {
                char[] pwdChars = pwd.toCharArray();
                entry.setPassword(pwdChars);
                SecureWiper.wipe(pwdChars);
            }
            entry.setUrl(sanitizeField(getField(parts, urlIdx)));
            entry.setNotes(sanitizeField(getField(parts, notesIdx)));
            String cat = sanitizeField(getField(parts, categoryIdx));
            entry.setCategory(cat.isEmpty() ? defaultImportCategory : cat);
            String tagsVal = sanitizeField(getField(parts, tagsIdx));
            if (!tagsVal.isEmpty()) {
                entry.setTags(Arrays.asList(tagsVal.split(";")));
            }
            String favVal = sanitizeField(getField(parts, favoriteIdx));
            entry.setFavorite("true".equalsIgnoreCase(favVal));

            if (!entry.getTitle().isEmpty() || !entry.getUsername().isEmpty() || entry.getPassword() != null) {
                vault.addEntry(entry);
                count++;
            }
        }

        if (count > 0) {
            vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
        }
        return count;
    }

    public int importFromJson(Vault vault, String jsonContent) {
        Vault imported;
        try {
            imported = gson.fromJson(jsonContent, Vault.class);
        } catch (Exception e) {
            return 0;
        }
        if (imported != null && imported.getEntries() != null) {
            int limit = Math.min(imported.getEntries().size(), MAX_IMPORT_ENTRIES);
            int count = 0;
            for (int i = 0; i < limit; i++) {
                VaultEntry entry = imported.getEntries().get(i);
                entry.setId(UUID.randomUUID().toString());
                // Validate mandatory fields exist
                if (entry.getTitle() != null) {
                    entry.setTitle(truncateField(sanitizeField(entry.getTitle())));
                }
                if (entry.getUsername() != null) {
                    entry.setUsername(truncateField(sanitizeField(entry.getUsername())));
                }
                if (entry.getEmail() != null) {
                    entry.setEmail(truncateField(sanitizeField(entry.getEmail())));
                }
                if (entry.getUrl() != null) {
                    entry.setUrl(truncateField(sanitizeField(entry.getUrl())));
                }
                if (entry.getNotes() != null) {
                    entry.setNotes(truncateField(sanitizeField(entry.getNotes())));
                }
                if (entry.getCategory() != null) {
                    entry.setCategory(truncateField(sanitizeField(entry.getCategory())));
                }
                vault.addEntry(entry);
                count++;
            }
            if (count > 0) {
                vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
            }
            return count;
        }
        return 0;
    }

    private static String getField(String[] parts, int index) {
        return (index >= 0 && index < parts.length) ? parts[index] : "";
    }

    private static String stripAccents(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    /**
     * Strips control characters from imported data to prevent display
     * corruption and potential injection attacks.
     */
    private static String sanitizeField(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    private static String truncateField(String value) {
        if (value == null) return "";
        return value.length() > MAX_FIELD_LENGTH ? value.substring(0, MAX_FIELD_LENGTH) : value;
    }

    private static Map<String, String> buildAliasMap() {
        Map<String, String> map = new HashMap<>();
        for (String alias : new String[]{"title", "organisme", "name", "nom", "titre"})
            map.put(alias, "title");
        for (String alias : new String[]{"username", "identifiant", "login",
                "adresse mail / identifiant"})
            map.put(alias, "username");
        for (String alias : new String[]{"email", "mail", "adresse mail", "e-mail", "courriel"})
            map.put(alias, "email");
        for (String alias : new String[]{"pseudo", "nickname", "alias", "surnom", "display name"})
            map.put(alias, "pseudo");
        for (String alias : new String[]{"password", "mdp", "mot de passe", "pass"})
            map.put(alias, "password");
        for (String alias : new String[]{"url", "site", "website", "lien"})
            map.put(alias, "url");
        for (String alias : new String[]{"notes", "description", "commentaire"})
            map.put(alias, "notes");
        for (String alias : new String[]{"category", "categorie", "type"})
            map.put(alias, "category");
        for (String alias : new String[]{"tags", "etiquettes"})
            map.put(alias, "tags");
        for (String alias : new String[]{"favorite", "favori", "starred"})
            map.put(alias, "favorite");
        return map;
    }

    private String[] parseCsvLine(String line, char separator) {
        List<String> fields = new ArrayList<>();
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
        // If quotes were never closed, treat the content as a regular field
        // rather than consuming data from subsequent lines
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * Maximum number of entries that can be imported in a single operation.
     */
    private static final int MAX_IMPORT_ENTRIES = 10_000;

    /**
     * Maximum length of a single field value to prevent memory abuse.
     */
    private static final int MAX_FIELD_LENGTH = 10_000;
}
