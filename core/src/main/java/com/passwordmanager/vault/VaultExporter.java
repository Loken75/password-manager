package com.passwordmanager.vault;

import com.google.gson.Gson;
import com.passwordmanager.util.SecureWiper;

/**
 * Handles exporting vault data to CSV and JSON formats.
 * Returns char[] to allow callers to wipe sensitive data after use.
 */
public class VaultExporter {
    private final Gson gson;

    public VaultExporter(Gson gson) {
        this.gson = gson;
    }

    public char[] exportAsJson(Vault vault) {
        // Use the secret-safe codec: never materializes the vault as a String.
        return VaultJsonCodec.encode(vault);
    }

    public char[] exportAsCsv(Vault vault) {
        StringBuilder sb = new StringBuilder();
        sb.append("type,title,username,email,password,url,notes,category,tags,favorite,pin\n");

        // Password entries
        for (PasswordEntry e : vault.getEntries()) {
            sb.append("PASSWORD,");
            sb.append(csvEscape(e.getTitle())).append(",");
            sb.append(csvEscape(e.getUsername())).append(",");
            sb.append(csvEscape(e.getEmail())).append(",");
            char[] pwd = e.getPassword();
            csvEscapeChars(sb, pwd);
            sb.append(",");
            SecureWiper.wipe(pwd);
            sb.append(csvEscape(e.getUrl())).append(",");
            sb.append(csvEscape(e.getNotes())).append(",");
            sb.append(csvEscape(e.getCategory())).append(",");
            sb.append(csvEscape(e.getTags() != null ? String.join(";", e.getTags()) : "")).append(",");
            sb.append(e.isFavorite() ? "true" : "false");
            sb.append(",\n"); // empty pin column
        }

        // App entries
        for (AppEntry e : vault.getAppEntries()) {
            sb.append("APP,");
            sb.append(csvEscape(e.getTitle())).append(",");
            sb.append(csvEscape(e.getUsername())).append(",");
            sb.append(","); // email
            sb.append(","); // password
            sb.append(","); // url
            sb.append(csvEscape(e.getNotes())).append(",");
            sb.append(","); // category
            sb.append(","); // tags
            sb.append(e.isFavorite() ? "true" : "false").append(",");
            char[] pin = e.getPin();
            csvEscapeChars(sb, pin);
            sb.append("\n");
            SecureWiper.wipe(pin);
        }

        // Extract to char[] and wipe the StringBuilder
        char[] result = new char[sb.length()];
        sb.getChars(0, sb.length(), result, 0);
        for (int i = 0; i < sb.length(); i++) {
            sb.setCharAt(i, '\0');
        }
        return result;
    }

    /**
     * Appends a CSV-escaped char[] directly to the StringBuilder without
     * creating an intermediate String. Wipes the temporary char arrays.
     */
    private static void csvEscapeChars(StringBuilder sb, char[] value) {
        if (value == null || value.length == 0) return;

        boolean needsQuoting = false;
        boolean formulaRisk = "=+-@\t\r".indexOf(value[0]) >= 0;
        for (char c : value) {
            if (c == ',' || c == '"' || c == '\n' || c == ';') {
                needsQuoting = true;
                break;
            }
        }

        if (formulaRisk || needsQuoting) {
            sb.append('"');
            if (formulaRisk) sb.append('\'');
            for (char c : value) {
                if (c == '"') sb.append('"');
                sb.append(c);
            }
            sb.append('"');
        } else {
            sb.append(value);
        }
    }

    /**
     * Escapes a CSV value. Protects against CSV injection by prefixing
     * formula-triggering characters with a single quote.
     */
    private static String csvEscape(String value) {
        if (value == null) return "";

        boolean formulaRisk = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0;
        if (formulaRisk) {
            return "\"'" + value.replace("\"", "\"\"") + "\"";
        }

        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains(";")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
