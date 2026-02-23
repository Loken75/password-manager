# Documentation technique

## Table des matières

1. [Vue d'ensemble](#1-vue-densemble)
2. [Prérequis et compilation](#2-prérequis-et-compilation)
3. [Architecture logicielle](#3-architecture-logicielle)
4. [Architecture cryptographique](#4-architecture-cryptographique)
5. [Structure des packages](#5-structure-des-packages)
6. [Format des fichiers](#6-format-des-fichiers)
7. [Sécurité applicative](#7-sécurité-applicative)
8. [Synchronisation SFTP](#8-synchronisation-sftp)
9. [Internationalisation](#9-internationalisation)
10. [Interface graphique](#10-interface-graphique)
11. [Tests](#11-tests)
12. [Dépendances](#12-dépendances)
13. [Arbre des fichiers sources](#13-arbre-des-fichiers-sources)

---

## 1. Vue d'ensemble

Password Manager est une application de bureau Java 17 permettant de stocker et gérer des mots de passe dans un coffre-fort chiffré. L'application utilise Swing avec le thème FlatLaf pour l'interface graphique, AES-256-GCM pour le chiffrement, et supporte la synchronisation via SFTP.

**Caractéristiques techniques principales :**

- Chiffrement par enveloppe DEK/KEK (AES-256-GCM + PBKDF2-HMAC-SHA256)
- Protection mémoire des données sensibles (`char[]` + effacement sécurisé)
- Écriture atomique des fichiers avec permissions POSIX/ACL restrictives
- Multi-utilisateurs avec coffres isolés
- Import/export CSV et JSON avec protection contre l'injection de formules
- Synchronisation SFTP avec gestion des conflits et mode hors-ligne
- Interface bilingue français/anglais
- 105 tests unitaires et d'intégration

---

## 2. Prérequis et compilation

| Composant | Version requise |
|-----------|-----------------|
| Java (JDK) | 17 ou supérieur |
| Maven | 3.8+ |

### Compilation

```bash
mvn clean package
```

Produit un fat JAR exécutable dans `target/password-manager.jar` (via `maven-assembly-plugin`, descripteur `jar-with-dependencies`).

### Exécution

```bash
java -jar target/password-manager.jar
```

Des scripts de lancement sont fournis dans `scripts/` :
- `run.sh` (Linux/macOS) : compile si le JAR est absent, puis lance l'application
- `run.bat` (Windows) : idem

### Exécution des tests

```bash
mvn test
```

---

## 3. Architecture logicielle

### Diagramme des dépendances entre packages

```
Main
  └── LoginFrame (ui)
        ├── ConfigManager (config) ──── AppConfig
        ├── VaultManager (vault) ────── EncryptionService (crypto)
        │     ├── CryptoService          ├── KeyDerivation
        │     ├── VaultImporter          ├── VaultSession
        │     └── VaultExporter          └── EncryptedPayload
        └── [login] ──► MainFrame (ui)
              ├── VaultService (vault) ──── Vault ──── VaultEntry[]
              ├── SyncService (sync)
              │     ├── LocalRepository
              │     └── SFTPRepository
              └── VaultPanel (ui)
                    ├── EntryDialog
                    │     └── PasswordGeneratorDialog
                    ├── SettingsDialog
                    └── StrengthBarHelper ──── PasswordStrengthAnalyzer (crypto)
```

### Principes architecturaux

- **Séparation des responsabilités** : les packages `crypto`, `vault`, `sync`, `config`, `ui`, `util` et `i18n` sont découplés. Le package `ui` ne contient aucune logique métier ou cryptographique.
- **Interface d'abstraction crypto** : `EncryptionService` est une interface permettant d'injecter un service mock dans les tests, sans dépendre de l'implémentation `CryptoService`.
- **Aucune rétention du mot de passe maître** : après l'authentification, seule la `VaultSession` (contenant la DEK) est conservée en mémoire. Le mot de passe maître est effacé immédiatement.
- **AutoCloseable / Destroyable** : `VaultSession` implémente les deux interfaces pour garantir l'effacement des clés via `try-with-resources` ou appel explicite.

---

## 4. Architecture cryptographique

### 4.1. Chiffrement par enveloppe (DEK/KEK)

Le système utilise deux niveaux de clés :

```
Mot de passe maître (char[])
    │
    ├── PBKDF2-HMAC-SHA256 (600 000 itérations, sel 32 octets)
    │       │
    │       └── KEK (Key Encryption Key) ── AES-256
    │                │
    │                └── AES-256-GCM-encrypt(DEK) ── stocké dans le fichier .enc
    │
    └── [effacé immédiatement après dérivation]

DEK (Data Encryption Key) ── AES-256, 32 octets aléatoires (SecureRandom)
    │
    └── AES-256-GCM-encrypt(données du coffre)
```

**Avantages :**
- La DEK est générée une seule fois à la création du coffre. Les sauvegardes ne nécessitent pas de re-dérivation PBKDF2.
- Le changement de mot de passe ne re-chiffre que la DEK (quelques octets), pas l'ensemble des données.
- La KEK n'est jamais stockée ; elle est dérivée à la demande et effacée après usage.

### 4.2. Paramètres cryptographiques

| Paramètre | Valeur |
|-----------|--------|
| Algorithme de chiffrement | AES-256-GCM (chiffrement authentifié) |
| Taille de l'IV | 12 octets (GCM standard) |
| Taille du tag d'authentification | 128 bits |
| Taille de la DEK | 32 octets (256 bits) |
| Taille du sel PBKDF2 | 32 octets |
| Itérations PBKDF2 (v2.0) | 600 000 (minimum OWASP 2025) |
| Itérations PBKDF2 (legacy v1.0) | 100 000 |
| Générateur aléatoire | `java.security.SecureRandom` |

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

#### `CryptoService` (implémentation)

Implémente `EncryptionService` avec AES-256-GCM. Points clés :
- `createSession()` : génère une DEK aléatoire, dérive la KEK, chiffre la DEK avec la KEK, efface `rawDek` et détruit la KEK dans le bloc `finally`.
- `openSession()` : dérive la KEK, déchiffre la DEK, retourne une `VaultSession`. Efface la KEK dans le `finally`.
- `changePassword()` : génère un nouveau sel + KEK, re-chiffre la DEK existante, met à jour l'enveloppe dans la session. L'ancienne KEK est détruite.
- `decryptLegacy()` : supporte les coffres v1.0 où le mot de passe dérivait directement la clé de données (100 000 itérations).

#### `KeyDerivation`

Utilitaire statique pour PBKDF2-HMAC-SHA256.

```java
public static SecretKey deriveKey(char[] password, byte[] salt, int iterations)
public static SecretKey deriveKey(char[] password, byte[] salt) // 600 000 itérations
public static byte[] generateSalt()                             // 32 octets SecureRandom
public static int getDefaultIterations()                        // 600 000
```

Nettoyage : `PBEKeySpec.clearPassword()` est appelé après dérivation ; le tableau d'octets intermédiaire est effacé via `SecureWiper`.

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
    public void destroy()            // efface toutes les données sensibles
    public void close()              // délègue à destroy()
}
```

#### `EncryptedPayload`

Objet de valeur contenant le résultat d'un chiffrement AES-GCM :
- `byte[] iv` (12 octets)
- `byte[] ciphertext` (inclut le tag GCM de 16 octets)
- `void wipe()` : efface les deux tableaux

#### `PasswordGenerator`

Génère des mots de passe cryptographiquement sûrs via `SecureRandom`.

```java
public static char[] generate(int length, boolean useUpper, boolean useLower,
                               boolean useDigits, boolean useSpecial,
                               boolean excludeAmbiguous)
```

- Longueur bornée entre 8 et 128
- Garantit au moins un caractère de chaque type activé
- Mélange Fisher-Yates après remplissage
- Retourne `char[]` (l'appelant est responsable de l'effacement)

#### `PasswordStrengthAnalyzer`

Analyse la force d'un mot de passe sur une échelle à 4 niveaux (`WEAK`, `MEDIUM`, `STRONG`, `VERY_STRONG`) et un score numérique 0-100.

Critères :
- Longueur < 8 ou ≤ 1 type de caractère → `WEAK`
- Pénalités : caractères séquentiels (≥ 4), caractères répétés (≥ 4), tout en même casse
- `VERY_STRONG` : longueur effective ≥ 16 et ≥ 4 types de caractères
- `STRONG` : longueur effective ≥ 12 et ≥ 3 types

Les surcharges `String` effacent le `char[]` intermédiaire après analyse.

#### Exceptions

- `VaultEncryptionException` : échec de chiffrement ou de dérivation de clé
- `VaultDecryptionException` : mot de passe incorrect ou données corrompues (tag GCM invalide)

### 4.4. Chiffrement de la configuration

Les champs sensibles de la configuration (identifiants SFTP) sont chiffrés au repos via `ConfigEncryptor` (classe package-private) :

- Clé AES-256 dérivée via PBKDF2 (10 000 itérations) à partir d'un fichier de matériau de clé (`~/.password-manager/.config_key`, 64 octets aléatoires)
- Format stocké : `ENC:` + Base64(IV[12] + ciphertext)
- Écriture atomique du fichier de clé avec permissions restrictives

---

## 5. Structure des packages

### 5.1. `com.passwordmanager.vault`

| Classe | Rôle |
|--------|------|
| `Vault` | Modèle de données du coffre (version, utilisateur, entrées, catégories, paramètres) |
| `VaultEntry` | Entrée individuelle (titre, identifiant, mot de passe `char[]`, URL, notes, catégorie, tags, dates) |
| `VaultManager` | Persistance : création, chargement, sauvegarde, migration v1→v2, backup, import/export |
| `VaultService` | Opérations métier : CRUD, recherche, tri, filtrage, détection de doublons/anciens mots de passe |
| `VaultExporter` | Export CSV/JSON en `char[]` avec protection anti-injection de formules |
| `VaultImporter` | Import CSV/JSON avec détection automatique du séparateur et alias multilingues |
| `VaultLoadResult` | Objet de valeur : `Vault` + `VaultSession` |
| `SortField` | Enum : `TITLE`, `DATE`, `CATEGORY` |

**VaultManager — détails :**
- Format de fichier v2.0 : enveloppe JSON contenant `version`, `kdf`, `kdf_iterations`, `salt`, `kek_iv`, `encrypted_dek`, `data_iv`, `encrypted_data`
- Migration automatique v1.0 → v2.0 au chargement
- Écriture atomique : fichier temporaire → permissions → `Files.move(ATOMIC_MOVE)` avec fallback
- Backup roulant (3 fichiers `.bak` max par utilisateur)
- Validation du nom d'utilisateur : regex `[a-zA-Z0-9_]+`
- `CharArrayAdapter` interne : `TypeAdapter<char[]>` Gson pour sérialiser les mots de passe comme chaînes JSON

**VaultImporter — détails :**
- Détection du séparateur : fréquence `,` vs `;` dans la première ligne
- Alias de colonnes FR/EN (insensible à la casse et aux accents) :
  - `title` ← titre, organisme, name, nom
  - `username` ← identifiant, email, login, adresse mail
  - `password` ← mdp, mot de passe, pass
  - `url` ← site, website, lien
  - etc.
- Repli positionnel si aucun en-tête reconnu
- Assainissement : suppression des caractères de contrôle, troncature à 10 000 caractères
- Limite : 10 000 entrées par import

**VaultExporter — détails :**
- Protection anti-injection de formules CSV : préfixe `'` devant les champs commençant par `=`, `+`, `-`, `@`, `\t`, `\r`
- Export en `char[]` pour permettre l'effacement sécurisé par l'appelant

### 5.2. `com.passwordmanager.config`

| Classe | Rôle |
|--------|------|
| `AppConfig` | Modèle de configuration avec setters validants (port 1-65535, auto-lock 1-60 min, etc.) |
| `ConfigManager` | Lecture/écriture de `config.properties` avec chiffrement des champs sensibles |
| `ConfigEncryptor` | Chiffrement AES-256-GCM des identifiants SFTP (package-private) |
| `StorageMode` | Enum : `LOCAL`, `REMOTE` |
| `ThemeMode` | Enum : `LIGHT`, `DARK` |

**Valeurs par défaut d'AppConfig :**

| Champ | Valeur par défaut |
|-------|-------------------|
| `language` | `"fr"` |
| `storageMode` | `LOCAL` |
| `sftpPort` | `22` |
| `sftpRemotePath` | `"/vault/data"` |
| `localVaultDirectory` | `~/.password-manager/vaults` |
| `autoLockMinutes` | `15` |
| `clipboardClearSeconds` | `30` |
| `theme` | `LIGHT` |

### 5.3. `com.passwordmanager.sync`

| Classe | Rôle |
|--------|------|
| `SyncService` | Orchestration de la synchronisation (hash SHA-256, détection de conflit, mode hors-ligne) |
| `LocalRepository` | Gestion des fichiers locaux (écritures atomiques, prévention du path traversal, backups) |
| `SFTPRepository` | Client SFTP via JSch (authentification par clé, `StrictHostKeyChecking=yes`, limite 50 Mo) |
| `ConflictResolver` | Enum : `KEEP_LOCAL`, `KEEP_REMOTE`, `KEEP_BOTH` |

**Flux de synchronisation :**

1. Vérification du mode (LOCAL → pas de sync)
2. Flush des modifications en attente (mode hors-ligne)
3. Si le fichier distant n'existe pas → upload
4. Téléchargement du fichier distant dans un fichier temporaire
5. Comparaison des hashes SHA-256 (local vs distant)
6. Si identiques → synchronisé
7. Si différents : timestamp local ≥ distant → upload ; sinon → conflit
8. Nettoyage du fichier temporaire dans le bloc `finally`

**Gestion hors-ligne :**
- Si le serveur est injoignable, un marqueur `.pending` est créé localement
- Au prochain sync réussi, les modifications en attente sont rejouées

### 5.4. `com.passwordmanager.util`

| Classe | Rôle |
|--------|------|
| `SecureWiper` | Effacement sécurisé de `byte[]` et `char[]` avec lecture volatile anti-optimisation JIT |
| `FileSecurityUtils` | Permissions fichiers cross-platform : POSIX 600/700 ou ACL Windows owner-only |
| `PasswordValidator` | Validation du mot de passe maître (12+ chars, 4 types, pas dans la liste des 40 mots de passe courants) |
| `DateUtils` | Formatage ISO-8601 UTC thread-safe via `java.time` |

**SecureWiper — technique :**
```java
private static volatile byte volatileByte;
public static void wipe(byte[] data) {
    Arrays.fill(data, (byte) 0);
    volatileByte = data[0]; // empêche le JIT d'éliminer le fill
}
```

**FileSecurityUtils — comportement :**
- POSIX : fichiers → `rw-------` (600), répertoires → `rwx------` (700)
- Windows : ACL owner-only (READ_DATA, WRITE_DATA, READ_ATTRIBUTES, WRITE_ATTRIBUTES, DELETE, READ_ACL, SYNCHRONIZE) + préservation des entrées SYSTEM/SYSTÈME
- Échec silencieux (log FINE) pour ne pas bloquer l'application sur les systèmes sans support

**PasswordValidator — politique :**
- Minimum 12 caractères
- Au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractère spécial
- Rejet des 40+ mots de passe courants (comparaison sur la base alphabétique, ex: `Password123!` → `password` → rejeté)
- Toutes les opérations en `char[]` sans création de `String` à partir de l'entrée utilisateur

### 5.5. `com.passwordmanager.i18n`

| Classe | Rôle |
|--------|------|
| `LanguageManager` | Singleton thread-safe (`volatile bundle`), gestion FR/EN via `ResourceBundle` |

- 177 clés de traduction par langue
- Changement de langue dynamique via `setLanguage()`
- Retourne la clé elle-même si la traduction n'est pas trouvée (pas d'exception)

### 5.6. `com.passwordmanager.ui`

| Classe | Rôle |
|--------|------|
| `LoginFrame` | Écran de connexion, création d'utilisateur, changement de langue |
| `MainFrame` | Fenêtre principale avec menus, barre d'outils, auto-lock, shutdown hook |
| `VaultPanel` | Panneau 3 colonnes : catégories, table d'entrées, détails |
| `EntryDialog` | Formulaire modal de création/édition d'entrée |
| `PasswordGeneratorDialog` | Dialogue du générateur de mots de passe |
| `SettingsDialog` | Dialogue des paramètres (3 onglets : Général, Sécurité, Synchronisation) |
| `StrengthBarHelper` | Utilitaire d'affichage de la barre de force (couleurs : rouge/orange/vert/bleu) |

---

## 6. Format des fichiers

### 6.1. Fichier coffre (`.enc`, format v2.0)

Emplacement : `~/.password-manager/vaults/vault_<username>.enc`

```json
{
  "version": "2.0",
  "kdf": "PBKDF2WithHmacSHA256",
  "kdf_iterations": 600000,
  "salt": "<base64, 32 octets>",
  "kek_iv": "<base64, 12 octets>",
  "encrypted_dek": "<base64, DEK chiffrée + tag GCM>",
  "data_iv": "<base64, 12 octets>",
  "encrypted_data": "<base64, coffre JSON chiffré AES-256-GCM>"
}
```

Le coffre JSON déchiffré contient :
```json
{
  "version": "2.0",
  "user": "alice",
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-06-20T14:22:00Z",
  "entries": [ ... ],
  "categories": ["Email", "Bancaire", "Réseaux sociaux", "Travail", "Autre"],
  "settings": {
    "auto_lock_minutes": 15,
    "clipboard_clear_seconds": 30,
    "password_expiry_days": 180
  }
}
```

### 6.2. Fichier de configuration

Emplacement : `~/.password-manager/config.properties`

```properties
language=fr
theme=light
auto_lock_minutes=15
clipboard_clear_seconds=30
storage_mode=local
sftp_host=ENC:base64(...)
sftp_port=22
sftp_user=ENC:base64(...)
sftp_key_path=ENC:base64(...)
sftp_remote_path=ENC:base64(...)
```

Les champs SFTP sont chiffrés via `ConfigEncryptor` (préfixe `ENC:`).

### 6.3. Fichier de clé de configuration

Emplacement : `~/.password-manager/.config_key`

64 octets aléatoires (`SecureRandom`), permissions 600. Utilisé pour dériver la clé AES-256 de chiffrement des champs de configuration. Écriture atomique.

### 6.4. Arborescence sur disque

```
~/.password-manager/
├── .config_key                    # Matériau de clé (64 octets, permissions 600)
├── config.properties              # Configuration (champs SFTP chiffrés)
└── vaults/                        # Répertoire des coffres (permissions 700)
    ├── vault_alice.enc            # Coffre chiffré
    ├── vault_alice.enc.bak        # Backup automatique
    └── vault_bob.enc
```

---

## 7. Sécurité applicative

### 7.1. Protection mémoire

| Mesure | Implémentation |
|--------|----------------|
| Mots de passe en `char[]` | `VaultEntry.password`, `PasswordGenerator.generate()`, `VaultExporter` retournent `char[]` |
| Effacement sécurisé | `SecureWiper.wipe()` avec barrière volatile anti-optimisation JIT |
| Copie défensive | `VaultEntry.getPassword()`, `VaultSession.getSalt/getKekIv/getEncryptedDek()` retournent des clones |
| Nettoyage de session | `VaultSession.destroy()` efface DEK, sel, IV, DEK chiffrée |
| Nettoyage Swing | Insertion via `Document.insertString()` au lieu de `JPasswordField.setText(String)` pour minimiser l'interning |
| Auto-masquage | Le mot de passe affiché se re-masque automatiquement après 30 secondes |

### 7.2. Sécurité des fichiers

| Mesure | Implémentation |
|--------|----------------|
| Permissions restrictives | POSIX 600 (fichiers) / 700 (répertoires) ; ACL owner-only sur Windows |
| Écriture atomique | Fichier temporaire → permissions → `Files.move(ATOMIC_MOVE)` avec fallback |
| Backup roulant | 3 fichiers `.bak` max par utilisateur, permissions restrictives |
| Validation des noms d'utilisateur | Regex `[a-zA-Z0-9_]+` pour prévenir le path traversal |
| Validation des noms de fichiers (sync) | Rejet de `..`, `/`, `\`, `~` dans `LocalRepository` |

### 7.3. Protection anti brute-force

- Compteur par utilisateur (`Map<String, Integer>`)
- Après 3 tentatives échouées : désactivation du formulaire avec backoff `min(attempts * 2000, 30000)` ms
- Réinitialisation du compteur après une connexion réussie

### 7.4. Validation des entrées

| Contexte | Validation |
|----------|-----------|
| Mot de passe maître | 12+ chars, 4 types, pas dans la liste commune (`PasswordValidator`) |
| Nom d'utilisateur | Regex `[a-zA-Z0-9_]+` |
| Import CSV/JSON | Assainissement des caractères de contrôle, troncature 10 000 chars, limite 10 000 entrées |
| Export CSV | Échappement RFC 4180, préfixe anti-formule (`'`) |
| Fichier clé SSH | Validation d'existence et de lisibilité avant sauvegarde |
| Téléchargement SFTP | Limite de taille 50 Mo, vérification de contenu JSON valide |

### 7.5. Sécurité SFTP

- Authentification par clé SSH uniquement (pas de mot de passe)
- `StrictHostKeyChecking=yes` (vérification via `~/.ssh/known_hosts`)
- Timeouts : connexion 30s, canal 10s
- Identifiants stockés chiffrés dans la configuration

### 7.6. Presse-papiers

- Effacement automatique après le délai configuré (défaut 30s)
- Timer annulé et relancé à chaque nouvelle copie
- Effacement au verrouillage et à la fermeture

---

## 8. Synchronisation SFTP

### 8.1. Architecture

```
MainFrame
    └── SyncService (synchronized)
            ├── LocalRepository (écritures atomiques, prévention path traversal)
            └── SFTPRepository (JSch, authentification par clé)
```

### 8.2. Algorithme de synchronisation

```
1. Si mode LOCAL → retour immédiat
2. Flush des modifications .pending (mode hors-ligne)
3. Si fichier distant absent → upload du fichier local
4. Téléchargement du fichier distant dans un temporaire
5. Calcul SHA-256 des deux fichiers
6. Si hashes identiques → pas de changement
7. Si timestamp local ≥ distant → upload (local gagne)
8. Sinon → CONFLIT (intervention utilisateur)
9. finally: suppression du fichier temporaire, déconnexion SFTP
```

### 8.3. Résolution des conflits

| Mode | Comportement |
|------|-------------|
| `KEEP_LOCAL` | Upload du fichier local (écrase le distant) |
| `KEEP_REMOTE` | Téléchargement du distant (vérifié non-vide et JSON valide) |
| `KEEP_BOTH` | Backup local horodaté, puis téléchargement du distant |

### 8.4. Mode hors-ligne

Quand le serveur est injoignable, un fichier `.pending` est créé. Au prochain sync réussi, les modifications en attente sont envoyées avant la synchronisation normale.

---

## 9. Internationalisation

### 9.1. Mécanisme

`LanguageManager` est un singleton thread-safe utilisant `java.util.ResourceBundle` avec un champ `volatile` pour la visibilité inter-threads.

### 9.2. Ressources

| Fichier | Langue | Clés |
|---------|--------|------|
| `i18n/messages_fr.properties` | Français | 177 |
| `i18n/messages_en.properties` | Anglais | 177 |

### 9.3. Sections couvertes

`app.*`, `login.*`, `error.*`, `vault.*`, `entry.*`, `generator.*`, `strength.*`, `category.*`, `settings.*`, `menu.*` (file/edit/view/tools/help), `sync.*`, `import.*`, `export.*`, `common.*`, `security.*`, `about.*`, `audit.*`

### 9.4. Changement dynamique

Le changement de langue est possible depuis l'écran de connexion (effet immédiat) et depuis les paramètres (nécessite un redémarrage).

---

## 10. Interface graphique

### 10.1. Technologies

- **Swing** avec **FlatLaf 3.2.5** pour un rendu moderne (thèmes clair et sombre)
- Thème appliqué au démarrage via `FlatLightLaf.setup()` ou `FlatDarkLaf.setup()`

### 10.2. Structure des fenêtres

| Fenêtre | Type | Description |
|---------|------|-------------|
| `LoginFrame` | `JFrame` | Écran de connexion avec sélection d'utilisateur et changement de langue |
| `MainFrame` | `JFrame` | Fenêtre principale avec menus, barre d'outils, panneau central et barre de statut |
| `EntryDialog` | `JDialog` (modal) | Formulaire de création/édition d'entrée avec barre de force |
| `PasswordGeneratorDialog` | `JDialog` (modal) | Générateur avec options de configuration et barre de force |
| `SettingsDialog` | `JDialog` (modal) | Paramètres en 3 onglets (Général, Sécurité, Synchronisation) |

### 10.3. Auto-lock

- `Toolkit.addAWTEventListener` sur les événements clavier et souris
- `javax.swing.Timer` vérifie l'inactivité toutes les 30 secondes
- Dépassement du seuil configurable → verrouillage (sauvegarde → nettoyage → effacement des clés → écran de connexion)

### 10.4. Shutdown hook

Un `Runtime.addShutdownHook` efface le coffre (`vault.wipe()`) et détruit la session (`session.destroy()`) à la fermeture de la JVM.

---

## 11. Tests

### 11.1. Vue d'ensemble

**Framework** : JUnit 5 (Jupiter) 5.10.2
**Total** : 105 tests (unitaires + intégration)

### 11.2. Matrice des tests

| Module | Classe de test | Nombre | Description |
|--------|---------------|--------|-------------|
| crypto | `CryptoServiceTest` | 8 | Enveloppe DEK/KEK, chiffrement/déchiffrement, changement de mot de passe, lifecycle |
| crypto | `PasswordGeneratorTest` | 5 | Longueur, types de caractères, exclusion ambigus |
| crypto | `PasswordStrengthAnalyzerTest` | 9 | Niveaux de force, score, cas limites |
| config | `ConfigManagerTest` | 3 | Valeurs par défaut, persistance, auto-création |
| util | `PasswordValidatorTest` | 9 | Politique de mot de passe maître |
| vault | `VaultServiceTest` | 14 | CRUD, recherche, tri, doublons, catégories |
| vault | `VaultImporterTest` | 10 | CSV (séparateurs, alias, positionnement), JSON, limites |
| vault | `VaultExporterTest` | 8 | CSV, JSON, injection, round-trip |
| vault | `VaultManagerIntegrationTest` | 10 | Cycle complet avec vraie crypto (`@TempDir`) |
| security | `SecurityAuditTest` | 24 | IV, KDF, mémoire, permissions, format, import/export, générateur |
| | | **105** | |

### 11.3. Tests de sécurité remarquables (`SecurityAuditTest`)

- **Unicité des IV** : 100 chiffrements successifs → tous les IV distincts
- **KDF** : itérations ≥ 600 000, taille du sel = 32 octets
- **Effacement mémoire** : vérification que `SecureWiper.wipe()` met à zéro les tableaux
- **Permissions fichiers** : aucune permission groupe/monde sur les fichiers de coffre (POSIX conditionnel)
- **Intégrité GCM** : altération d'un octet du ciphertext → exception `VaultDecryptionException`
- **Format v2.0** : vérification de la présence de tous les champs d'enveloppe, absence de texte clair
- **Assainissement import** : caractères de contrôle supprimés, tabulations préservées
- **Anti-injection export** : tous les caractères déclencheurs (`=`, `+`, `-`, `@`) sont préfixés

---

## 12. Dépendances

| Bibliothèque | GroupId | Version | Usage |
|--------------|---------|---------|-------|
| Gson | `com.google.code.gson` | 2.10.1 | Sérialisation JSON des coffres et de la configuration |
| JSch (mwiede) | `com.github.mwiede` | 0.2.18 | Client SFTP pour la synchronisation distante |
| FlatLaf | `com.formdev` | 3.2.5 | Look & Feel Swing moderne (thèmes clair/sombre) |
| JUnit 5 | `org.junit.jupiter` | 5.10.2 | Tests unitaires et d'intégration (scope test) |

**Plugins Maven :**

| Plugin | Version | Rôle |
|--------|---------|------|
| `maven-compiler-plugin` | 3.11.0 | Compilation Java 17, encodage UTF-8 |
| `maven-jar-plugin` | 3.3.0 | Manifest avec `Main-Class` |
| `maven-assembly-plugin` | 3.6.0 | Fat JAR (`jar-with-dependencies`) |
| `maven-surefire-plugin` | 3.2.2 | Exécution des tests JUnit 5 |

---

## 13. Arbre des fichiers sources

```
password-manager/
├── pom.xml
├── README.md
├── docs/
│   ├── TECHNICAL.md                       # Ce document
│   └── FUNCTIONAL.md                      # Documentation fonctionnelle
├── scripts/
│   ├── run.sh
│   └── run.bat
└── src/
    ├── main/
    │   ├── java/com/passwordmanager/
    │   │   ├── Main.java
    │   │   ├── config/
    │   │   │   ├── AppConfig.java
    │   │   │   ├── ConfigEncryptor.java
    │   │   │   ├── ConfigManager.java
    │   │   │   ├── StorageMode.java
    │   │   │   └── ThemeMode.java
    │   │   ├── crypto/
    │   │   │   ├── CryptoService.java
    │   │   │   ├── EncryptedPayload.java
    │   │   │   ├── EncryptionService.java
    │   │   │   ├── KeyDerivation.java
    │   │   │   ├── PasswordGenerator.java
    │   │   │   ├── PasswordStrengthAnalyzer.java
    │   │   │   ├── VaultDecryptionException.java
    │   │   │   ├── VaultEncryptionException.java
    │   │   │   └── VaultSession.java
    │   │   ├── i18n/
    │   │   │   └── LanguageManager.java
    │   │   ├── sync/
    │   │   │   ├── ConflictResolver.java
    │   │   │   ├── LocalRepository.java
    │   │   │   ├── SFTPRepository.java
    │   │   │   └── SyncService.java
    │   │   ├── ui/
    │   │   │   ├── EntryDialog.java
    │   │   │   ├── LoginFrame.java
    │   │   │   ├── MainFrame.java
    │   │   │   ├── PasswordGeneratorDialog.java
    │   │   │   ├── SettingsDialog.java
    │   │   │   ├── StrengthBarHelper.java
    │   │   │   └── VaultPanel.java
    │   │   ├── util/
    │   │   │   ├── DateUtils.java
    │   │   │   ├── FileSecurityUtils.java
    │   │   │   ├── PasswordValidator.java
    │   │   │   └── SecureWiper.java
    │   │   └── vault/
    │   │       ├── SortField.java
    │   │       ├── Vault.java
    │   │       ├── VaultEntry.java
    │   │       ├── VaultExporter.java
    │   │       ├── VaultImporter.java
    │   │       ├── VaultLoadResult.java
    │   │       ├── VaultManager.java
    │   │       └── VaultService.java
    │   └── resources/
    │       └── i18n/
    │           ├── messages_en.properties
    │           └── messages_fr.properties
    └── test/
        └── java/com/passwordmanager/
            ├── config/
            │   └── ConfigManagerTest.java
            ├── crypto/
            │   ├── CryptoServiceTest.java
            │   ├── PasswordGeneratorTest.java
            │   └── PasswordStrengthAnalyzerTest.java
            ├── security/
            │   └── SecurityAuditTest.java
            ├── util/
            │   └── PasswordValidatorTest.java
            └── vault/
                ├── VaultExporterTest.java
                ├── VaultImporterTest.java
                ├── VaultManagerIntegrationTest.java
                └── VaultServiceTest.java
```
