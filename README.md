# Password Manager

Gestionnaire de mots de passe de bureau securise. Stocke, organise et protege vos identifiants dans un coffre-fort chiffre AES-256-GCM avec chiffrement par enveloppe DEK/KEK. Interface bilingue francais/anglais.

## Fonctionnalites

- Coffre-fort chiffre par utilisateur (AES-256-GCM, PBKDF2 600 000 iterations)
- Chiffrement par enveloppe DEK/KEK (changement de mot de passe instantane)
- Generateur de mots de passe cryptographiquement sur (SecureRandom)
- Analyse de securite (mots de passe faibles, reutilises, anciens)
- Import/export CSV et JSON avec detection automatique du format
- Sauvegarde chiffree exportable
- Synchronisation SFTP avec gestion des conflits et mode hors-ligne
- Interface bilingue francais / anglais
- Themes Systeme, Clair et Sombre (FlatLaf)
- Verrouillage automatique apres inactivite
- Effacement automatique du presse-papiers
- Protection anti brute-force sur l'ecran de connexion
- Distribution autonome avec JRE embarque (aucune installation Java requise)

---

## Telechargement

Les releases pretes a l'emploi sont disponibles sur la page [Releases](../../releases). Chaque release contient un JRE embarque — aucune installation de Java n'est necessaire.

| Plateforme | Archive | Systemes supportes |
|---|---|---|
| Linux x64 | `password-manager-<version>-linux-x64.tar.gz` | Ubuntu, Debian, Fedora, Arch, etc. (glibc) |
| Windows x64 | `password-manager-<version>-windows-x64.zip` | Windows 10 / 11 |
| macOS ARM | `password-manager-<version>-macos-aarch64.tar.gz` | macOS 14+ (Apple Silicon M1/M2/M3/M4) |

### Installation

1. Telecharger l'archive correspondant a votre systeme
2. Extraire l'archive
3. Lancer l'application :

```bash
# Linux / macOS
./run.sh

# Windows
run.bat
```

---

## Compilation depuis les sources

### Prerequis

| Composant | Version requise |
|---|---|
| Java (JDK) | 17 ou superieur |
| Maven | 3.8+ |

### Compilation et lancement

```bash
mvn clean package
java -jar target/password-manager.jar
```

### Construction de la distribution avec JRE embarque

```bash
mvn clean package -Pdist
```

Produit `dist/PasswordManager/` contenant le fat JAR, les scripts de lancement et un JRE minimal cree par jlink.

### Execution des tests

```bash
mvn test
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

## Interface principale

Apres connexion, l'interface se compose de :

- **Barre de menus** : Fichier, Edition, Affichage, Outils, Aide
- **Barre d'outils** : Nouvelle entree, Generateur, Synchroniser, Verrouiller
- **Panneau gauche** (180 px) : liste des categories avec filtrage au clic
- **Panneau central** : barre de recherche en temps reel + tableau des entrees (Titre, Identifiant, Categorie, Force)
- **Panneau droit** (300 px) : details de l'entree selectionnee avec boutons copier identifiant/mot de passe
- **Barre de statut** : statut de synchronisation, utilisateur connecte, nombre d'entrees

### Gestion des entrees

| Action | Acces |
|---|---|
| Creer | Edition > Nouvelle entree, `Ctrl+N`, barre d'outils |
| Modifier | Double-clic sur l'entree, ou Edition > Modifier |
| Supprimer | Touche `Suppr`, ou Edition > Supprimer |

Champs disponibles : titre, identifiant/email, mot de passe (avec generateur integre), URL, notes, categorie, tags.

### Recherche, tri et filtres

- **Recherche** : filtre en temps reel sur tous les champs (insensible a la casse)
- **Tri** : par nom, date ou categorie (menu Affichage)
- **Filtres de securite** : mots de passe faibles, mots de passe reutilises

### Generateur de mots de passe

Acces : Outils > Generateur | barre d'outils | bouton Generer dans le formulaire d'entree.

Options : longueur (8-128), majuscules, minuscules, chiffres, caracteres speciaux, exclusion des caracteres ambigus (0/O, 1/l/I). Garantit au moins un caractere de chaque type active.

### Analyse de securite

Acces : Outils > Analyse de securite. Genere un rapport sur :

- Mots de passe **faibles** (score insuffisant)
- Mots de passe **reutilises** (partages entre plusieurs entrees)
- Mots de passe **anciens** (non modifies depuis 180+ jours, configurable)

Indicateur de force : Faible (rouge), Moyen (orange), Fort (vert), Tres fort (bleu).

---

## Import / Export

### Import CSV

Acces : Fichier > Importer CSV. Detection automatique du separateur (`,` ou `;`) et des colonnes via alias multilingues :

| Champ | Alias acceptes |
|---|---|
| Titre | `title`, `organisme`, `name`, `nom`, `titre` |
| Identifiant | `username`, `identifiant`, `email`, `adresse mail`, `login` |
| Mot de passe | `password`, `mdp`, `mot de passe`, `pass` |
| URL | `url`, `site`, `website`, `lien` |
| Notes | `notes`, `description`, `commentaire` |
| Categorie | `category`, `categorie`, `type` |
| Tags | `tags`, `etiquettes` |

Repli positionnel si aucun en-tete n'est reconnu. Limite : 10 000 entrees par import.

### Import JSON

Acces : Fichier > Importer JSON. Format d'export de l'application.

### Export CSV / JSON

Acces : Fichier > Exporter CSV / JSON. Les donnees exportees ne sont **pas chiffrees**. Protection anti-injection de formules pour les tableurs.

### Sauvegarde chiffree

Acces : Fichier > Exporter sauvegarde chiffree. Copie du fichier `.enc` — ne peut etre ouverte qu'avec le mot de passe maitre.

---

## Synchronisation SFTP

### Configuration

Acces : Fichier > Parametres > onglet Synchronisation.

| Parametre | Description |
|---|---|
| Mode de stockage | Local uniquement / Serveur distant |
| Hote | Adresse du serveur SFTP |
| Port | Port SSH (defaut : 22) |
| Utilisateur SSH | Nom d'utilisateur sur le serveur |
| Cle privee SSH | Chemin vers la cle privee (authentification par cle uniquement) |
| Repertoire distant | Dossier de stockage sur le serveur |

Bouton **Tester la connexion** pour verifier la configuration (test hors EDT via SwingWorker).

### Fonctionnement

- Comparaison par empreinte SHA-256 (local vs distant)
- Mode hors-ligne : modifications mises en attente automatiquement (fichier `.pending`)
- Gestion des conflits : garder local, garder distant, ou sauvegarder les deux versions

---

## Parametres

Acces : Fichier > Parametres.

| Onglet | Parametre | Valeurs |
|---|---|---|
| General | Langue | Francais / English |
| General | Theme | Systeme / Clair / Sombre |
| General | Repertoire des coffres | Chemin avec bouton parcourir |
| Securite | Verrouillage automatique | 1 a 60 min (defaut : 15) |
| Securite | Effacement presse-papiers | 5 a 120 s (defaut : 30) |
| Synchronisation | Configuration SFTP | Voir section dediee |

Changement de mot de passe maitre : Edition > Changer mot de passe maitre. Seule la DEK est re-chiffree (operation quasi instantanee).

---

## Raccourcis clavier

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

| Composant | Technologie | Version |
|---|---|---|
| Langage | Java | 17 |
| Interface graphique | Swing + FlatLaf | 3.7 |
| Serialisation JSON | Gson | 2.13.2 |
| Connexion SFTP | JSch (fork mwiede) | 2.27.8 |
| Tests | JUnit 5 (Jupiter) | 5.14.2 |
| Build | Maven | 3.8+ |

### Structure des packages

```
src/main/java/com/passwordmanager/
|-- Main.java                          # Point d'entree, detection app.home
|-- config/                            # Configuration (AppConfig, ConfigManager, ConfigEncryptor)
|-- crypto/                            # Cryptographie (CryptoService, KeyDerivation, VaultSession)
|-- i18n/                              # Internationalisation (LanguageManager, FR/EN)
|-- sync/                              # Synchronisation (SyncService, LocalRepository, SFTPRepository)
|-- ui/                                # Interface Swing (LoginFrame, MainFrame, VaultPanel, dialogs)
|-- util/                              # Utilitaires (SecureWiper, FileSecurityUtils, PasswordValidator)
+-- vault/                             # Coffre (Vault, VaultEntry, VaultManager, VaultService)
```

### Tests

**150 tests** unitaires et d'integration :

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
