# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Cross-platform password manager with three clients sharing one core. Stores passwords, app PIN codes, and SSH keys in an AES-256-GCM vault using DEK/KEK envelope encryption. The user-facing README and `docs/` are written in French; code, identifiers, and Javadoc are in English.

## Build & Test Commands

```bash
# Desktop: build fat JAR and run
./gradlew :desktop:fatJar
java -jar desktop/build/libs/password-manager.jar
# or during dev:
./gradlew :desktop:run

# Android APK (debug)
./gradlew :android:assembleDebug        # -> android/build/outputs/apk/debug/

# Tests
./gradlew :core:test :desktop:test                          # JVM modules
./gradlew :android:testDebugUnitTest                        # Android (local JVM)
./gradlew :core:test :desktop:test :android:testDebugUnitTest   # everything

# Single test class / method (JUnit 5)
./gradlew :core:test --tests "com.passwordmanager.vault.VaultServiceTest"
./gradlew :core:test --tests "com.passwordmanager.vault.VaultServiceTest.addEntry*"

# Portable desktop distribution with embedded JRE (jlink, per-OS)
./scripts/build-dist.sh                 # -> dist/PasswordManager/
```

JDK: `:core`/`:desktop` compile to Java 17 bytecode (CI builds them with JDK 21); Android needs JDK 17 + Android SDK API 35. The app version lives in `gradle.properties` (`appVersion`) — it is injected into `:core` as `version.properties` and into the Android `versionCode`/`versionName`. CI runs in two workflows: `.github/workflows/ci.yml` (core+desktop tests on JDK 21, Android compile+test on JDK 17) and `.github/workflows/release.yml` (triggered on version tags — tests, builds the fat JAR, creates a minimal JRE via jlink, packages per-OS desktop archives, and builds the Android APK).

## Architecture

Three Gradle modules (`settings.gradle.kts`): `:core` (shared Java 17 logic), `:desktop` (Swing + FlatLaf), `:android` (Kotlin + Compose + Hilt). Both `:desktop` and `:android` depend on `:core` via `implementation(project(":core"))` — **all business logic, crypto, and vault models live in `:core` and must stay UI-agnostic.** Never put Swing or Android types in `:core`.

### Encryption (the central design)

`crypto/EncryptionService` (implemented by `CryptoService`) defines the envelope scheme; understanding it is key to the codebase:

```
master password --PBKDF2-HMAC-SHA256 (600k iters, 32-byte salt)--> KEK
KEK --AES-256-GCM--> encrypts a random 32-byte DEK   (stored in .enc)
DEK --AES-256-GCM--> encrypts the vault JSON          (data_iv + encrypted_data)
```

Consequences that affect almost every change:
- **Saves are fast** — they reuse the in-memory DEK (`VaultSession`) and never run PBKDF2.
- **Changing the master password re-encrypts only the DEK** (`VaultManager.changeMasterPassword`), not the whole vault.
- The vault version string (`"2.0"`) is bound as GCM **AAD** — changing serialization format means handling AAD/migration.
- `VaultSession` holds the live DEK; it is `Destroyable`/`AutoCloseable` and must be wiped. `VaultManager` orchestrates load/save/migrate (auto-migrates v1.0 → v2.0) and enforces the 50 MB file cap.

### Vault model & service layer

All entry types extend the abstract `vault/VaultItem` (id, title, notes, favorite, timestamps, **soft-delete tombstone** fields). Three concrete types: `PasswordEntry`, `AppEntry` (PIN), `SshKeyEntry`. CRUD goes through `BaseVaultService<T extends VaultItem>` subclasses: `PasswordService`, `AppService`, `SshKeyService` (the older `VaultService` is the password-specific service). Key conventions:
- **Deletes are soft** — `deleteEntry` marks a tombstone (kept for sync propagation), purged after merge via `purgeTombstones`. Don't hard-remove entries.
- `Vault.getEntries()/getCategories()/getSettings()` return **unmodifiable views**; mutate only through the `*Mutable()` accessors. `Vault.ensureInitialized()` guarantees non-null collections after Gson deserialization.
- Sensitive getters (`PasswordEntry.getPassword()`, `AppEntry.getPin()`, `SshKeyEntry.getPrivateKey()`) return **defensive clones of `char[]`/`byte[]`**; secrets are never `String`. Wipe with `util/SecureWiper`. Every `VaultItem` implements `wipe()`.

Import/export is delegated out of `VaultManager` to `VaultImporter`/`VaultExporter` (CSV is RFC 4180 compliant with a `type` column; SSH keys only in JSON/.enc, not CSV). Filtering is in `EntryFilter`. Generic bidirectional sync merge is `sync/EntryMerger` (shared by both clients). The sync orchestration **engine** (`sync/SyncService`, `LocalRepository`, the `*SyncRepository` interfaces, `ConflictStrategy`) also lives in `:core`: desktop builds it via `DesktopSyncFactory`, while Android keeps an equivalent inline implementation in `VaultListViewModel` (documented duplication). Vault JSON (de)serialization uses a bespoke `vault/VaultJsonCodec` (not Gson for the vault body) so password/PIN/SSH-key secrets stay `char[]` end-to-end on save/load.

### Config & storage abstraction

App settings live in `config/AppConfig` (validated setters; holds `StorageMode`, `ThemeMode`, SFTP params, auto-lock/clipboard timers, `faviconsEnabled`, and the **working folder** state — `localVaultDirectory` + a capped `recentWorkspaces` MRU list). Each client persists it: desktop via `config/ConfigManager`, Android via `data/ConfigRepository`/`AndroidConfigRepository`.

`VaultManager` does **not** touch the filesystem directly — it goes through the `vault/store/VaultStore` abstraction, which owns a single configurable "working folder" (Obsidian-style: the user picks where vault files live) and exposes filename-based primitives (bare filenames only, path-traversal rejected). `FileVaultStore` backs desktop and Android internal storage with atomic, owner-only writes; Android also has a SAF (`content://` document-tree) backing via `data/SafVaultStore`/`WorkspaceManager` for user-chosen external folders. Switching or remembering working folders is wired through `LoginFrame`/`SettingsDialog` (desktop) and the login/settings screens (Android).

### Outbound network calls

Three `:core` services reach the network — relevant for offline behavior, privacy, and tests (mock or guard them): `security/HibpChecker` (Have I Been Pwned breach check via the k-anonymity range API — only a SHA-1 prefix is ever sent), `util/FaviconService` (fetches each site's own `/favicon.ico` directly over HTTPS — no third-party favicon service), and `update/UpdateChecker` (checks for new releases). Everything else, including all crypto and vault I/O, is fully local.

### Client structure

- **Desktop** (`desktop/src/main/java/com/passwordmanager/`): Swing UI under `ui/` (`LoginFrame`, `MainFrame`, the per-tab `CoffrePasswordsPanel`/`CoffreAppsPanel`/`CoffreSshPanel`/`CoffreSettingsPanel`, dialogs, `SecureClipboard`, plus `ui/theme/` token-derived FlatLaf theming and reusable `ui/components/` from the calm UI redesign), `i18n/LanguageManager` for localization, sync under `sync/` (`SFTPRepository` JSch client + `DesktopSyncFactory`; the orchestration engine itself lives in `:core`). Entry point `Main.java`.
- **Android** (`android/src/main/kotlin/com/passwordmanager/android/`): MVVM + Compose. `data/` wraps `:core` (`AndroidVaultRepository`, `SessionHolder`, `BiometricHelper`, repositories, plus `SshHostKeyStore`/`SftpHostKeyVerifier` for SFTP host-key pinning); `di/AppModule.kt` is the single Hilt module; `ui/<feature>/` holds screens + ViewModels; `autofill/` is the Autofill API service. ViewModels wipe secrets in `onCleared()`.

Both clients keep secrets in `char[]`, clear the clipboard on a timer/lock, and authenticate SFTP by SSH key only. Thread-safety is explicit: all access to a `Vault`'s shared collections — the `*Service` CRUD, `Vault`'s own mutators, and save-time serialization in `VaultManager.saveVault` — synchronizes on the **single `Vault` monitor** (`synchronized (vault)`); session state is `@Volatile`/`volatile`. Preserve this when editing those paths — don't reintroduce a second monitor. On desktop, blocking crypto/I/O (PBKDF2, file reads) runs off the Swing EDT via `ui/BackgroundTask`; keep live-vault access on the EDT so it stays serialized with the sync-merge path.

## Conventions

- Match the existing security posture: secrets as `char[]`/`byte[]` (never `String`), defensive copies on sensitive getters, explicit wiping, soft-delete tombstones, unmodifiable collection views. These are load-bearing, not stylistic.
- A change to vault behavior usually needs to land in `:core` and be surfaced in **both** clients (the README feature-parity table tracks desktop vs Android gaps).
- Known open issues and planned work are tracked in `docs/TODO.md` (untracked, French).
