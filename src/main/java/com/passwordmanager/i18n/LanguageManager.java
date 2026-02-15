package com.passwordmanager.i18n;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Singleton for managing internationalization (FR/EN).
 * Thread-safe: bundle is volatile so changes are visible across threads (e.g. EDT and timers).
 */
public class LanguageManager {
    private static LanguageManager instance;
    private volatile ResourceBundle bundle;
    private String language;

    private LanguageManager() {
        this.language = "fr";
        loadBundle();
    }

    public static synchronized LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    /**
     * Returns the translated string for the given key.
     * Returns the key itself if not found.
     */
    public String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Changes the language and reloads the resource bundle.
     */
    public void setLanguage(String lang) {
        this.language = lang;
        loadBundle();
    }

    public String getLanguage() {
        return language;
    }

    public String[] getAvailableLanguages() {
        return new String[]{"fr", "en"};
    }

    private void loadBundle() {
        Locale locale = new Locale.Builder().setLanguage(language).build();
        bundle = ResourceBundle.getBundle("i18n.messages", locale);
    }
}
