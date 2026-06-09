# Password Manager

Gestionnaire de mots de passe multiplateforme securise. Stocke, organise et protege vos identifiants, codes PIN d'applications et cles SSH dans un coffre-fort chiffre AES-256-GCM avec chiffrement par enveloppe DEK/KEK. Disponible sur **desktop** (Windows, Linux, macOS) et **Android**. Interface bilingue francais/anglais.

## Fonctionnalites

- **3 types d'entrees** :
  - **Mots de passe** : identifiant, email, pseudo, mot de passe, URL, categorie, tags (page dediee)
  - **Applications** : nom d'utilisateur et code PIN (page dediee)
  - **Cles SSH** : cle privee, cle publique, type, empreinte, generation (ED25519/RSA) et import PEM (gerees dans un onglet des parametres, sur desktop comme sur Android)
- Coffre-fort chiffre par utilisateur (AES-256-GCM avec AAD, PBKDF2 600 000 iterations)
- Chiffrement par enveloppe DEK/KEK (changement de mot de passe instantane)
- Generateur de mots de passe cryptographiquement sur (SecureRandom)
- Analyse de securite (mots de passe faibles, reutilises, anciens, **compromis HIBP**) — s'applique aux mots de passe uniquement
- Systeme de **favoris** avec tri prioritaire et operations en masse (tous types d'entrees)
- **Filtres avances** combinables (categorie, force, date, favoris, recherche textuelle) — categories et tags exclusifs aux mots de passe
- **Favicons** des sites web (recuperes directement depuis `/favicon.ico` du site, en HTTPS, + cache disque — aucun tiers)
- Import/export unifie (CSV, JSON, coffre chiffre) avec popup parametrable et import par fusion — CSV avec colonne `type` (retrocompatible), JSON gere les 3 listes automatiquement (cles SSH incluses en JSON et .enc, pas en CSV), CSV conforme RFC 4180
- Synchronisation SFTP **bidirectionnelle** avec fusion par entree et resolution de conflits (tous types d'entrees)
- **Dossier de travail configurable** (workspace, a la Obsidian) : choix du repertoire des coffres avant connexion et changement depuis les parametres (desktop : n'importe quel dossier, avec migration des coffres existants et liste des dossiers recents ; Android : dossier choisi via le Storage Access Framework)
- Interface bilingue francais / anglais
- Themes Systeme, Clair et Sombre
- Verrouillage automatique apres inactivite
- Effacement securise du presse-papiers (`SecureClipboard` avec `char[]`, efface a la perte de propriete)
- Protection anti brute-force sur l'ecran de connexion
- Verification semi-automatique des mises a jour via l'API GitHub Releases
- **Desktop** : navigation laterale (Mots de passe, Applications, Audit, Parametres) + barre de menus de fenetre ; recherche, tri et filtres integres a chaque page
- **Desktop** : tableau de bord (cartes Entrees / Favoris / Securite) et rangee "Recemment utilises" sur la page Mots de passe
- **Desktop** : distribution autonome avec JRE embarque (aucune installation Java requise)
- **Desktop** : selection multiple, menu "Actions..." (suppression, categorie, favoris en masse), menu contextuel (clic droit) et **navigation au clavier** de la liste (fleches + Entree, anneau de focus)
- **Desktop** : boutons de copie en ligne dans le panneau de details (identifiant, email, pseudo, mot de passe, URL) avec confirmation "Copie ✓"
- **Android** : application native Jetpack Compose / Material 3 avec injection de dependances Hilt (APK)
- **Android** : HorizontalPager (swipe) avec selecteur de page deroulant (Mots de passe / Applications)
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
| Gradle | 8.12 (wrapper inclus) | Tous |

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

Au premier lancement, aucun coffre n'existe. Cliquez sur **Creer un coffre** (a droite du bouton Connexion) puis :

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

- **Barre de menus** (fenetre) : Fichier (Importer, Exporter, Deconnexion, Quitter), Edition (Changer le mot de passe maitre), Outils (Synchroniser, Generateur), Aide (A propos)
- **Barre laterale "NAVIGATION"** : les pages **Mots de passe**, **Applications**, **Audit** et **Parametres** (la page active est surlignee)
- **Page** (centre) : barre de recherche en temps reel + icone **tri** (menu : sens + critere) + icone **filtres** (panneau de chips multi-selection : favoris, categories, force, dates) + bouton **"+ Nouvelle entree"**
  - Sur **Mots de passe** : tableau de bord (cartes **Entrees / Favoris / Securite** /20) et rangee **"Recemment utilises"** au-dessus de la liste
  - **Liste de cartes** (favicon/avatar, titre, sous-titre, badge de force, etoile favori) navigable au clavier (fleches + Entree) avec selection multiple et menu "Actions..." en masse
- **Panneau droit** (~360 px) : details de l'entree selectionnee avec boutons copier en ligne (confirmation "Copie ✓") ; champs adaptes au type
- **Menu contextuel** (clic droit) : modifier, supprimer, dupliquer, copier mot de passe/identifiant/email/URL, ouvrir l'URL
- **Barre de notification** : mise a jour disponible (barre jaune en haut, masquable)
- **Barre de statut** : statut de synchronisation, utilisateur connecte, nombre d'entrees (mis a jour dynamiquement)

Les **categories** et les **cles SSH** se gerent depuis **Parametres** (onglets dedies). L'**Audit** est une page a part entiere (organisee en sections, voir plus bas).

### Android

Apres connexion, l'interface se compose de :

- **TopAppBar** : selecteur de page deroulant (**Mots de passe** / **Applications**), recherche, tri (criteres incl. force et dates creation/modification, sens croissant/decroissant), menu overflow (import/export, sync) — present sur les deux pages
- **HorizontalPager** : navigation entre Mots de passe et Applications par swipe ou via le selecteur de page
- **Filtres** multi-selection (favoris, categories, force, dates) appliques en direct ; aucun selectionne = tout affiche
- **Liste scrollable** des entrees avec favicon (ou avatar lettre), etoile favori, indicateur de force (mots de passe) et selection multiple (appui long) avec menu "Actions..." (suppression, categorie, favoris en masse)
- **FAB** pour nouvelle entree (adapte au type de l'onglet actif)
- **Barre de navigation du bas** : Coffre, Generateur, Audit, Parametres, **Quitter** (deconnexion)
- **Mise a jour** : detectee automatiquement sur l'ecran de connexion (dialog) ; une icone de mise a jour reste accessible en haut a droite tant qu'une version est disponible (rouvre le dialog)
- **Navigation** : ecrans detail (champs adaptes au type), edition, generateur, parametres (SFTP, gestion des categories), audit (mots de passe uniquement)

### Fonctionnalites communes

| Fonctionnalite | Desktop | Android |
|---|---|---|
| Navigation entre types | Oui (barre laterale de navigation) | Oui (selecteur de page deroulant + HorizontalPager, 2 pages + ecran dedie SSH) |
| Mots de passe (identifiant, email, URL, categorie, tags) | Oui | Oui |
| Applications (nom d'utilisateur, code PIN) | Oui | Oui |
| Cles SSH (cle privee, cle publique, type, empreinte) | Oui (onglet dans les parametres) | Oui (ecran dedie dans les parametres) |
| CRUD entrees (tous types) | Oui | Oui |
| Dupliquer une entree | Oui (3 types, clic droit) | Oui (mots de passe et applications) |
| Favoris (etoile, tri prioritaire, tous types) | Oui | Oui |
| Filtres avances (categorie, force, date, favoris) | Oui (chips multi-selection) | Oui (categorie et force multi-selection) |
| Favicons des sites web | Oui | Oui |
| Generateur de mots de passe | Oui | Oui |
| Analyse de securite + HIBP (mots de passe uniquement) | Oui | Oui |
| Import/export unifie (CSV/JSON/chiffre) | Oui | Oui (via SAF) |
| Recherche et tri (9 criteres, tous types) | Oui | Oui |
| Selection multiple + actions en masse (tous types) | Oui (menu "Actions...") | Oui |
| Menu contextuel (clic droit) | Oui | Non |
| Gestion des categories (ajout et suppression) | Oui (onglet des parametres) | Oui (ecran dedie) |
| Verification des mises a jour | Oui (auto + manuel) | Oui (auto a la connexion + icone persistante) |
| Themes Systeme/Clair/Sombre | Oui (FlatLaf) | Oui (Material 3 / Dynamic Colors) |
| Deverrouillage biometrique (empreinte digitale) | Non | Oui (BiometricPrompt + AndroidKeyStore) |
| Verrouillage automatique | Oui | Oui |
| Verrouillage ecran eteint | Non | Oui |
| Synchronisation SFTP bidirectionnelle (tous types) | Oui (cle fichier ou cle du coffre) | Oui |
| Resolution de conflits (fusion par entree, tous types) | Oui | Oui |
| Dossier de travail configurable (workspace) | Oui (dossier libre + recents, selecteur au login et parametres) | Oui (via SAF) |
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

Sur desktop comme sur Android, l'Audit est une **page dediee** organisee en sections (Vue d'ensemble, A risque, Points forts, Composition, Completude, Activite) avec des stats : score /20, forts, % uniques, categories, favoris, sans URL/email, activite 30 jours, plus ancien.

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
| Dossier de travail (workspace) | Oui (changement = reconnexion) | Oui (via SAF) |

Changement de mot de passe maitre : seule la DEK est re-chiffree (operation quasi instantanee). Les donnees biometriques sont automatiquement invalidees.

---

## Raccourcis clavier (desktop)

| Raccourci | Action |
|---|---|
| `Ctrl+N` | Nouvelle entree |
| `Ctrl+F` | Placer le focus sur la recherche de la page active |
| `Suppr` | Supprimer l'entree selectionnee |
| `F5` | Actualiser l'affichage |
| Fleches `Haut`/`Bas` | Naviguer dans la liste des entrees (carte focalisee) |
| `Entree` | Se connecter (ecran de connexion) ou modifier la carte focalisee |
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
| Rejet mots de passe courants | 93 mots de passe connus rejetes (comparaison a temps constant) |
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
| Build | Gradle (Kotlin DSL) | 8.12 |
| CI/CD | GitHub Actions | - |

### Structure multi-module

```
password-manager/
|-- core/                              # Logique metier partagee (Java 17)
|   +-- crypto/                        # CryptoService, KeyDerivation, VaultSession, PasswordGenerator
|   +-- vault/                         # VaultItem (abstract), PasswordEntry, AppEntry, SshKeyEntry
|   |                                  # Vault, BaseVaultService, PasswordService, AppService
|   |                                  # VaultManager, VaultStoreMigrator, EntryFilter, VaultImporter, VaultExporter
|   |                                  # VaultJsonCodec + JsonCharWriter/JsonCharReader (codec JSON, secrets en char[])
|   +-- vault/store/                   # VaultStore (abstraction stockage), FileVaultStore
|   +-- security/                      # HibpChecker (detection de mots de passe compromis)
|   +-- sync/                          # EntryMerger, SyncService (moteur), LocalRepository,
|   |                                  # LocalSyncRepository, RemoteSyncRepository, ConflictStrategy
|   +-- config/                        # AppConfig, AppVersion, StorageMode, ThemeMode
|   +-- update/                        # UpdateChecker, UpdateInfo, VersionComparator
|   +-- util/                          # SecureWiper, FileSecurityUtils, PasswordValidator, FaviconService
|
|-- desktop/                           # Interface Swing (Java 17)
|   +-- ui/                            # LoginFrame, MainFrame (sidebar + JMenuBar), CoffrePasswordsPanel/CoffreAppsPanel/CoffreSshPanel/CoffreSettingsPanel, SecurityAuditController, ui/components, SecureClipboard, dialogs
|   +-- config/                        # ConfigManager, ConfigEncryptor (persistance config desktop)
|   +-- i18n/                          # LanguageManager (FR/EN)
|   +-- sync/                          # SFTPRepository (client JSch), DesktopSyncFactory
|   +-- update/                        # DesktopUpdateManager
|
+-- android/                           # Interface Jetpack Compose (Kotlin)
    +-- autofill/                      # PasswordManagerAutofillService (API 26+)
    +-- data/                          # AndroidVaultRepository, ConfigRepository, SessionHolder, BiometricHelper, FaviconRepository,
    |                                  # SshHostKeyStore + SftpHostKeyVerifier (epinglage host-key SFTP)
    +-- di/                            # AppModule (Hilt DI)
    +-- ui/                            # Ecrans Compose (login, vault, generator, settings, audit, sync)
    +-- update/                        # AndroidUpdateManager
```

### Tests

**~529 tests** unitaires et d'integration dans `:core` (371), `:desktop` (52) et `:android` (106) — dont des tests d'integration SFTP reels via un serveur embarque :

| Module | Classe de test | Tests | Description |
|---|---|:---:|---|
| vault | `VaultServiceTest` | 33 | CRUD mots de passe, recherche, tri, favoris, filtre, doublons, categories, timestamps |
| vault | `VaultImporterTest` | 33 | CSV (separateurs, alias, BOM, sanitisation, favoris, RFC 4180, round-trip multi-type), JSON, limites |
| security | `SecurityAuditTest` | 31 | IV, KDF, memoire, permissions, format, import/export, generateur |
| sync | `SyncServiceTest` | 33 | Synchronisation, hash a 3 voies, conflits, mode hors-ligne, mode local (tous types) |
| **android** | `LoginViewModelTest` | 27 | Etat initial, biometrie, selection utilisateur, login, enrollment, creation utilisateur |
| vault | `VaultTest` | 24 | Constructeurs, add/remove (3 types), dedup par id, unmodifiable views, mutable accessors, wipe, ensureInitialized, settings |
| sync | `LocalRepositoryTest` | 17 | Path traversal, CRUD, pending, backups |
| vault | `VaultManagerIntegrationTest` | 16 | Cycle complet avec vraie crypto, validation username |
| util | `PasswordValidatorTest` | 15 | Politique mot de passe maitre, rejet des mots de passe courants |
| crypto | `CryptoServiceTest` | 14 | Enveloppe DEK/KEK, chiffrement, AAD, changement mdp, tampering, legacy |
| update | `VersionComparatorTest` | 13 | Comparaison semantique, pre-release, null, egalite |
| **android** | `SettingsViewModelTest` | 13 | Configuration initiale, theme, langue, auto-lock, clipboard, toggle biometrique |
| **desktop** | `ConfigEncryptorTest` | 11 | Round-trip, caracteres speciaux, corruption, unicite IV |
| **android** | `GeneratorViewModelTest` | 11 | Etat initial, generation, longueur, toggles, force, nettoyage |
| sync | `EntryMergerTest` | 13 | Fusion generique, conflits exclus du merge (R1), entrees identiques (tous types) |
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
| **desktop** | `LanguageManagerTest` | 6 | Singleton, getString, setLanguage, langues disponibles |
| **desktop** | `AutoLockManagerTest` | 5 | Idempotence start, cleanup, lifecycle, listener |
| util | `FaviconServiceTest` | 7 | Extraction domaine, cache disque, favicon null, validation image (anti-HTML) |
| security | `HibpCheckerTest` | 5 | Null, vide, entree valide, caracteres speciaux, unicode |
| **android** | `SecurityAuditViewModelTest` | 5 | Audit vide, faibles, dupliques, anciens, total |
| **android** | `EntryDetailViewModelTest` | 5 | Chargement, visibilite, suppression |
| util | `DateUtilsTest` | 5 | ISO 8601, round-trip, parsing valide/invalide/null |
| crypto | `PasswordGeneratorTest` | 5 | Longueur, types, exclusion ambigus |
| **desktop** | `ConfigManagerTest` | 3 | Valeurs par defaut, persistance |
| vault | `VaultJsonCodecTest` | 8 | Codec JSON maison : round-trip, caracteres pieges, retro-compat Gson, echec propre (R3) |
| **android** | `SshHostKeyStoreTest` | 10 | Epinglage host-key SFTP : empreinte SHA-256, Match/Unknown/Changed, persistance (C2) |
| **android** | `AutofillDomainMatcherTest` | 6 | Matching autofill : exact/sous-domaine, rejet parent et look-alike (R10) |
| **android** | `PinningHostKeyRepositoryTest` | 4 | Verdicts JSch OK/NOT_INCLUDED/CHANGED (C2) |
| android+desktop | `ConflictApplyTest` | 4+4 | Application de fusion sans doublon d'id (R1) |
| **desktop** | `SFTPRepositoryIntegrationTest` | 3 | SFTP reel (MINA) : round-trip, host-key strict |
| **desktop** | `DesktopSyncFactoryTest` | 2 | Construction du SyncService depuis AppConfig |
| **desktop** | `MasterPasswordSyncIntegrationTest` | 2 | Propagation du mot de passe maitre, 2 appareils (R4) |
| **desktop** | `SyncConflictIntegrationTest` | 1 | Conflit expose le vrai distant pour la fusion (R-merge) |
| | | **~526** | |

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
