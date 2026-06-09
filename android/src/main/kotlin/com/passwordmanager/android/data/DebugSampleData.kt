package com.passwordmanager.android.data

import com.passwordmanager.vault.AppEntry
import com.passwordmanager.vault.PasswordEntry

/**
 * Debug-only helper that makes sure the vault contains a wide variety of sample entries —
 * varied titles, usernames, emails, categories, password strengths, dates and favorites —
 * so every sort and filter can be exercised when running a debug build.
 *
 * Guarded by the caller with `BuildConfig.DEBUG`. Idempotent: only the sample entries that
 * are not already present (matched by title) are added, so it never duplicates and is safe
 * to run on every launch, even on a vault that already holds other (or these) entries.
 */
object DebugSampleData {

    /** @return true if any sample entries were added (the caller should then persist). */
    fun ensureSamples(session: SessionHolder): Boolean {
        val service = session.vaultService ?: return false
        val vault = session.vault ?: return false

        val cats = vault.categories.ifEmpty {
            listOf("Email", "Banque", "Réseaux sociaux", "Travail", "Autre")
        }
        fun cat(i: Int) = cats[i % cats.size]

        // Passwords spanning all 4 strengths, every category, favorites and a wide date range.
        // (title, username, email, password, url, categoryIndex, favorite, createdAt, updatedAt)
        data class P(
            val title: String, val user: String?, val email: String?, val pw: String,
            val url: String?, val catIdx: Int, val fav: Boolean,
            val created: String, val updated: String
        )

        val passwords = listOf(
            P("Amazon", "alice", "alice@mail.com", "azerty", "https://amazon.fr", 0, false, "2023-03-01T08:00:00Z", "2023-09-12T08:00:00Z"),
            P("Banque Populaire", "alice.b", null, "1234", "https://banquepop.fr", 1, true, "2022-06-15T08:00:00Z", "2024-02-01T08:00:00Z"),
            P("Discord", "al_ice", "alice@mail.com", "Bonjour12", "https://discord.com", 2, false, "2024-01-20T08:00:00Z", "2025-11-05T08:00:00Z"),
            P("GitHub", "alice-dev", "dev@mail.com", "Tr0ub4dour&3xpl0it!Zq2026", "https://github.com", 3, true, "2021-10-10T08:00:00Z", "2026-05-20T08:00:00Z"),
            P("Gmail", "alice", "alice@gmail.com", "Password1", "https://gmail.com", 0, false, "2020-01-05T08:00:00Z", "2026-01-02T08:00:00Z"),
            P("LinkedIn", "alice.pro", "pro@mail.com", "Str0ng!Pass12", "https://linkedin.com", 3, false, "2023-07-22T08:00:00Z", "2024-07-22T08:00:00Z"),
            P("Netflix", "alice", null, "motdepasse", "https://netflix.com", 4, true, "2022-12-25T08:00:00Z", "2023-12-25T08:00:00Z"),
            P("PayPal", "alice", "alice@mail.com", "P@ymentS3cure!2025", "https://paypal.com", 1, false, "2024-04-18T08:00:00Z", "2025-04-18T08:00:00Z"),
            P("Reddit", "u_alice", null, "123456", "https://reddit.com", 2, false, "2025-02-14T08:00:00Z", "2026-03-01T08:00:00Z"),
            P("Spotify", "alice.music", null, "Sp0tify#Long&Strong22", "https://spotify.com", 4, false, "2021-05-30T08:00:00Z", "2026-04-10T08:00:00Z"),
            P("Twitter", "alice_x", "x@mail.com", "qwerty", "https://twitter.com", 2, true, "2020-08-08T08:00:00Z", "2022-08-08T08:00:00Z"),
            P("Zoom", "alice", "alice@work.com", "Z00mMeeting!Secure9", "https://zoom.us", 3, false, "2024-09-09T08:00:00Z", "2026-06-01T08:00:00Z"),
        )

        // App PIN entries spanning titles, usernames, favorites and dates.
        data class A(val title: String, val user: String, val pin: String, val fav: Boolean, val created: String, val updated: String)

        val apps = listOf(
            A("Alarme domicile", "alice", "9137", false, "2024-03-03T08:00:00Z", "2026-02-02T08:00:00Z"),
            A("Appli bancaire", "alice", "3692", false, "2020-02-02T08:00:00Z", "2024-12-12T08:00:00Z"),
            A("Carte SIM", "free", "0000", false, "2023-01-01T08:00:00Z", "2023-01-01T08:00:00Z"),
            A("Coffre maison", "famille", "4821", true, "2022-04-04T08:00:00Z", "2025-04-04T08:00:00Z"),
            A("Téléphone", "alice", "2580", true, "2021-07-07T08:00:00Z", "2026-05-05T08:00:00Z"),
            A("Vélo cadenas", "alice", "7410", false, "2025-06-06T08:00:00Z", "2025-06-06T08:00:00Z"),
        )

        var added = false

        // Add only the samples not already present (matched by title) — idempotent.
        val existingPwTitles = service.search("").mapNotNull { it.title }.toHashSet()
        for (p in passwords) {
            if (p.title in existingPwTitles) continue
            val entry = PasswordEntry(
                p.title, p.user, p.email, p.pw.toCharArray(), p.url, null, cat(p.catIdx), emptyList()
            )
            entry.setCreatedAt(p.created)
            entry.setUpdatedAt(p.updated)
            entry.setFavorite(p.fav)
            service.addEntry(entry)
            added = true
        }

        val existingAppTitles = vault.appEntries.mapNotNull { it.title }.toHashSet()
        for (a in apps) {
            if (a.title in existingAppTitles) continue
            val entry = AppEntry(a.title, a.user, a.pin.toCharArray(), null)
            entry.setCreatedAt(a.created)
            entry.setUpdatedAt(a.updated)
            entry.setFavorite(a.fav)
            vault.addAppEntry(entry)
            added = true
        }

        return added
    }
}
