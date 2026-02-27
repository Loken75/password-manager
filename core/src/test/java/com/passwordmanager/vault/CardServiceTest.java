package com.passwordmanager.vault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CardServiceTest {
    private Vault vault;
    private CardService service;

    @BeforeEach
    void setUp() {
        vault = new Vault("testuser");
        service = new CardService(vault);
    }

    @Test
    void addEntry() {
        CardEntry entry = new CardEntry("Visa Gold", "John Doe",
            "4111222233334444".toCharArray(), "12/25", "123".toCharArray(),
            "0000".toCharArray(), CardType.VISA, null);
        service.addEntry(entry);
        assertEquals(1, vault.getCardEntries().size());
    }

    @Test
    void deleteEntry() {
        CardEntry entry = new CardEntry("Card", "Holder",
            "4111".toCharArray(), "01/26", null, null, CardType.VISA, null);
        service.addEntry(entry);
        assertTrue(service.deleteEntry(entry.getId()));
        assertEquals(0, vault.getCardEntries().size());
    }

    @Test
    void search() {
        service.addEntry(new CardEntry("Visa Gold", "John Doe",
            "4111".toCharArray(), "12/25", null, null, CardType.VISA, null));
        service.addEntry(new CardEntry("MC Black", "Jane Doe",
            "5555".toCharArray(), "01/26", null, null, CardType.MASTERCARD, null));

        List<CardEntry> results = service.search("john");
        assertEquals(1, results.size());
        assertEquals("Visa Gold", results.get(0).getTitle());
    }

    @Test
    void searchByCardType() {
        service.addEntry(new CardEntry("Card1", "Holder",
            "4111".toCharArray(), "12/25", null, null, CardType.VISA, null));
        service.addEntry(new CardEntry("Card2", "Holder",
            "5555".toCharArray(), "01/26", null, null, CardType.MASTERCARD, null));

        List<CardEntry> results = service.search("mastercard");
        assertEquals(1, results.size());
    }

    @Test
    void sortByCardholderName() {
        service.addEntry(new CardEntry("C1", "Zorro",
            "1111".toCharArray(), "01/26", null, null, CardType.VISA, null));
        service.addEntry(new CardEntry("C2", "Alpha",
            "2222".toCharArray(), "01/26", null, null, CardType.VISA, null));

        List<CardEntry> sorted = service.sorted(service.getReadOnlyList(), SortField.CARDHOLDER_NAME);
        assertEquals("Alpha", sorted.get(0).getCardholderName());
        assertEquals("Zorro", sorted.get(1).getCardholderName());
    }

    @Test
    void sortByCardType() {
        service.addEntry(new CardEntry("C1", "H",
            "1111".toCharArray(), "01/26", null, null, CardType.VISA, null));
        service.addEntry(new CardEntry("C2", "H",
            "2222".toCharArray(), "01/26", null, null, CardType.AMEX, null));

        List<CardEntry> sorted = service.sorted(service.getReadOnlyList(), SortField.CARD_TYPE);
        assertEquals(CardType.AMEX, sorted.get(0).getCardType());
        assertEquals(CardType.VISA, sorted.get(1).getCardType());
    }

    @Test
    void toggleFavorite() {
        CardEntry entry = new CardEntry("Card", "H",
            "4111".toCharArray(), "01/26", null, null, CardType.VISA, null);
        service.addEntry(entry);
        service.toggleFavorite(entry.getId());
        assertTrue(vault.getCardEntries().get(0).isFavorite());
    }
}
