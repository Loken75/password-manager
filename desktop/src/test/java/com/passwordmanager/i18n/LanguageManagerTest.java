package com.passwordmanager.i18n;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanguageManagerTest {

    private LanguageManager manager;

    @BeforeEach
    void setUp() {
        manager = LanguageManager.getInstance();
        // Reset to default state
        manager.setLanguage("fr");
    }

    @Test
    void singletonReturnsSameInstance() {
        LanguageManager a = LanguageManager.getInstance();
        LanguageManager b = LanguageManager.getInstance();
        assertSame(a, b);
    }

    @Test
    void getStringReturnsTranslation() {
        String result = manager.getString("app.title");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void getStringMissingKeyReturnsKey() {
        String key = "non.existent.key.xyz";
        assertEquals(key, manager.getString(key));
    }

    @Test
    void setLanguageToEnglish() {
        manager.setLanguage("en");
        assertEquals("en", manager.getLanguage());
        String title = manager.getString("app.title");
        assertEquals("Password Manager", title);
    }

    @Test
    void setLanguageToFrench() {
        manager.setLanguage("fr");
        assertEquals("fr", manager.getLanguage());
        String title = manager.getString("app.title");
        assertNotNull(title);
        assertFalse(title.isEmpty());
    }

    @Test
    void getAvailableLanguages() {
        String[] langs = manager.getAvailableLanguages();
        assertNotNull(langs);
        assertEquals(2, langs.length);
        assertTrue(langs[0].equals("fr") || langs[0].equals("en"));
        assertTrue(langs[1].equals("fr") || langs[1].equals("en"));
    }
}
