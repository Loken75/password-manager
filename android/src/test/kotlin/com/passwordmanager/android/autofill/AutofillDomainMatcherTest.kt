package com.passwordmanager.android.autofill

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R10: autofill domain matching must not over-offer credentials. A stored
 * sub-domain credential must never be suggested to its parent/sibling domain,
 * and look-alikes must not match.
 */
class AutofillDomainMatcherTest {

    @Test
    fun `exact domain matches`() {
        assertTrue(AutofillDomainMatcher.matches("example.com", "example.com"))
    }

    @Test
    fun `request sub-domain of stored is offered`() {
        // Stored example.com -> usable on its sub-domains.
        assertTrue(AutofillDomainMatcher.matches("example.com", "login.example.com"))
        assertTrue(AutofillDomainMatcher.matches("example.com", "a.b.example.com"))
    }

    @Test
    fun `stored sub-domain is NOT offered to parent`() {
        // The R10 fix: mail.example.com credential must not autofill on example.com.
        assertFalse(AutofillDomainMatcher.matches("mail.example.com", "example.com"))
    }

    @Test
    fun `look-alike is rejected`() {
        assertFalse(AutofillDomainMatcher.matches("example.com", "evil-example.com"))
        assertFalse(AutofillDomainMatcher.matches("example.com", "example.com.attacker.com"))
    }

    @Test
    fun `unrelated domain is rejected`() {
        assertFalse(AutofillDomainMatcher.matches("example.com", "other.com"))
    }

    @Test
    fun `blank inputs are rejected`() {
        assertFalse(AutofillDomainMatcher.matches("", "example.com"))
        assertFalse(AutofillDomainMatcher.matches("example.com", ""))
    }
}
