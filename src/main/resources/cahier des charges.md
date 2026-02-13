# 📋 CAHIER DES CHARGES TECHNICO-FONCTIONNEL
## Gestionnaire de Mots de Passe Sécurisé - Version Desktop

---

## 1. PRÉSENTATION GÉNÉRALE

### 1.1 Contexte du projet
Développement d'une application desktop de gestion de mots de passe sécurisée, permettant le stockage chiffré de données sensibles avec support de synchronisation locale ou distante.

### 1.2 Objectifs principaux
- Offrir un gestionnaire de mots de passe sécurisé et professionnel
- Garantir une sécurité de niveau bancaire (AES-256-GCM)
- Permettre un fonctionnement local ou avec serveur distant
- Proposer une interface utilisateur intuitive et multilingue (FR/EN)

### 1.3 Public cible
- Utilisateur principal : usage personnel
- Support multi-utilisateurs : chaque utilisateur dispose de son propre coffre-fort chiffré

---

## 2. SPÉCIFICATIONS FONCTIONNELLES

### 2.1 Fonctionnalités principales

#### 2.1.1 Gestion des entrées
- ✅ **Création** de fiches de mots de passe
- ✅ **Modification** des entrées existantes
- ✅ **Suppression** avec confirmation
- ✅ **Visualisation** sécurisée (masquage par défaut)
- ✅ **Copie dans le presse-papiers** avec auto-effacement après 30 secondes

**Champs par entrée :**
- Titre/Nom du compte
- Identifiant/Email
- Mot de passe (masqué par défaut)
- URL du site
- Notes sécurisées (texte libre, chiffré)
- Catégorie/Dossier
- Tags optionnels
- Date de création
- Date de dernière modification

#### 2.1.2 Générateur de mots de passe
**Paramètres configurables :**
- Longueur (8 à 128 caractères)
- Inclusion/exclusion :
    - Majuscules (A-Z)
    - Minuscules (a-z)
    - Chiffres (0-9)
    - Caractères spéciaux (!@#$%^&*...)
- Exclusion de caractères ambigus (0/O, 1/l/I)
- Génération aléatoire cryptographiquement sécurisée (SecureRandom)

#### 2.1.3 Organisation et navigation
- ✅ **Dossiers/Catégories** : arborescence personnalisable
- ✅ **Recherche** : temps réel sur tous les champs
- ✅ **Filtres** : par catégorie, tags, force du mot de passe
- ✅ **Tri** : par nom, date, catégorie
- ✅ **Affichage** : liste détaillée ou grille compacte

#### 2.1.4 Analyse de sécurité
- ✅ **Indicateur de force** : score visuel pour chaque mot de passe
    - Faible (rouge) : < 8 caractères ou sans diversité
    - Moyen (orange) : 8-12 caractères avec 2 types
    - Fort (vert) : 12+ caractères avec 3+ types
    - Très fort (bleu) : 16+ caractères avec tous types

- ✅ **Détection des failles** :
    - Mots de passe réutilisés (alerte + liste)
    - Mots de passe faibles (suggestions d'amélioration)
    - Mots de passe anciens (> 6 mois sans changement)

#### 2.1.5 Import/Export
**Import supporté :**
- CSV (format standard : titre,identifiant,mot_de_passe,url,notes)
- JSON (format personnalisé détaillé)

**Export supporté :**
- CSV (données déchiffrées, avec avertissement sécurité)
- JSON (données déchiffrées)
- Sauvegarde chiffrée complète (.backup chiffré AES-256)

#### 2.1.6 Mode de fonctionnement

**Mode 1 : Local uniquement**
- Fichier JSON chiffré stocké localement
- Pas de connexion réseau requise
- Sauvegarde manuelle possible

**Mode 2 : Synchronisation serveur distant**
- Fichier JSON chiffré synchronisé via SFTP
- Connexion SSH avec clé privée (pas de mot de passe)
- Fonctionnement hors ligne :
    - Modifications stockées localement en cache
    - Synchronisation automatique à la reconnexion
    - Gestion des conflits (dernière modification prioritaire + sauvegarde du conflit)

**Paramètres serveur :**
- Hôte (IP ou domaine)
- Port SSH (configurable, par défaut 22)
- Utilisateur SSH
- Chemin de la clé privée SSH
- Répertoire distant du coffre

---

### 2.2 Gestion multi-utilisateurs

#### 2.2.1 Structure
- Chaque utilisateur = 1 coffre-fort chiffré indépendant
- Nom de fichier : `vault_[nom_utilisateur].enc`
- Aucun partage de données entre utilisateurs
- Isolation totale des coffres

#### 2.2.2 Création de compte
- Choix du nom d'utilisateur (unique)
- Définition du mot de passe maître (voir section sécurité)
- Création automatique du coffre vide

#### 2.2.3 Connexion
- Sélection de l'utilisateur (liste déroulante)
- Saisie du mot de passe maître
- Déverrouillage du coffre

---

### 2.3 Sécurité

#### 2.3.1 Mot de passe maître

**Exigences minimales (recommandations sécurisées) :**
- Longueur : minimum 12 caractères
- Composition obligatoire :
    - Au moins 1 majuscule
    - Au moins 1 minuscule
    - Au moins 1 chiffre
    - Au moins 1 caractère spécial
- Validation en temps réel avec indicateur visuel

**Gestion :**
- ✅ Modification possible via menu sécurisé
- ✅ Vérification de l'ancien mot de passe
- ✅ Re-chiffrement complet du coffre avec la nouvelle clé
- ❌ Pas de récupération en cas d'oubli (sécurité maximale)
- ⚠️ Avertissement clair lors de la création : "Aucune récupération possible"

#### 2.3.2 Chiffrement

**Algorithme : AES-256-GCM**
- Mode GCM (Galois/Counter Mode) : authentification intégrée
- Clé de 256 bits dérivée du mot de passe maître
- IV (Initialization Vector) unique et aléatoire par opération
- Tag d'authentification pour vérifier l'intégrité

**Dérivation de clé : PBKDF2**
- Algorithme : PBKDF2WithHmacSHA256
- Itérations : 100 000 (compromis sécurité/performance)
- Sel (salt) : 32 octets générés aléatoirement (SecureRandom)
- Sel stocké avec le fichier chiffré (non secret, mais unique)

**Structure du fichier chiffré :**
```json
{
  "version": "1.0",
  "salt": "<base64_salt>",
  "iv": "<base64_iv>",
  "encrypted_data": "<base64_encrypted_json>",
  "auth_tag": "<base64_gcm_tag>"
}
```

#### 2.3.3 Protection de la mémoire
- ✅ Effacement du presse-papiers après 30 secondes
- ✅ Verrouillage automatique après inactivité (configurable : 5/10/15/30 min)
- ✅ Masquage des mots de passe dans l'interface (affichage •••)
- ✅ Nettoyage des variables sensibles en mémoire (Arrays.fill() pour char[])

#### 2.3.4 Logs et audit (serveur uniquement)

**Fichiers de logs côté serveur :**
- Emplacement : `/var/log/password-manager/audit.log`
- Permissions : lecture root uniquement (chmod 600)
- Format : JSON structuré

**Événements tracés :**
- Connexion utilisateur (succès/échec) avec timestamp
- Synchronisation (upload/download) avec hash du fichier
- Modifications du coffre (création/modification/suppression d'entrée)
- Changement du mot de passe maître

**Anonymisation :**
- ❌ Aucun mot de passe ou donnée sensible logué
- ✅ Hash anonyme de l'utilisateur (SHA-256)
- ✅ Timestamps UTC
- ✅ IP source (si nécessaire pour sécurité)

**Exemple de log :**
```json
{
  "timestamp": "2026-02-05T14:32:11Z",
  "event": "vault_sync",
  "user_hash": "a3f5b8c...",
  "action": "upload",
  "file_hash": "e9d2c1a...",
  "ip": "192.168.1.10"
}
```

⚠️ **Important : Les logs ne sont PAS accessibles depuis l'application cliente.**

---

## 3. SPÉCIFICATIONS TECHNIQUES

### 3.1 Technologies

#### 3.1.1 Environnement de développement
- **Langage** : Java 8 (JDK 1.8)
- **Framework UI** : Java Swing
- **Build** : Maven ou Gradle (au choix du développeur)
- **IDE recommandé** : IntelliJ IDEA, Eclipse, NetBeans

#### 3.1.2 Bibliothèques tierces autorisées

**Cryptographie :**
- `javax.crypto.*` (natif JDK)
- Optionnel : Bouncy Castle (si nécessaire pour extensions)

**Réseau/SFTP :**
- **JSch** (Java Secure Channel) : client SFTP/SSH
- Alternative : Apache Commons VFS avec SFTP

**Manipulation JSON :**
- **Gson** (Google) ou **Jackson** : parsing/sérialisation JSON

**Tests :**
- JUnit 4.x (compatible Java 8)
- Mockito (optionnel pour mocks)

**Interface :**
- FlatLaf (Modern Look & Feel pour Swing, optionnel)
- Substance (autre Look & Feel moderne, optionnel)

### 3.2 Architecture logicielle

#### 3.2.1 Pattern architectural
**MVC (Model-View-Controller) avec couche Service**

```
┌─────────────────────────────────────────────────┐
│                    View Layer                    │
│              (Swing UI Components)               │
│  - LoginFrame, MainFrame, PasswordDialog, etc.  │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│               Controller Layer                   │
│     - UserController, VaultController, etc.     │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│                Service Layer                     │
│  - CryptoService, SyncService, VaultService     │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│                 Model Layer                      │
│    - User, VaultEntry, Vault, Config, etc.      │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│               Repository Layer                   │
│  - FileRepository, SFTPRepository, ConfigRepo   │
└──────────────────────────────────────────────────┘
```

#### 3.2.2 Modules principaux

**1. Module Cryptographie (`crypto`)**
- `CryptoService` : chiffrement/déchiffrement AES-256-GCM
- `KeyDerivation` : PBKDF2 pour générer clé depuis mot de passe
- `PasswordGenerator` : génération sécurisée de mots de passe
- `PasswordStrengthAnalyzer` : analyse de la robustesse

**2. Module Gestion du coffre (`vault`)**
- `Vault` : modèle du coffre (liste d'entrées)
- `VaultEntry` : modèle d'une entrée de mot de passe
- `VaultService` : CRUD sur les entrées
- `VaultManager` : chargement/sauvegarde du coffre

**3. Module Synchronisation (`sync`)**
- `SyncService` : orchestration de la synchro
- `LocalRepository` : gestion fichier local
- `SFTPRepository` : gestion fichier distant via SFTP
- `ConflictResolver` : résolution conflits de synchro

**4. Module Interface (`ui`)**
- `LoginFrame` : écran de connexion
- `MainFrame` : fenêtre principale
- `VaultPanel` : affichage de la liste des entrées
- `EntryDialog` : formulaire ajout/édition entrée
- `SettingsDialog` : configuration de l'application
- `PasswordGeneratorDialog` : générateur de mots de passe

**5. Module Configuration (`config`)**
- `AppConfig` : paramètres de l'application
- `UserPreferences` : préférences utilisateur
- `ConfigManager` : lecture/écriture configuration

**6. Module Internationalisation (`i18n`)**
- `ResourceBundles` : fichiers de traductions (FR/EN)
- `LanguageManager` : gestion du changement de langue

### 3.3 Structure des données

#### 3.3.1 Modèle Vault (déchiffré en mémoire)

```json
{
  "version": "1.0",
  "user": "john_doe",
  "created_at": "2026-02-05T10:00:00Z",
  "updated_at": "2026-02-05T14:30:00Z",
  "entries": [
    {
      "id": "uuid-1234-5678",
      "title": "Gmail",
      "username": "john@example.com",
      "password": "SuperSecretPass123!",
      "url": "https://gmail.com",
      "notes": "Compte principal",
      "category": "Email",
      "tags": ["personnel", "important"],
      "created_at": "2026-01-15T08:00:00Z",
      "updated_at": "2026-02-05T14:30:00Z"
    }
  ],
  "categories": ["Email", "Bancaire", "Réseaux sociaux", "Travail"],
  "settings": {
    "auto_lock_minutes": 15,
    "clipboard_clear_seconds": 30,
    "password_expiry_days": 180
  }
}
```

#### 3.3.2 Configuration application

**Fichier : `config.properties` (non chiffré, pas de données sensibles)**

```properties
# Langue
app.language=fr

# Mode de stockage
storage.mode=remote  # ou "local"

# Paramètres serveur (si mode remote)
sftp.host=ubuntu-server.local
sftp.port=22
sftp.user=vault_user
sftp.key_path=/home/user/.ssh/id_rsa
sftp.remote_path=/vault/data

# Paramètres locaux
local.vault_directory=/home/user/.password-manager/vaults

# Sécurité
security.pbkdf2_iterations=100000
security.auto_lock_minutes=15
security.clipboard_clear_seconds=30
```

### 3.4 Flux de synchronisation

#### 3.4.1 Mode hors ligne → Reconnexion

```
1. Modifications locales → Stockées dans cache local (.pending)
2. Détection de connexion → SyncService déclenché
3. Téléchargement du fichier distant → Comparaison timestamps
4. Résolution de conflits :
   - Si local > distant → Upload local
   - Si distant > local → Prompt utilisateur (garder local/distant/fusionner)
   - Si conflit → Sauvegarde des deux versions + choix manuel
5. Synchronisation terminée → Suppression du cache .pending
```

#### 3.4.2 Gestion des conflits

**Cas 1 : Aucune modification distante**
→ Upload direct du fichier local

**Cas 2 : Modifications distantes détectées**
→ Téléchargement du fichier distant
→ Affichage d'une boîte de dialogue :
```
"Le coffre distant a été modifié depuis votre dernière synchronisation.
Que souhaitez-vous faire ?

[ ] Garder mes modifications locales (écraser le serveur)
[ ] Utiliser la version du serveur (perdre mes modifications locales)
[ ] Sauvegarder les deux versions et choisir manuellement

[Annuler] [Valider]"
```

**Cas 3 : Sauvegarde de conflit**
→ Création de `vault_[user]_conflict_[timestamp].enc`
→ Logs côté serveur

---

## 4. INTERFACE UTILISATEUR

### 4.1 Principes de design

- **Ergonomie** : interface claire, intuitive, navigation fluide
- **Lisibilité** : police lisible (minimum 12pt), contrastes adaptés
- **Accessibilité** : raccourcis clavier, support clavier complet
- **Cohérence** : utilisation uniforme des couleurs, icônes, espacements

### 4.2 Écrans principaux

#### 4.2.1 Écran de connexion (`LoginFrame`)

**Éléments :**
- Logo/Titre de l'application
- Liste déroulante : sélection de l'utilisateur
- Champ mot de passe maître (masqué)
- Bouton "Connexion"
- Lien "Créer un nouvel utilisateur"
- Sélecteur de langue (FR/EN)

**Actions :**
- Validation du mot de passe maître
- Déverrouillage du coffre
- Affichage d'erreur si mot de passe incorrect

#### 4.2.2 Fenêtre principale (`MainFrame`)

**Structure :**
```
┌─────────────────────────────────────────────────────────┐
│  [Fichier] [Édition] [Affichage] [Outils] [Aide]       │
├─────────────────────────────────────────────────────────┤
│  [+ Nouveau] [Générer] [Importer] [Exporter] [🔒 Verr] │
├───────────────┬─────────────────────────────────────────┤
│ 📁 Catégories │  [🔍 Rechercher...]                    │
│               │                                          │
│ - Email       │  ┌───────────────────────────────────┐ │
│ - Bancaire    │  │ Gmail                              │ │
│ - Réseaux     │  │ john@example.com  ••••••••  [👁] │ │
│ - Travail     │  │ gmail.com         [📋]            │ │
│               │  ├───────────────────────────────────┤ │
│ [+ Catégorie] │  │ Facebook                          │ │
│               │  │ john123           ••••••••  [👁] │ │
│               │  │ facebook.com      [📋]            │ │
│               │  └───────────────────────────────────┘ │
│               │                                          │
│               │  [Modifier] [Supprimer] [Détails]      │
└───────────────┴─────────────────────────────────────────┘
│ 🟢 Synchronisé │ Dernière synchro : il y a 5 min       │
└─────────────────────────────────────────────────────────┘
```

**Menu "Fichier" :**
- Nouveau coffre
- Importer (CSV/JSON)
- Exporter (CSV/JSON/Backup)
- Paramètres
- Verrouiller
- Quitter

**Menu "Édition" :**
- Nouvelle entrée
- Modifier l'entrée
- Supprimer l'entrée
- Changer mot de passe maître

**Menu "Affichage" :**
- Actualiser
- Tri (par nom, date, catégorie)
- Filtres (mots de passe faibles, réutilisés)

**Menu "Outils" :**
- Générateur de mots de passe
- Analyse de sécurité
- Synchroniser maintenant (si mode distant)
- Logs (désactivé depuis l'app, sécurité)

**Menu "Aide" :**
- Documentation
- À propos
- Vérifier les mises à jour

#### 4.2.3 Dialogue Ajout/Édition d'entrée

**Formulaire :**
```
┌─────────────────────────────────────────────┐
│  [x] Nouvelle entrée                        │
├─────────────────────────────────────────────┤
│  Titre* :       [___________________________]│
│                                              │
│  Identifiant :  [___________________________]│
│                                              │
│  Mot de passe*: [•••••••••••] [👁] [🔄]    │
│                 Force : [████████░░] Fort   │
│                                              │
│  URL :          [___________________________]│
│                                              │
│  Catégorie :    [Email ▼]                   │
│                                              │
│  Tags :         [___________________________]│
│                 (séparés par des virgules)  │
│                                              │
│  Notes :        [___________________________]│
│                 [___________________________]│
│                 [___________________________]│
│                                              │
├─────────────────────────────────────────────┤
│               [Annuler] [Enregistrer]       │
└─────────────────────────────────────────────┘
```

**Actions :**
- [👁] : Afficher/masquer le mot de passe
- [🔄] : Ouvrir le générateur de mot de passe
- Validation en temps réel de la force du mot de passe

#### 4.2.4 Générateur de mots de passe

```
┌─────────────────────────────────────────────┐
│  [x] Générateur de mot de passe             │
├─────────────────────────────────────────────┤
│  Mot de passe généré :                      │
│  ┌───────────────────────────────────────┐ │
│  │ Kp9$mT2#vL8@wQx4 [👁] [📋] [🔄]      │ │
│  └───────────────────────────────────────┘ │
│  Force : [██████████] Très fort             │
│                                              │
│  ──────────────────────────────────────────│
│                                              │
│  Longueur : [16 ▼] caractères               │
│                                              │
│  ☑ Majuscules (A-Z)                         │
│  ☑ Minuscules (a-z)                         │
│  ☑ Chiffres (0-9)                           │
│  ☑ Caractères spéciaux (!@#$%...)          │
│  ☐ Exclure caractères ambigus (0/O, 1/l)   │
│                                              │
├─────────────────────────────────────────────┤
│        [Annuler] [Générer] [Utiliser]       │
└─────────────────────────────────────────────┘
```

#### 4.2.5 Paramètres

**Onglets :**

**Général :**
- Langue (Français/English)
- Thème (Clair/Sombre)
- Démarrage automatique

**Sécurité :**
- Verrouillage automatique après : [15 ▼] minutes
- Effacement presse-papiers après : [30 ▼] secondes
- Alerte mots de passe anciens : [180 ▼] jours
- Changer le mot de passe maître

**Synchronisation :**
- Mode : ( ) Local uniquement  (•) Serveur distant
- Hôte : [___________________________]
- Port : [22]
- Utilisateur : [___________________________]
- Clé SSH : [/home/user/.ssh/id_rsa]
- Répertoire distant : [/vault/data]
- Tester la connexion

**Sauvegarde :**
- Sauvegarde automatique locale : ☑ Activée
- Fréquence : [Quotidienne ▼]
- Répertoire : [/backups]

### 4.3 Internationalisation

#### 4.3.1 Fichiers de ressources

**Structure :**
```
src/main/resources/i18n/
├── messages_fr.properties
└── messages_en.properties
```

**Exemple `messages_fr.properties` :**
```properties
app.title=Gestionnaire de Mots de Passe
login.username=Nom d'utilisateur
login.password=Mot de passe maître
login.button=Connexion
login.create_user=Créer un nouvel utilisateur
error.invalid_password=Mot de passe incorrect
vault.new_entry=Nouvelle entrée
vault.edit_entry=Modifier l'entrée
vault.delete_entry=Supprimer l'entrée
# ... etc.
```

#### 4.3.2 Changement de langue
- Changement à la volée : redémarrage de l'application requis
- Sauvegarde de la préférence dans `config.properties`

---

## 5. CONTRAINTES ET EXIGENCES

### 5.1 Contraintes techniques

| Contrainte | Spécification |
|------------|---------------|
| **Java Version** | Java 8 (JDK 1.8) minimum |
| **Compatibilité OS** | Windows, macOS, Linux (JAR exécutable cross-platform) |
| **Résolution minimale** | 1280x720 pixels |
| **RAM recommandée** | 256 MB minimum, 512 MB recommandé |
| **Espace disque** | 50 MB pour l'application + taille des coffres |

### 5.2 Exigences de performance

- **Démarrage** : < 3 secondes sur machine moderne
- **Déchiffrement du coffre** : < 2 secondes (coffre de 1000 entrées)
- **Recherche** : temps réel (< 200ms pour 1000 entrées)
- **Synchronisation** : < 5 secondes pour un coffre de 5 MB via SFTP

### 5.3 Exigences de sécurité

✅ **OBLIGATOIRE :**
- Chiffrement AES-256-GCM pour toutes les données sensibles
- PBKDF2 avec 100 000 itérations minimum
- Aucun stockage de mot de passe en clair (mémoire ou disque)
- Validation stricte du mot de passe maître
- Logs côté serveur uniquement (anonymisés)

❌ **INTERDIT :**
- Stockage du mot de passe maître
- Transmission de données non chiffrées
- Logs de données sensibles (mots de passe, contenu des notes)
- Connexion SSH avec mot de passe (clé privée uniquement)

### 5.4 Exigences de qualité du code

- **Convention Java** : respect des conventions Oracle (CamelCase, etc.)
- **Commentaires** : JavaDoc pour toutes les classes et méthodes publiques
- **Tests unitaires** : couverture minimale de 60% pour les modules critiques (crypto, sync)
- **Gestion des exceptions** : try-catch appropriés, messages d'erreur clairs
- **Logging** : utilisation de `java.util.logging` ou SLF4J

---

## 6. LIVRABLES ATTENDUS

### 6.1 Code source

✅ **Livraison requise :**

1. **Code source complet** :
    - Structure Maven/Gradle complète
    - Tous les fichiers `.java`
    - Fichiers de ressources (i18n, icônes)
    - Fichiers de configuration

2. **JAR exécutable** :
    - `password-manager.jar` (avec toutes les dépendances embarquées)
    - Exécutable via : `java -jar password-manager.jar`

3. **Scripts de lancement** :
    - `run.sh` (Linux/macOS)
    - `run.bat` (Windows)

4. **Documentation** :
    - `README.md` : présentation, installation, utilisation
    - `ARCHITECTURE.md` : description technique de l'architecture
    - `API.md` : documentation des principales classes/méthodes
    - JavaDoc générée (HTML)

5. **Tests** :
    - Tests unitaires JUnit
    - Instructions pour exécuter les tests

6. **Configuration serveur** :
    - Script d'installation serveur Ubuntu (`setup-server.sh`)
    - Configuration SSH/SFTP recommandée
    - Configuration des logs (`logrotate`, etc.)

### 6.2 Structure du projet attendue

```
password-manager/
├── pom.xml / build.gradle
├── README.md
├── ARCHITECTURE.md
├── API.md
├── LICENSE
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/passwordmanager/
│   │   │       ├── Main.java
│   │   │       ├── crypto/
│   │   │       │   ├── CryptoService.java
│   │   │       │   ├── KeyDerivation.java
│   │   │       │   ├── PasswordGenerator.java
│   │   │       │   └── PasswordStrengthAnalyzer.java
│   │   │       ├── vault/
│   │   │       │   ├── Vault.java
│   │   │       │   ├── VaultEntry.java
│   │   │       │   ├── VaultService.java
│   │   │       │   └── VaultManager.java
│   │   │       ├── sync/
│   │   │       │   ├── SyncService.java
│   │   │       │   ├── LocalRepository.java
│   │   │       │   ├── SFTPRepository.java
│   │   │       │   └── ConflictResolver.java
│   │   │       ├── ui/
│   │   │       │   ├── LoginFrame.java
│   │   │       │   ├── MainFrame.java
│   │   │       │   ├── VaultPanel.java
│   │   │       │   ├── EntryDialog.java
│   │   │       │   ├── SettingsDialog.java
│   │   │       │   └── PasswordGeneratorDialog.java
│   │   │       ├── config/
│   │   │       │   ├── AppConfig.java
│   │   │       │   ├── UserPreferences.java
│   │   │       │   └── ConfigManager.java
│   │   │       ├── i18n/
│   │   │       │   └── LanguageManager.java
│   │   │       └── model/
│   │   │           └── User.java
│   │   │
│   │   └── resources/
│   │       ├── i18n/
│   │       │   ├── messages_fr.properties
│   │       │   └── messages_en.properties
│   │       ├── icons/
│   │       │   ├── logo.png
│   │       │   └── ...
│   │       └── config.properties
│   │
│   └── test/
│       └── java/
│           └── com/passwordmanager/
│               ├── crypto/
│               │   └── CryptoServiceTest.java
│               └── vault/
│                   └── VaultServiceTest.java
│
├── scripts/
│   ├── run.sh
│   ├── run.bat
│   └── setup-server.sh
│
├── docs/
│   └── javadoc/
│       └── index.html
│
└── target/ ou build/
    └── password-manager.jar
```

---

## 7. ANNEXES TECHNIQUES

### 7.1 Exemple de chiffrement AES-256-GCM

```java
public class CryptoService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    public static EncryptedData encrypt(String plaintext, String masterPassword, byte[] salt) 
        throws Exception {
        
        // Dérivation de clé avec PBKDF2
        SecretKey key = deriveKey(masterPassword, salt);
        
        // Génération d'un IV aléatoire
        byte[] iv = generateRandomBytes(GCM_IV_LENGTH);
        
        // Chiffrement
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        return new EncryptedData(iv, ciphertext);
    }
    
    public static String decrypt(EncryptedData encrypted, String masterPassword, byte[] salt) 
        throws Exception {
        
        SecretKey key = deriveKey(masterPassword, salt);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, encrypted.getIv());
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        
        byte[] plaintext = cipher.doFinal(encrypted.getCiphertext());
        
        return new String(plaintext, StandardCharsets.UTF_8);
    }
    
    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(
            password.toCharArray(), 
            salt, 
            100000,  // Itérations
            KEY_SIZE
        );
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
```

### 7.2 Exemple de connexion SFTP avec JSch

```java
public class SFTPRepository {
    private Session session;
    private ChannelSftp sftpChannel;
    
    public void connect(String host, int port, String user, String privateKeyPath) 
        throws Exception {
        
        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath);  // Clé privée SSH
        
        session = jsch.getSession(user, host, port);
        
        // Configuration sécurisée
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "yes");  // Vérifier le host key
        session.setConfig(config);
        
        session.connect(30000);  // Timeout 30s
        
        Channel channel = session.openChannel("sftp");
        channel.connect();
        sftpChannel = (ChannelSftp) channel;
    }
    
    public void uploadFile(String localPath, String remotePath) throws Exception {
        sftpChannel.put(localPath, remotePath, ChannelSftp.OVERWRITE);
    }
    
    public void downloadFile(String remotePath, String localPath) throws Exception {
        sftpChannel.get(remotePath, localPath);
    }
    
    public void disconnect() {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
```

### 7.3 Configuration serveur Ubuntu recommandée

**Fichier `/etc/ssh/sshd_config` :**
```bash
# Port SSH personnalisé (recommandé)
Port 2222

# Désactiver l'authentification par mot de passe
PasswordAuthentication no
PubkeyAuthentication yes

# Désactiver root login
PermitRootLogin no

# Limiter les utilisateurs autorisés
AllowUsers vault_user

# Timeout de session
ClientAliveInterval 300
ClientAliveCountMax 2
```

**Installation Fail2Ban :**
```bash
sudo apt update
sudo apt install fail2ban

# Configuration pour SSH
sudo nano /etc/fail2ban/jail.local
```

```ini
[sshd]
enabled = true
port = 2222
filter = sshd
logpath = /var/log/auth.log
maxretry = 3
bantime = 3600
```

**Firewall UFW :**
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 2222/tcp  # Port SSH custom
sudo ufw enable
```

---

## 8. RÉCAPITULATIF DES FONCTIONNALITÉS

### ✅ Fonctionnalités implémentées

| Catégorie | Fonctionnalité | Priorité |
|-----------|----------------|----------|
| **Gestion des entrées** | CRUD complet (Créer, Lire, Modifier, Supprimer) | 🔴 Critique |
| | Coffre-fort pour notes sécurisées | 🔴 Critique |
| | Organisation par dossiers/catégories | 🟡 Importante |
| | Tags personnalisables | 🟢 Souhaitée |
| **Recherche et tri** | Recherche temps réel multi-champs | 🔴 Critique |
| | Filtres avancés (catégorie, force, etc.) | 🟡 Importante |
| | Tri personnalisable | 🟢 Souhaitée |
| **Générateur** | Génération de mots de passe sécurisés | 🔴 Critique |
| | Configuration longueur/types de caractères | 🟡 Importante |
| **Analyse sécurité** | Indicateur de force visuel | 🔴 Critique |
| | Détection mots de passe réutilisés | 🟡 Importante |
| | Détection mots de passe faibles | 🟡 Importante |
| | Alerte mots de passe anciens | 🟢 Souhaitée |
| **Import/Export** | Import CSV/JSON | 🟡 Importante |
| | Export CSV/JSON | 🟡 Importante |
| | Export backup chiffré | 🔴 Critique |
| **Synchronisation** | Mode local uniquement | 🔴 Critique |
| | Mode serveur SFTP | 🔴 Critique |
| | Fonctionnement hors ligne | 🔴 Critique |
| | Gestion des conflits | 🟡 Importante |
| **Sécurité** | Chiffrement AES-256-GCM | 🔴 Critique |
| | PBKDF2 (100k itérations) | 🔴 Critique |
| | Mot de passe maître avec validation | 🔴 Critique |
| | Changement du mot de passe maître | 🟡 Importante |
| | Auto-verrouillage après inactivité | 🟡 Importante |
| | Effacement presse-papiers (30s) | 🟡 Importante |
| | Logs audit côté serveur | 🟢 Souhaitée |
| **Multi-utilisateurs** | Coffres individuels isolés | 🔴 Critique |
| | Gestion création de comptes | 🔴 Critique |
| **Interface** | Bilingue FR/EN | 🔴 Critique |
| | Interface Swing moderne | 🟡 Importante |
| | Thème clair/sombre | 🟢 Souhaitée |
| | Raccourcis clavier | 🟢 Souhaitée |

### ❌ Fonctionnalités volontairement exclues

- Récupération du mot de passe maître oublié (sécurité)
- Authentification biométrique (complexité Java desktop)
- Synchronisation cloud public (auto-hébergement uniquement)
- Partage de mots de passe entre utilisateurs (usage personnel)
- Remplissage automatique de formulaires (complexité navigateurs)
- Extension navigateur (hors scope desktop)
- Application mobile native (focus desktop)

---

## 9. PLANNING INDICATIF

### Phase 1 : Socle technique (2-3 semaines)
- ✅ Architecture projet (Maven/Gradle)
- ✅ Module cryptographie (AES-256-GCM, PBKDF2)
- ✅ Modèles de données (Vault, VaultEntry)
- ✅ Tests unitaires crypto

### Phase 2 : Gestion du coffre (2 semaines)
- ✅ VaultService (CRUD)
- ✅ Sauvegarde/Chargement local (JSON chiffré)
- ✅ Générateur de mots de passe
- ✅ Analyseur de force

### Phase 3 : Interface utilisateur (3-4 semaines)
- ✅ LoginFrame
- ✅ MainFrame et navigation
- ✅ Dialogues (Entry, Settings, Generator)
- ✅ Internationalisation (FR/EN)

### Phase 4 : Synchronisation (2 semaines)
- ✅ Module SFTP (JSch)
- ✅ SyncService et gestion conflits
- ✅ Mode hors ligne

### Phase 5 : Sécurité avancée (1-2 semaines)
- ✅ Auto-verrouillage
- ✅ Effacement presse-papiers
- ✅ Changement mot de passe maître
- ✅ Logs serveur

### Phase 6 : Tests et finalisation (1-2 semaines)
- ✅ Tests d'intégration
- ✅ Tests de sécurité
- ✅ Documentation
- ✅ Packaging JAR
- ✅ Scripts de déploiement

**Durée totale estimée : 11-15 semaines**

---

## 10. CONCLUSION

Ce cahier des charges définit un **gestionnaire de mots de passe sécurisé de niveau professionnel** avec les caractéristiques suivantes :

🔒 **Sécurité maximale** : AES-256-GCM, PBKDF2, isolation des coffres  
🌐 **Flexibilité** : mode local ou serveur distant auto-hébergé  
💻 **Accessibilité** : interface desktop multiplateforme Java Swing  
🌍 **International** : support français/anglais  
📦 **Autonomie** : fonctionnement hors ligne avec synchronisation

Le code source complet, les tests, la documentation et les scripts de déploiement devront être livrés conformément à ce cahier des charges.

---

**Version du document** : 1.0  
**Date** : 05 février 2026  
**Auteur** : Anonyme  
**Statut** : Validé ✅

---