package com.passwordmanager.vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VaultJsonCodecTest {

    private static String pw(PasswordEntry e) { return new String(e.getPassword()); }

    private Vault sampleVault() {
        Vault v = new Vault("alice", List.of("Email", "Work"));
        PasswordEntry p = new PasswordEntry("Gmail", "alice", "alice@example.com", "S3cr3t!".toCharArray(),
            "https://gmail.com", "Main account", "Email", Arrays.asList("google", "mail"));
        v.addEntry(p);
        AppEntry a = new AppEntry("Bank", "alice2", "1234".toCharArray(), "my bank pin");
        v.addAppEntry(a);
        SshKeyEntry s = new SshKeyEntry("server", "-----BEGIN KEY-----\nabc\n-----END KEY-----".toCharArray(),
            "ssh-ed25519 AAAA", "ED25519", "SHA256:xyz");
        v.addSshKeyEntry(s);
        return v;
    }

    @Test
    void roundTrip_allTypes() {
        Vault decoded = VaultJsonCodec.decode(VaultJsonCodec.encode(sampleVault()));

        assertEquals("alice", decoded.getUser());
        assertEquals("2.0", decoded.getVersion());
        assertEquals(List.of("Email", "Work"), decoded.getCategories());

        assertEquals(1, decoded.getEntries().size());
        PasswordEntry p = decoded.getEntries().get(0);
        assertEquals("Gmail", p.getTitle());
        assertEquals("alice", p.getUsername());
        assertEquals("alice@example.com", p.getEmail());
        assertEquals("https://gmail.com", p.getUrl());
        assertEquals("Email", p.getCategory());
        assertEquals("S3cr3t!", pw(p));
        assertEquals(Arrays.asList("google", "mail"), p.getTags());

        assertEquals(1, decoded.getAppEntries().size());
        AppEntry a = decoded.getAppEntries().get(0);
        assertEquals("Bank", a.getTitle());
        assertEquals("1234", new String(a.getPin()));

        assertEquals(1, decoded.getSshKeyEntries().size());
        SshKeyEntry s = decoded.getSshKeyEntries().get(0);
        assertEquals("ED25519", s.getKeyType());
        assertEquals("-----BEGIN KEY-----\nabc\n-----END KEY-----", new String(s.getPrivateKey()));
        assertEquals("SHA256:xyz", s.getFingerprint());
    }

    @Test
    void roundTrip_trickyCharacters() {
        Vault v = new Vault("bob", List.of("Other"));
        PasswordEntry p = new PasswordEntry("T", "u", "a\"b\\c\nd\teé😀f".toCharArray(),
            "", "", "Other", null);
        p.setNotes("quote\" backslash\\ angle< equals= amp& newline\n tab\t accent-é emoji-😀");
        p.setTitle("Ti\"tle");
        v.addEntry(p);

        Vault decoded = VaultJsonCodec.decode(VaultJsonCodec.encode(v));
        PasswordEntry d = decoded.getEntries().get(0);
        assertEquals("a\"b\\c\nd\teé😀f", pw(d), "secret with quotes/backslash/newline/tab/accent/emoji");
        assertEquals("quote\" backslash\\ angle< equals= amp& newline\n tab\t accent-é emoji-😀", d.getNotes());
        assertEquals("Ti\"tle", d.getTitle());
    }

    @Test
    void roundTrip_tombstone() {
        Vault v = new Vault("c", List.of("Other"));
        PasswordEntry p = new PasswordEntry("Gone", "u", "p".toCharArray(), "", "", "Other", null);
        p.markDeleted();
        v.addEntry(p);

        PasswordEntry d = VaultJsonCodec.decode(VaultJsonCodec.encode(v)).getEntries().get(0);
        assertTrue(d.isDeleted());
        assertNotNull(d.getDeletedAt());
        assertEquals(p.getId(), d.getId());
    }

    @Test
    void roundTrip_emptyVault() {
        Vault v = new Vault("empty");
        Vault decoded = VaultJsonCodec.decode(VaultJsonCodec.encode(v));
        assertEquals("empty", decoded.getUser());
        assertTrue(decoded.getEntries().isEmpty());
        assertTrue(decoded.getAppEntries().isEmpty());
        assertTrue(decoded.getSshKeyEntries().isEmpty());
        // settings numbers survive as Number (Gson-compatible)
        assertTrue(decoded.getSettings().get("password_expiry_days") instanceof Number);
        assertEquals(180, ((Number) decoded.getSettings().get("password_expiry_days")).intValue());
    }

    @Test
    void decodesVaultWrittenByGson() {
        // Backward-compat: existing .enc vaults were serialized by Gson with the
        // CharArrayAdapter (char[] -> JSON string) and pretty-printing + html-safe escaping.
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(char[].class, new VaultManager.CharArrayAdapter())
            .create();
        Vault original = sampleVault();
        // include html-escaped characters Gson encodes as backslash-u escapes (< & = ')
        original.getEntries().get(0).setNotes("a < b & c = d 'e'");
        // edge cases Gson emits: a null secret (omitted), a tombstone, empty tags
        PasswordEntry noPwd = new PasswordEntry();
        noPwd.setTitle("NoPassword");
        original.addEntry(noPwd);
        PasswordEntry dead = new PasswordEntry("Dead", "u", "x".toCharArray(), "", "", "Email", null);
        dead.markDeleted();
        original.addEntry(dead);

        String gsonJson = gson.toJson(original);
        Vault decoded = VaultJsonCodec.decode(gsonJson.toCharArray());

        assertEquals("alice", decoded.getUser());
        assertEquals("S3cr3t!", pw(decoded.getEntries().get(0)));
        assertEquals("a < b & c = d 'e'", decoded.getEntries().get(0).getNotes());
        assertEquals("1234", new String(decoded.getAppEntries().get(0).getPin()));
        assertEquals("ED25519", decoded.getSshKeyEntries().get(0).getKeyType());
        // settings numbers survive as Number (Gson serializes ints, we decode Double)
        assertEquals(180, ((Number) decoded.getSettings().get("password_expiry_days")).intValue());
        // null secret stays null; tombstone preserved
        PasswordEntry decodedNoPwd = decoded.getEntries().stream()
            .filter(e -> "NoPassword".equals(e.getTitle())).findFirst().orElseThrow();
        assertNull(decodedNoPwd.getPassword());
        PasswordEntry decodedDead = decoded.getEntries().stream()
            .filter(e -> e.isDeleted()).findFirst().orElseThrow();
        assertEquals("Dead", decodedDead.getTitle());
    }

    @Test
    void encoderOutputIsReadableByGson() {
        // The encoder's output must also be parseable by Gson (forward sanity).
        Gson gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(char[].class, new VaultManager.CharArrayAdapter())
            .create();
        char[] json = VaultJsonCodec.encode(sampleVault());
        Vault viaGson = gson.fromJson(new String(json), Vault.class);
        viaGson.ensureInitialized();
        assertEquals("S3cr3t!", new String(viaGson.getEntries().get(0).getPassword()));
    }

    @Test
    void malformedSecretEscapeThrowsCleanly() {
        // A corrupt backslash-u escape inside a secret must fail with a parse error
        // (and the partial secret buffer is wiped in readStringChars' finally).
        String json = "{\"version\":\"2.0\",\"user\":\"x\","
            + "\"entries\":[{\"title\":\"T\",\"password\":\"ab\\uZZZZcd\"}],"
            + "\"appEntries\":[],\"sshKeyEntries\":[],\"categories\":[],\"settings\":{}}";
        assertThrows(IllegalArgumentException.class,
            () -> VaultJsonCodec.decode(json.toCharArray()));
    }

    @Test
    void toleratesUnknownKeys() {
        String json = "{\"version\":\"2.0\",\"user\":\"x\",\"futureField\":{\"a\":1},"
            + "\"entries\":[{\"title\":\"T\",\"password\":\"p\",\"extra\":[1,2,3]}],"
            + "\"appEntries\":[],\"sshKeyEntries\":[],\"categories\":[],\"settings\":{}}";
        Vault decoded = VaultJsonCodec.decode(json.toCharArray());
        assertEquals("x", decoded.getUser());
        assertEquals("p", pw(decoded.getEntries().get(0)));
    }
}
