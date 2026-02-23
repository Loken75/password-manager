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
11. [Tests](#11-tests)
12. [Dependances](#12-dependances)
13. [Arbre des fichiers sources](#13-arbre-des-fichiers-sources)

---

## 1. Vue d'ensemble

Password Manager est une application de bureau Java 17 permettant de stocker et gerer des mots de passe dans un coffre-fort chiffre. L'application utilise Swing avec le theme FlatLaf pour l'interface graphique, AES-256-GCM pour le chiffrement, et supporte la synchronisation via SFTP.

**Caracteristiques techniques principales :**

- Chiffrement par enveloppe DEK/KEK (AES-256-GCM + PBKDF2-HMAC-SHA256)
- Protection memoire des donnees sensibles (`char[]` + effacement securise)
- Ecriture atomique des fichiers avec permissions POSIX/ACL restrictives
- Multi-utilisateurs avec coffres isoles
- Import/export CSV et JSON avec protection contre l'injection de formules
- Synchronisation SFTP avec gestion des conflits et mode hors-ligne
- Interface bilingue francais/anglais
- Themes systeme, clair et sombre
- 105 tests unitaires et d'integration

---

## 2. Prerequis et compilation

| Composant | Version requise |
|-----------|-----------------|
| Java (JDK) | 17 ou superieur |
| Maven | 3.8+ |

### Compilation

```bash
mvn clean package
```

Produit deux JARs dans `target/` :
- `password-manager-1.0.jar` : JAR standard (~135 Ko)
- `password-manager.jar` : fat JAR executable avec toutes les dependances (~2 Mo), via `maven-assembly-plugin` (descripteur `jar-with-dependencies`)

### Execution

```bash
java -jar target/password-manager.jar
```

Des scripts de lancement sont fournis dans `scripts/` :
- `run.sh` (Linux/macOS) : utilise le JRE embarque (`runtime/bin/java`) s'il est present, sinon le Java systeme
- `run.bat` (Windows) : idem

### Construction de la distribution avec JRE embarque

```bash
mvn clean package -Pdist
```

Ou directement :
```bash
./scripts/build-dist.sh
```

Produit `dist/PasswordManager/` contenant le fat JAR, les scripts de lancement et un JRE minimal (~57 Mo) cree par `jlink`. L'utilisateur final n'a pas besoin d'installer Java.

### Execution des tests

```bash
mvn test
```

---

## 3. Architecture logicielle

### Diagramme des dependances entre packages

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
              +-- VaultPanel (ui)
                    |-- EntryDialog
                    |     +-- PasswordGeneratorDialog
                    |-- SettingsDialog
                    +-- StrengthBarHelper ---- PasswordStrengthAnalyzer (crypto)
```

### Principes architecturaux

- **Separation des responsabilites** : les packages `crypto`, `vault`, `sync`, `config`, `ui`, `util` et `i18n` sont decouples. Le package `ui` ne contient aucune logique metier ou cryptographique.
- **Interface d'abstraction crypto** : `EncryptionService` est une interface permettant d'injecter un service mock dans les tests, sans dependre de l'implementation `CryptoService`.
- **Aucune retention du mot de passe maitre** : apres l'authentification, seule la `VaultSession` (contenant la DEK) est conservee en memoire. Le mot de passe maitre est efface immediatement.
- **AutoCloseable / Destroyable** : `VaultSession` implemente les deux interfaces pour garantir l'effacement des cles via `try-with-resources` ou appel explicite.

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
- `changePassword()` : genere un nouveau sel + KEK, re-chiffre la DEK existante, met a jour l'enveloppe dans la session. L'ancienne KEK est detruite.
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

---

## 5. Structure des packages

### 5.1. `com.passwordmanager.vault`

| Classe | Role |
|--------|------|
| `Vault` | Modele de donnees du coffre (version, utilisateur, entrees, categories, parametres) |
| `VaultEntry` | Entree individuelle (titre, identifiant, mot de passe `char[]`, URL, notes, categorie, tags, dates) |
| `VaultManager` | Persistance : creation, chargement, sauvegarde, migration v1->v2, backup, import/export |
| `VaultService` | Operations metier : CRUD, recherche, tri, filtrage, detection de doublons/anciens mots de passe |
| `VaultExporter` | Export CSV/JSON en `char[]` avec protection anti-injection de formules |
| `VaultImporter` | Import CSV/JSON avec detection automatique du separateur et alias multilingues |
| `VaultLoadResult` | Objet de valeur : `Vault` + `VaultSession` |
| `SortField` | Enum : `TITLE`, `DATE`, `CATEGORY` |

**VaultManager — details :**
- Format de fichier v2.0 : enveloppe JSON contenant `version`, `kdf`, `kdf_iterations`, `salt`, `kek_iv`, `encrypted_dek`, `data_iv`, `encrypted_data`
- Migration automatique v1.0 -> v2.0 au chargement
- Ecriture atomique : fichier temporaire -> permissions -> `Files.move(ATOMIC_MOVE)` avec fallback
- Backup roulant (3 fichiers `.bak` max par utilisateur)
- Validation du nom d'utilisateur : regex `[a-zA-Z0-9_]+`
- `CharArrayAdapter` interne : `TypeAdapter<char[]>` Gson pour serialiser les mots de passe comme chaines JSON
- Gson configure avec `setPrettyPrinting()`

**VaultEntry — details :**
- `getPassword()` retourne un clone (copie defensive)
- `setPassword()` efface l'ancien mot de passe via `SecureWiper.wipe()` avant d'affecter le nouveau clone
- `wipe()` efface le mot de passe et nullifie les champs sensibles

**VaultService — details :**
- Recherche insensible a la casse sur titre, identifiant, URL, notes, categorie et tags
- Tri : `TITLE` (alphabetique croissant), `DATE` (plus recent en premier), `CATEGORY` (alphabetique croissant)
- `findDuplicatePasswords()` : utilise des hashes SHA-256 des mots de passe comme cles (evite de stocker le clair comme cle de Map)
- `findOldPasswords(int days)` : compare les timestamps `updatedAt` au seuil configure

**VaultImporter — details :**
- Detection du separateur : frequence `,` vs `;` dans la premiere ligne
- Alias de colonnes FR/EN (insensible a la casse et aux accents) :
  - `title` <- titre, organisme, name, nom
  - `username` <- identifiant, email, login, adresse mail, adresse mail / identifiant
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
| `SyncService` | Orchestration de la synchronisation (hash SHA-256, detection de conflit, mode hors-ligne) |
| `LocalRepository` | Gestion des fichiers locaux (ecritures atomiques, prevention du path traversal, backups) |
| `SFTPRepository` | Client SFTP via JSch (authentification par cle, `StrictHostKeyChecking=yes`, limite 50 Mo) |
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
- Windows : ACL owner-only (READ_DATA, WRITE_DATA, READ_ATTRIBUTES, WRITE_ATTRIBUTES, DELETE, READ_ACL, SYNCHRONIZE) + preservation des entrees SYSTEM/SYSTEME
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
- Changement de langue dynamique via `setLanguage()`
- Retourne la cle elle-meme si la traduction n'est pas trouvee (pas d'exception)

### 5.6. `com.passwordmanager.ui`

| Classe | Role |
|--------|------|
| `LoginFrame` | Ecran de connexion, creation d'utilisateur, toggle visibilite mot de passe, changement de langue |
| `MainFrame` | Fenetre principale avec menus, barre d'outils, auto-lock, shutdown hook |
| `VaultPanel` | Panneau 3 colonnes : categories, table d'entrees, details avec boutons copier |
| `EntryDialog` | Formulaire modal de creation/edition d'entree |
| `PasswordGeneratorDialog` | Dialogue du generateur de mots de passe |
| `SettingsDialog` | Dialogue des parametres (3 onglets : General, Securite, Synchronisation) |
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
| Auto-masquage | Le mot de passe affiche se re-masque automatiquement apres 30 secondes |

### 7.2. Securite des fichiers

| Mesure | Implementation |
|--------|----------------|
| Permissions restrictives | POSIX 600 (fichiers) / 700 (repertoires) ; ACL owner-only sur Windows |
| Ecriture atomique | Fichier temporaire -> permissions -> `Files.move(ATOMIC_MOVE)` avec fallback |
| Backup roulant coffre | 3 fichiers `.bak` max par utilisateur (VaultManager) |
| Backup roulant sync | 5 fichiers horodates max (LocalRepository) |
| Validation des noms d'utilisateur | Regex `[a-zA-Z0-9_]+` pour prevenir le path traversal |
| Validation des noms de fichiers (sync) | Rejet de `..`, `/`, `\`, `~` dans `LocalRepository` |

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
| Telechargement SFTP | Limite de taille 50 Mo, verification de contenu JSON valide (commence par `{`) |

### 7.5. Securite SFTP

- Authentification par cle SSH uniquement (pas de mot de passe)
- `StrictHostKeyChecking=yes` (verification via `~/.ssh/known_hosts`)
- Timeouts : connexion 30s, canal 10s
- Identifiants stockes chiffres dans la configuration

### 7.6. Presse-papiers

- Effacement automatique apres le delai configure (defaut 30s)
- Timer annule et relance a chaque nouvelle copie
- Effacement au verrouillage et a la fermeture

---

## 8. Synchronisation SFTP

### 8.1. Architecture

```
MainFrame
    +-- SyncService (synchronized)
            |-- LocalRepository (ecritures atomiques, prevention path traversal)
            +-- SFTPRepository (JSch, authentification par cle)
```

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

### 10.1. Technologies

- **Swing** avec **FlatLaf 3.7** pour un rendu moderne
- Trois themes : **Systeme** (detecte automatiquement le theme de l'OS), **Clair** (`FlatLightLaf`), **Sombre** (`FlatDarkLaf`)
- Theme applique au demarrage et modifiable dynamiquement depuis les parametres

### 10.2. Structure des fenetres

| Fenetre | Type | Taille | Description |
|---------|------|--------|-------------|
| `LoginFrame` | `JFrame` | 450x450 (non-redimensionnable) | Connexion, creation utilisateur, toggle mot de passe, selection de langue |
| `MainFrame` | `JFrame` | 1100x700 (min 900x600) | Menus, barre d'outils, panneau central, barre de statut |
| `EntryDialog` | `JDialog` (modal) | min 550x480 | Creation/edition d'entree avec barre de force |
| `PasswordGeneratorDialog` | `JDialog` (modal) | min 450x420 | Generateur avec options et barre de force |
| `SettingsDialog` | `JDialog` (modal) | min 500x450 | Parametres en 3 onglets |

### 10.3. Panneau 3 colonnes (VaultPanel)

| Colonne | Largeur | Contenu |
|---------|---------|---------|
| Gauche | 180 px | Liste des categories (JList) + bouton ajout |
| Centre | flexible | Barre de recherche + table (Titre, Identifiant, Categorie, Force) |
| Droite | 300 px | Details : titre, grille de champs avec separateurs, boutons copier identifiant/mot de passe, case a cocher afficher |

La colonne Force du tableau est coloree selon le niveau : rouge (Faible), orange (Moyen), vert (Fort), bleu (Tres fort).

### 10.4. Auto-lock

- `Toolkit.addAWTEventListener` sur les evenements clavier et souris
- `javax.swing.Timer` verifie l'inactivite toutes les 30 secondes
- Depassement du seuil configurable -> verrouillage (sauvegarde -> nettoyage -> effacement des cles -> ecran de connexion)

### 10.5. Shutdown hook

Un `Runtime.addShutdownHook` efface le coffre (`vault.wipe()`) et detruit la session (`session.destroy()`) a la fermeture de la JVM. Le hook est recree proprement lors d'un changement de langue (qui reconstruit la fenetre).

---

## 11. Tests

### 11.1. Vue d'ensemble

**Framework** : JUnit 5 (Jupiter) 5.14.2
**Total** : 105 tests (unitaires + integration)

### 11.2. Matrice des tests

| Module | Classe de test | Nombre | Description |
|--------|---------------|--------|-------------|
| security | `SecurityAuditTest` | 31 | IV, KDF, memoire, permissions, format, import/export, generateur |
| vault | `VaultServiceTest` | 13 | CRUD, recherche, tri, doublons, categories |
| vault | `VaultImporterTest` | 10 | CSV (separateurs, alias, positionnement), JSON, limites |
| vault | `VaultManagerIntegrationTest` | 9 | Cycle complet avec vraie crypto (`@TempDir`) |
| util | `PasswordValidatorTest` | 9 | Politique de mot de passe maitre |
| crypto | `PasswordStrengthAnalyzerTest` | 9 | Niveaux de force, score, cas limites |
| crypto | `CryptoServiceTest` | 8 | Enveloppe DEK/KEK, chiffrement/dechiffrement, changement de mot de passe, lifecycle |
| vault | `VaultExporterTest` | 8 | CSV, JSON, injection, round-trip |
| crypto | `PasswordGeneratorTest` | 5 | Longueur, types de caracteres, exclusion ambigus |
| config | `ConfigManagerTest` | 3 | Valeurs par defaut, persistance, auto-creation |
| | | **105** | |

### 11.3. Tests de securite remarquables (`SecurityAuditTest`)

- **Unicite des IV** : 100 chiffrements successifs -> tous les IV distincts
- **KDF** : iterations >= 600 000, taille du sel = 32 octets
- **Effacement memoire** : verification que `SecureWiper.wipe()` met a zero les tableaux
- **Permissions fichiers** : aucune permission groupe/monde sur les fichiers de coffre (POSIX conditionnel)
- **Integrite GCM** : alteration d'un octet du ciphertext -> exception `VaultDecryptionException`
- **Format v2.0** : verification de la presence de tous les champs d'enveloppe, absence de texte clair
- **Assainissement import** : caracteres de controle supprimes, tabulations preservees
- **Anti-injection export** : tous les caracteres declencheurs (`=`, `+`, `-`, `@`) sont prefixes

---

## 12. Dependances

| Bibliotheque | GroupId | Version | Usage |
|--------------|---------|---------|-------|
| Gson | `com.google.code.gson` | 2.13.2 | Serialisation JSON des coffres et de la configuration |
| JSch (mwiede) | `com.github.mwiede` | 2.27.8 | Client SFTP pour la synchronisation distante |
| FlatLaf | `com.formdev` | 3.7 | Look & Feel Swing moderne (themes systeme/clair/sombre) |
| JUnit 5 | `org.junit.jupiter` | 5.14.2 | Tests unitaires et d'integration (scope test) |

**Plugins Maven :**

| Plugin | Version | Role |
|--------|---------|------|
| `maven-compiler-plugin` | 3.15.0 | Compilation Java 17, encodage UTF-8 |
| `maven-jar-plugin` | 3.5.0 | Manifest avec `Main-Class` |
| `maven-assembly-plugin` | 3.8.0 | Fat JAR (`jar-with-dependencies`) |
| `maven-surefire-plugin` | 3.5.5 | Execution des tests JUnit 5 |
| `exec-maven-plugin` | 3.6.3 | Execution de `build-dist.sh` (profil `dist`) |

---

## 13. Arbre des fichiers sources

```
password-manager/
|-- pom.xml
|-- README.md
|-- docs/
|   |-- TECHNICAL.md                       # Ce document
|   |-- FUNCTIONAL.md                      # Documentation fonctionnelle
|   +-- CI-RELEASE.md                      # Documentation workflow CI/CD
|-- scripts/
|   |-- build-dist.sh                      # Construction distribution avec JRE
|   |-- run.sh                             # Lanceur Linux/macOS
|   +-- run.bat                            # Lanceur Windows
+-- src/
    |-- main/
    |   |-- java/com/passwordmanager/
    |   |   |-- Main.java
    |   |   |-- config/
    |   |   |   |-- AppConfig.java
    |   |   |   |-- ConfigEncryptor.java
    |   |   |   |-- ConfigManager.java
    |   |   |   |-- StorageMode.java
    |   |   |   +-- ThemeMode.java
    |   |   |-- crypto/
    |   |   |   |-- CryptoService.java
    |   |   |   |-- EncryptedPayload.java
    |   |   |   |-- EncryptionService.java
    |   |   |   |-- KeyDerivation.java
    |   |   |   |-- PasswordGenerator.java
    |   |   |   |-- PasswordStrengthAnalyzer.java
    |   |   |   |-- VaultDecryptionException.java
    |   |   |   |-- VaultEncryptionException.java
    |   |   |   +-- VaultSession.java
    |   |   |-- i18n/
    |   |   |   +-- LanguageManager.java
    |   |   |-- sync/
    |   |   |   |-- ConflictResolver.java
    |   |   |   |-- LocalRepository.java
    |   |   |   |-- SFTPRepository.java
    |   |   |   +-- SyncService.java
    |   |   |-- ui/
    |   |   |   |-- EntryDialog.java
    |   |   |   |-- LoginFrame.java
    |   |   |   |-- MainFrame.java
    |   |   |   |-- PasswordGeneratorDialog.java
    |   |   |   |-- SettingsDialog.java
    |   |   |   |-- StrengthBarHelper.java
    |   |   |   +-- VaultPanel.java
    |   |   |-- util/
    |   |   |   |-- DateUtils.java
    |   |   |   |-- FileSecurityUtils.java
    |   |   |   |-- PasswordValidator.java
    |   |   |   +-- SecureWiper.java
    |   |   +-- vault/
    |   |       |-- SortField.java
    |   |       |-- Vault.java
    |   |       |-- VaultEntry.java
    |   |       |-- VaultExporter.java
    |   |       |-- VaultImporter.java
    |   |       |-- VaultLoadResult.java
    |   |       |-- VaultManager.java
    |   |       +-- VaultService.java
    |   +-- resources/
    |       +-- i18n/
    |           |-- messages_en.properties
    |           +-- messages_fr.properties
    +-- test/
        +-- java/com/passwordmanager/
            |-- config/
            |   +-- ConfigManagerTest.java
            |-- crypto/
            |   |-- CryptoServiceTest.java
            |   |-- PasswordGeneratorTest.java
            |   +-- PasswordStrengthAnalyzerTest.java
            |-- security/
            |   +-- SecurityAuditTest.java
            |-- util/
            |   +-- PasswordValidatorTest.java
            +-- vault/
                |-- VaultExporterTest.java
                |-- VaultImporterTest.java
                |-- VaultManagerIntegrationTest.java
                +-- VaultServiceTest.java
```
