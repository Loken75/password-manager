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
        if (csvContent == null || csvContent.isEmpty()) return 0;

        // Strip UTF-8 BOM (common in Excel exports on Windows)
        if (csvContent.startsWith("\uFEFF")) {
            csvContent = csvContent.substring(1);
        }

        // Parse all logical CSV records (RFC 4180: handles quoted newlines)
        List<String[]> records = readAllCsvRecords(csvContent);
        if (records.size() < 2) return 0;

        String[] headers = records.get(0);

        // Detect separator from header - rebuild header line for counting
        // (already parsed, but we need to detect separator for remaining records)
        // The separator was used during parsing; detect from raw first line
        String headerLine = getFirstLine(csvContent);
        int semicolons = 0, commas = 0;
        for (int i = 0; i < headerLine.length(); i++) {
            if (headerLine.charAt(i) == ';') semicolons++;
            else if (headerLine.charAt(i) == ',') commas++;
        }
        char separator = semicolons > commas ? ';' : ',';

        // Re-parse with correct separator if it's not comma
        if (separator != ',') {
            records = readAllCsvRecords(csvContent, separator);
            if (records.size() < 2) return 0;
            headers = records.get(0);
        }

        Map<String, String> aliasMap = buildAliasMap();
        int titleIdx = -1, usernameIdx = -1, emailIdx = -1;
        int passwordIdx = -1, urlIdx = -1;
        int notesIdx = -1, categoryIdx = -1, tagsIdx = -1, favoriteIdx = -1;
        int typeIdx = -1, pinIdx = -1;
        int cardholderNameIdx = -1, cardNumberIdx = -1, expiryDateIdx = -1;
        int cvvIdx = -1, cardPinIdx = -1, cardTypeIdx = -1;
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
                    case "pseudo":
                        if (usernameIdx == -1) usernameIdx = i; break;
                    case "password": passwordIdx = i; break;
                    case "url": urlIdx = i; break;
                    case "notes": notesIdx = i; break;
                    case "category": categoryIdx = i; break;
                    case "tags": tagsIdx = i; break;
                    case "favorite": favoriteIdx = i; break;
                    case "type": typeIdx = i; break;
                    case "pin": pinIdx = i; break;
                    case "cardholder_name": cardholderNameIdx = i; break;
                    case "card_number": cardNumberIdx = i; break;
                    case "expiry_date": expiryDateIdx = i; break;
                    case "cvv": cvvIdx = i; break;
                    case "card_pin": cardPinIdx = i; break;
                    case "card_type": cardTypeIdx = i; break;
                }
            }
        }

        if (!headerRecognized) {
            titleIdx = 0; usernameIdx = 1; passwordIdx = 2; urlIdx = 3;
            notesIdx = 4; categoryIdx = 5; tagsIdx = 6;
        }

        int count = 0;
        for (int i = 1; i < records.size(); i++) {
            if (count >= MAX_IMPORT_ENTRIES) break;
            String[] parts = records.get(i);
            // Skip empty records (all fields empty)
            if (isEmptyRecord(parts)) continue;

            String typeVal = sanitizeField(getField(parts, typeIdx)).toUpperCase();

            if ("APP".equals(typeVal)) {
                AppEntry entry = new AppEntry();
                entry.setTitle(sanitizeField(getField(parts, titleIdx)));
                entry.setUsername(sanitizeField(getField(parts, usernameIdx)));
                String pinStr = sanitizeField(getField(parts, pinIdx));
                if (!pinStr.isEmpty()) {
                    char[] pinChars = pinStr.toCharArray();
                    entry.setPin(pinChars);
                    SecureWiper.wipe(pinChars);
                }
                entry.setNotes(sanitizeField(getField(parts, notesIdx)));
                String favVal = sanitizeField(getField(parts, favoriteIdx));
                entry.setFavorite("true".equalsIgnoreCase(favVal));
                if (entry.getTitle() != null && !entry.getTitle().isEmpty()) {
                    vault.addAppEntry(entry);
                    count++;
                }
            } else if ("CARD".equals(typeVal)) {
                CardEntry entry = new CardEntry();
                entry.setTitle(sanitizeField(getField(parts, titleIdx)));
                entry.setCardholderName(sanitizeField(getField(parts, cardholderNameIdx)));
                String cardNumStr = sanitizeField(getField(parts, cardNumberIdx));
                if (!cardNumStr.isEmpty()) {
                    char[] chars = cardNumStr.toCharArray();
                    entry.setCardNumber(chars);
                    SecureWiper.wipe(chars);
                }
                entry.setExpiryDate(sanitizeField(getField(parts, expiryDateIdx)));
                String cvvStr = sanitizeField(getField(parts, cvvIdx));
                if (!cvvStr.isEmpty()) {
                    char[] chars = cvvStr.toCharArray();
                    entry.setCvv(chars);
                    SecureWiper.wipe(chars);
                }
                String cardPinStr = sanitizeField(getField(parts, cardPinIdx));
                if (!cardPinStr.isEmpty()) {
                    char[] chars = cardPinStr.toCharArray();
                    entry.setCardPin(chars);
                    SecureWiper.wipe(chars);
                }
                entry.setCardType(CardType.normalize(sanitizeField(getField(parts, cardTypeIdx))));
                entry.setNotes(sanitizeField(getField(parts, notesIdx)));
                String favVal = sanitizeField(getField(parts, favoriteIdx));
                entry.setFavorite("true".equalsIgnoreCase(favVal));
                if (entry.getTitle() != null && !entry.getTitle().isEmpty()) {
                    vault.addCardEntry(entry);
                    count++;
                }
            } else {
                // PASSWORD (default, also handles missing type column for retrocompat)
                PasswordEntry entry = new PasswordEntry();
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
        if (imported == null) return 0;

        int count = 0;

        // Import password entries
        if (imported.getEntries() != null) {
            int limit = Math.min(imported.getEntries().size(), MAX_IMPORT_ENTRIES);
            for (int i = 0; i < limit; i++) {
                PasswordEntry entry = imported.getEntries().get(i);
                entry.setId(UUID.randomUUID().toString());
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
        }

        // Import app entries
        if (!imported.getAppEntries().isEmpty()) {
            int limit = Math.min(imported.getAppEntries().size(), MAX_IMPORT_ENTRIES - count);
            for (int i = 0; i < limit; i++) {
                AppEntry entry = imported.getAppEntries().get(i);
                entry.setId(UUID.randomUUID().toString());
                if (entry.getTitle() != null) {
                    entry.setTitle(truncateField(sanitizeField(entry.getTitle())));
                }
                if (entry.getUsername() != null) {
                    entry.setUsername(truncateField(sanitizeField(entry.getUsername())));
                }
                if (entry.getNotes() != null) {
                    entry.setNotes(truncateField(sanitizeField(entry.getNotes())));
                }
                vault.addAppEntry(entry);
                count++;
            }
        }

        // Import card entries
        if (!imported.getCardEntries().isEmpty()) {
            int limit = Math.min(imported.getCardEntries().size(), MAX_IMPORT_ENTRIES - count);
            for (int i = 0; i < limit; i++) {
                CardEntry entry = imported.getCardEntries().get(i);
                entry.setId(UUID.randomUUID().toString());
                if (entry.getTitle() != null) {
                    entry.setTitle(truncateField(sanitizeField(entry.getTitle())));
                }
                if (entry.getCardholderName() != null) {
                    entry.setCardholderName(truncateField(sanitizeField(entry.getCardholderName())));
                }
                if (entry.getExpiryDate() != null) {
                    String expiry = sanitizeField(entry.getExpiryDate()).trim();
                    entry.setExpiryDate(expiry.matches("\\d{2}/\\d{2}") ? expiry : "");
                }
                // Normalize legacy localized card type to internal key
                entry.setCardType(CardType.normalize(entry.getCardType()));
                if (entry.getNotes() != null) {
                    entry.setNotes(truncateField(sanitizeField(entry.getNotes())));
                }
                vault.addCardEntry(entry);
                count++;
            }
        }

        if (count > 0) {
            vault.setUpdatedAt(DateUtils.getCurrentTimestamp());
        }
        return count;
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
        for (String alias : new String[]{"category", "categorie"})
            map.put(alias, "category");
        for (String alias : new String[]{"tags", "etiquettes"})
            map.put(alias, "tags");
        for (String alias : new String[]{"favorite", "favori", "starred"})
            map.put(alias, "favorite");
        // Entry type
        for (String alias : new String[]{"type", "entry_type"})
            map.put(alias, "type");
        // App fields
        for (String alias : new String[]{"pin", "code pin", "code"})
            map.put(alias, "pin");
        // Card fields
        for (String alias : new String[]{"cardholdername", "cardholder_name", "nom du porteur", "porteur"})
            map.put(alias, "cardholder_name");
        for (String alias : new String[]{"cardnumber", "card_number", "numero de carte", "numero carte"})
            map.put(alias, "card_number");
        for (String alias : new String[]{"expirydate", "expiry_date", "expiration", "date expiration"})
            map.put(alias, "expiry_date");
        for (String alias : new String[]{"cvv", "cvc", "code securite"})
            map.put(alias, "cvv");
        for (String alias : new String[]{"cardpin", "card_pin", "code carte", "pin carte"})
            map.put(alias, "card_pin");
        for (String alias : new String[]{"cardtype", "card_type", "type carte"})
            map.put(alias, "card_type");
        return map;
    }

    /**
     * Returns the first physical line from CSV content (up to the first
     * unquoted newline), used for separator detection.
     */
    private static String getFirstLine(String content) {
        int end = content.length();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\r' || c == '\n') {
                end = i;
                break;
            }
        }
        return content.substring(0, end);
    }

    /**
     * Checks whether a parsed record consists entirely of empty fields.
     */
    private static boolean isEmptyRecord(String[] parts) {
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Parses all CSV records using comma as separator.
     */
    private List<String[]> readAllCsvRecords(String csvContent) {
        return readAllCsvRecords(csvContent, ',');
    }

    /**
     * RFC 4180 compliant CSV parser that handles:
     * - Quoted fields containing newlines (\n, \r\n)
     * - Quoted fields containing the separator character
     * - Escaped double-quotes ("" inside quoted fields)
     *
     * Reads the entire CSV content character-by-character and returns a list
     * of logical records, where each record is a String[] of field values.
     */
    private List<String[]> readAllCsvRecords(String csvContent, char separator) {
        List<String[]> records = new ArrayList<>();
        if (csvContent == null || csvContent.isEmpty()) return records;

        List<String> currentFields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int len = csvContent.length();

        for (int i = 0; i < len; i++) {
            char c = csvContent.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // Check for escaped quote ("")
                    if (i + 1 < len && csvContent.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i++; // skip the second quote
                    } else {
                        // End of quoted field
                        inQuotes = false;
                    }
                } else {
                    // Inside quotes: accept everything including newlines
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == separator) {
                    currentFields.add(currentField.toString());
                    currentField = new StringBuilder();
                } else if (c == '\r') {
                    // End of record; handle \r\n
                    if (i + 1 < len && csvContent.charAt(i + 1) == '\n') {
                        i++; // skip \n
                    }
                    currentFields.add(currentField.toString());
                    records.add(currentFields.toArray(new String[0]));
                    currentFields = new ArrayList<>();
                    currentField = new StringBuilder();
                } else if (c == '\n') {
                    // End of record
                    currentFields.add(currentField.toString());
                    records.add(currentFields.toArray(new String[0]));
                    currentFields = new ArrayList<>();
                    currentField = new StringBuilder();
                } else {
                    currentField.append(c);
                }
            }
        }

        // Handle last record (content not ending with newline)
        if (currentField.length() > 0 || !currentFields.isEmpty()) {
            currentFields.add(currentField.toString());
            if (!isEmptyRecord(currentFields.toArray(new String[0]))) {
                records.add(currentFields.toArray(new String[0]));
            }
        }

        return records;
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
