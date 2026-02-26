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
- Import/export CSV et JSON avec protection contre l'injection de formules
- Synchronisation SFTP avec gestion des conflits et mode hors-ligne (desktop + Android)
- Interface bilingue francais/anglais
- Themes systeme, clair et sombre
- Injection de dependances Hilt sur le module Android
- 264 tests unitaires et d'integration (core, desktop et Android)

---

## 2. Prerequis et compilation

| Composant | Version requise | Necessaire pour |
|-----------|-----------------|-----------------|
| Java (JDK) | 21 ou superieur | Desktop (build + jlink) |
| Android SDK | API 35 | Android |
| Gradle | 8.11+ (wrapper inclus) | Tous |

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
  |-- vault/      Vault, VaultEntry, VaultManager, VaultService, VaultImporter, VaultExporter
  |-- config/     AppConfig, ConfigManager, ConfigEncryptor
  |-- sync/       SyncService, LocalRepository, SFTPRepository
  |-- update/     UpdateChecker, UpdateInfo, VersionComparator
  |-- util/       SecureWiper, FileSecurityUtils, PasswordValidator
  +-- i18n/       LanguageManager (FR/EN)

:desktop (Java 17, depends on :core)
  |-- ui/         LoginFrame, MainFrame, VaultPanel, EntryDialog, SettingsDialog, ...
  +-- update/     DesktopUpdateManager

:android (Kotlin 2.1, depends on :core, Hilt DI)
  |-- data/       AndroidVaultRepository, AndroidConfigRepository, ConfigRepository, SessionHolder
  |-- di/         AppModule (Hilt @Provides @Singleton)
  |-- ui/         Compose screens + @HiltViewModel (login, vault, generator, settings, audit)
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
              |-- VaultService (vault) ---- Vault ---- VaultEntry[]
              |-- SyncService (sync)
              |     |-- LocalRepository
              |     +-- SFTPRepository
              |-- DesktopUpdateManager (update)
              |     +-- UpdateChecker (core/update)
              +-- VaultPanel (ui)
                    |-- EntryDialog
                    |     +-- PasswordGeneratorDialog
                    |-- SettingsDialog
                    +-- StrengthBarHelper ---- PasswordStrengthAnalyzer (crypto)
```

### 3.3. Diagramme Android

```
@HiltAndroidApp
PasswordManagerApp (Application)

AppModule (@Module @InstallIn(SingletonComponent))
  +-- @Provides @Singleton AndroidVaultRepository (wraps VaultManager)
  +-- @Provides @Singleton ConfigRepository (-> AndroidConfigRepository)
  +-- @Provides @Singleton SessionHolder (init + return)

@AndroidEntryPoint
MainActivity (extends AppCompatActivity, single Activity)
  +-- @Inject ConfigRepository, SessionHolder
  +-- Applique la locale sauvegardee au demarrage via AppCompatDelegate.setApplicationLocales()
  +-- AppNavigation (NavHost)
        |-- LoginScreen / @HiltViewModel LoginViewModel
        |     +-- @Inject AndroidVaultRepository, SessionHolder
        |-- VaultListScreen / @HiltViewModel VaultListViewModel
        |     +-- @Inject SessionHolder, @ApplicationContext
        |-- EntryDetailScreen / @HiltViewModel EntryDetailViewModel
        |     +-- @Inject SessionHolder
        |-- EntryEditScreen / @HiltViewModel EntryEditViewModel
        |     +-- @Inject SessionHolder (onCleared efface le password)
        |-- GeneratorScreen / @HiltViewModel GeneratorViewModel
        |     +-- PasswordGenerator.generate()
        |-- SettingsScreen / @HiltViewModel SettingsViewModel
        |     +-- @Inject ConfigRepository
        |-- CategoryManagementScreen / @HiltViewModel CategoryManagementViewModel
        |     +-- @Inject SessionHolder (addCategory, removeCategory with cascade)
        |-- ChangeMasterPasswordScreen / @HiltViewModel ChangeMasterPasswordViewModel
        |     +-- @Inject SessionHolder (onCleared efface les passwords)
        +-- SecurityAuditScreen / @HiltViewModel SecurityAuditViewModel
              +-- @Inject SessionHolder
```

### 3.4. Principes architecturaux

- **Separation des responsabilites** : le `:core` ne contient aucune dependance UI. Les packages `crypto`, `vault`, `sync`, `config`, `util` et `i18n` sont decouples.
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
EncryptedPayload encryptData(byte[] plaintext, SecretKey dataKey)
byte[] decryptData(byte[] iv, byte[] ciphertext, SecretKey dataKey)
VaultSession changePassword(VaultSession session, char[] newPassword)
byte[] decryptLegacy(byte[] salt, byte[] iv, byte[] ciphertext, char[] masterPassword)
```

#### `CryptoService` (implementation)

Implemente `EncryptionService` avec AES-256-GCM. Points cles :
- `createSession()` : genere une DEK aleatoire, derive la KEK, chiffre la DEK avec la KEK, efface `rawDek` et detruit la KEK dans le bloc `finally`.
- `openSession()` : derive la KEK, dechiffre la DEK, retourne une `VaultSession`. Efface la KEK dans le `finally`.
- `changePassword()` : genere un nouveau sel + KEK, re-chiffre la DEK existante, met a jour l'enveloppe dans la session. Les octets bruts de la DEK (`rawDek`) sont effaces dans le bloc `finally`, et l'ancienne KEK est detruite.
- `decryptLegacy()` : supporte les coffres v1.0 ou le mot de passe derivait directement la cle de donnees (100 000 iterations).

#### `KeyDerivation`

Utilitaire statique pour PBKDF2-HMAC-SHA256.

```java
public static SecretKey deriveKey(char[] password, byte[] salt, int iterations)
public static SecretKey deriveKey(char[] password, byte[] salt) // 600 000 iterations
public static byte[] generateSalt()                             // 32 octets SecureRandom
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
    public void destroy()            // efface toutes les donnees sensibles
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
- Caracteres ambigus exclus : `0OoIl1`
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
| `Vault` | Modele de donnees du coffre (version, utilisateur, entrees, categories, parametres). Constructeur prive no-arg pour la deserialisation Gson (les JRE jlink n'incluent pas `jdk.unsupported` / `sun.misc.Unsafe`) |
| `VaultEntry` | Entree individuelle (titre, identifiant, email, pseudo, mot de passe `char[]`, URL, notes, categorie, tags, dates) |
| `VaultManager` | Persistance : creation, chargement, sauvegarde, migration v1->v2, backup, import/export |
| `VaultService` | Operations metier : CRUD, recherche, tri, filtrage, detection de doublons/anciens mots de passe, operations en masse, gestion des categories. Methodes de mutation `synchronized` |
| `VaultExporter` | Export CSV/JSON en `char[]` avec protection anti-injection de formules |
| `VaultImporter` | Import CSV/JSON avec detection automatique du separateur et alias multilingues |
| `VaultLoadResult` | Objet de valeur : `Vault` + `VaultSession` |
| `SortField` | Enum : `TITLE`, `USERNAME`, `EMAIL`, `PSEUDO`, `URL`, `DATE`, `CATEGORY` |

**VaultManager — details :**
- Format de fichier v2.0 : enveloppe JSON contenant `version`, `kdf`, `kdf_iterations`, `salt`, `kek_iv`, `encrypted_dek`, `data_iv`, `encrypted_data`
- Migration automatique v1.0 -> v2.0 au chargement
- Ecriture atomique : fichier temporaire -> permissions restrictives sur le temporaire -> `Files.move(ATOMIC_MOVE)` avec fallback
- Backup roulant (3 fichiers `.bak` max par utilisateur)
- `loadVault()` verifie la taille du fichier avant lecture (limite 50 Mo) et valide la presence de tous les champs JSON obligatoires (via `requireJsonField()`) avant decodage
- `reloadVault()` et `importEncryptedVault()` appliquent la meme validation des champs JSON
- `importEncryptedVault(char[] sourcePassword, String encFilePath)` : dechiffre un coffre externe et retourne les entrees sans ecraser le coffre courant
- `deleteVault()` supprime aussi tous les fichiers `.bak` de l'utilisateur
- Validation du nom d'utilisateur : regex `[a-zA-Z0-9_]+`
- `CharArrayAdapter` interne : `TypeAdapter<char[]>` Gson pour serialiser les mots de passe comme chaines JSON
- Gson configure avec `setPrettyPrinting()`
- **Compatibilite jlink** : `Vault` et `VaultEntry` disposent de constructeurs no-arg pour que Gson puisse les instancier sans `sun.misc.Unsafe` (absent du module `jdk.unsupported`, non inclus dans le JRE jlink)

**VaultEntry — details :**
- Champs : `title`, `username` (identifiant), `email`, `pseudo`, `password` (char[]), `url`, `notes`, `category`, `tags` (List\<String\>), `createdAt`, `updatedAt`
- `getPassword()` retourne un clone (copie defensive)
- `setPassword()` efface l'ancien mot de passe via `SecureWiper.wipe()` avant d'affecter le nouveau clone
- `getTags()` retourne une vue non-modifiable (`Collections.unmodifiableList`)
- `wipe()` efface le mot de passe, email, pseudo et nullifie les champs sensibles

**VaultService — details :**
- Recherche insensible a la casse sur titre, identifiant, email, pseudo, URL, notes, categorie et tags
- Tri : `TITLE`, `USERNAME`, `EMAIL`, `PSEUDO`, `URL` (alphabetique croissant, insensible a la casse), `DATE` (plus recent en premier), `CATEGORY` (alphabetique croissant, insensible a la casse)
- `findDuplicatePasswords()` : utilise des hashes SHA-256 des mots de passe comme cles (evite de stocker le clair comme cle de Map). Chaque clone `getPassword()` est efface dans un bloc `finally`
- `findOldPasswords(int days)` : compare les timestamps `updatedAt` au seuil configure
- `bulkDelete(List<String> entryIds)` : suppression en masse avec effacement securise de chaque entree
- `bulkChangeCategory(List<String> entryIds, String newCategory)` : reassignation de categorie en masse
- `addCategory(String)` / `removeCategory(String)` : gestion des categories du coffre
- **Thread safety** : toutes les methodes de mutation (`addEntry`, `updateEntry`, `deleteEntry`, `bulkDelete`, `bulkChangeCategory`, `addCategory`, `removeCategory`) sont `synchronized`

**VaultImporter — details :**
- Detection du separateur : frequence `,` vs `;` dans la premiere ligne
- Alias de colonnes FR/EN (insensible a la casse et aux accents) :
  - `title` <- titre, organisme, name, nom
  - `username` <- identifiant, login, adresse mail / identifiant
  - `email` <- email, mail, adresse mail, e-mail, courriel
  - `pseudo` <- pseudo, nickname, alias, surnom, display name
  - `password` <- mdp, mot de passe, pass
  - `url` <- site, website, lien
  - etc.
- Repli positionnel si aucun en-tete reconnu
- Assainissement : suppression des caracteres de controle, troncature a 10 000 caracteres
- Limite : 10 000 entrees par import
- Categorie par defaut si vide : "Autre"
- Tags separes par des points-virgules

**VaultExporter — details :**
- Protection anti-injection de formules CSV : prefixe `'` devant les champs commencant par `=`, `+`, `-`, `@`, `\t`, `\r`
- Export en `char[]` pour permettre l'effacement securise par l'appelant
- Effacement du StringBuilder intermediaire apres extraction en `char[]`

### 5.2. `com.passwordmanager.config`

| Classe | Role |
|--------|------|
| `AppConfig` | Modele de configuration avec setters validants (port 1-65535, auto-lock 1-60 min, etc.) |
| `ConfigManager` | Lecture/ecriture de `config.properties` avec chiffrement des champs sensibles |
| `ConfigEncryptor` | Chiffrement AES-256-GCM des identifiants SFTP (package-private) |
| `StorageMode` | Enum : `LOCAL`, `REMOTE` |
| `ThemeMode` | Enum : `SYSTEM`, `LIGHT`, `DARK` |

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
| `sftpRemotePath` | `sftp.remote_path` | `"/vault/data"` |
| `localVaultDirectory` | `local.vault_directory` | `{app.home}/data/vaults` |
| `autoLockMinutes` | `security.auto_lock_minutes` | `15` |
| `clipboardClearSeconds` | `security.clipboard_clear_seconds` | `30` |

`app.home` est la propriete systeme (defaut : `~/.password-manager`).

**ConfigManager — details :**
- Fichier de configuration : `{app.home}/data/config.properties`
- Les champs SFTP (`sftp.host`, `sftp.user`, `sftp.key_path`, `sftp.remote_path`) sont chiffres via `ConfigEncryptor` a l'ecriture et dechiffres a la lecture
- Ecriture atomique : fichier `.tmp` -> permissions -> renommage
- Permissions fichier via `FileSecurityUtils.setOwnerOnlyPermissions()`

### 5.3. `com.passwordmanager.sync`

| Classe | Role |
|--------|------|
| `SyncService` | Orchestration de la synchronisation (hash SHA-256, detection de conflit, mode hors-ligne). `hashFile()` propage les `IOException` (jamais de retour silencieux `""`) |
| `LocalRepository` | Gestion des fichiers locaux (ecritures atomiques, prevention du path traversal, backups) |
| `SFTPRepository` | Client SFTP via JSch (authentification par cle, `StrictHostKeyChecking=yes`, limite 50 Mo, validation des noms de fichiers distants contre le path traversal) |
| `ConflictResolver` | Enum : `KEEP_LOCAL`, `KEEP_REMOTE`, `KEEP_BOTH` |

**Flux de synchronisation :**

1. Verification du mode (LOCAL -> pas de sync)
2. Flush des modifications en attente (mode hors-ligne)
3. Si le fichier distant n'existe pas -> upload
4. Telechargement du fichier distant dans un fichier temporaire
5. Verification du fichier telecharge (non-vide, commence par `{`)
6. Comparaison des hashes SHA-256 (local vs distant)
7. Si identiques -> synchronise
8. Si differents : timestamp local >= distant -> upload ; sinon -> conflit
9. Nettoyage du fichier temporaire dans le bloc `finally`

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
| `PasswordValidator` | Validation du mot de passe maitre (12+ chars, 4 types, pas dans la liste des 44 mots de passe courants) |
| `DateUtils` | Formatage ISO-8601 UTC thread-safe via `java.time` |

**SecureWiper — technique :**
```java
private static volatile byte volatileByte;
private static volatile char volatileChar;

public static void wipe(byte[] data) {
    if (data == null || data.length == 0) return;
    Arrays.fill(data, (byte) 0);
    volatileByte = data[0]; // empeche le JIT d'eliminer le fill
}

public static void wipe(char[] data) {
    if (data == null || data.length == 0) return;
    Arrays.fill(data, '\0');
    volatileChar = data[0];
}
```

**FileSecurityUtils — comportement :**
- POSIX : fichiers -> `rw-------` (600), repertoires -> `rwx------` (700)
- Windows : ACL owner-only avec controle total (`EnumSet.allOf(AclEntryPermission.class)`) + preservation des entrees SYSTEM/SYSTEME. La securite repose sur la suppression de toutes les autres entrees ACL (non-owner, non-SYSTEM).
- Echec silencieux (log FINE) pour ne pas bloquer l'application sur les systemes sans support

**PasswordValidator — politique :**
- Minimum 12 caracteres
- Au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractere special
- Rejet des 44 mots de passe courants (comparaison sur la base alphabetique, ex: `Password123!` -> `password` -> rejete)
- Comparaison a temps constant (accumulation XOR) pour prevenir les canaux auxiliaires de timing
- Toutes les operations en `char[]` sans creation de `String` a partir de l'entree utilisateur

### 5.5. `com.passwordmanager.i18n`

| Classe | Role |
|--------|------|
| `LanguageManager` | Singleton thread-safe (`volatile bundle`), gestion FR/EN via `ResourceBundle` |

- 88 cles de traduction par langue
- Changement de langue dynamique via `setLanguage()` (desktop : reconstruit l'interface, Android : `AppCompatDelegate.setApplicationLocales()` + `locales_config.xml`)
- Retourne la cle elle-meme si la traduction n'est pas trouvee (pas d'exception)

### 5.6. `com.passwordmanager.ui`

| Classe | Role |
|--------|------|
| `LoginFrame` | Ecran de connexion, creation d'utilisateur, toggle visibilite mot de passe, changement de langue |
| `MainFrame` | Fenetre principale avec menus, barre d'outils, auto-lock, shutdown hook (retire au verrouillage) |
| `VaultPanel` | Panneau 3 colonnes : categories, table d'entrees, details avec boutons copier. Selection multiple (`MULTIPLE_INTERVAL_SELECTION`), barre d'actions en masse, menu contextuel (clic droit). Timers `javax.swing.Timer` (pas `java.util.Timer`) |
| `EntryDialog` | Formulaire modal de creation/edition d'entree |
| `PasswordGeneratorDialog` | Dialogue du generateur de mots de passe. Timer clipboard `javax.swing.Timer` annule a la fermeture |
| `ImportExportController` | Popup unifiee d'import/export (CSV, JSON, coffre chiffre .enc) avec champ mot de passe pour l'import chiffre |
| `SettingsDialog` | Dialogue des parametres (3 onglets : General, Securite, Synchronisation). Test SFTP sur `SwingWorker` (hors EDT) |
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
| Mots de passe en `char[]` | `VaultEntry.password`, `PasswordGenerator.generate()`, `VaultExporter` retournent `char[]` |
| Effacement securise | `SecureWiper.wipe()` avec barriere volatile anti-optimisation JIT |
| Copie defensive | `VaultEntry.getPassword()` retourne un clone, `setPassword()` efface l'ancien avant clone |
| Copie defensive session | `VaultSession.getSalt/getKekIv/getEncryptedDek()` retournent des clones |
| Nettoyage de session | `VaultSession.destroy()` efface DEK, sel, IV, DEK chiffree |
| Nettoyage Swing | Insertion via `Document.insertString()` au lieu de `JPasswordField.setText(String)` pour minimiser l'interning |
| Nettoyage ViewModel (Android) | `EntryEditViewModel.onCleared()` et `ChangeMasterPasswordViewModel.onCleared()` effacent les mots de passe de l'etat UI |
| Thread safety SessionHolder | Champs `@Volatile` (`vault`, `session`, `vaultService`, `username`) + `@Synchronized` sur `unlock()`, `lock()`, `save()` |
| Auto-masquage | Le mot de passe affiche se re-masque automatiquement apres 30 secondes |

### 7.2. Securite des fichiers

| Mesure | Implementation |
|--------|----------------|
| Permissions restrictives | POSIX 600 (fichiers) / 700 (repertoires) ; ACL owner-only sur Windows |
| Ecriture atomique | Fichier temporaire -> permissions restrictives -> `Files.move(ATOMIC_MOVE)` avec fallback |
| Backup roulant coffre | 3 fichiers `.bak` max par utilisateur (VaultManager) |
| Backup roulant sync | 5 fichiers horodates max (LocalRepository) |
| Suppression complete | `deleteVault()` supprime le coffre et tous les fichiers `.bak` associes |
| Validation des noms d'utilisateur | Regex `[a-zA-Z0-9_]+` pour prevenir le path traversal |
| Validation des noms de fichiers (local) | Rejet de `..`, `/`, `\`, `~` dans `LocalRepository` |
| Validation des noms de fichiers (distant) | Rejet de `..`, `/`, `\`, `~` dans `SFTPRepository` |

### 7.3. Protection anti brute-force

- Compteur statique par utilisateur (`Map<String, Integer>`), persiste entre les cycles verrouillage/deverrouillage
- Apres 3 tentatives echouees : desactivation du formulaire avec backoff `min(attempts * 2000, 30000)` ms
- Reinitialisation du compteur apres une connexion reussie

### 7.4. Validation des entrees

| Contexte | Validation |
|----------|-----------|
| Mot de passe maitre | 12+ chars, 4 types, pas dans la liste des 44 mots de passe courants (`PasswordValidator`) |
| Nom d'utilisateur | Regex `[a-zA-Z0-9_]+` |
| Import CSV/JSON | Assainissement des caracteres de controle, troncature 10 000 chars, limite 10 000 entrees |
| Export CSV | Echappement RFC 4180, prefixe anti-formule (`'`) |
| Fichier cle SSH | Validation d'existence et de lisibilite avant sauvegarde |
| Fichier coffre local | Limite de taille 50 Mo avant lecture (`VaultManager.loadVault()`) |
| Telechargement SFTP | Limite de taille 50 Mo, verification de contenu JSON valide (commence par `{`) |

### 7.5. Securite SFTP

- Authentification par cle SSH uniquement (pas de mot de passe)
- Desktop : `StrictHostKeyChecking=yes` (verification via `~/.ssh/known_hosts`)
- Android : `StrictHostKeyChecking=yes` si `known_hosts` existe, `accept-new` sinon (premiere connexion)
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
            |-- JSch (authentification par cle)
            |-- Comparaison SHA-256 local vs distant
            +-- Upload si difference (local = source de verite)
```

La synchronisation Android est integree directement dans `VaultListViewModel` car les classes `SyncService`/`SFTPRepository`/`LocalRepository` sont dans le module `:desktop`, non accessible depuis `:android`. Le mode Android est simplifie (pas de gestion hors-ligne ni de resolution de conflit interactive).

### 8.2. Algorithme de synchronisation

```
1. Si mode LOCAL -> retour immediat
2. Flush des modifications .pending (mode hors-ligne)
3. Si fichier distant absent -> upload du fichier local
4. Telechargement du fichier distant dans un temporaire
5. Verification : non-vide et commence par '{'
6. Calcul SHA-256 des deux fichiers
7. Si hashes identiques -> pas de changement
8. Si timestamp local >= distant -> upload (local gagne)
9. Sinon -> CONFLIT (intervention utilisateur)
10. finally: suppression du fichier temporaire, deconnexion SFTP
```

### 8.3. Resolution des conflits

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

`LanguageManager` est un singleton thread-safe utilisant `java.util.ResourceBundle` avec un champ `volatile` pour la visibilite inter-threads.

### 9.2. Ressources

| Fichier | Langue | Cles |
|---------|--------|------|
| `i18n/messages_fr.properties` | Francais | 88 |
| `i18n/messages_en.properties` | Anglais | 88 |

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
| `LoginFrame` | `JFrame` | 450x450 (non-redimensionnable) | Connexion, creation utilisateur, toggle mot de passe, selection de langue |
| `MainFrame` | `JFrame` | 1100x700 (min 900x600) | Menus, barre d'outils, panneau central, barre de statut |
| `EntryDialog` | `JDialog` (modal) | min 550x480 | Creation/edition d'entree avec barre de force |
| `PasswordGeneratorDialog` | `JDialog` (modal) | min 450x420 | Generateur avec options et barre de force |
| `SettingsDialog` | `JDialog` (modal) | min 500x450 | Parametres en 3 onglets |

#### Panneau 3 colonnes (VaultPanel)

| Colonne | Largeur | Contenu |
|---------|---------|---------|
| Gauche | 180 px | Liste des categories (JList) + bouton ajout |
| Centre | flexible | Barre de recherche + table 6 colonnes (Titre, Identifiant, Email, Pseudo, Categorie, Force) — en-tetes cliquables pour tri |
| Droite | 300 px | Details : titre, grille de champs avec separateurs, boutons copier identifiant/mot de passe, case a cocher afficher |

La colonne Force du tableau est coloree selon le niveau : rouge (Faible), orange (Moyen), vert (Fort), bleu (Tres fort).

#### Auto-lock (desktop)

- `Toolkit.addAWTEventListener` sur les evenements clavier et souris
- `javax.swing.Timer` verifie l'inactivite toutes les 30 secondes
- Depassement du seuil configurable -> verrouillage (sauvegarde -> nettoyage -> effacement des cles -> ecran de connexion)

#### Shutdown hook

Un `Runtime.addShutdownHook` efface le coffre (`vault.wipe()`) et detruit la session (`session.destroy()`) a la fermeture de la JVM. Le hook est retire proprement lors du verrouillage (`doLock()`) et lors d'un changement de langue (qui reconstruit la fenetre).

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
| Login | `LoginScreen` | `LoginViewModel` | Dropdown utilisateurs, creation, anti brute-force |
| Liste | `VaultListScreen` | `VaultListViewModel` | Recherche, dropdown categories, tri (7 criteres), import/export unifie (fusion), selection multiple (suppression + categorie en masse), sync SFTP |
| Detail | `EntryDetailScreen` | `EntryDetailViewModel` | Lecture seule avec email/pseudo, URL cliquable, copier, supprimer |
| Edition | `EntryEditScreen` | `EntryEditViewModel` | Formulaire CRUD (identifiant, email, pseudo), lien generateur |
| Generateur | `GeneratorScreen` | `GeneratorViewModel` | Options, barre de force, copier/utiliser |
| Parametres | `SettingsScreen` | `SettingsViewModel` | Theme, langue, auto-lock, clipboard, synchronisation SFTP, gestion des categories |
| Categories | `CategoryManagementScreen` | `CategoryManagementViewModel` | Ajout/suppression de categories avec validation et cascade |
| Mot de passe | `ChangeMasterPasswordScreen` | `ChangeMasterPasswordViewModel` | Ancien/nouveau/confirmer |
| Audit | `SecurityAuditScreen` | `SecurityAuditViewModel` | Faibles, dupliques, anciens |

#### Composants reutilisables (`ui/components/`)

| Composant | Role |
|-----------|------|
| `PasswordStrengthBar` | Barre animee de force (rouge/orange/vert/bleu) |
| `PasswordField` | OutlinedTextField avec toggle visibilite |
| `EntryCard` | Carte pour la liste (titre, username, categorie, point de force). Support selection multiple (checkbox, long press) |
| `ConfirmDialog` | AlertDialog de confirmation reutilisable |
| `ImportExportDialog` | Popups import/export unifiees (CSV/JSON/chiffre) avec champ mot de passe masquable (toggle Afficher) |

#### Navigation

9 routes definies dans `AppNavigation.kt` : `Login`, `VaultList`, `EntryDetail(entryId)`, `EntryEdit(entryId?)`, `Generator(returnPassword)`, `Settings`, `CategoryManagement`, `ChangeMasterPassword`, `SecurityAudit`.

Le mot de passe genere est passe de `GeneratorScreen` a `EntryEditScreen` via `savedStateHandle`.

#### Auto-lock (Android)

- `ProcessLifecycleOwner` detecte `ON_STOP` (app en arriere-plan)
- Countdown configurable -> `SessionHolder.lock()` -> retour a l'ecran de connexion via `isUnlockedFlow` (StateFlow)

#### Injection de dependances (Hilt)

Le module `AppModule` (`@Module @InstallIn(SingletonComponent)`) fournit :

| Provider | Type | Description |
|----------|------|-------------|
| `provideVaultRepository` | `@Singleton AndroidVaultRepository` | Cree avec `@ApplicationContext.filesDir + "/vaults"` |
| `provideConfigRepository` | `@Singleton ConfigRepository` | Retourne `AndroidConfigRepository` (implementation de l'interface `ConfigRepository`) |
| `provideSessionHolder` | `@Singleton SessionHolder` | Appelle `SessionHolder.init(repository)` puis retourne l'objet singleton |

L'interface `ConfigRepository` abstrait les operations de configuration (theme, langue, auto-lock, clipboard) pour permettre l'injection d'un `FakeConfigRepository` dans les tests.

#### Couche data

| Classe | Role |
|--------|------|
| `AndroidVaultRepository` | Encapsule `VaultManager(context.filesDir + "/vaults")` |
| `ConfigRepository` | Interface de configuration (theme, langue, auto-lock, clipboard, SFTP) |
| `AndroidConfigRepository` | Implementation via `EncryptedSharedPreferences` (MasterKey AES256-GCM). Inclut les parametres SFTP (host, port, user, key path, remote path, storage mode) |
| `SessionHolder` | Singleton thread-safe : tient `Vault`, `VaultSession`, `VaultService`, `username` en memoire. Champs `@Volatile`, methodes `@Synchronized`. `isUnlockedFlow: StateFlow<Boolean>` |

#### Localisation Android

Les 100+ cles de traduction sont dans les fichiers de ressources Android :
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

- Verification au lancement via `LaunchedEffect` + coroutine `Dispatchers.IO`
- `AlertDialog` Material 3 avec boutons "Telecharger" / "Plus tard"
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
**Total** : **264 tests** (unitaires + integration) repartis sur les 3 modules

| Module Gradle | Tests | Framework |
|---|---|---|
| `:core` | 152 | JUnit 5 (Java) |
| `:desktop` | 71 | JUnit 5 (Java) |
| `:android` | 41 | JUnit 5 (Kotlin, JVM local) |

### 12.2. Matrice des tests

| Module | Classe de test | Nombre | Description |
|--------|---------------|--------|-------------|
| security | `SecurityAuditTest` | 31 | IV, KDF, memoire, permissions, format, import/export, generateur |
| vault | `VaultServiceTest` | 25 | CRUD, recherche, tri, doublons, categories, timestamps, mots de passe anciens, URL |
| sync | `SyncServiceTest` | 23 | Synchronisation, hash, conflits, mode hors-ligne, mode local |
| sync | `LocalRepositoryTest` | 17 | Path traversal, lecture/ecriture/suppression, pending, backups |
| vault | `VaultImporterTest` | 17 | CSV (separateurs, alias, BOM, sanitisation controle), JSON (malformed, null), limites |
| vault | `VaultManagerIntegrationTest` | 16 | Cycle complet avec vraie crypto, validation username, suppression backups |
| util | `PasswordValidatorTest` | 15 | Politique mot de passe maitre, rejet des 44 mots de passe courants |
| crypto | `CryptoServiceTest` | 13 | Enveloppe DEK/KEK, chiffrement, changement mdp, tampering (ciphertext/IV), legacy, destroy/close |
| config | `ConfigEncryptorTest` | 11 | Round-trip, caracteres speciaux, null/vide, corruption, unicite IV, reutilisation cle |
| **android** | `GeneratorViewModelTest` | 11 | Etat initial, generation, clamp longueur, toggles, force, nettoyage onCleared |
| sync | `SFTPRepositoryTest` | 10 | Validation filename sur upload/download/exists/getRemoteLastModified |
| crypto | `PasswordStrengthAnalyzerTest` | 9 | Niveaux de force, score, cas limites |
| vault | `VaultTest` | 9 | Constructeurs, add/remove entries, unmodifiable list, wipe, settings |
| vault | `VaultExporterTest` | 8 | CSV, JSON, injection formules, round-trip |
| **android** | `EntryEditViewModelTest` | 8 | Formulaire CRUD nouveau/existant, sauvegarde, validation titre vide |
| **android** | `ChangeMasterPasswordViewModelTest` | 7 | Etat initial, validation, mismatch, mot de passe faible, nettoyage onCleared |
| i18n | `LanguageManagerTest` | 6 | Singleton, getString valide/manquant, setLanguage fr/en, getAvailableLanguages |
| **android** | `SecurityAuditViewModelTest` | 5 | Audit vide, mots de passe faibles, dupliques, anciens, totalIssues |
| **android** | `EntryDetailViewModelTest` | 5 | Chargement existant/inconnu, toggle visibilite, suppression existant/inexistant |
| **android** | `SettingsViewModelTest` | 5 | Configuration initiale, setTheme, setLanguage, clamp autoLock, clamp clipboard |
| util | `DateUtilsTest` | 5 | Format ISO 8601, round-trip, parsing valide/invalide/null |
| crypto | `PasswordGeneratorTest` | 5 | Longueur, types de caracteres, exclusion ambigus |
| config | `ConfigManagerTest` | 3 | Valeurs par defaut, persistance, auto-creation |
| | | **264** | |

### 12.3. Infrastructure de test Android

Les tests Android s'executent sur la JVM locale (pas d'emulateur) :

| Classe | Role |
|--------|------|
| `MainDispatcherExtension` | Extension JUnit 5 qui remplace `Dispatchers.Main` par `UnconfinedTestDispatcher` |
| `FakeConfigRepository` | Implementation de `ConfigRepository` pour les tests (valeurs en memoire) |
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

---

## 13. Dependances

### 13.1. Core + Desktop

| Bibliotheque | GroupId | Version | Module | Usage |
|--------------|---------|---------|--------|-------|
| Gson | `com.google.code.gson` | 2.13.2 | :core | Serialisation JSON des coffres |
| JSch (mwiede) | `com.github.mwiede` | 2.27.8 | :core | Client SFTP (synchronisation) |
| FlatLaf | `com.formdev` | 3.7 | :desktop | Look & Feel Swing moderne |
| JUnit 5 | `org.junit.jupiter` | 5.14.2 | :core, :desktop | Tests (scope test) |

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
| Coroutines Android | 1.9.0 | Concurrence Kotlin |
| Desugar JDK Libs | 2.1.4 | Backport List.of() etc. |
| JUnit 5 | 5.11.4 | Tests unitaires Android (JVM local) |
| Coroutines Test | 1.9.0 | `UnconfinedTestDispatcher` pour les tests |
| kotlin-test-junit5 | 2.1.0 | Assertions Kotlin + JUnit 5 |

### 13.3. Build

| Outil | Version | Role |
|-------|---------|------|
| Gradle (wrapper) | 8.11.1 | Build multi-module |
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
|       |   |-- config/                     # AppConfig, ConfigManager, ConfigEncryptor, StorageMode, ThemeMode
|       |   |-- crypto/                     # CryptoService, EncryptionService, KeyDerivation, VaultSession,
|       |   |                               # PasswordGenerator, PasswordStrengthAnalyzer, EncryptedPayload
|       |   |-- i18n/                       # LanguageManager
|       |   |-- sync/                       # SyncService, LocalRepository, SFTPRepository, ConflictResolver
|       |   |-- update/                     # UpdateChecker, UpdateInfo, VersionComparator
|       |   |-- util/                       # SecureWiper, FileSecurityUtils, PasswordValidator, DateUtils
|       |   +-- vault/                      # Vault, VaultEntry, VaultManager, VaultService,
|       |                                   # VaultImporter, VaultExporter, VaultLoadResult, SortField
|       |-- main/resources/i18n/            # messages_en.properties, messages_fr.properties
|       |-- main/resources/update.properties # Configuration du systeme de mise a jour
|       +-- test/java/com/passwordmanager/  # 152 tests (15 classes)
|
|-- desktop/                                # :desktop — interface Swing (Java 17)
|   |-- build.gradle.kts
|   +-- src/
|       |-- main/java/com/passwordmanager/
|       |   |-- Main.java                   # Point d'entree, detection app.home
|       |   |-- ui/                         # LoginFrame, MainFrame, VaultPanel, EntryDialog,
|       |   |                               # PasswordGeneratorDialog, SettingsDialog, StrengthBarHelper
|       |   +-- update/                     # DesktopUpdateManager
|       +-- test/java/com/passwordmanager/  # 71 tests (SyncServiceTest, LocalRepositoryTest, etc.)
|
+-- android/                                # :android — interface Compose (Kotlin 2.1)
    |-- build.gradle.kts                    # AGP 8.7.3, Compose BOM 2024.12, Hilt 2.54, minSdk 26
    |-- proguard-rules.pro                 # Regles R8 : Tink, Hilt/Dagger, javax.inject
    +-- src/
        |-- main/
        |   |-- AndroidManifest.xml
        |   |-- res/
        |   |   |-- values/strings.xml      # 100+ cles EN
        |   |   |-- values-fr/strings.xml   # 100+ cles FR
        |   |   +-- values/themes.xml
        |   +-- kotlin/com/passwordmanager/android/
        |       |-- PasswordManagerApp.kt   # @HiltAndroidApp
        |       |-- MainActivity.kt         # @AndroidEntryPoint, AppCompatActivity, locale init
        |       |-- data/                   # AndroidVaultRepository, AndroidConfigRepository,
        |       |                           # ConfigRepository (interface), SessionHolder (@Volatile/@Synchronized)
        |       |-- di/                     # AppModule (@Module @InstallIn @Provides @Singleton)
        |       |-- update/                # AndroidUpdateManager
        |       +-- ui/
        |           |-- theme/              # Theme.kt, Color.kt, Type.kt
        |           |-- navigation/         # AppNavigation.kt (9 routes)
        |           |-- login/              # LoginScreen, @HiltViewModel LoginViewModel
        |           |-- vault/              # VaultListScreen/VM (multi-select, SFTP sync), EntryDetailScreen/VM, EntryEditScreen/VM
        |           |-- generator/          # GeneratorScreen, @HiltViewModel GeneratorViewModel
        |           |-- settings/           # SettingsScreen/VM, ChangeMasterPasswordScreen/VM,
        |           |                       # CategoryManagementScreen/VM
        |           |-- audit/              # SecurityAuditScreen, @HiltViewModel SecurityAuditViewModel
        |           +-- components/         # PasswordStrengthBar, PasswordField, EntryCard, ConfirmDialog, ImportExportDialog
        +-- test/kotlin/com/passwordmanager/android/  # 41 tests (6 classes)
            |-- test/                       # MainDispatcherExtension, FakeConfigRepository, TestSessionHelper
            +-- ui/                         # GeneratorVM, EntryDetailVM, EntryEditVM,
                                            # SettingsVM, ChangeMasterPasswordVM, SecurityAuditVM
```
