# Password Manager

Gestionnaire de mots de passe multiplateforme securise. Stocke, organise et protege vos identifiants, codes PIN d'applications et cles SSH dans un coffre-fort chiffre AES-256-GCM avec chiffrement par enveloppe DEK/KEK. Disponible sur **desktop** (Windows, Linux, macOS) et **Android**. Interface a onglets bilingue francais/anglais.

## Fonctionnalites

- **3 types d'entrees** :
  - **Mots de passe** : identifiant, email, pseudo, mot de passe, URL, categorie, tags (onglet dedie)
  - **Applications** : nom d'utilisateur et code PIN (onglet dedie)
  - **Cles SSH** : cle privee, cle publique, type, empreinte, generation (ED25519/RSA) et import PEM (desktop : onglet dedie, Android : ecran dedie dans les parametres)
- Coffre-fort chiffre par utilisateur (AES-256-GCM avec AAD, PBKDF2 600 000 iterations)
- Chiffrement par enveloppe DEK/KEK (changement de mot de passe instantane)
- Generateur de mots de passe cryptographiquement sur (SecureRandom)
- Analyse de securite (mots de passe faibles, reutilises, anciens, **compromis HIBP**) — s'applique aux mots de passe uniquement
- Systeme de **favoris** avec tri prioritaire et operations en masse (tous types d'entrees)
- **Filtres avances** combinables (categorie, force, date, favoris, recherche textuelle) — categories et tags exclusifs aux mots de passe
- **Favicons** des sites web (Google Favicon API + cache disque)
- Import/export unifie (CSV, JSON, coffre chiffre) avec popup parametrable et import par fusion — CSV avec colonne `type` (retrocompatible), JSON gere les 3 listes automatiquement (cles SSH incluses en JSON et .enc, pas en CSV), CSV conforme RFC 4180
- Synchronisation SFTP **bidirectionnelle** avec fusion par entree et resolution de conflits (tous types d'entrees)
- Interface a onglets bilingue francais / anglais
- Themes Systeme, Clair et Sombre
- Verrouillage automatique apres inactivite
- Effacement securise du presse-papiers (`SecureClipboard` avec `char[]`, efface a la perte de propriete)
- Protection anti brute-force sur l'ecran de connexion
- Verification semi-automatique des mises a jour via l'API GitHub Releases
- **Desktop** : JTabbedPane avec 3 onglets (Mots de passe, Applications, Cles SSH) avec generation/import de cles SSH
- **Desktop** : distribution autonome avec JRE embarque (aucune installation Java requise)
- **Desktop** : selection multiple, menu "Actions..." (suppression, categorie, favoris en masse) et menu contextuel (clic droit)
- **Desktop** : boutons de copie en ligne dans le panneau de details (identifiant, email, pseudo, mot de passe, URL)
- **Android** : application native Jetpack Compose / Material 3 avec injection de dependances Hilt (APK)
- **Android** : HorizontalPager avec TabRow (2 onglets)
- **Android** : gestion des cles SSH (ecran dedie dans les parametres)
- **Android** : gestion des categories (ajout, suppression avec reassignation)
- **Android** : service d'auto-remplissage (Autofill API 26+) — s'applique aux mots de passe uniquement
- **Android** : deverrouillage biometrique (empreinte digitale) — activable dans les parametres, desactive par defaut
- **Android** : verrouillage automatique a l'extinction de l'ecran

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
# Tests core + desktop (JVM)
./gradlew :core:test :desktop:test

# Tests Android (JVM local)
./gradlew :android:testDebugUnitTest

# Tous les tests
./gradlew :core:test :desktop:test :android:testDebugUnitTest
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
- **Panneau gauche** (180 px) : liste des categories avec filtrage au clic (mots de passe uniquement)
- **Panneau central** : JTabbedPane avec 3 onglets (**Mots de passe**, **Applications**, **Cles SSH**) + barre de recherche en temps reel + filtres avances (le filtre favoris s'applique meme quand le panneau de filtres est replie) + tableau des entrees avec tri par clic sur les en-tetes de colonnes + menu "Actions..." en masse (visible quand >1 entree selectionnee)
- **Panneau droit** (300 px) : details de l'entree selectionnee avec boutons copier en ligne (champs adaptes au type d'entree)
- **Menu contextuel** (clic droit) : modifier, supprimer, dupliquer, copier mot de passe/identifiant/email/URL, ouvrir l'URL
- **Barre de notification** : mise a jour disponible (barre jaune en haut, masquable)
- **Barre de statut** : statut de synchronisation, utilisateur connecte, nombre d'entrees (mis a jour dynamiquement)

### Android

Apres connexion, l'interface se compose de :

- **TopAppBar** : titre, recherche, tri (9 criteres), menu (import/export, sync, audit, parametres, verrouiller)
- **TabRow + HorizontalPager** : 2 onglets (**Mots de passe**, **Applications**)
- **Dropdown categories** pour le filtrage par categorie (onglet mots de passe uniquement)
- **Liste scrollable** des entrees avec favicon (ou avatar lettre), etoile favori, indicateur de force (mots de passe) et selection multiple (appui long) avec menu "Actions..." (suppression, categorie, favoris en masse)
- **FAB** pour nouvelle entree (adapte au type de l'onglet actif)
- **Notification** de mise a jour disponible au lancement (dialog avec lien vers la release GitHub)
- **Navigation** : ecrans detail (champs adaptes au type), edition, generateur, parametres (SFTP, gestion des categories), audit (mots de passe uniquement)

### Fonctionnalites communes

| Fonctionnalite | Desktop | Android |
|---|---|---|
| Interface a onglets (3 types dans les onglets) | Oui (JTabbedPane, 3 onglets) | Oui (TabRow + HorizontalPager, 2 onglets + ecran dedie SSH) |
| Mots de passe (identifiant, email, URL, categorie, tags) | Oui | Oui |
| Applications (nom d'utilisateur, code PIN) | Oui | Oui |
| Cles SSH (cle privee, cle publique, type, empreinte) | Oui (onglet dedie) | Oui (ecran dedie dans les parametres) |
| CRUD entrees (tous types) | Oui | Oui |
| Dupliquer une entree | Oui (3 types, clic droit) | Oui (mots de passe et applications) |
| Favoris (etoile, tri prioritaire, tous types) | Oui | Oui |
| Filtres avances (categorie, force, date, favoris) | Oui | Oui |
| Favicons des sites web | Oui | Oui |
| Generateur de mots de passe | Oui | Oui |
| Analyse de securite + HIBP (mots de passe uniquement) | Oui | Oui |
| Import/export unifie (CSV/JSON/chiffre) | Oui | Oui (via SAF) |
| Recherche et tri (9 criteres, tous types) | Oui | Oui |
| Selection multiple + actions en masse (tous types) | Oui (menu "Actions...") | Oui |
| Menu contextuel (clic droit) | Oui | Non |
| Gestion des categories (ajout et suppression) | Oui (panneau lateral) | Oui (ecran dedie) |
| Verification des mises a jour | Oui (auto + manuel) | Oui (au lancement) |
| Themes Systeme/Clair/Sombre | Oui (FlatLaf) | Oui (Material 3 / Dynamic Colors) |
| Deverrouillage biometrique (empreinte digitale) | Non | Oui (BiometricPrompt + AndroidKeyStore) |
| Verrouillage automatique | Oui | Oui |
| Verrouillage ecran eteint | Non | Oui |
| Synchronisation SFTP bidirectionnelle (tous types) | Oui (cle fichier ou cle du coffre) | Oui |
| Resolution de conflits (fusion par entree, tous types) | Oui | Oui |
| Service d'auto-remplissage (mots de passe uniquement) | Non | Oui (API 26+) |
| URL cliquable dans le detail | Oui | Oui |

### Generateur de mots de passe

Options : longueur (8-128), majuscules, minuscules, chiffres, caracteres speciaux, exclusion des caracteres ambigus (0/O, 1/l/I). Garantit au moins un caractere de chaque type active.

### Analyse de securite (mots de passe uniquement)

Genere un rapport sur :

- Mots de passe **faibles** (score insuffisant)
- Mots de passe **reutilises** (partages entre plusieurs entrees)
- Mots de passe **anciens** (non modifies depuis 180+ jours, configurable)
- Mots de passe **compromis** (verification HIBP via k-Anonymity, declenchee manuellement)

Indicateur de force : Faible (rouge), Moyen (orange), Fort (vert), Tres fort (bleu).

---

## Import / Export

Import et export unifies via une popup parametrable proposant 3 formats : **CSV**, **JSON** et **coffre chiffre (.enc)**. Les 3 types d'entrees sont geres : le CSV inclut une colonne `type` pour distinguer mots de passe et applications (retrocompatible : un CSV sans colonne `type` est importe comme mots de passe) ; le JSON gere automatiquement les 3 listes (mots de passe, applications, cles SSH). Les cles SSH sont incluses en JSON et .enc uniquement, pas en CSV. Parseur CSV conforme **RFC 4180** (supporte les retours a la ligne entre guillemets). Detection automatique du separateur (`,` ou `;`) et des colonnes via alias multilingues (incluant email et pseudo). Limite : 10 000 entrees par import. Pour CSV et JSON, les donnees exportees ne sont **pas chiffrees** (avertissement affiche). Protection anti-injection de formules pour les tableurs. L'import d'un coffre chiffre demande le mot de passe maitre du coffre source (avec option afficher/masquer). L'import fonctionne par **fusion** : les entrees importees sont ajoutees au coffre existant sans ecraser les donnees en place.

Sur Android, l'import/export utilise le Storage Access Framework (SAF) — selecteur de fichiers systeme.

---

## Synchronisation SFTP

Disponible sur **desktop** et **Android**.

- Synchronisation **bidirectionnelle** avec fusion par entree (`EntryMerger` generique, tous types)
- Comparaison par empreinte SHA-256 (local vs distant)
- Mode hors-ligne : modifications mises en attente automatiquement (fichier `.pending`, desktop)
- Resolution de conflits interactive : vue cote-a-cote local/distant par entree avec champs adaptes au type (`ConflictResolutionDialog`)
- Fusion automatique si aucun conflit (entrees uniquement locales + uniquement distantes)
- Authentification par cle SSH uniquement (fichier ou cle du coffre-fort sur desktop)
- `StrictHostKeyChecking` active (`yes` avec known_hosts, ou `accept-new` pour la premiere connexion)

---

## Parametres

| Parametre | Desktop | Android |
|---|---|---|
| Langue (FR/EN) | Oui | Oui (via AppCompatDelegate + locales_config) |
| Theme (Systeme/Clair/Sombre) | Oui | Oui |
| Verrouillage automatique (1-60 min) | Oui | Oui |
| Effacement presse-papiers (5-120 s) | Oui | Oui |
| Deverrouillage biometrique | Non | Oui (toggle ON/OFF) |
| Configuration SFTP | Oui | Oui |

Changement de mot de passe maitre : seule la DEK est re-chiffree (operation quasi instantanee). Les donnees biometriques sont automatiquement invalidees.

---

## Raccourcis clavier (desktop)

| Raccourci | Action |
|---|---|
| `Ctrl+N` | Nouvelle entree |
| `Suppr` | Supprimer l'entree selectionnee |
| `F5` | Actualiser l'affichage |
| `Entree` | Se connecter (ecran de connexion) |
| Double-clic | Modifier l'entree |
| Clic droit | Menu contextuel (modifier, supprimer, copier, ouvrir URL, dupliquer) |

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
| Barriere anti-optimisation | Lecture volatile par accumulateur sur tout le tableau apres `Arrays.fill()` (empeche le JIT dead-store elimination) |
| GCM AAD | Le chiffrement AES-256-GCM des donnees du coffre lie la version (`"2.0"`) en tant qu'Additional Authenticated Data, empechant la substitution de parametres |
| Copies defensives | `PasswordEntry.getPassword()`, `AppEntry.getPin()`, `SshKeyEntry.getPrivateKey()` et `VaultSession.getSalt/getKekIv/getEncryptedDek()` retournent des clones |
| Nettoyage de session | `VaultSession` implemente `Destroyable` et `AutoCloseable` |
| Presse-papiers securise | `SecureClipboard` (desktop) : stocke en `char[]` via `Transferable` personnalise, efface sur `lostOwnership()` |
| Presse-papiers sensible | Flag `EXTRA_IS_SENSITIVE` (Android 13+) pour masquer le contenu copie |
| Presse-papiers lifecycle | Efface automatiquement apres delai configure + au verrouillage + a la fermeture + a l'extinction de l'ecran (Android) |
| Permissions fichiers | POSIX `rw-------` (600) / `rwx------` (700) ; ACL owner-only sur Windows |
| Ecriture atomique | Fichier temporaire + permissions + `Files.move(ATOMIC_MOVE)` |
| Validation des entrees | Regex username, path traversal sur noms de fichiers (local et SFTP) |
| Anti brute-force | Backoff progressif apres 3 echecs (jusqu'a 30s), compteur par utilisateur |
| Masquage temporaire | Mot de passe re-masque automatiquement apres 30 secondes |
| Validation JSON vault | Null-checks sur les champs obligatoires du fichier `.enc` (corruption detectee) |
| Nettoyage ViewModel | `onCleared()` efface les donnees sensibles dans `EntryEditViewModel`, `EntryDetailViewModel`, `AppDetailViewModel`, `ChangeMasterPasswordViewModel`, `SettingsViewModel` |
| Thread safety | `VaultService` : toutes les methodes publiques `synchronized` ; `SessionHolder` : `@Volatile` + `@Synchronized` ; `AutoLockManager.lastActivity` : `volatile` ; `VaultListViewModel.pendingPasswordConflicts` : `@Volatile` |
| Collections non-modifiables | `Vault.getEntries()`/`getCategories()`/`getSettings()` retournent des vues non-modifiables ; `*Mutable()` pour l'acces en mutation |
| Post-deserialisation | `Vault.ensureInitialized()` garantit les collections non-null apres deserialisation Gson |
| Biometrie sans String | `BiometricHelper` utilise `CharBuffer`/`ByteBuffer` pour chiffrer/dechiffrer le mot de passe sans intermediaire `String` |
| SFTP path traversal | `SFTPRepository.validateLocalPath()` verifie le chemin canonique contre le repertoire vaults |
| Autofill domain matching | Correspondance par suffixe de domaine (`endsWith`) au lieu de `contains` |
| Transfert mot de passe genere | `GeneratedPasswordHolder` singleton thread-safe (remplace `savedStateHandle`) |
| Cache favicon borne | Limite a 50 entrees avec eviction des plus anciennes |
| Limite taille fichier | Rejet des fichiers coffre > 50 Mo au chargement |
| Rejet mots de passe courants | 44 mots de passe connus rejetes (comparaison a temps constant) |
| Verification mises a jour | API GitHub limitee a 1 Mo de reponse, URLs validees (`https://github.com/` uniquement) |
| Checksums releases | SHA256SUMS.txt genere et publie avec chaque release |

### Aucune recuperation

Par conception, aucun mecanisme de recuperation du mot de passe maitre n'existe. Il est recommande de conserver une sauvegarde chiffree en lieu sur.

---

## Architecture technique

### Technologies

| Module | Technologie | Version |
|---|---|---|
| `:core` | Java | 17 |
| `:desktop` | Swing + FlatLaf | 3.7 |
| `:android` | Kotlin + Jetpack Compose + Material 3 + Hilt | Kotlin 2.1, Compose BOM 2024.12, Hilt 2.54 |
| Serialisation JSON | Gson | 2.13.2 |
| Connexion SFTP | JSch (fork mwiede) | 2.27.8 |
| Tests | JUnit 5 (Jupiter) | 5.14.2 |
| Build | Gradle (Kotlin DSL) | 8.11 |
| CI/CD | GitHub Actions | - |

### Structure multi-module

```
password-manager/
|-- core/                              # Logique metier partagee (Java 17)
|   +-- crypto/                        # CryptoService, KeyDerivation, VaultSession, PasswordGenerator
|   +-- vault/                         # VaultItem (abstract), PasswordEntry, AppEntry, SshKeyEntry
|   |                                  # Vault, BaseVaultService, PasswordService, AppService
|   |                                  # VaultManager, EntryFilter, VaultImporter, VaultExporter
|   +-- security/                      # HibpChecker (detection de mots de passe compromis)
|   +-- sync/                          # EntryMerger (fusion bidirectionnelle generique, tous types)
|   +-- config/                        # AppConfig, ConfigManager, ConfigEncryptor
|   +-- update/                        # UpdateChecker, UpdateInfo, VersionComparator
|   +-- util/                          # SecureWiper, FileSecurityUtils, PasswordValidator, FaviconService
|   +-- i18n/                          # LanguageManager (FR/EN)
|
|-- desktop/                           # Interface Swing (Java 17)
|   +-- ui/                            # LoginFrame, MainFrame, VaultPanel, SecureClipboard, dialogs
|   +-- sync/                          # SyncService, LocalRepository, SFTPRepository
|   +-- update/                        # DesktopUpdateManager
|
+-- android/                           # Interface Jetpack Compose (Kotlin)
    +-- autofill/                      # PasswordManagerAutofillService (API 26+)
    +-- data/                          # AndroidVaultRepository, ConfigRepository, SessionHolder, BiometricHelper, FaviconRepository
    +-- di/                            # AppModule (Hilt DI)
    +-- ui/                            # Ecrans Compose (login, vault, generator, settings, audit, sync)
    +-- update/                        # AndroidUpdateManager
```

### Tests

**441+ tests** unitaires et d'integration dans `:core`, `:desktop` et `:android` :

| Module | Classe de test | Tests | Description |
|---|---|:---:|---|
| vault | `VaultServiceTest` | 33 | CRUD mots de passe, recherche, tri, favoris, filtre, doublons, categories, timestamps |
| vault | `VaultImporterTest` | 33 | CSV (separateurs, alias, BOM, sanitisation, favoris, RFC 4180, round-trip multi-type), JSON, limites |
| security | `SecurityAuditTest` | 31 | IV, KDF, memoire, permissions, format, import/export, generateur |
| sync | `SyncServiceTest` | 31 | Synchronisation, hash, conflits, mode hors-ligne, mode local (tous types) |
| **android** | `LoginViewModelTest` | 27 | Etat initial, biometrie, selection utilisateur, login, enrollment, creation utilisateur |
| vault | `VaultTest` | 20 | Constructeurs, add/remove (3 types), unmodifiable views, mutable accessors, wipe, ensureInitialized, settings |
| sync | `LocalRepositoryTest` | 17 | Path traversal, CRUD, pending, backups |
| vault | `VaultManagerIntegrationTest` | 16 | Cycle complet avec vraie crypto, validation username |
| util | `PasswordValidatorTest` | 15 | Politique mot de passe maitre, rejet des mots de passe courants |
| crypto | `CryptoServiceTest` | 14 | Enveloppe DEK/KEK, chiffrement, AAD, changement mdp, tampering, legacy |
| update | `VersionComparatorTest` | 13 | Comparaison semantique, pre-release, null, egalite |
| **android** | `SettingsViewModelTest` | 13 | Configuration initiale, theme, langue, auto-lock, clipboard, toggle biometrique |
| config | `ConfigEncryptorTest` | 11 | Round-trip, caracteres speciaux, corruption, unicite IV |
| **android** | `GeneratorViewModelTest` | 11 | Etat initial, generation, longueur, toggles, force, nettoyage |
| sync | `EntryMergerTest` | 11 | Fusion locale/distante generique, conflits, entrees identiques (tous types) |
| sync | `SFTPRepositoryTest` | 10 | Validation filename sur 4 methodes publiques |
| crypto | `VaultSessionTest` | 10 | Destroy idempotent, AutoCloseable, copies defensives, updateEnvelope |
| vault | `VaultExporterTest` | 9 | CSV multi-types, JSON multi-types, injection, favoris, round-trip |
| crypto | `PasswordStrengthAnalyzerTest` | 9 | Niveaux de force, score, cas limites |
| **android** | `EntryEditViewModelTest` | 9 | Formulaire CRUD, sauvegarde, validation, toggle favori |
| **android** | `ChangeMasterPasswordViewModelTest` | 9 | Validation, mismatch, nettoyage onCleared, invalidation biometrique |
| vault | `SshKeyEntryTest` | 8 | Constructeur, copies defensives, wipe, equals/hashCode, tombstone |
| vault | `SshKeyServiceTest` | 10 | CRUD cles SSH, recherche (titre/type/empreinte), tri, favoris, operations en masse |
| vault | `AppServiceTest` | 7 | CRUD applications, recherche, favoris, operations en masse |
| vault | `EntryFilterTest` | 7 | Filtres combines (categorie, force, date, favoris, texte) |
| crypto | `KeyDerivationTest` | 7 | Generation de cle, unicite du sel, iterations, SecureRandom partage |
| vault | `AppEntryTest` | 6 | Constructeur, copies defensives pin, wipe, equals/hashCode |
| i18n | `LanguageManagerTest` | 6 | Singleton, getString, setLanguage, langues disponibles |
| **desktop** | `AutoLockManagerTest` | 5 | Idempotence start, cleanup, lifecycle, listener |
| util | `FaviconServiceTest` | 5 | Extraction domaine, cache disque, favicon null |
| security | `HibpCheckerTest` | 5 | Null, vide, entree valide, caracteres speciaux, unicode |
| **android** | `SecurityAuditViewModelTest` | 5 | Audit vide, faibles, dupliques, anciens, total |
| **android** | `EntryDetailViewModelTest` | 5 | Chargement, visibilite, suppression |
| util | `DateUtilsTest` | 5 | ISO 8601, round-trip, parsing valide/invalide/null |
| crypto | `PasswordGeneratorTest` | 5 | Longueur, types, exclusion ambigus |
| config | `ConfigManagerTest` | 3 | Valeurs par defaut, persistance |
| | | **~441** | |

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
