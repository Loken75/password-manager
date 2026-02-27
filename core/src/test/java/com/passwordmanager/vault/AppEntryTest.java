package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppEntryTest {

    @Test
    void constructorSetsFields() {
        AppEntry entry = new AppEntry("MyApp", "user1", "1234".toCharArray(), "some notes");
        assertEquals("MyApp", entry.getTitle());
        assertEquals("user1", entry.getUsername());
        assertEquals("some notes", entry.getNotes());
        assertNotNull(entry.getId());
        assertNotNull(entry.getCreatedAt());
        assertNotNull(entry.getUpdatedAt());
    }

    @Test
    void pinDefensiveCopy() {
        char[] original = "5678".toCharArray();
        AppEntry entry = new AppEntry("App", null, original, null);

        // Modifying the original should not affect the entry
        original[0] = '0';
        char[] retrieved = entry.getPin();
        assertEquals('5', retrieved[0]);

        // Modifying the retrieved copy should not affect the entry
        retrieved[0] = '9';
        char[] retrieved2 = entry.getPin();
        assertEquals('5', retrieved2[0]);

        SecureWiper.wipe(retrieved);
        SecureWiper.wipe(retrieved2);
    }

    @Test
    void setPinWipesOld() {
        AppEntry entry = new AppEntry("App", null, "1111".toCharArray(), null);
        entry.setPin("2222".toCharArray());
        char[] pin = entry.getPin();
        assertArrayEquals("2222".toCharArray(), pin);
        SecureWiper.wipe(pin);
    }

    @Test
    void wipe() {
        AppEntry entry = new AppEntry("App", "user", "9999".toCharArray(), "notes");
        entry.wipe();
        assertNull(entry.getTitle());
        assertNull(entry.getUsername());
        assertNull(entry.getPin());
        assertNull(entry.getNotes());
    }

    @Test
    void nullPin() {
        AppEntry entry = new AppEntry("App", null, null, null);
        assertNull(entry.getPin());
    }

    @Test
    void defaultConstructor() {
        AppEntry entry = new AppEntry();
        assertNotNull(entry.getId());
        assertNull(entry.getUsername());
        assertNull(entry.getPin());
    }
}
