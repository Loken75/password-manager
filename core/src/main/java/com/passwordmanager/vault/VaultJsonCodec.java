package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bespoke streaming JSON (de)serializer for {@link Vault} (R3).
 *
 * <p>Unlike Gson, this codec keeps the secret fields ({@code password}, {@code pin},
 * {@code privateKey}) as {@code char[]} end-to-end: they are never materialized as an
 * immutable {@link String}. {@link #encode} returns a {@code char[]} (wipeable) instead
 * of building a whole-vault {@code String}, and {@link #decode} extracts secret values
 * directly into {@code char[]} without ever calling {@code nextString()}.
 *
 * <p>Non-secret fields (title, username, notes, ...) are already {@code String} in the
 * model and are handled as such — they are out of R3's scope.
 *
 * <p>Compatibility: {@link #decode} accepts general JSON (whitespace, {@code \\uXXXX}
 * escapes, html-safe output) so it parses vaults previously written by Gson.
 */
public final class VaultJsonCodec {

    private VaultJsonCodec() {}

    // ====================================================================
    // ENCODE
    // ====================================================================

    /** Serializes the vault to a JSON {@code char[]}; secrets are never turned into a String. */
    public static char[] encode(Vault vault) {
        JsonCharWriter w = new JsonCharWriter();
        w.beginObject();
        w.nameStringOrSkip("version", vault.getVersion());
        w.nameStringOrSkip("user", vault.getUser());
        w.nameStringOrSkip("createdAt", vault.getCreatedAt());
        w.nameStringOrSkip("updatedAt", vault.getUpdatedAt());

        w.name("entries").beginArray();
        for (PasswordEntry e : vault.getEntries()) writePasswordEntry(w, e);
        w.endArray();

        w.name("appEntries").beginArray();
        for (AppEntry e : vault.getAppEntries()) writeAppEntry(w, e);
        w.endArray();

        w.name("sshKeyEntries").beginArray();
        for (SshKeyEntry e : vault.getSshKeyEntries()) writeSshKeyEntry(w, e);
        w.endArray();

        w.name("categories").beginArray();
        for (String c : vault.getCategories()) w.valueString(c);
        w.endArray();

        w.name("settings");
        writeValue(w, vault.getSettings());

        w.endObject();
        return w.toCharArrayAndWipe();
    }

    private static void writeCommon(JsonCharWriter w, VaultItem e) {
        w.nameStringOrSkip("id", e.getId());
        w.nameStringOrSkip("title", e.getTitle());
        w.nameStringOrSkip("notes", e.getNotes());
        w.name("favorite").valueBoolean(e.isFavorite());
        w.nameStringOrSkip("createdAt", e.getCreatedAt());
        w.nameStringOrSkip("updatedAt", e.getUpdatedAt());
        if (e.isDeleted()) w.name("deleted").valueBoolean(true);
        w.nameStringOrSkip("deletedAt", e.getDeletedAt());
    }

    private static void writePasswordEntry(JsonCharWriter w, PasswordEntry e) {
        w.beginObject();
        writeCommon(w, e);
        w.nameStringOrSkip("username", e.getUsername());
        w.nameStringOrSkip("email", e.getEmail());
        char[] pwd = e.getPassword();
        if (pwd != null) {
            w.name("password").valueChars(pwd);
            SecureWiper.wipe(pwd);
        }
        w.nameStringOrSkip("url", e.getUrl());
        w.nameStringOrSkip("category", e.getCategory());
        if (e.getTags() != null) {
            w.name("tags").beginArray();
            for (String t : e.getTags()) w.valueString(t);
            w.endArray();
        }
        w.endObject();
    }

    private static void writeAppEntry(JsonCharWriter w, AppEntry e) {
        w.beginObject();
        writeCommon(w, e);
        w.nameStringOrSkip("username", e.getUsername());
        char[] pin = e.getPin();
        if (pin != null) {
            w.name("pin").valueChars(pin);
            SecureWiper.wipe(pin);
        }
        w.endObject();
    }

    private static void writeSshKeyEntry(JsonCharWriter w, SshKeyEntry e) {
        w.beginObject();
        writeCommon(w, e);
        char[] pk = e.getPrivateKey();
        if (pk != null) {
            w.name("privateKey").valueChars(pk);
            SecureWiper.wipe(pk);
        }
        w.nameStringOrSkip("publicKey", e.getPublicKey());
        w.nameStringOrSkip("keyType", e.getKeyType());
        w.nameStringOrSkip("fingerprint", e.getFingerprint());
        w.endObject();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(JsonCharWriter w, Object value) {
        if (value == null) {
            w.valueNull();
        } else if (value instanceof Map) {
            w.beginObject();
            for (Map.Entry<String, Object> en : ((Map<String, Object>) value).entrySet()) {
                w.name(en.getKey());
                writeValue(w, en.getValue());
            }
            w.endObject();
        } else if (value instanceof List) {
            w.beginArray();
            for (Object o : (List<Object>) value) writeValue(w, o);
            w.endArray();
        } else if (value instanceof Boolean) {
            w.valueBoolean((Boolean) value);
        } else if (value instanceof Number) {
            w.valueNumberRaw(String.valueOf(value));
        } else {
            w.valueString(value.toString());
        }
    }

    // ====================================================================
    // DECODE
    // ====================================================================

    /** Parses a vault from JSON {@code char[]}; secret values are read straight into char[]. */
    public static Vault decode(char[] json) {
        JsonCharReader r = new JsonCharReader(json);
        Vault vault = new Vault("");   // overwritten below; avoids the private no-arg ctor
        vault.setVersion(null);
        vault.setUser(null);
        vault.setCreatedAt(null);
        vault.setUpdatedAt(null);
        vault.setCategories(new ArrayList<>());
        vault.setEntries(new ArrayList<>());
        vault.setAppEntries(new ArrayList<>());
        vault.setSshKeyEntries(new ArrayList<>());
        vault.setSettings(new LinkedHashMap<>());

        r.skipWs();
        r.expect('{');
        if (!r.tryEndObject()) {
            do {
                String key = r.readString();
                r.expectColon();
                switch (key) {
                    case "version":       vault.setVersion(r.readStringOrNull()); break;
                    case "user":          vault.setUser(r.readStringOrNull()); break;
                    case "createdAt":     vault.setCreatedAt(r.readStringOrNull()); break;
                    case "updatedAt":     vault.setUpdatedAt(r.readStringOrNull()); break;
                    case "entries":       vault.setEntries(readPasswordEntries(r)); break;
                    case "appEntries":    vault.setAppEntries(readAppEntries(r)); break;
                    case "sshKeyEntries": vault.setSshKeyEntries(readSshKeyEntries(r)); break;
                    case "categories":    vault.setCategories(readStringArray(r)); break;
                    case "settings":      vault.setSettings(readObjectMap(r)); break;
                    default:              r.skipValue(); break; // tolerate unknown keys
                }
            } while (r.tryComma());
            r.expect('}');
        }
        vault.ensureInitialized();
        return vault;
    }

    private static List<PasswordEntry> readPasswordEntries(JsonCharReader r) {
        List<PasswordEntry> list = new ArrayList<>();
        r.skipWs();
        r.expect('[');
        if (!r.tryEndArray()) {
            do {
                PasswordEntry e = new PasswordEntry();
                r.skipWs(); r.expect('{');
                if (!r.tryEndObject()) {
                    do {
                        String key = r.readString();
                        r.expectColon();
                        if (readCommonField(r, e, key)) continue;
                        switch (key) {
                            case "username": e.setUsername(r.readStringOrNull()); break;
                            case "email":    e.setEmail(r.readStringOrNull()); break;
                            case "password": setSecret(r, e::setPassword); break;
                            case "url":      e.setUrl(r.readStringOrNull()); break;
                            case "category": e.setCategory(r.readStringOrNull()); break;
                            case "tags":     e.setTags(readStringArray(r)); break;
                            default:         r.skipValue(); break;
                        }
                    } while (r.tryComma());
                    r.expect('}');
                }
                list.add(e);
            } while (r.tryComma());
            r.expect(']');
        }
        return list;
    }

    private static List<AppEntry> readAppEntries(JsonCharReader r) {
        List<AppEntry> list = new ArrayList<>();
        r.skipWs();
        r.expect('[');
        if (!r.tryEndArray()) {
            do {
                AppEntry e = new AppEntry();
                r.skipWs(); r.expect('{');
                if (!r.tryEndObject()) {
                    do {
                        String key = r.readString();
                        r.expectColon();
                        if (readCommonField(r, e, key)) continue;
                        switch (key) {
                            case "username": e.setUsername(r.readStringOrNull()); break;
                            case "pin":      setSecret(r, e::setPin); break;
                            default:         r.skipValue(); break;
                        }
                    } while (r.tryComma());
                    r.expect('}');
                }
                list.add(e);
            } while (r.tryComma());
            r.expect(']');
        }
        return list;
    }

    private static List<SshKeyEntry> readSshKeyEntries(JsonCharReader r) {
        List<SshKeyEntry> list = new ArrayList<>();
        r.skipWs();
        r.expect('[');
        if (!r.tryEndArray()) {
            do {
                SshKeyEntry e = new SshKeyEntry();
                r.skipWs(); r.expect('{');
                if (!r.tryEndObject()) {
                    do {
                        String key = r.readString();
                        r.expectColon();
                        if (readCommonField(r, e, key)) continue;
                        switch (key) {
                            case "privateKey":  setSecret(r, e::setPrivateKey); break;
                            case "publicKey":   e.setPublicKey(r.readStringOrNull()); break;
                            case "keyType":     e.setKeyType(r.readStringOrNull()); break;
                            case "fingerprint": e.setFingerprint(r.readStringOrNull()); break;
                            default:            r.skipValue(); break;
                        }
                    } while (r.tryComma());
                    r.expect('}');
                }
                list.add(e);
            } while (r.tryComma());
            r.expect(']');
        }
        return list;
    }

    /** Handles the shared VaultItem fields; returns true if the key was consumed. */
    private static boolean readCommonField(JsonCharReader r, VaultItem e, String key) {
        switch (key) {
            case "id":        e.setId(r.readStringOrNull()); return true;
            case "title":     e.setTitle(r.readStringOrNull()); return true;
            case "notes":     e.setNotes(r.readStringOrNull()); return true;
            case "favorite":  e.setFavorite(r.readBoolean()); return true;
            case "createdAt": e.setCreatedAt(r.readStringOrNull()); return true;
            case "updatedAt": e.setUpdatedAt(r.readStringOrNull()); return true;
            case "deleted":   e.setDeleted(r.readBoolean()); return true;
            case "deletedAt": e.setDeletedAt(r.readStringOrNull()); return true;
            default:          return false;
        }
    }

    /** Reads a JSON string secret into char[], applies it via the setter, then wipes the temp. */
    private static void setSecret(JsonCharReader r, java.util.function.Consumer<char[]> setter) {
        r.skipWs();
        if (r.peek() == 'n') { r.readNull(); return; }
        char[] secret = r.readStringChars();
        try {
            setter.accept(secret); // setters clone defensively
        } finally {
            SecureWiper.wipe(secret);
        }
    }

    private static List<String> readStringArray(JsonCharReader r) {
        List<String> list = new ArrayList<>();
        r.skipWs();
        if (r.peek() == 'n') { r.readNull(); return list; }
        r.expect('[');
        if (!r.tryEndArray()) {
            do {
                list.add(r.readString());
            } while (r.tryComma());
            r.expect(']');
        }
        return list;
    }

    private static Map<String, Object> readObjectMap(JsonCharReader r) {
        Map<String, Object> map = new LinkedHashMap<>();
        r.skipWs();
        if (r.peek() == 'n') { r.readNull(); return map; }
        r.expect('{');
        if (!r.tryEndObject()) {
            do {
                String key = r.readString();
                r.expectColon();
                map.put(key, r.readValue());
            } while (r.tryComma());
            r.expect('}');
        }
        return map;
    }
}
