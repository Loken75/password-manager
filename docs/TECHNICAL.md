# Documentation technique

## Table des matieres

1. [Vue d'ensemble](#1-vue-densemble)
2. [Prerequis et compilation](#2-prerequis-et-compilation)
3. [Architecture logicielle](#3-architecture-logicielle)
4. [Architecture cryptographique](#4-architecture-cryptographique)
5. [Structure des packages](#5-structure-des-packages)
6. [Format des fichiers](#6-format-des-fichiers)
7. [Securite applicative](#7-securite-applicative)
8. [Synchronisation SFTP](#8-synchronisation-sftp)
9. [Internationalisation](#9-internationalisation)
10. [Interface graphique](#10-interface-graphique)
11. [Systeme de mise a jour](#11-systeme-de-mise-a-jour)
12. [Tests](#12-tests)
13. [Dependances](#13-dependances)
14. [Arbre des fichiers sources](#14-arbre-des-fichiers-sources)

---

## 1. Vue d'ensemble

Password Manager est une application multiplateforme (desktop + Android) permettant de stocker et gerer des mots de passe dans un coffre-fort chiffre. Le projet est organise en **trois modules Gradle** : `:core` (logique metier Java 17), `:desktop` (Swing + FlatLaf) et `:android` (Kotlin + Jetpack Compose / Material 3).

**Caracteristiques techniques principales :**

- Architecture multi-module Gradle (core / desktop / android)
- Chiffrement par enveloppe DEK/KEK (AES-256-GCM + PBKDF2-HMAC-SHA256)
- Protection memoire des donnees sensibles (`char[]` + effacement securise)
- Ecriture atomique des fichiers avec permissions POSIX/ACL restrictives
- Multi-utilisateurs avec coffres isoles
- 3 types d'entrees (mots de passe, applications, cles SSH)
- Import/export CSV et JSON avec protection contre l'injection de formules
- Synchronisation SFTP avec gestion des conflits et mode hors-ligne (desktop + Android)
- Interface bilingue francais/anglais
- Themes systeme, clair et sombre
- Injection de dependances Hilt sur le module Android
- ~529 tests unitaires et d'integration (core, desktop et Android), plus des tests
  instrumentes Android (Keystore AES-GCM, EncryptedSharedPreferences, smoke Compose)

---

## 2. Prerequis et compilation

| Composant | Version requise | Necessaire pour |
|-----------|-----------------|-----------------|
| Java (JDK) | 21 ou superieur | Desktop (build + jlink) |
| Android SDK | API 35 | Android |
| Gradle | 8.12 (wrapper inclus) | Tous |

### Architecture multi-module

```
password-manager/          # Projet Gradle racine
  settings.gradle.kts      # include(":core", ":desktop", ":android")
  build.gradle.kts          # Configuration commune (Java, JUnit)
  core/                     # :core — logique metier (Java 17, aucune dep UI)
  desktop/                  # :desktop — interface Swing (Java 17, dep :core)
  android/                  # :android — interface Compose (Kotlin, dep :core)
```

Le `:core` cible Java 17. AGP sait consommer du bytecode Java 17, avec `coreLibraryDesugaring` active pour `List.of()` et similaires.

### Compilation desktop

```bash
./gradlew :desktop:fatJar
java -jar desktop/build/libs/password-manager.jar
```

Des scripts de lancement sont fournis dans `scripts/` :
- `run.sh` (Linux/macOS) : utilise le JRE embarque (`runtime/bin/java`) s'il est present, sinon le Java systeme
- `run.bat` (Windows) : idem

### Compilation Android

```bash
./gradlew :android:assembleDebug
# APK dans android/build/outputs/apk/debug/
```

### Execution des tests

```bash
# Tests core + desktop (JVM)
./gradlew :core:test :desktop:test

# Tests Android (JVM local, JUnit 5)
./gradlew :android:testDebugUnitTest

# Tous les tests
./gradlew :core:test :desktop:test :android:testDebugUnitTest
```

---

## 3. Architecture logicielle

### 3.1. Diagramme des dependances entre modules

```
:core (Java 17, aucune dependance UI)
  |-- crypto/     CryptoService, KeyDerivation, VaultSession, PasswordGenerator, PasswordStrengthAnalyzer
  |-- vault/      Vault, VaultItem (abstract), PasswordEntry, AppEntry, SshKeyEntry,
  |               VaultManager, VaultStoreMigrator, VaultService (facade), BaseVaultService<T>,
  |               PasswordService, AppService, VaultImporter, VaultExporter, EntryFilter, SortField
  |-- vault/store/ VaultStore (abstraction stockage), FileVaultStore (java.nio.file)
  |-- security/   HibpChecker (k-Anonymity API)
  |-- sync/       EntryMerger (fusion generique), SyncService (orchestration), LocalRepository,
  |               LocalSyncRepository, RemoteSyncRepository (interfaces), ConflictStrategy
  |-- config/     AppConfig, AppVersion, StorageMode, ThemeMode (modeles uniquement)
  |-- update/     UpdateChecker, UpdateInfo, VersionComparator
  +-- util/       SecureWiper, FileSecurityUtils, PasswordValidator, DateUtils, FaviconService

:desktop (Java 17, depends on :core)
  |-- ui/         LoginFrame, MainFrame (sidebar + JMenuBar), CoffrePasswordsPanel, CoffreAppsPanel, CoffreSshPanel, CoffreSettingsPanel, SecurityAuditController,
  |               EntryDialog, AppEntryDialog, SshKeyEntryDialog, SecureClipboard,
  |               ConflictResolutionDialog (generique VaultItem), ...
  |-- config/     ConfigManager, ConfigEncryptor (persistance config.properties)
  |-- i18n/       LanguageManager (FR/EN) + resources/i18n/messages_{fr,en}.properties
  |-- sync/       SFTPRepository (client JSch implementant RemoteSyncRepository),
  |               DesktopSyncFactory (construit le SyncService :core depuis AppConfig)
  +-- update/     DesktopUpdateManager

:android (Kotlin 2.1, depends on :core, Hilt DI)
  |-- autofill/   PasswordManagerAutofillService (API 26+)
  |-- data/       AndroidVaultRepository, AndroidConfigRepository, ConfigRepository, SessionHolder,
  |               WorkspaceManager/AndroidWorkspaceManager, SafVaultStore, AndroidSftpRepository,
  |               BiometricHelper, SshHostKeyStore/SftpHostKeyVerifier, FaviconRepository/FaviconCache,
  |               DebugSampleData (builds debug only: seed idempotent d'exemples varies pour tester tris/filtres)
  |-- di/         AppModule (Hilt @Provides @Singleton)
  |-- ui/         Compose screens + @HiltViewModel (login, vault, app, generator, settings, audit, sync)
  |               VaultTabHost (HorizontalPager 2 pages + selecteur deroulant), AppListVM, AppEditVM, AppDetailVM
  +-- update/     AndroidUpdateManager
```

### 3.2. Diagramme desktop

```
Main
  +-- LoginFrame (ui)
        |-- ConfigManager (config) ---- AppConfig
        |-- VaultManager (vault) ------ EncryptionService (crypto)
        |     |-- CryptoService          |-- KeyDerivation
        |     |-- VaultImporter          |-- VaultSession
        |     +-- VaultExporter          +-- EncryptedPayload
        +-- [login] ---> MainFrame (ui)
              |-- VaultService (facade) ---- Vault ---- PasswordEntry[], AppEntry[], SshKeyEntry[]
              |     |-- PasswordService
              |     +-- AppService
              |-- SyncService (sync, syncAfterMerge)
              |     |-- LocalRepository
              |     +-- SFTPRepository
              |-- DesktopUpdateManager (update)
              |     +-- UpdateChecker (core/update)
              +-- Sidebar de navigation + CardLayout
                    |-- CoffrePasswordsPanel (page mots de passe)
                    |     |-- EntryDialog
                    |     |     +-- PasswordGeneratorDialog
                    |     +-- EntryCardPanel / StrengthMeter ---- PasswordStrengthAnalyzer (crypto)
                    |-- CoffreAppsPanel (page applications)
                    |     +-- AppEntryDialog
                    |-- SecurityAuditController.buildAuditView() (page Audit)
                    |-- CoffreSettingsPanel (page Parametres : onglets dont Cles SSH)
                    |     +-- CoffreSshPanel ---- SshKeyEntryDialog
                    +-- ConflictResolutionDialog (generique VaultItem)
```

### 3.3. Diagramme Android

```
@HiltAndroidApp
PasswordManagerApp (Application)

AppModule (@Module @InstallIn(SingletonComponent))
  +-- @Provides CoroutineDispatcher (Dispatchers.IO)
  +-- @Provides @Singleton WorkspaceManager (-> AndroidWorkspaceManager)
  +-- @Provides @Singleton AndroidVaultRepository (wraps VaultManager(workspaceManager.currentStore()))
  +-- @Provides @Singleton ConfigRepository (-> AndroidConfigRepository)
  +-- @Provides @Singleton SessionHolder (init + return)
  +-- @Provides @Singleton BiometricHelper
  +-- @Provides @Singleton SshHostKeyStore (epinglage host-key SFTP)
  +-- @Provides @Singleton FaviconService + FaviconRepository

@AndroidEntryPoint
MainActivity (extends AppCompatActivity, single Activity)
  +-- @Inject ConfigRepository, SessionHolder
  +-- Applique la locale sauvegardee au demarrage via AppCompatDelegate.setApplicationLocales()
  +-- AppNavigation (NavHost)
        |-- LoginScreen / @HiltViewModel LoginViewModel
        |     +-- @Inject AndroidVaultRepository, SessionHolder
        |-- VaultListScreen (VaultTabHost + HorizontalPager, 2 pages via selecteur deroulant)
        |     |-- VaultListViewModel (@Inject SessionHolder, @ApplicationContext)
        |     +-- AppListScreen / @HiltViewModel AppListViewModel
        |           +-- @Inject SessionHolder
        |-- EntryDetailScreen / @HiltViewModel EntryDetailViewModel
        |     +-- @Inject SessionHolder
        |-- EntryEditScreen / @HiltViewModel EntryEditViewModel
        |     +-- @Inject SessionHolder (onCleared efface le password)
        |-- AppDetailScreen / @HiltViewModel AppDetailViewModel
        |     +-- @Inject SessionHolder
        |-- AppEditScreen / @HiltViewModel AppEditViewModel
        |     +-- @Inject SessionHolder (onCleared efface le pin)
        |-- GeneratorScreen / @HiltViewModel GeneratorViewModel
        |     +-- PasswordGenerator.generate()
        |-- SettingsScreen / @HiltViewModel SettingsViewModel
        |     +-- @Inject ConfigRepository, BiometricHelper, SessionHolder
        |-- CategoryManagementScreen / @HiltViewModel CategoryManagementViewModel
        |     +-- @Inject SessionHolder (addCategory, removeCategory with cascade)
        |-- ChangeMasterPasswordScreen / @HiltViewModel ChangeMasterPasswordViewModel
        |     +-- @Inject SessionHolder, BiometricHelper, ConfigRepository (onCleared efface les passwords)
        |-- SshKeyManagementScreen / @HiltViewModel SshKeyManagementViewModel
        |     +-- @Inject SessionHolder (CRUD cles SSH dans le vault)
        +-- SecurityAuditScreen / @HiltViewModel SecurityAuditViewModel
              +-- @Inject SessionHolder
```

### 3.4. Principes architecturaux

- **Separation des responsabilites** : le `:core` ne contient aucune dependance UI. Les packages `crypto`, `vault`, `sync`, `security`, `update`, `util` sont decouples. Le `:core` n'expose que les **modeles** de configuration (`AppConfig`, `AppVersion`, `StorageMode`, `ThemeMode`) ; la **persistance** de la configuration (`ConfigManager`, `ConfigEncryptor`) et l'**internationalisation** (`LanguageManager` + fichiers `.properties`) vivent dans le module `:desktop`.
- **Interface d'abstraction crypto** : `EncryptionService` est une interface permettant d'injecter un service mock dans les tests, sans dependre de l'implementation `CryptoService`.
- **Aucune retention du mot de passe maitre** : apres l'authentification, seule la `VaultSession` (contenant la DEK) est conservee en memoire. Le mot de passe maitre est efface immediatement.
- **AutoCloseable / Destroyable** : `VaultSession` implemente les deux interfaces pour garantir l'effacement des cles via `try-with-resources` ou appel explicite.
- **MVVM (Android)** : chaque ecran a un `ViewModel` avec `StateFlow` pour l'etat UI. Les ecrans Compose collectent l'etat via `collectAsStateWithLifecycle()`.
- **Injection de dependances (Android)** : Hilt/Dagger fournit les singletons (`AndroidVaultRepository`, `ConfigRepository`, `SessionHolder`) via `AppModule`. Les ViewModels sont annotes `@HiltViewModel` et recoivent leurs dependances via `@Inject constructor`. Les ecrans utilisent `hiltViewModel()` au lieu de `viewModel()`.
- **Interface ConfigRepository (Android)** : interface extraite de `AndroidConfigRepository` pour la testabilite. Un `FakeConfigRepository` est utilise dans les tests unitaires.
- **Thread safety (SessionHolder)** : les champs mutables sont `@Volatile` et les methodes `unlock()`, `lock()`, `save()` sont `@Synchronized` pour prevenir les races entre les coroutines IO et le thread Main.

---

## 4. Architecture cryptographique

### 4.1. Chiffrement par enveloppe (DEK/KEK)

Le systeme utilise deux niveaux de cles :

```
Mot de passe maitre (char[])
    |
    |-- PBKDF2-HMAC-SHA256 (600 000 iterations, sel 32 octets)
    |       |
    |       +-- KEK (Key Encryption Key) -- AES-256
    |                |
    |                +-- AES-256-GCM-encrypt(DEK) -- stocke dans le fichier .enc
    |
    +-- [efface immediatement apres derivation]

DEK (Data Encryption Key) -- AES-256, 32 octets aleatoires (SecureRandom)
    |
    +-- AES-256-GCM-encrypt(donnees du coffre)
```

**Avantages :**
- La DEK est generee une seule fois a la creation du coffre. Les sauvegardes ne necessitent pas de re-derivation PBKDF2.
- Le changement de mot de passe ne re-chiffre que la DEK (quelques octets), pas l'ensemble des donnees.
- La KEK n'est jamais stockee ; elle est derivee a la demande et effacee apres usage.

### 4.2. Parametres cryptographiques

| Parametre | Valeur |
|-----------|--------|
| Algorithme de chiffrement | AES-256-GCM (chiffrement authentifie) |
| Taille de l'IV | 12 octets (GCM standard) |
| Taille du tag d'authentification | 128 bits |
| Taille de la DEK | 32 octets (256 bits) |
| Taille du sel PBKDF2 | 32 octets |
| Iterations PBKDF2 (v2.0) | 600 000 (minimum OWASP 2025) |
| Iterations PBKDF2 (legacy v1.0) | 100 000 |
| Generateur aleatoire | `java.security.SecureRandom` |

### 4.3. Classes du package `crypto`

#### `EncryptionService` (interface)

```java
VaultSession createSession(char[] masterPassword)
VaultSession openSession(byte[] salt, byte[] kekIv, byte[] encryptedDek,
                         int kdfIterations, char[] masterPassword)
EncryptedPayload encryptData(byte[] plaintext, SecretKey dataKey, byte[] aad)
EncryptedPayload encryptData(byte[] plaintext, SecretKey dataKey)  // default: aad=null
byte[] decryptData(byte[] iv, byte[] ciphertext, SecretKey dataKey, byte[] aad)
byte[] decryptData(byte[] iv, byte[] ciphertext, SecretKey dataKey)  // default: aad=null
VaultSession changePassword(VaultSession session, char[] newPassword)
byte[] decryptLegacy(byte[] salt, byte[] iv, byte[] ciphertext, char[] masterPassword)
```

#### `CryptoService` (implementation)

Implemente `EncryptionService` avec AES-256-GCM. Points cles :
- `createSession()` : genere une DEK aleatoire, derive la KEK, chiffre la DEK avec la KEK, efface `rawDek` et detruit la KEK dans le bloc `finally`.
- `openSession()` : derive la KEK, dechiffre la DEK, retourne une `VaultSession`. Efface la KEK dans le `finally`.
- `encryptData()/decryptData()` : supportent un parametre optionnel `byte[] aad` (Additional Authenticated Data) pour lier des metadonnees au ciphertext GCM. Le `VaultManager` passe la version du coffre (`"2.0"`) comme AAD pour prevenir la substitution de parametres.
- `changePassword()` : genere un nouveau sel + KEK, re-chiffre la DEK existante, met a jour l'enveloppe dans la session. Les octets bruts de la DEK (`rawDek`) sont effaces dans le bloc `finally`, et l'ancienne KEK est detruite.
- `decryptLegacy()` : supporte les coffres v1.0 ou le mot de passe derivait directement la cle de donnees (100 000 iterations).

#### `KeyDerivation`

Utilitaire statique pour PBKDF2-HMAC-SHA256.

```java
public static SecretKey deriveKey(char[] password, byte[] salt, int iterations)
public static SecretKey deriveKey(char[] password, byte[] salt) // 600 000 iterations
public static byte[] generateSalt()                             // 32 octets, SecureRandom singleton partage
public static int getDefaultIterations()                        // 600 000
```

Nettoyage : `PBEKeySpec.clearPassword()` est appele apres derivation ; le tableau d'octets intermediaire est efface via `SecureWiper`.

#### `VaultSession`

```java
public class VaultSession implements Destroyable, AutoCloseable {
    private SecretKey dataKey;
    private byte[] salt, kekIv, encryptedDek;
    private int kdfIterations;
    private boolean destroyed;

    public SecretKey getDataKey()
    public byte[] getSalt()          // retourne un clone
    public byte[] getKekIv()         // retourne un clone
    public byte[] getEncryptedDek()  // retourne un clone
    void updateEnvelope(...)         // package-private, pour changePassword
    public void destroy()            // efface toutes les donnees sensibles (log FINE si SecretKey.destroy() non supporte)
    public void close()              // delegue a destroy()
}
```

#### `EncryptedPayload`

Objet de valeur contenant le resultat d'un chiffrement AES-GCM :
- `byte[] iv` (12 octets)
- `byte[] ciphertext` (inclut le tag GCM de 16 octets)
- `void wipe()` : efface les deux tableaux

#### `PasswordGenerator`

Genere des mots de passe cryptographiquement surs via `SecureRandom`.

```java
public static char[] generate(int length, boolean useUpper, boolean useLower,
                               boolean useDigits, boolean useSpecial,
                               boolean excludeAmbiguous)
```

- Longueur bornee entre 8 et 128
- Garantit au moins un caractere de chaque type active
- Melange Fisher-Yates apres remplissage
- Retourne `char[]` (l'appelant est responsable de l'effacement)
- Caracteres ambigus exclus : `0O1lI`
- Caracteres speciaux : `!@#$%^&*()-_=+[]{}|;:',.<>?/`

#### `PasswordStrengthAnalyzer`

Analyse la force d'un mot de passe sur une echelle a 4 niveaux (`WEAK`, `MEDIUM`, `STRONG`, `VERY_STRONG`) et un score numerique 0-100.

Criteres :
- Longueur < 8 ou <= 1 type de caractere -> `WEAK`
- Penalites : caracteres sequentiels (>= 4), caracteres repetes (>= 4), tout en meme casse
- `VERY_STRONG` : longueur effective >= 16 et >= 4 types de caracteres
- `STRONG` : longueur effective >= 12 et >= 3 types

Score :
- Longueur * 4 (plafond 40) + types * 15 (plafond 60)
- Bonus : +10 si longueur >= 16, +10 supplementaire si >= 20
- Malus : -15 par penalite (sequences, repetitions)
- Borne entre 0 et 100

Les surcharges `String` effacent le `char[]` intermediaire apres analyse.

#### Exceptions

- `VaultEncryptionException` : echec de chiffrement ou de derivation de cle
- `VaultDecryptionException` : mot de passe incorrect ou donnees corrompues (tag GCM invalide)

### 4.4. Chiffrement de la configuration

Les champs sensibles de la configuration (identifiants SFTP) sont chiffres au repos via `ConfigEncryptor` (classe package-private) :

- Cle AES-256 derivee via PBKDF2 (100 000 iterations) a partir d'un fichier de materiau de cle (`~/.password-manager/data/.config_key`, 64 octets aleatoires)
- Migration transparente : le dechiffrement essaie d'abord 100 000 iterations, puis retombe sur 10 000 (ancien parametre) pour les configurations existantes
- Format stocke : `ENC:` + Base64(IV[12] + ciphertext)
- Sel fixe (`pm-config-key-derivation`) ; l'aleatoire provient du fichier de cle
- Ecriture atomique du fichier de cle avec permissions restrictives
- L'echec du chiffrement lance une `RuntimeException` (jamais de retour en clair)
- Nettoyage memoire : `keyMaterial`, `keyBytes` et `password` sont effaces dans les blocs `finally` de `deriveKey()`

---

## 5. Structure des packages

### 5.1. `com.passwordmanager.vault`

| Classe | Role |
|--------|------|
| `VaultItem` | Classe abstraite de base pour toutes les entrees (id, title, notes, favorite, createdAt, updatedAt, `wipe()`, `equals`/`hashCode` par id) |
| `PasswordEntry extends VaultItem` | Entree mot de passe (username, email, password `char[]`, url, category, tags) |
| `AppEntry extends VaultItem` | Entree application (username, pin `char[]`) |
| `Vault` | Modele de donnees du coffre (version, utilisateur, 3 listes : `entries` (passwords), `appEntries`, `sshKeyEntries`, categories, parametres). Constructeur prive no-arg pour la deserialisation Gson. `getEntries()`/`getCategories()`/`getSettings()` retournent des vues non-modifiables ; `*Mutable()` pour l'acces en mutation. `ensureInitialized()` garantit les collections non-null apres deserialisation |
| `VaultManager` | Persistance : creation, chargement, sauvegarde, migration v1->v2, backup, import/export. Appelle `ensureInitialized()` apres chaque deserialisation Gson. `decryptVaultFile()` pour le dechiffrement d'un coffre externe |
| `SshKeyEntry extends VaultItem` | Entree cle SSH (privateKey `char[]`, publicKey, keyType, fingerprint). Copie defensive / effacement securise sur `privateKey` |
| `VaultService` | Facade delegant aux 3 sous-services (`PasswordService`, `AppService`, `SshKeyService`). Expose `getPasswordService()`, `getAppService()`, `getSshKeyService()` |
| `SshKeyService extends BaseVaultService<SshKeyEntry>` | Operations specifiques cles SSH : recherche sur titre/type/empreinte |
| `BaseVaultService<T extends VaultItem>` | Service generique : CRUD, recherche, favoris, operations en masse |
| `PasswordService extends BaseVaultService<PasswordEntry>` | Operations specifiques mots de passe : categories, filtres, doublons, anciens mots de passe |
| `AppService extends BaseVaultService<AppEntry>` | Operations specifiques applications : recherche sur username |
| `VaultExporter` | Export CSV/JSON en `char[]` avec protection anti-injection de formules. Export multi-type avec colonne `type` |
| `VaultImporter` | Import CSV/JSON avec parseur RFC 4180 et support multi-type. Detection automatique du separateur et alias multilingues |
| `VaultJsonCodec` | (De)serialisation JSON maison du coffre, secrets en `char[]` de bout en bout (aucun `String` en clair). Parseur tolerant retro-compatible avec les coffres ecrits par Gson. Helpers `JsonCharWriter`/`JsonCharReader` |
| `VaultLoadResult` | Objet de valeur : `Vault` + `VaultSession` |
| `SortField` | Enum : `TITLE`, `USERNAME`, `EMAIL`, `URL`, `DATE` (modification), `CREATED` (creation), `CATEGORY`, `FAVORITE`, `STRENGTH` |
| `EntryFilter` | Filtres combines explicitement types pour `PasswordEntry` (categorie, force, date, favoris, texte) |
| `VaultStore` (package `vault.store`) | Interface d'abstraction du stockage des fichiers de coffre, decouplant `VaultManager` du systeme de fichiers. Primitives sur des noms de fichiers nus (`list`, `exists`, `size`, `read`, `writeAtomic`, `copy`, `delete`, `lastModified`, `pathOf`, `describe`) ; rejet du path traversal. Permet le « dossier de travail » (workspace) configurable |
| `FileVaultStore` (package `vault.store`) | Implementation `java.nio.file` : ecriture atomique (temp + `ATOMIC_MOVE` avec repli), permissions owner-only via `FileSecurityUtils`. Utilisee par le desktop et par le stockage interne Android |
| `VaultStoreMigrator` | Deplace les fichiers `vault_*` (coffre + sidecars `.bak`/`.sync_meta`/`.pending`) entre deux `VaultStore` (copie-puis-suppression, sans ecrasement). Partage desktop (changement de dossier) / Android (migration SAF). Retourne `Result(moved, skipped, failed)` |

**VaultManager — details :**
- Format de fichier v2.0 : enveloppe JSON contenant `version`, `kdf`, `kdf_iterations`, `salt`, `kek_iv`, `encrypted_dek`, `data_iv`, `encrypted_data`
- Migration automatique v1.0 -> v2.0 au chargement
- Ecriture atomique : fichier temporaire -> permissions restrictives sur le temporaire -> `Files.move(ATOMIC_MOVE)` avec fallback
- Sauvegarde `.bak` unique, remplacee a chaque sauvegarde (le nettoyage `cleanupOldBackups` vise 3 fichiers max mais reste sans effet en pratique : un seul nom `.bak` est ecrit)
- `loadVault()` verifie la taille du fichier avant lecture (limite 50 Mo) et valide la presence de tous les champs JSON obligatoires (via `requireJsonField()`) avant decodage
- `reloadVault()` et `importEncryptedVault()` appliquent la meme validation des champs JSON
- `decryptVaultFile(String encFilePath, VaultSession session)` : dechiffre un coffre externe avec la session courante et retourne le `Vault`
- `importEncryptedVault(char[] sourcePassword, String encFilePath)` : dechiffre un coffre externe et retourne les entrees sans ecraser le coffre courant
- `deleteVault()` supprime aussi tous les fichiers `.bak` de l'utilisateur
- Validation du nom d'utilisateur : regex `[a-zA-Z0-9_]+`
- Serialisation du coffre via `VaultJsonCodec` (codec JSON maison, `:core`) : les secrets (`password`/`pin`/`privateKey`) restent en `char[]` de bout en bout, **jamais materialises en `String`** ; `saveVault`/`loadVault` encodent/decodent en UTF-8 via `CharBuffer`/`ByteBuffer`. Le `CharArrayAdapter` Gson n'est conserve que pour l'import JSON externe
- Gson configure avec `setPrettyPrinting()`
- **Compatibilite jlink** : `Vault`, `PasswordEntry`, `AppEntry` et `SshKeyEntry` disposent de constructeurs no-arg pour que Gson puisse les instancier sans `sun.misc.Unsafe` (absent du module `jdk.unsupported`, non inclus dans le JRE jlink)

**VaultItem (classe abstraite) — details :**
- Champs communs : `id` (UUID), `title`, `notes`, `favorite` (boolean), `createdAt`, `updatedAt`
- `wipe()` : methode abstraite implementee par chaque sous-type
- `equals()`/`hashCode()` bases sur l'`id` uniquement

**PasswordEntry (extends VaultItem, ex VaultEntry) — details :**
- Champs specifiques : `username` (identifiant), `email`, `password` (char[]), `url`, `category`, `tags` (List\<String\>)
- `getPassword()` retourne un clone (copie defensive)
- `setPassword()` efface l'ancien mot de passe via `SecureWiper.wipe()` avant d'affecter le nouveau clone
- `getTags()` retourne une vue non-modifiable (`Collections.unmodifiableList`)
- `wipe()` efface le mot de passe, email et nullifie les champs sensibles

**AppEntry (extends VaultItem) — details :**
- Champs specifiques : `username`, `pin` (char[])
- `getPin()` retourne un clone (copie defensive)
- `setPin()` efface l'ancien pin via `SecureWiper.wipe()` avant d'affecter le nouveau clone
- `wipe()` efface le pin et nullifie les champs sensibles

**SshKeyEntry (extends VaultItem) — details :**
- Champs specifiques : `privateKey` (char[]), `publicKey`, `keyType`, `fingerprint`
- `getPrivateKey()` retourne un clone (copie defensive)
- `setPrivateKey()` efface l'ancienne cle via `SecureWiper.wipe()` avant d'affecter le nouveau clone
- `wipe()` efface la cle privee et nullifie les champs sensibles
- Desktop : onglet Parametres > Cles SSH (`CoffreSshPanel`) avec liste, detail et formulaire (`SshKeyEntryDialog`)
- Android : ecran dedie dans les parametres (`SshKeyManagementScreen`)

**VaultService (facade) — details :**
- Delegue aux 3 sous-services : `PasswordService`, `AppService`, `SshKeyService`
- Expose `getPasswordService()`, `getAppService()`, `getSshKeyService()`
- Les methodes historiques (CRUD, recherche, tri) sont conservees pour compatibilite et deleguent a `PasswordService`

**BaseVaultService\<T extends VaultItem\> — details :**
- CRUD generique : `addEntry(T)`, `updateEntry(T)`, `deleteEntry(String)` (soft-delete par tombstone), `purgeTombstones(int maxAgeDays)`
- Accesseurs de lecture : `getActiveList()` (hors tombstones), `getReadOnlyList()`, `search(String)`
- Recherche insensible a la casse sur titre et notes
- `toggleFavorite(String entryId)` / `bulkSetFavorite(List<String>, boolean)` : bascule et operations en masse sur les favoris
- `bulkDelete(List<String> entryIds)` : suppression en masse avec effacement securise de chaque entree
- **Thread safety** : toutes les methodes publiques sont `synchronized`

**PasswordService (extends BaseVaultService\<PasswordEntry\>) — details :**
- Recherche etendue sur identifiant, email, URL, categorie et tags
- Tri : `TITLE`, `USERNAME`, `EMAIL`, `URL` (alphabetique croissant, insensible a la casse), `DATE` (modification, plus recent en premier), `CREATED` (creation, plus recent en premier), `CATEGORY` (alphabetique croissant, insensible a la casse), `FAVORITE` (favoris en premier, titre en secondaire), `STRENGTH` (par force du mot de passe via `PasswordStrengthAnalyzer.ordinal()`, effacement securise du clone dans `finally`)
- `findDuplicatePasswords()` : utilise des hashes SHA-256 des mots de passe comme cles (evite de stocker le clair comme cle de Map). Chaque clone `getPassword()` est efface dans un bloc `finally`
- `findOldPasswords(int days)` : compare les timestamps `updatedAt` au seuil configure
- `bulkChangeCategory(List<String> entryIds, String newCategory)` : reassignation de categorie en masse
- `addCategory(String)` / `removeCategory(String)` : gestion des categories du coffre
- `filter(List<PasswordEntry>, EntryFilter)` : filtrage combine via `EntryFilter` (categorie, force, date, favoris, texte)
- `sorted(entries, sortBy)` / `sorted(entries, sortBy, descending)` : tri avec **favoris toujours en premier** (primaire : `Boolean.compare(b.isFavorite(), a.isFavorite())`). La surcharge `descending` inverse uniquement l'ordre du champ **a l'interieur de chaque bloc** (favoris / non-favoris) — elle ne fait jamais descendre les favoris. `AppService.sorted` expose la meme surcharge ; Android s'en sert pour le basculeur croissant/decroissant

**AppService (extends BaseVaultService\<AppEntry\>) — details :**
- Recherche etendue sur username

**VaultImporter — details :**
- Parseur CSV conforme RFC 4180 (guillemets doubles, retours a la ligne dans les champs)
- Support multi-type : colonne `type` (password/app) pour l'import CSV ; JSON importe les 3 types (passwords, apps, cles SSH)
- Detection du separateur : frequence `,` vs `;` dans la premiere ligne
- Alias de colonnes FR/EN (insensible a la casse et aux accents) :
  - `title` <- titre, organisme, name, nom
  - `username` <- identifiant, login, adresse mail / identifiant (et `pseudo`/`nickname`/`alias`/`surnom`/`display name`, mappes sur l'identifiant s'il n'est pas deja fourni — pas de champ pseudo distinct)
  - `email` <- email, mail, adresse mail, e-mail, courriel
  - `password` <- mdp, mot de passe, pass
  - `url` <- site, website, lien
  - etc.
- Repli positionnel si aucun en-tete reconnu
- Assainissement : suppression des caracteres de controle, troncature a 10 000 caracteres
- Limite : 10 000 entrees par import
- Categorie par defaut si vide : "Autre"
- Tags separes par des points-virgules

**VaultExporter — details :**
- Export multi-type CSV avec colonne `type` (password/app) ; les cles SSH ne sont pas incluses en CSV (JSON et .enc uniquement)
- Protection anti-injection de formules CSV : prefixe `'` devant les champs commencant par `=`, `+`, `-`, `@`, `\t`, `\r`
- Export en `char[]` pour permettre l'effacement securise par l'appelant
- Effacement du StringBuilder intermediaire apres extraction en `char[]`

### 5.2. `com.passwordmanager.config`

Les **modeles** de configuration sont dans `:core` ; la **persistance** (`ConfigManager`, `ConfigEncryptor`) est dans `:desktop` (package `com.passwordmanager.config` du module desktop). Sur Android, la persistance est assuree par `AndroidConfigRepository` (`EncryptedSharedPreferences`).

| Classe | Module | Role |
|--------|--------|------|
| `AppConfig` | :core | Modele de configuration avec setters validants (port 1-65535, auto-lock 1-60 min, etc.) |
| `AppVersion` | :core | Lecture de la version applicative (`get()`, `display()`) depuis `version.properties` |
| `StorageMode` | :core | Enum : `LOCAL`, `REMOTE` |
| `ThemeMode` | :core | Enum : `SYSTEM`, `LIGHT`, `DARK` |
| `ConfigManager` | :desktop | Lecture/ecriture de `config.properties` avec chiffrement des champs sensibles |
| `ConfigEncryptor` | :desktop | Chiffrement AES-256-GCM des identifiants SFTP (package-private) |

**Valeurs par defaut d'AppConfig :**

| Champ | Cle config | Valeur par defaut |
|-------|------------|-------------------|
| `language` | `app.language` | `"fr"` |
| `theme` | `app.theme` | `LIGHT` |
| `storageMode` | `storage.mode` | `LOCAL` |
| `sftpHost` | `sftp.host` | `""` |
| `sftpPort` | `sftp.port` | `22` |
| `sftpUser` | `sftp.user` | `""` |
| `sftpKeyPath` | `sftp.key_path` | `""` |
| `sftpVaultKeyId` | `sftp.vault_key_id` (champ modele ; **non persiste** par `ConfigManager`) | `""` |
| `sftpRemotePath` | `sftp.remote_path` | `"/vault/data"` |
| `localVaultDirectory` | `local.vault_directory` | `{app.home}/data/vaults` |
| `recentWorkspaces` | `workspace.recent.<i>` (desktop) | (vide ; historique des dossiers de travail recents, max 8) |
| `faviconsEnabled` | Android : `favicons_enabled` (**non persiste** par `ConfigManager` desktop) | `true` |
| `autoLockMinutes` | `security.auto_lock_minutes` | `15` |
| `clipboardClearSeconds` | `security.clipboard_clear_seconds` | `30` |

`app.home` est la propriete systeme (defaut : `~/.password-manager`).

**ConfigManager — details :**
- Fichier de configuration : `{app.home}/data/config.properties`
- Les champs SFTP (`sftp.host`, `sftp.user`, `sftp.key_path`, `sftp.remote_path`) sont chiffres via `ConfigEncryptor` a l'ecriture et dechiffres a la lecture (le port reste en clair). Le champ modele `sftpVaultKeyId` n'est pas serialise
- La liste des dossiers de travail recents est persistee sous les cles `workspace.recent.<i>` (en clair)
- Le champ `faviconsEnabled` n'est pas persiste par `ConfigManager` (le reglage favicons desktop n'est donc pas conserve entre sessions)
- Ecriture atomique : fichier `.tmp` -> permissions -> renommage
- Permissions fichier via `FileSecurityUtils.setOwnerOnlyPermissions()`

### 5.3. `com.passwordmanager.sync`

| Classe | Role |
|--------|------|
L'orchestration de synchronisation et ses abstractions vivent desormais dans `:core` (depuis le refactor « Lot E.1a »), ce qui les rend partageables et testables hors UI. Seul le client SFTP concret (`SFTPRepository`, JSch) et la fabrique de configuration (`DesktopSyncFactory`) restent dans `:desktop`. Cote Android, le **transport SFTP** a ete extrait dans `AndroidSftpRepository` (module `:android`), qui implemente l'interface `:core` `RemoteSyncRepository` (auparavant duplique inline dans `VaultListViewModel` et `SettingsViewModel`). Seule **l'orchestration** Android (comparaison de hash a trois voies) reste inline dans `VaultListViewModel` (migration vers `SyncService` planifiee).

| Classe | Module | Role |
|--------|--------|------|
| `EntryMerger` | :core | Fusion bidirectionnelle generique : `merge<T extends VaultItem>(List<T> local, List<T> remote)`. Appliquee aux 3 types d'entrees sur les deux plateformes (desktop et Android) |
| `SyncService` | :core | Orchestration de la synchronisation (comparaison de hash SHA-256 a trois voies, detection de conflit, mode hors-ligne). Constructeur a interfaces uniquement (`LocalSyncRepository`, `RemoteSyncRepository`, `StorageMode`) ; transport-agnostique |
| `LocalSyncRepository` | :core | Interface du depot local (testabilite / mock) |
| `RemoteSyncRepository` | :core | Interface du depot distant (testabilite / mock) |
| `LocalRepository` | :core | Implementation locale : ecritures atomiques, prevention du path traversal, backups, meta de sync |
| `ConflictStrategy` | :core | Enum : `KEEP_LOCAL`, `KEEP_REMOTE`, `KEEP_BOTH` |
| `SFTPRepository` | :desktop | Client SFTP via JSch implementant `RemoteSyncRepository` (authentification par cle fichier ou cle du coffre-fort en `byte[]`, `StrictHostKeyChecking=yes`, limite 50 Mo, validation des noms de fichiers distants contre le path traversal) |
| `DesktopSyncFactory` | :desktop | Construit un `SyncService` depuis `AppConfig` (depot local + `SFTPRepository`, choix cle fichier vs cle du coffre) |

**Flux de synchronisation :**

1. Verification du mode (LOCAL -> pas de sync)
2. Flush des modifications en attente (mode hors-ligne)
3. Si le fichier distant n'existe pas -> upload
4. Telechargement du fichier distant dans un fichier temporaire
5. Verification du fichier telecharge (non-vide, commence par `{`)
6. Comparaison des hashes SHA-256 a trois voies (local, distant, dernier sync)
7. Si local == distant -> synchronise
8. Si seul un cote a change -> upload/download ; si les deux ont change -> conflit (le distant telecharge est conserve pour la fusion)
9. Nettoyage du fichier temporaire dans le bloc `finally` (sauf en cas de conflit)

**Gestion hors-ligne :**
- Si le serveur est injoignable, un marqueur `.pending` est cree localement
- Au prochain sync reussi, les modifications en attente sont rejouees

**LocalRepository — details :**
- Validation des noms de fichiers : rejet de `..`, `/`, `\`, `File.separator`, noms commencant par `~`
- Backup horodate : `{filename}_backup_{yyyyMMdd_HHmmss}.enc`
- Rotation : 5 fichiers de backup maximum par fichier

### 5.4. `com.passwordmanager.util`

| Classe | Role |
|--------|------|
| `SecureWiper` | Effacement securise de `byte[]` et `char[]` avec lecture volatile anti-optimisation JIT |
| `FileSecurityUtils` | Permissions fichiers cross-platform : POSIX 600/700 ou ACL Windows owner-only |
| `PasswordValidator` | Validation du mot de passe maitre (12+ chars, 4 types, pas dans la liste des 93 mots de passe courants) |
| `DateUtils` | Formatage ISO-8601 UTC thread-safe via `java.time` |

**SecureWiper — technique :**
```java
private static volatile byte volatileByte;
private static volatile char volatileChar;

public static void wipe(byte[] data) {
    if (data != null && data.length > 0) {
        Arrays.fill(data, (byte) 0);
        byte check = 0;
        for (byte b : data) { check |= b; }
        volatileByte = check; // accumulateur sur tout le tableau — empeche le JIT d'eliminer le fill
    }
}

public static void wipe(char[] data) {
    if (data != null && data.length > 0) {
        Arrays.fill(data, '\0');
        char check = 0;
        for (char c : data) { check |= c; }
        volatileChar = check; // accumulateur sur tout le tableau
    }
}
```

**FileSecurityUtils — comportement :**
- POSIX : fichiers -> `rw-------` (600), repertoires -> `rwx------` (700)
- Windows : ACL owner-only avec controle total (`EnumSet.allOf(AclEntryPermission.class)`) + preservation des entrees SYSTEM/SYSTEME. La securite repose sur la suppression de toutes les autres entrees ACL (non-owner, non-SYSTEM).
- Echec silencieux (log FINE) pour ne pas bloquer l'application sur les systemes sans support

**PasswordValidator — politique :**
- Minimum 12 caracteres
- Au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractere special
- Rejet des 93 mots de passe courants (comparaison sur la base alphabetique, ex: `Password123!` -> `password` -> rejete)
- Comparaison a temps constant (accumulation XOR) pour prevenir les canaux auxiliaires de timing
- Toutes les operations en `char[]` sans creation de `String` a partir de l'entree utilisateur

### 5.5. `com.passwordmanager.i18n` (module `:desktop`)

Ce package et les fichiers de ressources `i18n/messages_{fr,en}.properties` sont dans le module `:desktop` (l'i18n Android est gere par `res/values*/strings.xml`).

| Classe | Role |
|--------|------|
| `LanguageManager` | Singleton thread-safe (`volatile bundle`), gestion FR/EN via `ResourceBundle` |

- 213 cles de traduction par langue
- Changement de langue dynamique via `setLanguage()` (desktop : reconstruit l'interface, Android : `AppCompatDelegate.setApplicationLocales()` + `locales_config.xml`)
- Retourne la cle elle-meme si la traduction n'est pas trouvee (pas d'exception)

### 5.6. `com.passwordmanager.ui`

| Classe | Role |
|--------|------|
| `LoginFrame` | Ecran de connexion, creation d'utilisateur, toggle visibilite mot de passe, changement de langue |
| `MainFrame` | Fenetre principale : `JMenuBar` de fenetre (Fichier/Edition/Outils/Aide, dont Deconnexion), **barre laterale de navigation** (Mots de passe, Applications, Audit, Parametres) pilotant un `CardLayout` central, barre de notification (NORTH) + barre de statut (SOUTH), auto-lock, shutdown hook (retire au verrouillage) |
| `CoffrePasswordsPanel` | Page mots de passe : barre de controle (recherche, icone tri -> menu, icone filtres -> chips multi-selection, bouton "+ Nouvelle entree"), tableau de bord (cartes Entrees/Favoris/Securite), rangee "Recemment utilises" (MRU en memoire), liste de cartes (`EntryCardPanel`) + panneau de details (boutons copier avec feedback "Copie ✓"). Selection multiple, menu contextuel (clic droit), **navigation clavier** (fleches/Entree, anneau de focus). Favicons asynchrones (`SwingWorker`) |
| `CoffreAppsPanel` | Page applications : meme structure de liste/details et de controles (sans categorie ni force) |
| `SecurityAuditController` | Construit la **page Audit** (`buildAuditView()` -> composant scrollable embarque dans `MainFrame`, plus de dialog) : sections Vue d'ensemble, A risque (callouts repliables faibles/reutilises/anciens/HIBP), Points forts, Composition, Completude, Activite. Verification HIBP asynchrone via `SwingWorker<List<PasswordEntry>, Integer>` + `JProgressBar` |
| `EntryDialog` | Formulaire modal de creation/edition d'entree mot de passe |
| `AppEntryDialog` | Formulaire modal de creation/edition d'entree application |
| `CoffreSshPanel` | Onglet **Parametres > Cles SSH** : liste de cartes `SshKeyEntry` (type, empreinte), detail, selection multiple, operations en masse, generation de cles (ED25519/RSA via JSch), import de fichiers PEM, bouton "+ Nouvelle cle" (creation manuelle) |
| `SshKeyEntryDialog` | Formulaire modal de creation/edition de cle SSH (nom, type, cle privee, cle publique, empreinte) |
| `PasswordGeneratorDialog` | Dialogue du generateur de mots de passe. Timer clipboard `javax.swing.Timer` annule a la fermeture |
| `ImportExportController` | Popup unifiee d'import/export (CSV, JSON, coffre chiffre .enc) avec champ mot de passe pour l'import chiffre |
| `CoffreSettingsPanel` | **Page** des parametres (in-shell, plus un dialogue) en onglets : General, Categories, Securite, Synchronisation, Cles SSH. Gestion des categories (ajout/suppression). Source de cle SSH configurable (`CardLayout`). Test SFTP sur `SwingWorker` (hors EDT). Pied de page : bouton Appliquer (plus de Retour) |
| `ConflictResolutionDialog` | Dialogue generique de resolution de conflits pour tous les sous-types `VaultItem` |
| `StrengthBarHelper` | Utilitaire d'affichage de la barre de force (couleurs : rouge/orange/vert/bleu) |

---

## 6. Format des fichiers

### 6.1. Fichier coffre (`.enc`, format v2.0)

Emplacement : `~/.password-manager/data/vaults/vault_<username>.enc`

```json
{
  "version": "2.0",
  "kdf": "PBKDF2WithHmacSHA256",
  "kdf_iterations": 600000,
  "salt": "<base64, 32 octets>",
  "kek_iv": "<base64, 12 octets>",
  "encrypted_dek": "<base64, DEK chiffree + tag GCM>",
  "data_iv": "<base64, 12 octets>",
  "encrypted_data": "<base64, coffre JSON chiffre AES-256-GCM>"
}
```

Le coffre JSON dechiffre contient :
```json
{
  "version": "2.0",
  "user": "alice",
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-06-20T14:22:00Z",
  "entries": [ ... ],
  "appEntries": [ ... ],
  "sshKeyEntries": [ ... ],
  "categories": ["Email", "Bancaire", "Reseaux sociaux", "Travail", "Autre"],
  "settings": {
    "auto_lock_minutes": 15,
    "clipboard_clear_seconds": 30,
    "password_expiry_days": 180
  }
}
```

### 6.2. Fichier de configuration

Emplacement : `~/.password-manager/data/config.properties`

```properties
app.language=fr
app.theme=light
security.auto_lock_minutes=15
security.clipboard_clear_seconds=30
storage.mode=local
local.vault_directory=/home/user/.password-manager/data/vaults
sftp.host=ENC:base64(...)
sftp.port=22
sftp.user=ENC:base64(...)
sftp.key_path=ENC:base64(...)
sftp.remote_path=ENC:base64(...)
```

Les champs SFTP sont chiffres via `ConfigEncryptor` (prefixe `ENC:`).

### 6.3. Fichier de cle de configuration

Emplacement : `~/.password-manager/data/.config_key`

64 octets aleatoires (`SecureRandom`), permissions 600. Utilise pour deriver la cle AES-256 de chiffrement des champs de configuration via PBKDF2 (100 000 iterations). Ecriture atomique.

### 6.4. Arborescence sur disque

```
~/.password-manager/
+-- data/
    +-- .config_key                    # Materiau de cle (64 octets, permissions 600)
    +-- config.properties              # Configuration (champs SFTP chiffres)
    +-- vaults/                        # Repertoire des coffres (permissions 700)
        +-- vault_alice.enc            # Coffre chiffre
        +-- vault_alice.enc.bak        # Backup automatique
        +-- vault_bob.enc
```

---

## 7. Securite applicative

### 7.1. Protection memoire

| Mesure | Implementation |
|--------|----------------|
| Donnees sensibles en `char[]` | `PasswordEntry.password`, `AppEntry.pin`, `SshKeyEntry.privateKey`, `PasswordGenerator.generate()`, `VaultExporter` retournent `char[]` |
| Serialisation sans `String` | Le corps du coffre est (de)serialise par `VaultJsonCodec` : les secrets ne transitent jamais par un `String` immuable, ni a la sauvegarde (`encode` -> `char[]`) ni au chargement (`decode` lit les secrets directement en `char[]`). Subsistent uniquement les `String` imposes par les API OS (presse-papiers, autofill, `JPasswordField`) |
| Effacement securise | `SecureWiper.wipe()` avec accumulateur volatile sur tout le tableau (empeche le JIT d'eliminer le `Arrays.fill`) |
| GCM AAD | Le chiffrement des donnees du coffre lie la version (`"2.0"`) en AAD. C'est une mesure de defense en profondeur de portee limitee : l'AAD est une constante codee en dur (non relue du fichier) et la lecture retombe en clair-AAD si la verification echoue (fallback pour les coffres pre-AAD). La confidentialite repose sur la cle, pas sur l'AAD |
| Presse-papiers securise (Desktop) | `SecureClipboard` : `Transferable` personnalise stockant `char[]`, efface sur `lostOwnership()` + `clear()` en shutdown hook. Aucun `new String(password)` dans le presse-papiers |
| Copie defensive | `PasswordEntry.getPassword()`, `AppEntry.getPin()`, `SshKeyEntry.getPrivateKey()` retournent des clones. Les setters effacent l'ancienne valeur avant clone |
| Copie defensive session | `VaultSession.getSalt/getKekIv/getEncryptedDek()` retournent des clones |
| Nettoyage de session | La DEK est detenue en clair sous forme de `byte[]` possede par `VaultSession` ; `destroy()` efface reellement (`SecureWiper.wipe`) la DEK, le sel, l'IV et la DEK chiffree. Le KEK derive du mot de passe est lui aussi manipule en `byte[]` (`KeyDerivation.deriveKeyBytes`) et efface en `finally` apres chaque operation dans `CryptoService`. **Limite JCA residuelle** : chaque operation cipher doit reconstruire un `SecretKeySpec` transitoire (`getDataKey()`), dont la copie interne des octets n'est pas effacable et subsiste jusqu'au GC ; cette exposition est de courte duree, contrairement a l'ancienne ou la DEK persistait toute la session. `getDataKey()` leve `IllegalStateException` apres destruction |
| Nettoyage Swing | Insertion via `Document.insertString()` au lieu de `JPasswordField.setText(String)` pour minimiser l'interning |
| Nettoyage ViewModel (Android) | `EntryEditViewModel.onCleared()`, `EntryDetailViewModel.onCleared()`, `AppDetailViewModel.onCleared()`, `ChangeMasterPasswordViewModel.onCleared()` et `SettingsViewModel.onCleared()` effacent les donnees sensibles de l'etat UI |
| Biometrie AndroidKeyStore | Cle AES-256-GCM par utilisateur, `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`. Le mot de passe maitre est chiffre/dechiffre via `CharBuffer`/`ByteBuffer` (sans intermediaire `String`) et stocke dans `EncryptedSharedPreferences`. L'inscription efface le `char[]` du mot de passe apres chiffrement |
| Invalidation biometrique | Changement de mot de passe maitre → `configRepo.clearBiometricData()` + `biometricHelper.deleteKey()`. Changement d'empreinte → `KeyPermanentlyInvalidatedException` interceptee |
| Thread safety SessionHolder | Champs `@Volatile` (`vault`, `session`, `vaultService`, `username`) + `@Synchronized` sur `unlock()`, `lock()`, `save()` |
| Thread safety VaultListViewModel | `pendingPasswordConflicts` marque `@Volatile` ; `syncJob` suivi et annule dans `onCleared()` ; cache favicon borne (`MAX_FAVICONS=50`) |
| Auto-masquage | Le mot de passe affiche se re-masque automatiquement apres 30 secondes |

### 7.2. Securite des fichiers

| Mesure | Implementation |
|--------|----------------|
| Permissions restrictives | POSIX 600 (fichiers) / 700 (repertoires) ; ACL owner-only sur Windows |
| Ecriture atomique | Fichier temporaire -> permissions restrictives -> `Files.move(ATOMIC_MOVE)` avec fallback |
| Backup coffre | 1 fichier `.bak` par utilisateur, remplace a chaque sauvegarde (VaultManager) |
| Backup roulant sync | 5 fichiers horodates max (LocalRepository) |
| Suppression complete | `deleteVault()` supprime le coffre et tous les fichiers `.bak` associes |
| Validation des noms d'utilisateur | Regex `[a-zA-Z0-9_]+` pour prevenir le path traversal |
| Validation des noms de fichiers (local) | Rejet de `..`, `/`, `\`, `~` dans `LocalRepository` |
| Validation des noms de fichiers (distant) | Rejet de `..`, `/`, `\`, `~` dans `SFTPRepository` |
| Validation du chemin local (SFTP) | `SFTPRepository.validateLocalPath()` verifie le chemin canonique contre le repertoire vaults pour prevenir le path traversal |

### 7.3. Protection anti brute-force

- Compteur statique par utilisateur (`Map<String, Integer>`), persiste entre les cycles verrouillage/deverrouillage
- Apres 3 tentatives echouees : desactivation du formulaire avec backoff `min(attempts * 2000, 30000)` ms
- Reinitialisation du compteur apres une connexion reussie

### 7.4. Validation des entrees

| Contexte | Validation |
|----------|-----------|
| Mot de passe maitre | 12+ chars, 4 types, pas dans la liste des 93 mots de passe courants (`PasswordValidator`) |
| Nom d'utilisateur | Regex `[a-zA-Z0-9_]+` |
| Import CSV/JSON | Assainissement des caracteres de controle, troncature 10 000 chars, limite 10 000 entrees |
| Export CSV | Echappement RFC 4180, prefixe anti-formule (`'`) |
| Fichier cle SSH | Validation d'existence et de lisibilite avant sauvegarde |
| Fichier coffre local | Limite de taille 50 Mo avant lecture (`VaultManager.loadVault()`) |
| Telechargement SFTP | Limite de taille 50 Mo, verification de contenu JSON valide (commence par `{`) |

### 7.5. Securite SFTP

- Authentification par cle SSH uniquement (pas de mot de passe)
- Desktop : `StrictHostKeyChecking=yes` (verification via `~/.ssh/known_hosts`)
- Android : verification stricte avec epinglage de cle d'hote. La cle est stockee en zone privee de l'app (`SshHostKeyStore`) ; au premier contact (ou si la cle change), l'empreinte SHA-256 est presentee a l'utilisateur pour confirmation explicite avant epinglage (`SftpHostKeyVerifier`). Aucune acceptation aveugle (`accept-new`)
- Timeouts : connexion 15-30s, canal 10s
- Identifiants stockes chiffres dans la configuration (desktop: `ConfigEncryptor`, Android: `EncryptedSharedPreferences`)

### 7.6. Presse-papiers

- Effacement automatique apres le delai configure (defaut 30s, configurable 5-120s)
- Desktop : `javax.swing.Timer` (EDT-safe) : annule et relance a chaque nouvelle copie, annule a la fermeture du dialogue
- Android : `ProcessLifecycleOwner.lifecycleScope` pour le delai, annule automatiquement si le processus meurt
- Android (API 33+) : flag `ClipDescription.EXTRA_IS_SENSITIVE` pour masquer le contenu copie dans la previsualisation systeme
- Pas de fuite de threads : chaque timer est suivi et arrete avant d'en creer un nouveau
- Effacement au verrouillage et a la fermeture

---

## 8. Synchronisation SFTP

### 8.1. Architecture

**Desktop :**
```
MainFrame
    +-- SyncService (synchronized, execute sur SwingWorker)
            |-- LocalRepository (ecritures atomiques, prevention path traversal)
            +-- SFTPRepository (JSch, authentification par cle)
```

**Android :**
```
VaultListViewModel
    +-- syncNow() (coroutine Dispatchers.IO)
            |-- AndroidSftpRepository (implemente RemoteSyncRepository :core ;
            |     JSch + host-key epingle via SftpHostKeyVerifier)
            |-- Comparaison SHA-256 local vs distant
            |-- EntryMerger (fusion bidirectionnelle pour les 3 types)
            +-- Resolution de conflits interactive (ConflictResolutionScreen)
```

Le **transport SFTP** Android est centralise dans `AndroidSftpRepository` (`AndroidSftpRepository.fromConfig(...)`), qui implemente l'interface `:core` `RemoteSyncRepository` (connexion, transfert, verification de la cle d'hote) — il n'est plus duplique inline dans `VaultListViewModel`/`SettingsViewModel`. En revanche, **l'orchestration** (comparaison a trois voies) reste implementee directement dans `VaultListViewModel`, fonctionnellement equivalente au moteur `:core` (meme `EntryMerger`, dechiffrement du vrai distant via `decryptVaultFile`, adoption d'enveloppe). La duplication de cette orchestration entre `SyncService` (`:core`) et la sync inline Android est une dette assumee (non bloquante).

### 8.2. Algorithme de synchronisation

```
1. Si mode LOCAL -> retour immediat
2. Flush des modifications .pending (mode hors-ligne)
3. Si fichier distant absent -> upload du fichier local + memorisation du hash synchronise
4. Telechargement du fichier distant dans un temporaire (verifie non-vide / JSON)
5. Calcul SHA-256 : hash local, hash distant, hash du dernier sync (sync_meta)
6. Hashes local == distant -> deja synchronise
7. Comparaison a TROIS voies (pas de timestamp ; via sync_meta) :
   - seul le local a change   -> upload
   - seul le distant a change -> download (+ backup local) + adoption d'enveloppe (R4)
   - les deux ont change      -> CONFLIT
8. Sur CONFLIT, le distant telecharge est CONSERVE (`SyncResult.getRemoteTempPath()`)
   pour fusionner sur le VRAI distant (R-merge), pas sur une relecture du local
9. finally : suppression du temporaire SAUF en cas de conflit, deconnexion SFTP
```

### 8.3. Resolution des conflits

**Voie principale (fusion par entree)** : sur CONFLIT, le client dechiffre le distant conserve
(`decryptVaultFile(remoteTempPath)`), **adopte son enveloppe** dans la session
(`adoptEnvelopeFromFile` — pour qu'un changement de mot de passe maitre fait sur un autre
appareil ne soit pas annule, **R4**), fusionne via `EntryMerger` (mots de passe en resolution
interactive, applications/cles SSH auto-resolues), puis sauvegarde et re-uploade le resultat.

**Voie de repli (fichier entier)**, si la fusion par entree echoue :

| Mode | Comportement |
|------|-------------|
| `KEEP_LOCAL` | Upload du fichier local (ecrase le distant) |
| `KEEP_REMOTE` | Telechargement du distant (verifie non-vide et JSON valide) |
| `KEEP_BOTH` | Backup local horodate, puis telechargement du distant |

### 8.4. Mode hors-ligne

Quand le serveur est injoignable, un fichier `.pending` est cree. Au prochain sync reussi, les modifications en attente sont envoyees avant la synchronisation normale.

### 8.5. Statuts de synchronisation

| Statut | Signification |
|--------|--------------|
| `local` | Mode stockage local, pas de synchronisation |
| `syncing` | Synchronisation en cours |
| `synced` | Coffre local et distant identiques |
| `conflict` | Conflit detecte, intervention requise |
| `offline` | Serveur injoignable, modifications en attente |
| `error` | Erreur de synchronisation |

---

## 9. Internationalisation

### 9.1. Mecanisme

`LanguageManager` est un singleton thread-safe utilisant `java.util.ResourceBundle` avec un champ `volatile` pour la visibilite inter-threads. Il reside dans le module `:desktop` (l'i18n Android utilise les ressources `strings.xml`).

### 9.2. Ressources

| Fichier (module `:desktop`) | Langue | Cles |
|---------|--------|------|
| `i18n/messages_fr.properties` | Francais | 213 |
| `i18n/messages_en.properties` | Anglais | 213 |

### 9.3. Sections couvertes

`app.*`, `login.*`, `error.*`, `vault.*`, `entry.*`, `generator.*`, `strength.*`, `category.*`, `settings.*`, `menu.*` (file/edit/view/tools/help), `sync.*`, `import.*`, `export.*`, `common.*`, `security.*`, `about.*`, `audit.*`

### 9.4. Changement dynamique

Le changement de langue est possible depuis l'ecran de connexion (effet immediat : la fenetre est reconstruite) et depuis les parametres (reconstruit la fenetre principale).

---

## 10. Interface graphique

### 10.1. Desktop — Swing + FlatLaf

- **Swing** avec **FlatLaf 3.7** pour un rendu moderne
- Trois themes : **Systeme** (detecte automatiquement le theme de l'OS), **Clair** (`FlatLightLaf`), **Sombre** (`FlatDarkLaf`)
- Theme applique au demarrage et modifiable dynamiquement depuis les parametres

#### Structure des fenetres

| Fenetre | Type | Taille | Description |
|---------|------|--------|-------------|
| `LoginFrame` | `JFrame` | 450x450 (non-redimensionnable) | Connexion, creation utilisateur, toggle mot de passe, selection de langue, et **selecteur de dossier de travail** (combo des dossiers recents + bouton `...`, migration des coffres entre dossiers via `VaultStoreMigrator`) |
| `MainFrame` | `JFrame` | 1100x700 (min 900x600) | Barre laterale de navigation + `JMenuBar` de fenetre + `CardLayout` central (Mots de passe, Applications, Audit, Parametres), barre de notification et barre de statut |
| `EntryDialog` | `JDialog` (modal) | min 550x480 | Creation/edition d'entree mot de passe avec barre de force |
| `AppEntryDialog` | `JDialog` (modal) | — | Creation/edition d'entree application |
| `PasswordGeneratorDialog` | `JDialog` (modal) | min 450x420 | Generateur avec options et barre de force |
| `CoffreSettingsPanel` | panneau in-shell (page) | — | Parametres en onglets (General, Categories, Securite, Synchronisation, Cles SSH) ; l'onglet General affiche le dossier de travail courant + bouton « Changer… » (declenche une reconnexion) |

#### Page Mots de passe (CoffrePasswordsPanel)

Disposition (`JSplitPane` liste + details) :

| Zone | Contenu |
|------|---------|
| Barre de controle (haut) | Recherche en temps reel + icone **tri** (menu : sens + critere) + icone **filtres** (panneau de chips multi-selection : favoris, categories, force, dates) + bouton "+ Nouvelle entree" |
| Tableau de bord + recents | Cartes **Entrees / Favoris / Securite** (/20, calculees sur tout le coffre) ; rangee **"Recemment utilises"** (MRU en memoire) |
| Liste (centre) | Liste de cartes `EntryCardPanel` (favicon/avatar, titre, sous-titre, badge de force, etoile). Selection multiple, menu contextuel (clic droit), **navigation clavier** (fleches/Entree, anneau de focus), menu "Actions..." en masse |
| Details (droite, ~360 px) | Titre, champs avec boutons **copier** (feedback "Copie ✓"), `SecretFieldPanel` masque + `StrengthMeter`, categorie editable, dates, actions Modifier/Dupliquer/Supprimer |

La force est coloree selon le niveau : rouge (Faible), orange (Moyen), vert (Fort), bleu (Tres fort).

#### Page Applications (CoffreAppsPanel)

Meme disposition liste de cartes + details (sans categorie ni force ; le filtre se limite a Favoris + dates). Recherche sur titre et identifiant. Selection multiple, menu contextuel, operations en masse (suppression, favoris). Formulaire via `AppEntryDialog`.

#### Auto-lock (desktop)

- `Toolkit.addAWTEventListener` sur les evenements clavier et souris
- `javax.swing.Timer` verifie l'inactivite toutes les 30 secondes
- Depassement du seuil configurable -> verrouillage (sauvegarde -> nettoyage -> effacement des cles -> ecran de connexion)

#### Shutdown hook

Un `Runtime.addShutdownHook` efface le coffre (`vault.wipe()`), detruit la session (`session.destroy()`) et efface le presse-papiers (`SecureClipboard.clear()`) a la fermeture de la JVM. Le hook est retire proprement lors du verrouillage (`doLock()`) et lors d'un changement de langue (qui reconstruit la fenetre).

### 10.2. Android — Jetpack Compose + Material 3

#### Technologies

- **Kotlin 2.1.0** avec **Jetpack Compose** (BOM 2024.12.01)
- **Material 3** (Material You) avec **Dynamic Colors** sur Android 12+
- **Hilt 2.54** (Dagger) pour l'injection de dependances, avec **KSP 2.1.0-1.0.29** pour le traitement d'annotations
- **Navigation Compose 2.8.5** pour le routage entre ecrans
- **ViewModel + StateFlow** (pattern MVVM) — tous les ViewModels sont `@HiltViewModel`
- **EncryptedSharedPreferences** (security-crypto 1.1.0-alpha06) pour la configuration
- Min SDK 26 (Android 8.0), Target SDK 35, Compile SDK 35

#### Structure des ecrans

| Ecran | Composable | ViewModel | Description |
|-------|-----------|-----------|-------------|
| Login | `LoginScreen` | `LoginViewModel` | Dropdown utilisateurs, creation, anti brute-force, deverrouillage biometrique, detection auto des mises a jour (dialog + icone en haut a droite) |
| Liste | `VaultListScreen` | `VaultListViewModel` | `VaultTabHost` avec `HorizontalPager` (2 pages : mots de passe, applications) selectionnees via un menu deroulant (`VaultPageSelector`) dans la TopAppBar. Recherche, filtres **multi-selection** (categories, force, dates, favoris), tri (incl. force et dates creation/modification, sens croissant/decroissant), menu d'actions partage (`VaultActionsMenu` : import/export/sync), selection multiple, favicons asynchrones, sync SFTP |
| Liste apps | `AppListScreen` | `AppListViewModel` | Page applications : recherche, tri, filtres, selection multiple, operations en masse, et le meme menu d'actions partage (`VaultActionsMenu`) que les mots de passe (instance `VaultListViewModel` partagee) |
| Detail | `EntryDetailScreen` | `EntryDetailViewModel` | Lecture seule (identifiant, email), URL cliquable, copier, supprimer |
| Detail app | `AppDetailScreen` | `AppDetailViewModel` | Lecture seule application, copier username/pin, supprimer |
| Edition | `EntryEditScreen` | `EntryEditViewModel` | Formulaire CRUD (identifiant, email), lien generateur |
| Edition app | `AppEditScreen` | `AppEditViewModel` | Formulaire CRUD application (username, pin) |
| Generateur | `GeneratorScreen` | `GeneratorViewModel` | Options, barre de force, copier/utiliser |
| Parametres | `SettingsScreen` | `SettingsViewModel` | Theme, langue, auto-lock, clipboard, synchronisation SFTP, gestion des categories, activation biometrie |
| Categories | `CategoryManagementScreen` | `CategoryManagementViewModel` | Ajout/suppression de categories avec validation et cascade |
| Mot de passe | `ChangeMasterPasswordScreen` | `ChangeMasterPasswordViewModel` | Ancien/nouveau/confirmer, invalidation biometrique |
| Cles SSH | `SshKeyManagementScreen` | `SshKeyManagementViewModel` | CRUD cles SSH (titre, cle privee, cle publique, type, empreinte) |
| Audit | `SecurityAuditScreen` | `SecurityAuditViewModel` | Page en sections thematiques : **Vue d'ensemble** (cartes Score /20, A corriger, Forts), **A risque** (callouts repliables Faibles, Reutilises, Anciens, Compromis HIBP — bouton "Verifier" qui lance l'analyse et deplie), **Points forts** (liste forts + % uniques), **Composition** (categories, favoris), **Completude** (sans URL, sans email), **Activite** (ajoutes/modifies 30 j, plus ancien). `runAudit()` fait une seule passe de force (effacement securise des clones) et calcule toutes les stats |
| Conflits | `ConflictResolutionScreen` | — | Resolution de conflits sync (vue cote-a-cote local/distant, tous types VaultItem) |

#### Composants reutilisables (`ui/components/`)

| Composant | Role |
|-----------|------|
| `PasswordStrengthBar` | Barre animee de force (rouge/orange/vert/bleu) |
| `BentoDashboard` | Rangee de statistiques en haut de la page mots de passe : **Entrees** (total), **Favoris** (nombre de favoris) et **Securite** (note sur 20, derivee de la force moyenne). Calcule sur **tout le coffre** (liste complete `allEntries`, pas la vue filtree), donc le score est identique a celui de l'ecran Audit. Un appui sur une carte affiche une bulle d'explication (refermee au tap exterieur ou apres 5 s) |
| `VaultPageSelector` | Menu deroulant de la TopAppBar pour basculer entre les pages **Mots de passe** et **Applications** (pilote le `HorizontalPager`) ; remplace l'ancienne `TabRow` |
| `PasswordField` | OutlinedTextField avec toggle visibilite |
| `EntryCard` | Carte mot de passe pour la liste (favicon ou avatar lettre, titre, username ou URL en sous-titre, categorie, etoile favori, barre de force). Support selection multiple (checkbox, long press). Swipe gauche = supprimer, swipe droit = copier mot de passe |
| `AppEntryCard` | Carte application pour la liste (avatar lettre, titre, username en sous-titre, etoile favori). Support selection multiple. Swipe gauche = supprimer, swipe droit = copier pin |
| `ConfirmDialog` | AlertDialog de confirmation reutilisable |
| `ImportExportDialog` | Popups import/export unifiees (CSV/JSON/chiffre) avec champ mot de passe masquable (toggle Afficher) |

#### Navigation

Navigation a deux niveaux definie dans `AppNavigation.kt` :

**Onglets (BottomNavBar)** : `TAB_VAULT` (avec `VaultTabHost` / `HorizontalPager` 2 pages, selectionnees via le menu deroulant `VaultPageSelector`), `TAB_GENERATOR`, `TAB_AUDIT`, `TAB_SETTINGS`, plus une action **Quitter** (icone porte de sortie) qui n'est pas une destination : elle appelle `SessionHolder.lock()` (deconnexion → retour au login). Le verrouillage n'est donc plus dans le menu overflow.

**Routes modales** : `ENTRY_DETAIL(entryId)`, `ENTRY_EDIT(entryId?)`, `APP_DETAIL(entryId)`, `APP_EDIT(entryId?)`, `GENERATOR(returnPassword)`, `CHANGE_MASTER_PASSWORD`, `CATEGORY_MANAGEMENT`, `SSH_KEY_MANAGEMENT`.

Le mot de passe genere est passe de `GeneratorScreen` a `EntryEditScreen` via `GeneratedPasswordHolder` (singleton thread-safe avec `set()`/`consume()`, transfert securise en `char[]` sans persistence dans `savedStateHandle`).

#### Auto-lock (Android)

- `ProcessLifecycleOwner` detecte `ON_STOP` (app en arriere-plan)
- Countdown configurable -> `SessionHolder.lock()` -> retour a l'ecran de connexion via `isUnlockedFlow` (StateFlow)

#### Injection de dependances (Hilt)

Le module `AppModule` (`@Module @InstallIn(SingletonComponent)`) fournit :

| Provider | Type | Description |
|----------|------|-------------|
| `provideIoDispatcher` | `CoroutineDispatcher` | `Dispatchers.IO` |
| `provideWorkspaceManager` | `@Singleton WorkspaceManager` | Retourne `AndroidWorkspaceManager(context, configRepository)` (resout le `VaultStore` du dossier de travail courant) |
| `provideVaultRepository` | `@Singleton AndroidVaultRepository` | Cree avec le `VaultStore` du workspace courant : `AndroidVaultRepository(workspaceManager.currentStore())` |
| `provideConfigRepository` | `@Singleton ConfigRepository` | Retourne `AndroidConfigRepository` (implementation de l'interface `ConfigRepository`) |
| `provideSessionHolder` | `@Singleton SessionHolder` | Appelle `SessionHolder.init(repository)` puis retourne l'objet singleton |
| `provideBiometricHelper` | `@Singleton BiometricHelper` | Chiffrement biometrique via AndroidKeyStore |
| `provideSshHostKeyStore` | `@Singleton SshHostKeyStore` | Magasin d'epinglage des cles d'hote SFTP |
| `provideFaviconService` / `provideFaviconRepository` | `@Singleton` | Recuperation des favicons (cache disque `:core` + cache memoire reactif) |

L'interface `ConfigRepository` abstrait les operations de configuration (theme, langue, auto-lock, clipboard, favicons, dossier de travail) pour permettre l'injection d'un `FakeConfigRepository` dans les tests.

#### Couche data

| Classe | Role |
|--------|------|
| `AndroidVaultRepository` | Encapsule `VaultManager(store)` ou `store` est un `VaultStore` ; le store est interchangeable a l'execution via `useStore(...)` (hors session) pour changer de dossier de travail |
| `WorkspaceManager` / `AndroidWorkspaceManager` | Resout et persiste le dossier de travail actif (specs opaques : stockage interne, dossier File, futur `saf:<treeUri>`) ; expose `currentStore(): VaultStore` et cloisonne le nommage des cles biometriques par workspace (`biometricAccount`) |
| `SafVaultStore` | `VaultStore` adosse a un arbre de documents SAF (`content://` via `DocumentFile`/`ContentResolver`) : pas de `pathOf`, pas de permissions POSIX, `.bak` comme filet de securite |
| `AndroidSftpRepository` | Client SFTP (JSch) implementant l'interface `:core` `RemoteSyncRepository` ; centralise connexion/transfert et verification host-key (`fromConfig(...)`) |
| `ConfigRepository` | Interface de configuration (theme, langue, auto-lock, clipboard, SFTP, favicons, dossier de travail) |
| `AndroidConfigRepository` | Implementation via `EncryptedSharedPreferences` (MasterKey AES256-GCM). Inclut les parametres SFTP (host, port, user, key path, remote path, storage mode), le dossier de travail (`vault_workspace`) et l'activation des favicons (`favicons_enabled`) |
| `FaviconCache` | Cache favicon en memoire, reactif (`StateFlow`), `@Singleton`, partage entre la liste et `EntryEditViewModel` |
| `SessionHolder` | Singleton thread-safe : tient `Vault`, `VaultSession`, `VaultService` (facade), `username` en memoire. Expose `vaultService` (facade avec acces a `passwordService`, `appService`). Champs `@Volatile`, methodes `@Synchronized`. `isUnlockedFlow: StateFlow<Boolean>` |
| `SshHostKeyStore` | Magasin d'epinglage des cles d'hote SFTP en zone privee (`filesDir`). Empreinte SHA-256 (format OpenSSH), verdicts `Match`/`Unknown`/`Changed`. Pur (testable JVM) |
| `SftpHostKeyVerifier` | Connexion JSch en `StrictHostKeyChecking=yes` adossee a `SshHostKeyStore` : leve `UnknownHostKeyException`/`HostKeyChangedException` pour confirmation explicite de l'empreinte au 1er contact (C2) |

#### Localisation Android

Les 232 cles de traduction sont dans les fichiers de ressources Android :
- `res/values/strings.xml` (anglais, langue par defaut)
- `res/values-fr/strings.xml` (francais)

La langue suit les parametres systeme du telephone.

---

## 11. Systeme de mise a jour

### 11.1. Architecture

Le systeme de verification des mises a jour repose sur trois couches :

```
:core/update/
  |-- UpdateChecker      # Client HTTP pour l'API GitHub Releases
  |-- UpdateInfo         # Modele de donnees (version, URL, assets)
  +-- VersionComparator  # Comparaison semantique de versions

:desktop/update/
  +-- DesktopUpdateManager  # SwingWorker + javax.swing.Timer

:android/update/
  +-- AndroidUpdateManager  # Coroutines (Dispatchers.IO)
```

### 11.2. UpdateChecker (core)

- Interroge `https://api.github.com/repos/{owner}/{repo}/releases/latest`
- Parse la reponse JSON avec Gson
- Compare la version distante a `AppVersion.get()` via `VersionComparator`
- Retourne `UpdateInfo` si une version plus recente est disponible, `null` sinon
- Configuration via `update.properties` (owner, repo, intervalle, activation)

**Mesures de securite :**
- Limite de taille de reponse : 1 Mo (`MAX_RESPONSE_SIZE`)
- Validation des URLs : seul le domaine `https://github.com/` est accepte pour `releaseUrl` et les URLs de telechargement des assets
- Timeouts : connexion et lecture 10 secondes

### 11.3. VersionComparator (core)

- Compare des versions semantiques `major.minor.patch`
- Gere le prefixe `v` / `V` (ex: `v2.1.0` -> `2.1.0`)
- Gere les suffixes pre-release (ex: `2.0.0-rc1` -> version de base `2.0.0`)
- `isPreRelease(String)` : detecte les versions contenant un suffixe `-`

### 11.4. DesktopUpdateManager

- Cree une barre de notification jaune (panel NORTH de MainFrame)
- Verification au lancement + toutes les 5 minutes (`javax.swing.Timer`)
- Verification manuelle via le bouton sur `LoginFrame`
- `SwingWorker` pour les appels reseau (hors EDT)
- Le bouton "Telecharger" ouvre la page de release dans le navigateur (apres validation de l'URL)
- Barre masquable (bouton X)

### 11.5. AndroidUpdateManager

- Verification automatique a l'ouverture de l'ecran de connexion via `LaunchedEffect` + coroutine `Dispatchers.IO`
- `AlertDialog` Material 3 affiche automatiquement a la detection, avec boutons "Telecharger" / "Plus tard"
- Icone de mise a jour (`Autorenew`) en haut a droite de l'ecran de connexion tant qu'une version est disponible ; un appui rouvre le dialog
- "Telecharger" ouvre le navigateur via `Intent.ACTION_VIEW` (apres validation de l'URL)

### 11.6. Configuration (`update.properties`)

```properties
update.github.owner=Loken75
update.github.repo=password-manager
update.check.interval.minutes=5
update.enabled=true
```

---

## 12. Tests

### 12.1. Vue d'ensemble

**Framework** : JUnit 5 (Jupiter) 5.14.2
**Total** : **~529 tests** (unitaires + integration) repartis sur les 3 modules

| Module Gradle | Tests | Framework |
|---|---|---|
| `:core` | 371 | JUnit 5 (Java) |
| `:desktop` | 52 | JUnit 5 (Java, dont tests d'integration SFTP reels) |
| `:android` | 106 | JUnit 5 (Kotlin, JVM local) |

> Note : les tests d'orchestration de sync (`SyncServiceTest`, `LocalRepositoryTest`) ont migre de `:desktop` vers `:core` avec le moteur (Lot E.1a). La matrice detaillee ci-dessous est **indicative** et peut etre legerement en retard sur les compteurs reels par classe.

### 12.2. Matrice des tests

| Module | Classe de test | Nombre | Description |
|--------|---------------|--------|-------------|
| vault | `VaultServiceTest` | 33 | CRUD, recherche, tri, favoris, filtre, doublons, categories, timestamps, mots de passe anciens |
| vault | `VaultImporterTest` | 33 | CSV (separateurs, alias, BOM, sanitisation, favoris, RFC 4180, round-trip multi-type), JSON (malformed, null), limites |
| security | `SecurityAuditTest` | 31 | IV, KDF, memoire, permissions, format, import/export, generateur |
| sync (core) | `SyncServiceTest` | 33 | Synchronisation, hash a 3 voies, conflits, mode hors-ligne, mode local, syncAfterMerge |
| **android** | `LoginViewModelTest` | 27 | Etat initial, biometrie, selection utilisateur, validation, creation, nettoyage onCleared |
| vault | `VaultTest` | 24 | Constructeurs, add/remove entries (3 types), dedup par id, unmodifiable views, mutable accessors, wipe, ensureInitialized, settings |
| sync (core) | `LocalRepositoryTest` | 17 | Path traversal, lecture/ecriture/suppression, pending, backups |
| vault | `VaultManagerIntegrationTest` | 16 | Cycle complet avec vraie crypto, validation username, suppression backups |
| util | `PasswordValidatorTest` | 15 | Politique mot de passe maitre, rejet des 93 mots de passe courants |
| crypto | `CryptoServiceTest` | 14 | Enveloppe DEK/KEK, chiffrement, AAD, changement mdp, tampering (ciphertext/IV), legacy, destroy/close |
| update | `VersionComparatorTest` | 13 | Comparaison semantique, pre-release, null, egalite, snapshot |
| **android** | `SettingsViewModelTest` | 13 | Configuration initiale, setTheme, setLanguage, clamp autoLock, clamp clipboard, biometrie (toggle, activation, desactivation, dialog) |
| **desktop** | `ConfigEncryptorTest` | 11 | Round-trip, caracteres speciaux, null/vide, corruption, unicite IV, reutilisation cle |
| **android** | `GeneratorViewModelTest` | 11 | Etat initial, generation, clamp longueur, toggles, force, nettoyage onCleared |
| sync (core) | `EntryMergerTest` | 13 | Fusion generique, conflits exclus du merge (contrat R1), entrees identiques (tous types) |
| **desktop** | `SFTPRepositoryTest` | 10 | Validation filename sur upload/download/exists/getRemoteLastModified |
| crypto | `VaultSessionTest` | 10 | Destroy idempotent, AutoCloseable, copies defensives (salt/iv/dek), updateEnvelope |
| vault | `VaultExporterTest` | 9 | CSV, JSON, injection formules, favoris, round-trip, multi-type |
| crypto | `PasswordStrengthAnalyzerTest` | 9 | Niveaux de force, score, cas limites |
| **android** | `EntryEditViewModelTest` | 9 | Formulaire CRUD nouveau/existant, sauvegarde, validation titre vide, toggle favori |
| **android** | `ChangeMasterPasswordViewModelTest` | 9 | Etat initial, validation, mismatch, mot de passe faible, nettoyage onCleared, invalidation biometrique |
| vault | `SshKeyEntryTest` | 8 | Constructeur, copies defensives privateKey, wipe, equals/hashCode, tombstone |
| vault | `SshKeyServiceTest` | 10 | CRUD cles SSH, recherche (titre/type/empreinte), tri, favoris, operations en masse |
| vault | `AppServiceTest` | 7 | CRUD, recherche sur username, favoris, operations en masse |
| vault | `EntryFilterTest` | 7 | Filtres combines (categorie, force, date, favoris, texte) |
| crypto | `KeyDerivationTest` | 7 | Generation de cle, unicite du sel, iterations, SecureRandom partage |
| vault | `AppEntryTest` | 6 | Constructeur, copies defensives pin, wipe, equals/hashCode |
| **desktop** | `LanguageManagerTest` | 6 | Singleton, getString valide/manquant, setLanguage fr/en, getAvailableLanguages |
| **desktop** | `AutoLockManagerTest` | 5 | Idempotence start, cleanup, lifecycle, listener |
| util | `FaviconServiceTest` | 7 | Extraction domaine, cache disque, favicon null, validation image (anti-HTML) |
| security | `HibpCheckerTest` | 5 | Null, vide, entree valide, caracteres speciaux, unicode |
| **android** | `SecurityAuditViewModelTest` | 5 | Audit vide, mots de passe faibles, dupliques, anciens, totalIssues |
| **android** | `EntryDetailViewModelTest` | 5 | Chargement existant/inconnu, toggle visibilite, suppression existant/inexistant |
| util | `DateUtilsTest` | 5 | Format ISO 8601, round-trip, parsing valide/invalide/null |
| crypto | `PasswordGeneratorTest` | 5 | Longueur, types de caracteres, exclusion ambigus |
| **desktop** | `ConfigManagerTest` | 3 | Valeurs par defaut, persistance, auto-creation |
| vault | `VaultJsonCodecTest` | 8 | Codec JSON maison : round-trip, caracteres pieges, tombstone, retro-compat Gson, echec propre (R3) |
| vault.store | `FileVaultStoreTest` | 13 | Primitives `VaultStore` filesystem : list/exists/read/writeAtomic/copy/delete, rejet du path traversal, permissions |
| vault | `VaultStoreMigratorTest` | 3 | Migration des fichiers `vault_*` entre deux stores (copie-puis-suppression, sans ecrasement) |
| vault | `VaultManagerCorruptionTest` | 7 | Chargement de coffres corrompus/tronques : detection et echec propre |
| crypto/util | `SshHostKeyStoreTest` (android) | 10 | Empreinte SHA-256, parseKeyType, Match/Unknown/Changed, persistance, isolation hote:port (C2) |
| **android** | `AutofillDomainMatcherTest` | 6 | Matching autofill : exact, sous-domaine, rejet parent/look-alike (R10) |
| **android** | `PinningHostKeyRepositoryTest` | 4 | Verdicts JSch OK/NOT_INCLUDED/CHANGED (C2) |
| **android** | `ConflictApplyTest` | 4 | Application de fusion sans doublon d'id (R1) |
| **desktop** | `ConflictApplyTest` | 4 | Application de fusion sans doublon d'id (R1) |
| **desktop** | `SFTPRepositoryIntegrationTest` | 3 | SFTP reel (MINA) : upload/exists/download, host-key strict |
| **desktop** | `DesktopSyncFactoryTest` | 2 | Construction du SyncService depuis AppConfig (sans connexion) |
| **desktop** | `MasterPasswordSyncIntegrationTest` | 2 | Deux appareils : propagation du changement de mot de passe maitre (R4) |
| **desktop** | `SyncConflictIntegrationTest` | 1 | Conflit expose le VRAI distant pour la fusion (R-merge) |
| | | **~529** | |

### 12.3. Infrastructure de test Android

Les tests Android s'executent sur la JVM locale (pas d'emulateur) :

| Classe | Role |
|--------|------|
| `MainDispatcherExtension` | Extension JUnit 5 qui remplace `Dispatchers.Main` par `UnconfinedTestDispatcher` |
| `FakeConfigRepository` | Implementation de `ConfigRepository` pour les tests (valeurs en memoire) |
| `FakeBiometricHelper` | Test double de `BiometricHelper` (JVM, pas de KeyStore Android) |
| `FakeWorkspaceManager` | Test double de `WorkspaceManager` (store en memoire/`@TempDir`, pas de SAF) |
| `TestSessionHelper` | Cree un vault reel sur un `@TempDir` et deverrouille `SessionHolder` |

Tous les tests utilisant `SessionHolder` ont un `@AfterEach` qui appelle `SessionHolder.lock()` pour garantir l'isolation entre tests.

### 12.4. Tests de securite remarquables (`SecurityAuditTest`)

- **Unicite des IV** : 100 chiffrements successifs -> tous les IV distincts
- **KDF** : iterations >= 600 000, taille du sel = 32 octets
- **Effacement memoire** : verification que `SecureWiper.wipe()` met a zero les tableaux
- **Permissions fichiers** : aucune permission groupe/monde sur les fichiers de coffre (POSIX conditionnel)
- **Integrite GCM** : alteration d'un octet du ciphertext -> exception `VaultDecryptionException`
- **Format v2.0** : verification de la presence de tous les champs d'enveloppe, absence de texte clair
- **Assainissement import** : caracteres de controle supprimes, tabulations preservees
- **Anti-injection export** : tous les caracteres declencheurs (`=`, `+`, `-`, `@`) sont prefixes

### 12.5. Tests de validation des entrees

- **ConfigEncryptor** : round-trip chiffrement/dechiffrement, caracteres speciaux et Unicode, entrees null/vides, donnees corrompues (prefixe `ENC:` invalide), unicite des IV, reutilisation du fichier de cle entre appels
- **LocalRepository** : rejet des noms de fichiers malicieux (`..`, `/`, `\`, `~`, null, vide), operations CRUD, pending changes, creation et nettoyage de backups
- **SFTPRepository** : validation des noms de fichiers distants sur les 4 methodes publiques (upload, download, exists, getRemoteLastModified)
- **VaultManager** : rejet des noms d'utilisateur malicieux (null, vide, `../etc`, `user/admin`, `user@host`, espaces, points-virgules), suppression des backups lors de `deleteVault()`

### 12.6. Tests d'integration SFTP (serveur embarque)

Un serveur SFTP **in-process** (Apache MINA SSHD, scope test du module `:desktop`) permet de tester la synchronisation contre un vrai serveur, sans demon externe ni conteneur (rejouable en CI). Helper : `SftpTestServer` (port ephemere, cle d'hote exposee, cle client generee).

- `SFTPRepositoryIntegrationTest` : upload/exists/download reel via le `SFTPRepository` de production ; rejet d'un hote non approuve (`StrictHostKeyChecking=yes`).
- `SyncConflictIntegrationTest` : prouve qu'un conflit charge le **vrai distant** pour la fusion (R-merge).
- `MasterPasswordSyncIntegrationTest` : deux appareils ; un changement de mot de passe maitre est **preserve** (et non annule) grace a l'adoption d'enveloppe (R4).

---

## 13. Dependances

### 13.1. Core + Desktop

| Bibliotheque | GroupId | Version | Module | Usage |
|--------------|---------|---------|--------|-------|
| Gson | `com.google.code.gson` | 2.13.2 | :core | JSON de l'enveloppe + import JSON externe (le corps du coffre est (de)serialise par `VaultJsonCodec`, pas par Gson) |
| JSch (mwiede) | `com.github.mwiede` | 2.27.8 | :desktop, :android | Client SFTP (synchronisation) |
| FlatLaf | `com.formdev` | 3.7 | :desktop | Look & Feel Swing moderne |
| JUnit 5 | `org.junit.jupiter` | 5.14.2 | :core, :desktop | Tests (scope test) |
| Apache MINA SSHD | `org.apache.sshd:sshd-sftp` | 2.12.1 | :desktop (scope test) | Serveur SFTP embarque pour les tests d'integration (sans impact sur le fat JAR) |

### 13.2. Android

| Bibliotheque | Version | Usage |
|--------------|---------|-------|
| Compose BOM | 2024.12.01 | Versions Compose alignees |
| Material 3 | (via BOM) | Composants UI Material You |
| Hilt Android | 2.54 | Injection de dependances |
| Hilt Navigation Compose | 1.2.0 | `hiltViewModel()` dans les ecrans Compose |
| KSP (Kotlin Symbol Processing) | 2.1.0-1.0.29 | Traitement d'annotations Hilt |
| Navigation Compose | 2.8.5 | Routage entre ecrans |
| Lifecycle ViewModel Compose | 2.8.7 | ViewModel + collectAsStateWithLifecycle |
| Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |
| Biometric | 1.1.0 | BiometricPrompt, BiometricManager |
| Coroutines Android | 1.9.0 | Concurrence Kotlin |
| Desugar JDK Libs | 2.1.4 | Backport List.of() etc. |
| JUnit 5 | 5.11.4 | Tests unitaires Android (JVM local) |
| Coroutines Test | 1.9.0 | `UnconfinedTestDispatcher` pour les tests |
| kotlin-test-junit5 | 2.1.0 | Assertions Kotlin + JUnit 5 |

### 13.3. Build

| Outil | Version | Role |
|-------|---------|------|
| Gradle (wrapper) | 8.12 | Build multi-module |
| AGP | 8.7.3 | Build Android |
| Kotlin plugin | 2.1.0 | Compilation Kotlin |
| Compose Compiler plugin | 2.1.0 | Compilation Compose |
| KSP plugin | 2.1.0-1.0.29 | Traitement d'annotations (Hilt) |
| Hilt Gradle plugin | 2.54 | Generation de composants Hilt |
| Shadow/fatJar | custom task | Fat JAR desktop |

---

## 14. Arbre des fichiers sources

```
password-manager/
|-- build.gradle.kts                        # Configuration racine (Java, JUnit, subprojects)
|-- settings.gradle.kts                     # include(":core", ":desktop", ":android")
|-- gradle.properties                       # android.useAndroidX, jvmargs
|-- README.md
|-- .github/
|   |-- dependabot.yml                      # MAJ hebdo deps Gradle + actions (Lot A)
|   +-- workflows/                          # ci.yml (tests + JaCoCo + lint), release.yml
|-- docs/
|   |-- TECHNICAL.md                        # Ce document
|   |-- FUNCTIONAL.md                       # Documentation fonctionnelle
|   +-- CI-RELEASE.md                       # Documentation workflow CI/CD
|-- scripts/
|   |-- run.sh                              # Lanceur Linux/macOS
|   +-- run.bat                             # Lanceur Windows
|
|-- core/                                   # :core — logique metier (Java 17)
|   |-- build.gradle.kts
|   +-- src/
|       |-- main/java/com/passwordmanager/
|       |   |-- config/                     # AppConfig, AppVersion, StorageMode, ThemeMode (modeles uniquement)
|       |   |-- crypto/                     # CryptoService, EncryptionService, KeyDerivation, VaultSession,
|       |   |                               # PasswordGenerator, PasswordStrengthAnalyzer, EncryptedPayload
|       |   |-- security/                   # HibpChecker (k-Anonymity HIBP breach detection)
|       |   |-- sync/                       # EntryMerger, SyncService (orchestration), LocalRepository,
|       |   |                               # LocalSyncRepository, RemoteSyncRepository, ConflictStrategy
|       |   |-- update/                     # UpdateChecker, UpdateInfo, VersionComparator
|       |   |-- util/                       # SecureWiper, FileSecurityUtils, PasswordValidator, DateUtils,
|       |   |                               # FaviconService (favicons via /favicon.ico du site, cache disque)
|       |   |-- vault/                      # Vault, VaultItem (abstract), PasswordEntry, AppEntry, SshKeyEntry,
|       |   |                               # VaultManager, VaultStoreMigrator, VaultService (facade),
|       |   |                               # BaseVaultService<T>, PasswordService, AppService,
|       |   |                               # VaultImporter, VaultExporter, VaultLoadResult, SortField,
|       |   |                               # EntryFilter (filtres combines builder pattern),
|       |   |                               # VaultJsonCodec + JsonCharWriter/JsonCharReader (codec JSON, secrets en char[], R3)
|       |   +-- vault/store/                 # VaultStore (abstraction stockage), FileVaultStore (java.nio.file, ecriture atomique)
|       |-- main/resources/update.properties # Configuration du systeme de mise a jour
|       +-- test/java/com/passwordmanager/  # 371 tests (dont SyncServiceTest/LocalRepositoryTest migres, vault.store)
|
|-- desktop/                                # :desktop — interface Swing (Java 17)
|   |-- build.gradle.kts
|   +-- src/
|       |-- main/java/com/passwordmanager/
|       |   |-- Main.java                   # Point d'entree, detection app.home
|       |   |-- config/                     # ConfigManager, ConfigEncryptor (persistance config.properties)
|       |   |-- i18n/                       # LanguageManager
|       |   |-- sync/                       # SFTPRepository (client JSch), DesktopSyncFactory
|       |   |-- ui/                         # LoginFrame, MainFrame (sidebar + JMenuBar + CardLayout),
|       |   |                               # CoffrePasswordsPanel, CoffreAppsPanel, CoffreSshPanel, CoffreSettingsPanel,
|       |   |                               # EntryDialog, AppEntryDialog, SshKeyEntryDialog, PasswordGeneratorDialog,
|       |   |                               # SecurityAuditController, ConflictResolutionDialog (generique),
|       |   |                               # ui/components (EntryCardPanel, BentoCard, ControlIcon, Buttons, ...), ui/theme (DesignTokens),
|       |   |                               # SecureClipboard, AutoLockManager, ImportExportController
|       |   +-- update/                     # DesktopUpdateManager
|       |-- main/resources/i18n/            # messages_en.properties, messages_fr.properties
|       |-- main/resources/icons/           # icon.png
|       +-- test/java/com/passwordmanager/  # 49 tests (dont integration SFTP reelle + helper SftpTestServer)
|
+-- android/                                # :android — interface Compose (Kotlin 2.1)
    |-- build.gradle.kts                    # AGP 8.7.3, Compose BOM 2024.12, Hilt 2.54, minSdk 26
    |-- proguard-rules.pro                 # Regles R8 : Tink, Hilt/Dagger, javax.inject
    +-- src/
        |-- main/
        |   |-- AndroidManifest.xml
        |   |-- res/
        |   |   |-- values/strings.xml      # 232 cles EN
        |   |   |-- values-fr/strings.xml   # 232 cles FR
        |   |   |-- values/themes.xml
        |   |   +-- xml/autofill_service_config.xml  # Configuration Autofill Service
        |   +-- kotlin/com/passwordmanager/android/
        |       |-- PasswordManagerApp.kt   # @HiltAndroidApp
        |       |-- MainActivity.kt         # @AndroidEntryPoint, AppCompatActivity, locale init, ACTION_SCREEN_OFF lock
        |       |-- autofill/              # PasswordManagerAutofillService (Android Autofill API 26+)
        |       |-- data/                   # AndroidVaultRepository, AndroidConfigRepository,
        |       |                           # ConfigRepository (interface), SessionHolder (@Volatile/@Synchronized),
        |       |                           # WorkspaceManager/AndroidWorkspaceManager + SafVaultStore (dossier de travail, SAF),
        |       |                           # AndroidSftpRepository (RemoteSyncRepository :core),
        |       |                           # FaviconRepository/FaviconCache, BiometricHelper (AndroidKeyStore),
        |       |                           # SshHostKeyStore + SftpHostKeyVerifier (epinglage host-key SFTP, C2)
        |       |-- di/                     # AppModule (@Module @InstallIn @Provides @Singleton)
        |       |-- update/                # AndroidUpdateManager
        |       +-- ui/
        |           |-- theme/              # Theme.kt, Color.kt, Type.kt
        |           |-- navigation/         # AppNavigation.kt (VaultTabHost, BottomNavTab, Routes)
        |           |-- login/              # LoginScreen, @HiltViewModel LoginViewModel
        |           |-- vault/              # VaultListScreen/VM (VaultTabHost, HorizontalPager, multi-select,
        |           |                       # SFTP sync, filtres), EntryDetailScreen/VM, EntryEditScreen/VM,
        |           |                       # AppListScreen/VM, AppDetailScreen/VM, AppEditScreen/VM
        |           |-- generator/          # GeneratorScreen, @HiltViewModel GeneratorViewModel
        |           |-- settings/           # SettingsScreen/VM, ChangeMasterPasswordScreen/VM,
        |           |                       # CategoryManagementScreen/VM, SshKeyManagementScreen/VM
        |           |-- audit/              # SecurityAuditScreen, @HiltViewModel SecurityAuditViewModel (HIBP)
        |           |-- sync/              # ConflictResolutionScreen (resolution bidirectionnelle, tous types VaultItem)
        |           +-- components/         # PasswordStrengthBar, PasswordField, EntryCard, AppEntryCard,
        |                                   # ConfirmDialog, ImportExportDialog
        +-- test/kotlin/com/passwordmanager/android/  # 106 tests (ViewModels + data : SshHostKeyStore, AutofillDomainMatcher, ... + helpers)
            |-- test/                       # MainDispatcherExtension, FakeConfigRepository,
            |                               # FakeBiometricHelper, FakeWorkspaceManager, TestSessionHelper
            +-- ui/                         # LoginVM, GeneratorVM, EntryDetailVM, EntryEditVM,
                                            # SettingsVM, ChangeMasterPasswordVM, SecurityAuditVM
```
