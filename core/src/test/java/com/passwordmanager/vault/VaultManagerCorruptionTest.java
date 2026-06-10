package com.passwordmanager.vault;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link VaultManager#loadVault} fails cleanly (throws, never returns a
 * partial/garbage vault) when the on-disk {@code .enc} envelope is corrupted or tampered.
 * Closes the audit gap: previously only one malformed-JSON case was covered.
 */
class VaultManagerCorruptionTest {

    @TempDir
    Path tempDir;

    private VaultManager manager;
    private final char[] password = "CorruptTestP@ss!1".toCharArray();

    @BeforeEach
    void setUp() throws Exception {
        manager = new VaultManager(tempDir.toString());
        manager.createVault("alice", password.clone()).getSession().destroy();
    }

    private Path vaultFile() {
        return tempDir.resolve("vault_alice.enc");
    }

    private JsonObject readEnvelope() throws Exception {
        return JsonParser.parseString(Files.readString(vaultFile())).getAsJsonObject();
    }

    private void writeEnvelope(JsonObject obj) throws Exception {
        Files.write(vaultFile(), obj.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Baseline: the freshly created (untampered) vault loads fine. */
    @Test
    void untamperedVaultLoads() throws Exception {
        VaultLoadResult loaded = manager.loadVault("alice", password.clone());
        assertEquals("alice", loaded.getVault().getUser());
        loaded.getSession().destroy();
    }

    @Test
    void nonJsonFileFailsCleanly() throws Exception {
        Files.write(vaultFile(), "this is not a vault".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> manager.loadVault("alice", password.clone()));
    }

    @Test
    void emptyFileFailsCleanly() throws Exception {
        Files.write(vaultFile(), new byte[0]);
        assertThrows(Exception.class, () -> manager.loadVault("alice", password.clone()));
    }

    @Test
    void missingRequiredFieldFailsCleanly() throws Exception {
        JsonObject env = readEnvelope();
        env.remove("encrypted_data");
        writeEnvelope(env);
        assertThrows(Exception.class, () -> manager.loadVault("alice", password.clone()));
    }

    @Test
    void tamperedCiphertextIsRejectedByGcm() throws Exception {
        JsonObject env = readEnvelope();
        byte[] ct = Base64.getDecoder().decode(env.get("encrypted_data").getAsString());
        ct[ct.length - 1] ^= 0x01; // flip one bit -> GCM auth tag must fail
        env.addProperty("encrypted_data", Base64.getEncoder().encodeToString(ct));
        writeEnvelope(env);
        assertThrows(Exception.class, () -> manager.loadVault("alice", password.clone()));
    }

    @Test
    void malformedBase64FailsCleanly() throws Exception {
        JsonObject env = readEnvelope();
        env.addProperty("salt", "!!! not base64 !!!");
        writeEnvelope(env);
        assertThrows(Exception.class, () -> manager.loadVault("alice", password.clone()));
    }

    @Test
    void unknownVersionFailsCleanly() throws Exception {
        // An unknown version falls through to the legacy v1.0 path, which requires
        // fields the v2.0 envelope lacks ("iv") -> must fail, not silently mis-parse.
        JsonObject env = readEnvelope();
        env.addProperty("version", "9.9");
        writeEnvelope(env);
        assertThrows(Exception.class, () -> manager.loadVault("alice", password.clone()));
    }

    // Asserting on the message (not just that *some* exception is thrown) is deliberate:
    // a wrong iteration count would also make GCM fail, so a bare assertThrows would pass
    // even without the bounds check. The message proves the bounds check itself rejected it.

    @Test
    void kdfIterationsBelowFloorIsRejectedByBoundsCheck() throws Exception {
        JsonObject env = readEnvelope();
        env.addProperty("kdf_iterations", 400_000); // below the 600k OWASP floor
        writeEnvelope(env);
        IOException ex = assertThrows(IOException.class, () -> manager.loadVault("alice", password.clone()));
        assertTrue(ex.getMessage().contains("out of accepted range"),
                "expected a bounds-check rejection, got: " + ex.getMessage());
    }

    @Test
    void kdfIterationsNonNumericIsRejectedByBoundsCheck() throws Exception {
        JsonObject env = readEnvelope();
        env.addProperty("kdf_iterations", "not-a-number");
        writeEnvelope(env);
        IOException ex = assertThrows(IOException.class, () -> manager.loadVault("alice", password.clone()));
        assertTrue(ex.getMessage().contains("not a valid integer"),
                "expected a non-numeric rejection, got: " + ex.getMessage());
    }

    /**
     * An absurdly large iteration count must be rejected by the bounds check BEFORE
     * PBKDF2 runs -- otherwise the load would stall for minutes/hours (denial of service).
     * The preemptive timeout proves the rejection is immediate, not after running the KDF.
     */
    @Test
    void kdfIterationsAboveCeilingIsRejectedWithoutStalling() throws Exception {
        JsonObject env = readEnvelope();
        env.addProperty("kdf_iterations", 2_000_000_000); // would hang for hours if passed to PBKDF2
        writeEnvelope(env);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            IOException ex = assertThrows(IOException.class, () -> manager.loadVault("alice", password.clone()));
            assertTrue(ex.getMessage().contains("out of accepted range"),
                    "expected a bounds-check rejection, got: " + ex.getMessage());
        });
    }
}
