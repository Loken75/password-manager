package com.passwordmanager.vault;

import com.passwordmanager.util.SecureWiper;

/**
 * Represents a bank card entry stored in the vault.
 * Sensitive fields (cardNumber, cvv, cardPin) are stored as char[]
 * to allow explicit memory wiping.
 */
public class CardEntry extends VaultItem {
    private String cardholderName;
    private char[] cardNumber;
    private String expiryDate; // "MM/YY"
    private char[] cvv;
    private char[] cardPin;
    private String cardType;

    /** Default constructor for Gson deserialization. */
    public CardEntry() {
        super();
    }

    public CardEntry(String title, String cardholderName, char[] cardNumber,
                     String expiryDate, char[] cvv, char[] cardPin,
                     String cardType, String notes) {
        super(title, notes);
        this.cardholderName = cardholderName;
        this.cardNumber = cardNumber != null ? cardNumber.clone() : null;
        this.expiryDate = expiryDate;
        this.cvv = cvv != null ? cvv.clone() : null;
        this.cardPin = cardPin != null ? cardPin.clone() : null;
        this.cardType = cardType;
    }

    @Override
    public void wipe() {
        SecureWiper.wipe(cardNumber);
        SecureWiper.wipe(cvv);
        SecureWiper.wipe(cardPin);
        cardNumber = null;
        cvv = null;
        cardPin = null;
        title = null;
        cardholderName = null;
        expiryDate = null;
        cardType = null;
        notes = null;
    }

    /**
     * Returns the last 4 digits of the card number, or null if unavailable.
     */
    public String getLast4Digits() {
        if (cardNumber == null || cardNumber.length < 4) return null;
        return new String(cardNumber, cardNumber.length - 4, 4);
    }

    public String getCardholderName() { return cardholderName; }
    public void setCardholderName(String cardholderName) { this.cardholderName = cardholderName; }

    /** Returns a defensive copy of the card number. */
    public char[] getCardNumber() { return cardNumber != null ? cardNumber.clone() : null; }
    public void setCardNumber(char[] cardNumber) {
        SecureWiper.wipe(this.cardNumber);
        this.cardNumber = cardNumber != null ? cardNumber.clone() : null;
    }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    /** Returns a defensive copy of the CVV. */
    public char[] getCvv() { return cvv != null ? cvv.clone() : null; }
    public void setCvv(char[] cvv) {
        SecureWiper.wipe(this.cvv);
        this.cvv = cvv != null ? cvv.clone() : null;
    }

    /** Returns a defensive copy of the card PIN. */
    public char[] getCardPin() { return cardPin != null ? cardPin.clone() : null; }
    public void setCardPin(char[] cardPin) {
        SecureWiper.wipe(this.cardPin);
        this.cardPin = cardPin != null ? cardPin.clone() : null;
    }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
}
