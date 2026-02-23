package com.passwordmanager.vault;

import com.google.gson.Gson;
import com.passwordmanager.util.SecureWiper;

/**
 * Handles exporting vault data to CSV and JSON formats.
 */
public class VaultExporter {
    private final Gson gson;

    public VaultExporter(Gson gson) {
        this.gson = gson;
    }

    public String exportAsJson(Vault vault) {
        return gson.toJson(vault);
    }

    public String exportAsCsv(Vault vault) {
        StringBuilder sb = new StringBuilder();
        sb.append("title,username,password,url,notes,category,tags\n");
        for (VaultEntry e : vault.getEntries()) {
            sb.append(csvEscape(e.getTitle())).append(",");
            sb.append(csvEscape(e.getUsername())).append(",");
            char[] pwd = e.getPassword();
            sb.append(csvEscape(pwd != null ? new String(pwd) : "")).append(",");
            SecureWiper.wipe(pwd);
            sb.append(csvEscape(e.getUrl())).append(",");
            sb.append(csvEscape(e.getNotes())).append(",");
            sb.append(csvEscape(e.getCategory())).append(",");
            sb.append(csvEscape(e.getTags() != null ? String.join(";", e.getTags()) : "")).append("\n");
        }
        return sb.toString();
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
