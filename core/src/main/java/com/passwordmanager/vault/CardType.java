package com.passwordmanager.vault;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Internal constant keys for card types.
 * These keys are stored in the vault JSON and are locale-independent.
 * Each platform maps them to localized display names at the UI layer.
 */
public final class CardType {

    public static final String VISA = "VISA";
    public static final String MASTERCARD = "MASTERCARD";
    public static final String AMEX = "AMEX";
    public static final String CB = "CB";
    public static final String OTHER = "OTHER";

    /** All known card type keys in display order. */
    public static final List<String> ALL = Collections.unmodifiableList(
        Arrays.asList(VISA, MASTERCARD, AMEX, CB, OTHER)
    );

    private CardType() { /* utility class */ }

    /**
     * Returns the desktop i18n message key for a given card type constant.
     * E.g. "VISA" -> "card.type.visa".
     */
    public static String toDesktopMessageKey(String cardTypeKey) {
        if (cardTypeKey == null) return "card.type.other";
        switch (cardTypeKey) {
            case VISA:       return "card.type.visa";
            case MASTERCARD: return "card.type.mastercard";
            case AMEX:       return "card.type.amex";
            case CB:         return "card.type.cb";
            case OTHER:      return "card.type.other";
            default:         return "card.type.other";
        }
    }

    /**
     * Normalizes a potentially legacy localized card type value to an internal key.
     * Handles both old localized values (e.g. "Visa", "Autre", "American Express")
     * and already-normalized keys (e.g. "VISA", "AMEX").
     */
    public static String normalize(String value) {
        if (value == null || value.isEmpty()) return OTHER;
        String upper = value.toUpperCase().trim();
        if (ALL.contains(upper)) return upper;
        // Legacy localized values
        switch (upper) {
            case "VISA":             return VISA;
            case "MASTERCARD":       return MASTERCARD;
            case "AMERICAN EXPRESS": return AMEX;
            case "AMEX":             return AMEX;
            case "CB":               return CB;
            case "OTHER":            return OTHER;
            case "AUTRE":            return OTHER;
            default:                 return OTHER;
        }
    }
}
