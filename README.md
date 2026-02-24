# Password Manager

Gestionnaire de mots de passe multiplateforme securise. Stocke, organise et protege vos identifiants dans un coffre-fort chiffre AES-256-GCM avec chiffrement par enveloppe DEK/KEK. Disponible sur **desktop** (Windows, Linux, macOS) et **Android**. Interface bilingue francais/anglais.

## Fonctionnalites

- Coffre-fort chiffre par utilisateur (AES-256-GCM, PBKDF2 600 000 iterations)
- Chiffrement par enveloppe DEK/KEK (changement de mot de passe instantane)
- Generateur de mots de passe cryptographiquement sur (SecureRandom)
- Analyse de securite (mots de passe faibles, reutilises, anciens)
- Import/export CSV et JSON avec detection automatique du format
- Sauvegarde chiffree exportable
- Synchronisation SFTP avec gestion des conflits et mode hors-ligne (desktop)
- Interface bilingue francais / anglais
- Themes Systeme, Clair et Sombre
- Verrouillage automatique apres inactivite
- Effacement automatique du presse-papiers
- Protection anti brute-force sur l'ecran de connexion
- **Desktop** : distribution autonome avec JRE embarque (aucune installation Java requise)
- **Android** : application native Jetpack Compose / Material 3 (APK)

---

## Telechargement

Les releases pretes a l'emploi sont disponibles sur la page [Releases](../../releases).

### Desktop

Chaque archive desktop contient un JRE embarque — aucune installation de Java n'est necessaire.

| Plateforme | Archive | Systemes supportes |
|---|---|---|
| Linux x64 | `password-manager-<version>-linux-x64.tar.gz` | Ubuntu, Debian, Fedora, Arch, etc. (glibc) |
| Windows x64 | `password-manager-<version>-windows-x64.zip` | Windows 10 / 11 |
| macOS ARM | `password-manager-<version>-macos-aarch64.tar.gz` | macOS 14+ (Apple Silicon M1/M2/M3/M4) |

**Installation desktop :**

1. Telecharger l'archive correspondant a votre systeme
2. Extraire l'archive
3. Lancer l'application :

```bash
# Linux / macOS
./run.sh

# Windows
run.bat
```

### Android

| Plateforme | Archive | Systemes supportes |
|---|---|---|
| Android | `password-manager-<version>-android.apk` | Android 8.0+ (API 26) |

**Installation Android :**

1. Telecharger l'APK depuis la page Releases
2. Activer "Sources inconnues" dans les parametres Android (si necessaire)
3. Installer l'APK et lancer l'application

---

## Compilation depuis les sources

### Prerequis

| Composant | Version requise | Necessaire pour |
|---|---|---|
| Java (JDK) | 21 ou superieur | Desktop |
| Android SDK | API 35 | Android |
| Gradle | 8.11+ (wrapper inclus) | Tous |

### Architecture multi-module

```
password-manager/
  :core       # Logique metier, crypto, vault (Java 17)
  :desktop    # Interface Swing + FlatLaf (Java 17)
  :android    # Interface Jetpack Compose / Material 3 (Kotlin)
```

### Compilation et lancement (desktop)

```bash
./gradlew :desktop:fatJar
java -jar desktop/build/libs/password-manager.jar
```

### Compilation Android (APK)

```bash
./gradlew :android:assembleDebug
# APK dans android/build/outputs/apk/debug/
```

### Execution des tests

```bash
./gradlew :core:test :desktop:test
```

---

## Premier lancement

Au premier lancement, aucun utilisateur n'existe. Cliquez sur **Creer un nouvel utilisateur** puis :

1. Saisissez un nom d'utilisateur (lettres, chiffres et underscores uniquement)
2. Choisissez un **mot de passe maitre** respectant les exigences :
   - Minimum **12 caracteres**
   - Au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractere special
   - Pas dans la liste des mots de passe courants
3. Confirmez le mot de passe et validez

> **Attention** : en cas d'oubli du mot de passe maitre, aucune recuperation n'est possible. Le chiffrement AES-256-GCM rend les donnees definitivement inaccessibles sans ce mot de passe.

---

## Interface

### Desktop (Windows, Linux, macOS)

Apres connexion, l'interface se compose de :

- **Barre de menus** : Fichier, Edition, Affichage, Outils, Aide
- **Barre d'outils** : Nouvelle entree, Generateur, Synchroniser, Verrouiller
- **Panneau gauche** (180 px) : liste des categories avec filtrage au clic
- **Panneau central** : barre de recherche en temps reel + tableau des entrees (Titre, Identifiant, Categorie, Force)
- **Panneau droit** (300 px) : details de l'entree selectionnee avec boutons copier identifiant/mot de passe
- **Barre de statut** : statut de synchronisation, utilisateur connecte, nombre d'entrees

### Android

Apres connexion, l'interface se compose de :

- **TopAppBar** : titre, recherche, menu (import/export, audit, parametres, verrouiller)
- **FilterChips** horizontaux pour les categories
- **Liste scrollable** des entrees avec indicateur de force
- **FAB** pour nouvelle entree
- **Navigation** : ecrans detail, edition, generateur, parametres, audit

### Fonctionnalites communes

| Fonctionnalite | Desktop | Android |
|---|---|---|
| CRUD entrees | Oui | Oui |
| Generateur de mots de passe | Oui | Oui |
| Analyse de securite | Oui | Oui |
| Import/export CSV/JSON | Oui | Oui (via SAF) |
| Sauvegarde chiffree | Oui | Oui |
| Recherche et tri | Oui | Oui |
| Themes Systeme/Clair/Sombre | Oui (FlatLaf) | Oui (Material 3 / Dynamic Colors) |
| Verrouillage automatique | Oui | Oui |
| Synchronisation SFTP | Oui | Non |

### Generateur de mots de passe

Options : longueur (8-128), majuscules, minuscules, chiffres, caracteres speciaux, exclusion des caracteres ambigus (0/O, 1/l/I). Garantit au moins un caractere de chaque type active.

### Analyse de securite

Genere un rapport sur :

- Mots de passe **faibles** (score insuffisant)
- Mots de passe **reutilises** (partages entre plusieurs entrees)
- Mots de passe **anciens** (non modifies depuis 180+ jours, configurable)

Indicateur de force : Faible (rouge), Moyen (orange), Fort (vert), Tres fort (bleu).

---

## Import / Export

Detection automatique du separateur (`,` ou `;`) et des colonnes via alias multilingues. Limite : 10 000 entrees par import. Les donnees exportees ne sont **pas chiffrees** (avertissement affiche). Protection anti-injection de formules pour les tableurs.

Sur Android, l'import/export utilise le Storage Access Framework (SAF) — selecteur de fichiers systeme.

---

## Synchronisation SFTP (desktop uniquement)

- Comparaison par empreinte SHA-256 (local vs distant)
- Mode hors-ligne : modifications mises en attente automatiquement (fichier `.pending`)
- Gestion des conflits : garder local, garder distant, ou sauvegarder les deux versions
- Authentification par cle SSH uniquement

---

## Parametres

| Parametre | Desktop | Android |
|---|---|---|
| Langue (FR/EN) | Oui | Oui |
| Theme (Systeme/Clair/Sombre) | Oui | Oui |
| Repertoire des coffres | Oui | Non (interne) |
| Verrouillage automatique (1-60 min) | Oui | Oui |
| Effacement presse-papiers (5-120 s) | Oui | Oui |
| Configuration SFTP | Oui | Non |

Changement de mot de passe maitre : seule la DEK est re-chiffree (operation quasi instantanee).

---

## Raccourcis clavier (desktop)

| Raccourci | Action |
|---|---|
| `Ctrl+N` | Nouvelle entree |
| `Suppr` | Supprimer l'entree selectionnee |
| `F5` | Actualiser l'affichage |
| `Entree` | Se connecter (ecran de connexion) |
| Double-clic | Modifier l'entree |

---

## Securite

### Chiffrement par enveloppe (DEK/KEK)

```
Mot de passe maitre
    |-- PBKDF2-HMAC-SHA256 (600 000 iterations, sel 32 octets)
    |       |-- KEK (Key Encryption Key)
    |              |-- AES-256-GCM --> DEK chiffree (stockee dans .enc)
    |
    +-- [efface immediatement]

DEK (Data Encryption Key) -- AES-256, 32 octets aleatoires
    |-- AES-256-GCM --> donnees du coffre chiffrees
```

### Mesures de securite

| Mesure | Implementation |
|---|---|
| Mots de passe en `char[]` | Jamais de `String`, effacement explicite via `SecureWiper` |
| Barriere anti-optimisation | Lecture volatile apres `Arrays.fill()` pour empecher le JIT dead-store elimination |
| Copies defensives | `VaultEntry.getPassword()` et `VaultSession.getSalt/getKekIv/getEncryptedDek()` retournent des clones |
| Nettoyage de session | `VaultSession` implemente `Destroyable` et `AutoCloseable` |
| Permissions fichiers | POSIX `rw-------` (600) / `rwx------` (700) ; ACL owner-only sur Windows |
| Ecriture atomique | Fichier temporaire + permissions + `Files.move(ATOMIC_MOVE)` |
| Validation des entrees | Regex username, path traversal sur noms de fichiers (local et SFTP) |
| Anti brute-force | Backoff progressif apres 3 echecs (jusqu'a 30s), compteur par utilisateur |
| Masquage temporaire | Mot de passe re-masque automatiquement apres 30 secondes |
| Presse-papiers | Efface automatiquement apres delai configure + au verrouillage + a la fermeture |

### Aucune recuperation

Par conception, aucun mecanisme de recuperation du mot de passe maitre n'existe. Il est recommande de conserver une sauvegarde chiffree en lieu sur.

---

## Architecture technique

### Technologies

| Module | Technologie | Version |
|---|---|---|
| `:core` | Java | 17 |
| `:desktop` | Swing + FlatLaf | 3.7 |
| `:android` | Kotlin + Jetpack Compose + Material 3 | Kotlin 2.1, Compose BOM 2024.12 |
| Serialisation JSON | Gson | 2.13.2 |
| Connexion SFTP | JSch (fork mwiede) | 2.27.8 |
| Tests | JUnit 5 (Jupiter) | 5.14.2 |
| Build | Gradle (Kotlin DSL) | 8.11 |
| CI/CD | GitHub Actions | - |

### Structure multi-module

```
password-manager/
|-- core/                              # Logique metier partagee (Java 17)
|   +-- crypto/                        # CryptoService, KeyDerivation, VaultSession
|   +-- vault/                         # Vault, VaultEntry, VaultManager, VaultService
|   +-- config/                        # AppConfig, ConfigManager, ConfigEncryptor
|   +-- sync/                          # SyncService, LocalRepository, SFTPRepository
|   +-- util/                          # SecureWiper, FileSecurityUtils, PasswordValidator
|   +-- i18n/                          # LanguageManager (FR/EN)
|
|-- desktop/                           # Interface Swing (Java 17)
|   +-- ui/                            # LoginFrame, MainFrame, VaultPanel, dialogs
|
+-- android/                           # Interface Jetpack Compose (Kotlin)
    +-- data/                          # AndroidVaultRepository, AndroidConfigRepository, SessionHolder
    +-- ui/                            # Ecrans Compose (login, vault, generator, settings, audit)
```

### Tests

**150 tests** unitaires et d'integration dans `:core` et `:desktop` :

| Module | Classe de test | Tests | Description |
|---|---|:---:|---|
| security | `SecurityAuditTest` | 31 | IV, KDF, memoire, permissions, format, import/export, generateur |
| sync | `LocalRepositoryTest` | 17 | Path traversal, CRUD, pending, backups |
| vault | `VaultManagerIntegrationTest` | 16 | Cycle complet avec vraie crypto, validation username |
| vault | `VaultServiceTest` | 13 | CRUD, recherche, tri, doublons, categories |
| config | `ConfigEncryptorTest` | 11 | Round-trip, caracteres speciaux, corruption, unicite IV |
| vault | `VaultImporterTest` | 10 | CSV (separateurs, alias), JSON, limites |
| sync | `SFTPRepositoryTest` | 10 | Validation filename sur 4 methodes publiques |
| util | `PasswordValidatorTest` | 9 | Politique mot de passe maitre |
| crypto | `PasswordStrengthAnalyzerTest` | 9 | Niveaux de force, score, cas limites |
| crypto | `CryptoServiceTest` | 8 | Enveloppe DEK/KEK, chiffrement, changement mdp |
| vault | `VaultExporterTest` | 8 | CSV, JSON, injection, round-trip |
| crypto | `PasswordGeneratorTest` | 5 | Longueur, types, exclusion ambigus |
| config | `ConfigManagerTest` | 3 | Valeurs par defaut, persistance |
| | | **150** | |

---

## Structure des fichiers utilisateur

```
~/.password-manager/
+-- data/
    +-- .config_key                    # Materiau de cle (64 octets, permissions 600)
    +-- config.properties              # Configuration (champs SFTP chiffres)
    +-- vaults/                        # Repertoire des coffres (permissions 700)
        +-- vault_alice.enc            # Coffre chiffre
        +-- vault_alice.enc.bak        # Sauvegarde automatique (3 max)
        +-- vault_bob.enc
```

### Format du fichier coffre (v2.0)

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

---

## Documentation

- [Documentation fonctionnelle](docs/FUNCTIONAL.md) — guide utilisateur detaille
- [Documentation technique](docs/TECHNICAL.md) — architecture, cryptographie, API internes
- [Documentation CI/CD](docs/CI-RELEASE.md) — workflow GitHub Actions et releases

---

## Licence

Usage prive.
