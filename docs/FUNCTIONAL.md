# Documentation fonctionnelle

## Table des matieres

1. [Presentation](#1-presentation)
2. [Installation et lancement](#2-installation-et-lancement)
3. [Premier demarrage](#3-premier-demarrage)
4. [Connexion](#4-connexion)
5. [Interface principale](#5-interface-principale)
6. [Gestion des entrees](#6-gestion-des-entrees)
7. [Recherche, tri et filtres](#7-recherche-tri-et-filtres)
8. [Categories](#8-categories)
9. [Generateur de mots de passe](#9-generateur-de-mots-de-passe)
10. [Analyse de securite](#10-analyse-de-securite)
11. [Import et export](#11-import-et-export)
12. [Synchronisation distante](#12-synchronisation-distante)
13. [Parametres](#13-parametres)
14. [Changement du mot de passe maitre](#14-changement-du-mot-de-passe-maitre)
15. [Securite](#15-securite)
16. [Raccourcis clavier](#16-raccourcis-clavier)
17. [Verification des mises a jour](#17-verification-des-mises-a-jour)
18. [Structure des donnees utilisateur](#18-structure-des-donnees-utilisateur)
19. [Differences entre plateformes](#19-differences-entre-plateformes)

---

## 1. Presentation

Password Manager est une application multiplateforme permettant de stocker, organiser et securiser vos mots de passe dans un coffre-fort chiffre. Elle est disponible sur **desktop** (Windows, Linux, macOS) et **Android**, en francais et en anglais.

### Fonctionnalites principales

- Coffre-fort chiffre par utilisateur (AES-256-GCM)
- Creation, modification, suppression et recherche d'entrees
- Generateur de mots de passe cryptographiquement sur
- Analyse de securite (mots de passe faibles, reutilises, anciens)
- Import/export unifie (CSV, JSON, sauvegarde chiffree) via une popup avec choix du format
- Synchronisation SFTP avec gestion des conflits (desktop et Android)
- Interface bilingue (francais / anglais)
- Themes systeme, clair et sombre
- Verrouillage automatique apres inactivite
- Effacement automatique du presse-papiers
- Desktop : distribution autonome avec JRE embarque (aucune installation Java requise)
- Android : application native Jetpack Compose / Material 3 (APK)

---

## 2. Installation et lancement

### 2.1. Desktop (Windows, Linux, macOS)

#### Prerequis (compilation)

- **Java (JDK) 21** ou superieur
- **Gradle 8.11+** (wrapper inclus)

#### Compilation et lancement

```bash
./gradlew :desktop:fatJar
java -jar desktop/build/libs/password-manager.jar
```

Des scripts sont egalement fournis :

| Systeme | Script |
|---------|--------|
| Linux / macOS | `./scripts/run.sh` |
| Windows | `scripts\run.bat` |

Ces scripts utilisent automatiquement le JRE embarque s'il est present (`runtime/`), sinon le Java systeme.

#### Version autonome (avec JRE embarque)

La distribution autonome inclut un JRE minimal (~57 Mo). L'utilisateur final n'a pas besoin d'installer Java. Les archives sont disponibles sur la page [Releases](../../releases).

### 2.2. Android

#### Installation depuis la release

1. Telecharger l'APK depuis la page [Releases](../../releases)
2. Activer "Sources inconnues" dans les parametres Android (si necessaire)
3. Installer l'APK et lancer l'application

**Systemes supportes** : Android 8.0+ (API 26)

#### Compilation depuis les sources

Prerequis : Android SDK (API 35), JDK 17+

```bash
./gradlew :android:assembleDebug
# APK dans android/build/outputs/apk/debug/
```

---

## 3. Premier demarrage

Au premier lancement, aucun utilisateur n'existe. Vous devez en creer un :

1. Cliquez sur **Creer un nouvel utilisateur**
2. Saisissez un nom d'utilisateur
   - Caracteres autorises : lettres, chiffres et underscores (`_`)
3. Choisissez un **mot de passe maitre** respectant les exigences :
   - Minimum **12 caracteres**
   - Au moins **1 majuscule** (A-Z)
   - Au moins **1 minuscule** (a-z)
   - Au moins **1 chiffre** (0-9)
   - Au moins **1 caractere special** (!@#$%...)
   - Ne doit pas etre un mot de passe courant (44 mots de passe connus rejetes, ex: "password", "letmein", "motdepasse")
4. Confirmez le mot de passe
5. Validez la creation

Une case a cocher **Afficher le mot de passe** permet de verifier la saisie lors de la creation.

> **Attention** : en cas d'oubli du mot de passe maitre, aucune recuperation n'est possible. Le chiffrement utilise (AES-256-GCM) rend les donnees definitivement inaccessibles sans ce mot de passe.

---

## 4. Connexion

L'ecran de connexion propose les actions suivantes :

| Action | Description |
|--------|-------------|
| Selectionner un utilisateur | Liste deroulante des utilisateurs existants |
| Saisir le mot de passe maitre | Champ masque, valider avec le bouton **Connexion** ou la touche **Entree** |
| Afficher / masquer le mot de passe | Case a cocher pour verifier la saisie |
| Creer un nouvel utilisateur | Lien en bas du formulaire |
| Changer la langue | Selecteur Francais / English en bas de l'ecran (effet immediat) |
| Verifier les mises a jour | Lien en bas de l'ecran, verifie la derniere version sur GitHub |

### Protection anti brute-force

Apres **3 tentatives echouees consecutives** pour un meme utilisateur, le formulaire de connexion est temporairement desactive. Le delai augmente progressivement (jusqu'a 30 secondes) avant de pouvoir reessayer. Le compteur est reinitialise apres une connexion reussie. Ce compteur persiste meme apres un verrouillage/deverrouillage de l'application.

---

## 5. Interface principale

### Desktop

Apres connexion, l'interface desktop se compose de quatre zones :

### 5.1. Barre de menus

| Menu | Sous-menus |
|------|-----------|
| **Fichier** | Importer..., Exporter..., --- , Parametres, --- , Verrouiller, Quitter |
| **Edition** | Nouvelle entree, Modifier l'entree, Supprimer l'entree, --- , Changer mot de passe maitre |
| **Affichage** | Actualiser, --- , Trier par nom, Trier par identifiant, Trier par email, Trier par pseudo, Trier par site, Trier par date, Trier par categorie, --- , Mots de passe faibles, Mots de passe reutilises |
| **Outils** | Generateur de mots de passe, Analyse de securite, --- , Synchroniser maintenant |
| **Aide** | A propos |

### 5.2. Barre d'outils

Acces rapide aux fonctions courantes :

| Bouton | Action |
|--------|--------|
| Nouvelle entree | Ouvrir le formulaire de creation |
| Generateur de mots de passe | Ouvrir le generateur |
| Synchroniser maintenant | Lancer la synchronisation (mode distant) |
| Verrouiller | Verrouiller le coffre |

### 5.3. Zone centrale (3 colonnes)

| Colonne | Contenu |
|---------|---------|
| **Gauche** (180 px) | Liste des categories avec bouton d'ajout |
| **Centre** | Barre de recherche + tableau des entrees (Titre, Identifiant, Email, Pseudo, Categorie, Force) — en-tetes cliquables pour trier. Barre d'actions en masse en bas (visible quand >1 entree selectionnee) |
| **Droite** (300 px) | Details de l'entree selectionnee |

### 5.4. Barre de statut

Affiche en bas de la fenetre :
- Le statut de synchronisation (Mode local, Synchronise, Erreur, etc.)
- Le nom de l'utilisateur connecte
- Le nombre total d'entrees

### Android

Apres connexion, l'interface Android se compose de :

- **TopAppBar** avec titre, icone de recherche et menu overflow (importer..., exporter..., synchroniser, audit de securite, parametres, verrouiller)
- **Dropdown categorie** pour le filtrage (remplace les FilterChips) : liste deroulante "Toutes les categories" + categories existantes
- **Liste scrollable** (LazyColumn) des entrees avec indicateur de force, titre, identifiant et categorie
- **Selection multiple** : appui long sur une entree pour activer le mode selection, checkbox sur chaque entree, bouton "Modifier" (changement de categorie en masse) et bouton "Supprimer" (suppression en masse avec confirmation)
- **FAB** (bouton flottant) pour creer une nouvelle entree
- **Navigation** par ecrans : detail, edition, generateur, parametres, changement de mot de passe maitre, audit

La navigation suit le pattern Android standard : appui sur le bouton retour pour revenir a l'ecran precedent.

---

## 6. Gestion des entrees

### 6.1. Creer une entree

**Acces** : Edition > Nouvelle entree | `Ctrl+N` | Barre d'outils

Le formulaire contient les champs suivants :

| Champ | Obligatoire | Description |
|-------|:-----------:|-------------|
| Titre | Oui | Nom du service ou du site |
| Identifiant | Non | Nom d'utilisateur |
| Email | Non | Adresse email |
| Pseudo | Non | Pseudo / surnom / display name |
| Mot de passe | Oui | Mot de passe du compte |
| URL du site | Non | Adresse web du service (cliquable dans le detail) |
| Notes | Non | Informations complementaires |
| Categorie | Non | Categorie de classement (liste deroulante) |
| Tags | Non | Etiquettes separees par des virgules |

Le formulaire affiche en temps reel un **indicateur de force** du mot de passe (barre coloree + texte).

Une case a cocher **Afficher** permet de reveler le mot de passe saisi.

Un bouton **Generer** permet d'ouvrir le generateur integre et d'inserer directement le mot de passe genere dans le champ.

### 6.2. Modifier une entree

- **Double-clic** sur l'entree dans le tableau
- Ou selectionner l'entree puis Edition > Modifier l'entree
- Ou **clic droit** > Modifier (menu contextuel)

Le formulaire de modification est identique a celui de creation, pre-rempli avec les valeurs existantes.

### 6.3. Supprimer une entree

**Acces** : Edition > Supprimer l'entree | Touche `Suppr` | Clic droit > Supprimer

Une boite de confirmation est affichee avant la suppression. L'action est irreversible. Si plusieurs entrees sont selectionnees, la confirmation indique le nombre d'entrees concernees.

### 6.4. Selection multiple et operations en masse (Desktop)

Le tableau des entrees supporte la selection multiple (Ctrl+clic, Shift+clic). Lorsque plus d'une entree est selectionnee, une barre d'actions en masse apparait sous le tableau :

| Action | Description |
|--------|-------------|
| **Supprimer la selection (N)** | Supprime les N entrees selectionnees apres confirmation |
| **Changer la categorie** | Reassigne toutes les entrees selectionnees a la categorie choisie |

### 6.5. Menu contextuel (Desktop - clic droit)

Un clic droit sur le tableau affiche un menu contextuel avec les actions suivantes :

| Action | Disponibilite | Description |
|--------|:------------:|-------------|
| Modifier | 1 entree | Ouvre le formulaire d'edition |
| Supprimer | 1+ entrees | Supprime avec confirmation |
| Copier le mot de passe | 1 entree | Copie dans le presse-papiers |
| Copier l'identifiant | 1 entree | Copie dans le presse-papiers |
| Copier l'email | 1 entree | Copie dans le presse-papiers |
| Copier l'URL | 1 entree | Copie dans le presse-papiers |
| Ouvrir l'URL | 1 entree | Ouvre l'URL dans le navigateur par defaut |
| Dupliquer | 1 entree | Cree une copie de l'entree avec le prefixe "Copie de" |

### 6.6. Dupliquer une entree (Desktop)

**Acces** : Clic droit > Dupliquer

Cree une nouvelle entree identique a l'entree selectionnee avec le titre prefixe par "Copie de". Tous les champs sont copies (identifiant, email, pseudo, mot de passe, URL, notes, categorie, tags).

### 6.7. Consulter les details

Cliquer sur une entree dans le tableau affiche ses details dans le panneau droit :

- Titre (en gras, centre)
- Grille de details : identifiant, email, pseudo, mot de passe, URL (cliquable — ouvre le navigateur), categorie, notes, dates de creation et modification
- **Mot de passe masque** par defaut (case a cocher **Afficher le mot de passe** pour le reveler)
- Le mot de passe se re-masque automatiquement apres **30 secondes**
- Bouton **Copier l'identifiant** pour copier le nom d'utilisateur dans le presse-papiers
- Bouton **Copier le mot de passe** pour copier le mot de passe dans le presse-papiers

---

## 7. Recherche, tri et filtres

### 7.1. Recherche en temps reel

La barre de recherche en haut du tableau filtre les entrees au fur et a mesure de la saisie. La recherche porte sur : titre, identifiant, email, pseudo, URL, notes, categorie et tags. Elle est insensible a la casse.

### 7.2. Tri

**Menu** : Affichage > Trier par...

| Option | Comportement |
|--------|-------------|
| Trier par nom | Ordre alphabetique sur le titre |
| Trier par identifiant | Ordre alphabetique sur l'identifiant |
| Trier par email | Ordre alphabetique sur l'email |
| Trier par pseudo | Ordre alphabetique sur le pseudo |
| Trier par site | Ordre alphabetique sur l'URL |
| Trier par date | Plus recemment modifie en premier |
| Trier par categorie | Regroupement alphabetique par categorie |

Sur le desktop, cliquer sur un en-tete de colonne du tableau applique directement le tri correspondant.

### 7.3. Filtres de securite

**Menu** : Affichage

| Filtre | Description |
|--------|-------------|
| Mots de passe faibles | Affiche les entrees dont le mot de passe est evalue comme **Faible** |
| Mots de passe reutilises | Affiche les entrees partageant le meme mot de passe |

### 7.4. Actualiser

**Menu** : Affichage > Actualiser | `F5`

Recharge l'affichage du coffre depuis les donnees en memoire.

---

## 8. Categories

### Categories par defaut

| Categorie |
|-----------|
| Email |
| Bancaire |
| Reseaux sociaux |
| Travail |
| Autre |

Les noms des categories par defaut sont localises selon la langue de l'interface.

### Actions

| Action | Desktop | Android |
|--------|---------|---------|
| Voir toutes les entrees | Cliquer sur **Toutes les categories** dans le panneau gauche | Selectionner "Toutes les categories" dans le dropdown |
| Filtrer par categorie | Cliquer sur une categorie dans le panneau gauche | Selectionner dans le dropdown |
| Ajouter une categorie | Bouton **Ajouter une categorie** en bas du panneau gauche | Parametres > Gerer les categories > champ d'ajout |
| Supprimer une categorie | Non disponible | Parametres > Gerer les categories > icone supprimer |

Les categories personnalisees sont sauvegardees dans le coffre et persistent entre les sessions.

### Gestion des categories (Android)

**Acces** : Parametres > Gerer les categories

Cet ecran permet de :
- **Ajouter** une nouvelle categorie (max 50 caracteres, pas de doublons)
- **Supprimer** une categorie existante (confirmation requise)

Lors de la suppression d'une categorie, toutes les entrees qui lui etaient assignees sont automatiquement reassignees sans categorie.

Les categories creees dans le formulaire d'edition d'entree (en saisissant un nouveau nom dans le champ categorie) sont automatiquement ajoutees a la liste des categories du coffre.

---

## 9. Generateur de mots de passe

**Acces** : Outils > Generateur de mots de passe | Barre d'outils | Bouton **Generer** dans le formulaire d'entree

### Options de generation

| Option | Description | Valeur par defaut |
|--------|-------------|:-----------------:|
| Longueur | Nombre de caracteres (8 a 128) | 16 |
| Majuscules (A-Z) | Inclure des lettres majuscules | Active |
| Minuscules (a-z) | Inclure des lettres minuscules | Active |
| Chiffres (0-9) | Inclure des chiffres | Active |
| Caracteres speciaux | Inclure `!@#$%^&*()-_=+[]{}|;:',.<>?/` | Active |
| Exclure caracteres ambigus | Retirer 0/O/o, 1/l/I | Desactive |

Le generateur garantit qu'au moins un caractere de chaque type active est present dans le mot de passe.

### Boutons d'action

| Bouton | Action |
|--------|--------|
| **Generer** | Cree un nouveau mot de passe |
| **Copier** | Copie le mot de passe dans le presse-papiers (efface automatiquement apres le delai configure) |
| **Utiliser** | Insere le mot de passe dans le champ du formulaire (disponible quand ouvert depuis un formulaire) |

Un indicateur de force du mot de passe est affiche en temps reel.

---

## 10. Analyse de securite

**Acces** : Outils > Analyse de securite

L'analyse examine l'ensemble du coffre et genere un rapport comprenant trois sections :

### 10.1. Mots de passe faibles

Liste les entrees dont le mot de passe est evalue comme **Faible** selon les criteres :
- Longueur insuffisante (< 8 caracteres)
- Diversite de caracteres insuffisante
- Sequences repetitives ou sequentielles

### 10.2. Mots de passe reutilises

Identifie les groupes d'entrees partageant un meme mot de passe. La reutilisation est un risque majeur : si un service est compromis, tous les comptes partageant ce mot de passe deviennent vulnerables.

### 10.3. Mots de passe anciens

Liste les entrees dont le mot de passe n'a pas ete modifie depuis plus de **180 jours** (configurable via les parametres du coffre).

### Indicateur de force

| Niveau | Couleur | Signification |
|--------|---------|---------------|
| Faible | Rouge | Mot de passe facilement devinable |
| Moyen | Orange | Protection minimale |
| Fort | Vert | Bonne securite |
| Tres fort | Bleu | Securite optimale |

Le resultat affiche le nombre total de problemes detectes, ou confirme qu'aucun probleme n'a ete trouve.

---

## 11. Import et export

L'import et l'export se font via une **popup unifiee** (desktop et Android) qui propose le choix du format : CSV, JSON ou sauvegarde chiffree (.enc).

**Acces** : Fichier > Importer... / Exporter... (desktop) | Menu overflow > Importer... / Exporter... (Android)

### 11.1. Import CSV

Selectionnez un fichier CSV (taille max : 10 Mo). L'import detecte automatiquement :

- **Le separateur** : virgule (`,`) ou point-virgule (`;`)
- **Les colonnes** : reconnaissance multilingue des en-tetes

#### Noms de colonnes reconnus

| Champ | Alias acceptes |
|-------|---------------|
| Titre | `title`, `organisme`, `name`, `nom`, `titre` |
| Identifiant | `username`, `identifiant`, `login`, `adresse mail / identifiant` |
| Email | `email`, `mail`, `adresse mail`, `e-mail`, `courriel` |
| Pseudo | `pseudo`, `nickname`, `alias`, `surnom`, `display name` |
| Mot de passe | `password`, `mdp`, `mot de passe`, `pass` |
| URL | `url`, `site`, `website`, `lien` |
| Notes | `notes`, `description`, `commentaire` |
| Categorie | `category`, `categorie`, `type` |
| Tags | `tags`, `etiquettes` |

La detection est insensible a la casse et aux accents.

#### Repli positionnel

Si aucun en-tete n'est reconnu, les colonnes sont interpretees dans l'ordre : titre, identifiant, email, pseudo, mot de passe, URL, notes, categorie, tags.

#### Limites

- Maximum **10 000 entrees** par import
- Maximum **10 000 caracteres** par champ
- Les caracteres de controle (sauf tabulation et retour a la ligne) sont automatiquement supprimes
- Categorie par defaut si vide : "Autre"
- Tags separes par des points-virgules dans le CSV

#### Exemples de formats supportes

Format standard avec virgule :
```csv
title,username,email,pseudo,password,url,notes,category,tags
Gmail,john_doe,john@example.com,JohnD,MyP@ssw0rd,https://gmail.com,Compte principal,Email,google;mail
```

Format francais avec point-virgule :
```csv
Organisme;URL;Adresse mail / Identifiant;Mdp;Description
Gmail;https://gmail.com;user@example.com;MyP@ssw0rd;Compte principal
```

### 11.2. Import JSON

Importe un fichier JSON au format d'export de l'application. Chaque entree recoit un nouvel identifiant unique. Les champs sont assainis et tronques a 10 000 caracteres.

### 11.3. Import sauvegarde chiffree

Importe les entrees depuis un fichier coffre chiffre (`.enc`) provenant d'un autre utilisateur ou d'une sauvegarde. Un champ de mot de passe (masque par defaut, avec case a cocher **Afficher**) permet de saisir le mot de passe maitre du vault source. Les entrees dechiffrees sont **ajoutees par fusion** au coffre courant (les entrees existantes ne sont pas ecrasees).

### 11.4. Export CSV

Exporte toutes les entrees avec les colonnes : `title`, `username`, `email`, `pseudo`, `password`, `url`, `notes`, `category`, `tags`.

> **Avertissement** : les donnees exportees ne sont **pas chiffrees**. Un message d'avertissement est affiche avant l'export.

> **Protection anti-injection** : les champs commencant par `=`, `+`, `-`, `@`, tabulation ou retour chariot sont automatiquement prefixes par `'` pour empecher l'execution de formules dans les tableurs (Excel, Calc).

Le fichier exporte recoit automatiquement des permissions restrictives (proprietaire uniquement).

### 11.5. Export JSON

Exporte le coffre complet au format JSON. Les donnees ne sont pas chiffrees. Le fichier exporte recoit des permissions restrictives.

### 11.6. Export sauvegarde chiffree

Cree une copie du fichier coffre chiffre (`.enc`). Ce fichier ne peut etre ouvert qu'avec le mot de passe maitre correspondant. C'est le **moyen le plus sur** de sauvegarder vos donnees.

### 11.7. Specificites Android

Sur Android, l'import et l'export utilisent le **Storage Access Framework (SAF)** : un selecteur de fichiers systeme permet de choisir l'emplacement. L'application n'a pas besoin de permissions de stockage globales.

---

## 12. Synchronisation distante

La synchronisation SFTP est disponible sur **desktop et Android**. Elle permet de maintenir le coffre synchronise entre plusieurs appareils via un serveur SFTP.

### 12.1. Modes de stockage

| Mode | Description |
|------|-------------|
| **Local uniquement** (defaut) | Le coffre est stocke uniquement sur la machine locale |
| **Serveur distant** | Le coffre est synchronise avec un serveur SFTP |

### 12.2. Configuration SFTP

**Acces** : Fichier > Parametres > onglet Synchronisation (desktop) | Parametres > section Synchronisation (Android)

| Parametre | Description | Defaut |
|-----------|-------------|--------|
| Mode de stockage | Local uniquement / Serveur distant | Local |
| Hote | Adresse du serveur SFTP | -- |
| Port | Port SSH | 22 |
| Utilisateur SSH | Nom d'utilisateur sur le serveur | -- |
| Cle privee SSH | Chemin vers la cle privee (authentification par cle uniquement) | -- |
| Repertoire distant | Dossier de stockage sur le serveur | `/vault/data` |

Le bouton **Tester la connexion** permet de verifier la configuration.

> **Note** : seule l'authentification par cle SSH est supportee. L'authentification par mot de passe n'est pas disponible.

> En mode distant, la validation requiert que le fichier de cle SSH existe et soit lisible.

### 12.3. Synchronisation manuelle

**Acces** : Outils > Synchroniser maintenant | Barre d'outils

La synchronisation compare le coffre local et le coffre distant via leurs empreintes SHA-256 :
- Si identiques : aucune action
- Si differents et le local est plus recent : envoi du local vers le serveur
- Si differents et le distant est plus recent : notification de conflit

### 12.4. Mode hors-ligne

Si le serveur est injoignable, les modifications sont mises en attente localement (fichier `.pending`). Elles sont automatiquement synchronisees lors de la prochaine connexion reussie.

### 12.5. Gestion des conflits

Lorsque le coffre a ete modifie a la fois localement et sur le serveur, un dialogue propose trois options :

| Option | Comportement |
|--------|-------------|
| **Garder mes modifications locales** | Le coffre local ecrase la version distante |
| **Utiliser la version du serveur** | Le coffre distant remplace la version locale |
| **Sauvegarder les deux versions** | Une copie de sauvegarde locale horodatee est creee, puis la version distante est appliquee |

---

## 13. Parametres

**Acces** : Fichier > Parametres

### 13.1. Onglet General

| Parametre | Description | Valeurs |
|-----------|-------------|---------|
| Langue | Langue de l'interface | Francais / English |
| Theme | Apparence visuelle | Systeme / Clair / Sombre |

Le theme **Systeme** detecte automatiquement le theme clair ou sombre de l'OS.

Le changement de theme prend effet immediatement. Le changement de langue reconstruit l'interface.

### 13.2. Onglet Securite

| Parametre | Description | Plage | Defaut |
|-----------|-------------|-------|--------|
| Verrouillage automatique | Delai d'inactivite avant verrouillage | 1 a 60 minutes | 15 min |
| Effacement presse-papiers | Delai d'effacement apres copie d'un mot de passe | 5 a 120 secondes | 30 s |

### 13.3. Onglet Synchronisation

Voir la section [Synchronisation distante](#12-synchronisation-distante).

---

## 14. Changement du mot de passe maitre

**Acces** : Edition > Changer mot de passe maitre

### Procedure

1. Saisir l'**ancien mot de passe** (verifie par re-dechiffrement du coffre)
2. Saisir le **nouveau mot de passe** (memes exigences que lors de la creation)
3. **Confirmer** le nouveau mot de passe
4. Valider

### Fonctionnement interne

Le changement de mot de passe ne re-chiffre **pas** l'ensemble des donnees. Seule la cle de donnees (DEK) est re-chiffree avec la nouvelle cle derivee du nouveau mot de passe. L'operation est donc quasi instantanee, quelle que soit la taille du coffre.

---

## 15. Securite

### 15.1. Chiffrement

- **Algorithme** : AES-256-GCM (chiffrement authentifie)
- **Derivation de cle** : PBKDF2-HMAC-SHA256 avec 600 000 iterations et sel de 32 octets
- **Architecture** : chiffrement par enveloppe (DEK/KEK) separant la cle de donnees de la cle derivee du mot de passe

### 15.2. Protection du mot de passe maitre

- Politique stricte : minimum 12 caracteres, 4 types requis, rejet des mots de passe courants (44 mots de passe connus incluant des variantes francaises comme "motdepasse")
- Le mot de passe n'est **jamais conserve en memoire** apres l'authentification
- Manipulation en `char[]` (pas en `String`) pour permettre l'effacement explicite
- Comparaison a temps constant contre la liste de mots de passe courants (empeche les attaques par canal auxiliaire de timing)
- Sur Android, les formulaires contenant des mots de passe effacent automatiquement leurs donnees a la destruction du ViewModel

### 15.3. Verrouillage automatique

Apres une periode d'inactivite configurable (defaut : 15 minutes), le coffre se verrouille automatiquement. Les cles sont effacees de la memoire et l'ecran de connexion est affiche.

L'inactivite est detectee par l'absence d'evenements clavier et souris.

### 15.4. Presse-papiers

Lorsqu'un mot de passe ou un identifiant est copie :
- Le presse-papiers est automatiquement efface apres le delai configure (defaut : 30 secondes)
- Le presse-papiers est egalement efface au verrouillage et a la fermeture de l'application
- Sur Android 13+ (API 33), le flag `EXTRA_IS_SENSITIVE` empeche l'affichage du contenu dans la previsualisation du presse-papiers

### 15.5. Masquage des mots de passe

- Les mots de passe sont masques par defaut dans tous les affichages
- Le devoilement est temporaire : **retour automatique au masquage apres 30 secondes**
- L'ecran de connexion et le formulaire de creation d'utilisateur disposent d'une case a cocher pour afficher/masquer le mot de passe

### 15.6. Securite des fichiers

- Tous les fichiers sensibles (coffres, configuration, cle) ont des permissions restreintes au proprietaire uniquement
  - Linux/macOS : `rw-------` (600) pour les fichiers, `rwx------` (700) pour les repertoires
  - Windows : ACL limitee au proprietaire (avec preservation de SYSTEM)
- Ecriture atomique (fichier temporaire + permissions + renommage) pour prevenir la corruption
- Les fichiers exportes en clair recoivent egalement des permissions restrictives

### 15.7. Aucune recuperation

Par conception, il n'existe **aucun mecanisme de recuperation** du mot de passe maitre. Cela garantit qu'un attaquant ne peut pas contourner le chiffrement. Il est recommande de conserver une sauvegarde chiffree du coffre en lieu sur.

---

## 16. Raccourcis clavier

| Raccourci | Action |
|-----------|--------|
| `Ctrl+N` | Nouvelle entree |
| `Suppr` | Supprimer l'entree selectionnee |
| `F5` | Actualiser l'affichage |
| `Entree` (ecran de connexion) | Se connecter |
| Double-clic sur une entree | Modifier l'entree |
| Clic droit sur une entree | Menu contextuel (modifier, supprimer, copier, ouvrir URL, dupliquer) |
| `Ctrl+Clic` / `Shift+Clic` | Selection multiple d'entrees |

---

## 17. Verification des mises a jour

L'application verifie automatiquement la disponibilite de nouvelles versions via l'API GitHub Releases.

### 17.1. Desktop

- **Verification automatique** : au lancement et toutes les 5 minutes en arriere-plan
- **Verification manuelle** : lien **Verifier les mises a jour** sur l'ecran de connexion
- **Notification** : une barre jaune non intrusive apparait en haut de la fenetre principale si une mise a jour est disponible, avec un bouton pour ouvrir la page de release
- La barre de notification est masquable (bouton X)

### 17.2. Android

- **Verification automatique** : au lancement de l'ecran principal
- **Notification** : une boite de dialogue s'affiche si une mise a jour est disponible, avec les options **Telecharger** (ouvre le navigateur vers la page GitHub) ou **Plus tard**

### 17.3. Securite

- Les URLs sont validees avant ouverture (seul le domaine `https://github.com/` est accepte)
- La taille maximale de la reponse API est limitee a 1 Mo pour prevenir les attaques par depassement de memoire
- La comparaison de version gere le format semantique (major.minor.patch) et ignore les suffixes pre-release (ex: `-rc1`)

---

## 18. Structure des donnees utilisateur

Les donnees de l'application sont stockees dans le repertoire `~/.password-manager/` :

```
~/.password-manager/
+-- data/
    +-- config.properties              # Configuration de l'application
    +-- .config_key                    # Cle de chiffrement de la configuration
    +-- vaults/
        +-- vault_alice.enc            # Coffre chiffre de l'utilisateur "alice"
        +-- vault_alice.enc.bak        # Sauvegarde automatique
        +-- vault_bob.enc              # Coffre chiffre de l'utilisateur "bob"
```

### Fichier de configuration

`data/config.properties` stocke les preferences (langue, theme, parametres de securite) et la configuration SFTP. Les identifiants SFTP sont chiffres au repos.

### Fichiers coffre

Chaque utilisateur dispose d'un fichier `.enc` distinct, chiffre independamment avec son propre mot de passe maitre. Des sauvegardes automatiques (`.bak`) sont creees a chaque modification (3 fichiers max conserves).

### Portabilite

Pour migrer vers un autre poste :
1. Exportez une **sauvegarde chiffree** (Fichier > Exporter sauvegarde chiffree)
2. Copiez le fichier `.enc` sur le nouveau poste dans `~/.password-manager/data/vaults/`
3. Connectez-vous avec votre mot de passe maitre habituel

Alternativement, utilisez la **synchronisation SFTP** (desktop et Android) pour maintenir le coffre synchronise entre plusieurs appareils.

---

## 19. Differences entre plateformes

| Fonctionnalite | Desktop | Android |
|---|---|---|
| Coffre-fort chiffre AES-256-GCM | Oui | Oui |
| CRUD entrees | Oui | Oui |
| Generateur de mots de passe | Oui | Oui |
| Analyse de securite | Oui | Oui |
| Import/export unifie (CSV/JSON/.enc) | Fichier > Importer.../Exporter... | Menu overflow > Importer.../Exporter... (SAF) |
| Import sauvegarde chiffree | Oui | Oui |
| Recherche en temps reel | Oui (titre, id, email, pseudo, URL, notes, categorie, tags) | Oui |
| Tri (7 criteres) | En-tetes cliquables + menu Affichage | Menu overflow tri |
| Filtrage par categorie | Panneau lateral | Dropdown (liste deroulante) |
| Selection multiple + operations en masse | Oui (Ctrl/Shift+clic, barre d'actions) | Oui (appui long) |
| Menu contextuel (clic droit) | Oui (modifier, supprimer, copier, ouvrir URL, dupliquer) | Non |
| Gestion des categories | Ajout via panneau lateral | Ecran dedie (Parametres > Gerer les categories) |
| Verification des mises a jour | Auto (5 min) + manuel (ecran connexion) | Au lancement (dialog) |
| URL cliquable dans le detail | Oui (Desktop.browse) | Oui (UriHandler) |
| Themes Systeme/Clair/Sombre | FlatLaf | Material 3 (Dynamic Colors Android 12+) |
| Verrouillage automatique | Oui (evenements AWT) | Oui (ProcessLifecycleOwner) |
| Effacement presse-papiers | Oui | Oui |
| Anti brute-force | Oui | Oui |
| Synchronisation SFTP | Oui | Oui |
| Injection de dependances | N/A (gestion manuelle) | Hilt/Dagger |
| Stockage configuration | `config.properties` chiffre | EncryptedSharedPreferences |
| Raccourcis clavier | Oui | N/A |
| Distribution | JRE embarque (jlink) | APK |
| Langue | Changeable dans l'app | Changeable dans l'app (AppCompatDelegate + locales_config) |
