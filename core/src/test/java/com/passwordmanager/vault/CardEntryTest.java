package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardEntryTest {

    @Test
    void constructorSetsFields() {
        CardEntry entry = new CardEntry("Ma Carte", "John Doe",
            "4111222233334444".toCharArray(), "12/25",
            "123".toCharArray(), "0000".toCharArray(), CardType.VISA, "notes");

        assertEquals("Ma Carte", entry.getTitle());
        assertEquals("John Doe", entry.getCardholderName());
        assertEquals("12/25", entry.getExpiryDate());
        assertEquals(CardType.VISA, entry.getCardType());
        assertEquals("notes", entry.getNotes());
        assertNotNull(entry.getId());
    }

    @Test
    void getLast4Digits() {
        CardEntry entry = new CardEntry("Card", "Holder",
            "4111222233334444".toCharArray(), "01/26",
            null, null, CardType.VISA, null);
        assertEquals("4444", entry.getLast4Digits());
    }

    @Test
    void getLast4DigitsShortNumber() {
        CardEntry entry = new CardEntry("Card", "Holder",
            "123".toCharArray(), "01/26", null, null, CardType.VISA, null);
        assertNull(entry.getLast4Digits());
    }

    @Test
    void getLast4DigitsNull() {
        CardEntry entry = new CardEntry("Card", "Holder",
            null, "01/26", null, null, CardType.VISA, null);
        assertNull(entry.getLast4Digits());
    }

    @Test
    void cardNumberDefensiveCopy() {
        char[] original = "4111222233334444".toCharArray();
        CardEntry entry = new CardEntry("Card", "Holder", original,
            "01/26", null, null, CardType.VISA, null);

        original[0] = '0';
        char[] retrieved = entry.getCardNumber();
        assertEquals('4', retrieved[0]);

        retrieved[0] = '9';
        char[] retrieved2 = entry.getCardNumber();
        assertEquals('4', retrieved2[0]);

        SecureWiper.wipe(retrieved);
        SecureWiper.wipe(retrieved2);
    }

    @Test
    void cvvDefensiveCopy() {
        char[] original = "123".toCharArray();
        CardEntry entry = new CardEntry("Card", "Holder", null,
            "01/26", original, null, CardType.VISA, null);

        original[0] = '0';
        char[] retrieved = entry.getCvv();
        assertEquals('1', retrieved[0]);
        SecureWiper.wipe(retrieved);
    }

    @Test
    void cardPinDefensiveCopy() {
        char[] original = "0000".toCharArray();
        CardEntry entry = new CardEntry("Card", "Holder", null,
            "01/26", null, original, CardType.VISA, null);

        original[0] = '9';
        char[] retrieved = entry.getCardPin();
        assertEquals('0', retrieved[0]);
        SecureWiper.wipe(retrieved);
    }

    @Test
    void wipe() {
        CardEntry entry = new CardEntry("Card", "John Doe",
            "4111222233334444".toCharArray(), "12/25",
            "123".toCharArray(), "0000".toCharArray(), CardType.VISA, "notes");

        entry.wipe();
        assertNull(entry.getTitle());
        assertNull(entry.getCardholderName());
        assertNull(entry.getCardNumber());
        assertNull(entry.getExpiryDate());
        assertNull(entry.getCvv());
        assertNull(entry.getCardPin());
        assertNull(entry.getCardType());
        assertNull(entry.getNotes());
    }

    @Test
    void setCardNumberWipesOld() {
        CardEntry entry = new CardEntry("Card", "Holder",
            "1111222233334444".toCharArray(), "01/26", null, null, CardType.VISA, null);
        entry.setCardNumber("5555666677778888".toCharArray());
        char[] num = entry.getCardNumber();
        assertEquals("8888", new String(num, num.length - 4, 4));
        SecureWiper.wipe(num);
    }

    @Test
    void defaultConstructor() {
        CardEntry entry = new CardEntry();
        assertNotNull(entry.getId());
        assertNull(entry.getCardholderName());
        assertNull(entry.getCardNumber());
    }
}
