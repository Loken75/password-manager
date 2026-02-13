# Gestionnaire de Mots de Passe - Documentation fonctionnelle

Application de bureau permettant de stocker, organiser et sécuriser vos mots de passe dans un coffre-fort chiffré. Disponible en français et en anglais.

---

## Table des matières

1. [Prérequis et installation](#prérequis-et-installation)
2. [Premier lancement](#premier-lancement)
3. [Connexion](#connexion)
4. [Interface principale](#interface-principale)
5. [Gestion des entrées](#gestion-des-entrées)
6. [Recherche et filtres](#recherche-et-filtres)
7. [Catégories](#catégories)
8. [Générateur de mots de passe](#générateur-de-mots-de-passe)
9. [Analyse de sécurité](#analyse-de-sécurité)
10. [Import / Export](#import--export)
11. [Synchronisation distante](#synchronisation-distante)
12. [Paramètres](#paramètres)
13. [Sécurité](#sécurité)
14. [Raccourcis clavier](#raccourcis-clavier)
15. [Structure des fichiers](#structure-des-fichiers)

---

## Prérequis et installation

- **Java** 8 ou supérieur
- **Maven** pour la compilation

Compiler et lancer l'application :

```bash
mvn clean package
java -jar target/password-manager-1.0.jar
```

---

## Premier lancement

Au premier lancement, aucun utilisateur n'existe. Vous devez en créer un.

1. Cliquez sur **Créer un nouvel utilisateur**
2. Saisissez un nom d'utilisateur (lettres, chiffres et underscores uniquement)
3. Choisissez un **mot de passe maître** respectant les exigences suivantes :
   - Minimum 12 caractères
   - Au moins 1 majuscule
   - Au moins 1 minuscule
   - Au moins 1 chiffre
   - Au moins 1 caractère spécial
4. Confirmez le mot de passe
5. Validez

> **Attention** : en cas d'oubli du mot de passe maître, aucune récupération n'est possible. Le chiffrement utilisé (AES-256-GCM) rend les données définitivement inaccessibles sans ce mot de passe.

---

## Connexion

L'écran de connexion permet de :

- **Sélectionner un utilisateur** existant dans la liste déroulante
- **Saisir le mot de passe maître** et cliquer sur **Connexion** (ou appuyer sur Entrée)
- **Créer un nouvel utilisateur** via le lien dédié
- **Changer la langue** (Français / English) en bas de l'écran — le changement est immédiat

---

## Interface principale

L'interface se compose de quatre zones :

### Barre de menus

Située en haut, elle donne accès à toutes les fonctionnalités (voir les sections dédiées ci-dessous).

### Barre d'outils

Accès rapide aux actions courantes :

| Bouton | Action |
|---|---|
| Nouvelle entrée | Ouvrir le formulaire de création d'une entrée |
| Générateur de mots de passe | Ouvrir le générateur |
| Synchroniser maintenant | Lancer une synchronisation (si le mode distant est activé) |
| Verrouiller | Verrouiller le coffre et revenir à l'écran de connexion |

### Zone centrale

- **Panneau gauche** : liste des catégories avec un bouton pour en ajouter
- **Panneau central** : barre de recherche et tableau des entrées (Titre, Identifiant, Catégorie, Force du mot de passe)
- **Panneau droit** : détails de l'entrée sélectionnée

### Barre de statut

Affiche en bas de la fenêtre :

- Le statut de synchronisation (Mode local / Synchronisé / Hors ligne / Erreur)
- Le nom de l'utilisateur connecté
- Le nombre total d'entrées dans le coffre

---

## Gestion des entrées

### Créer une entrée

**Menu** : Edition > Nouvelle entrée | **Raccourci** : `Ctrl+N` | **Barre d'outils** : bouton Nouvelle entrée

Le formulaire de création contient les champs suivants :

| Champ | Description |
|---|---|
| Titre | Nom du service ou du site (obligatoire) |
| Identifiant / Email | Nom d'utilisateur ou adresse email |
| Mot de passe | Le mot de passe du compte. Un bouton **Générer** ouvre le générateur intégré |
| URL du site | Adresse web du service |
| Notes | Informations complémentaires (texte libre) |
| Catégorie | Catégorie de classement (liste déroulante) |
| Tags | Étiquettes séparées par des virgules |

Un indicateur de force du mot de passe est affiché en temps réel lors de la saisie.

### Modifier une entrée

- **Double-clic** sur une entrée dans le tableau
- Ou sélectionner l'entrée puis **Menu** : Edition > Modifier l'entrée

Le formulaire de modification reprend les mêmes champs que la création.

### Supprimer une entrée

Sélectionner l'entrée puis **Menu** : Edition > Supprimer l'entrée | **Raccourci** : touche `Suppr`

Une confirmation est demandée avant la suppression.

### Consulter les détails

Cliquer sur une entrée dans le tableau affiche ses détails dans le panneau droit :

- Titre, identifiant, URL, catégorie, notes
- Mot de passe masqué par défaut (case à cocher **Afficher** pour le révéler)
- Bouton **Copier** pour copier le mot de passe dans le presse-papiers
- Dates de création et de dernière modification

---

## Recherche et filtres

### Recherche en temps réel

La barre de recherche en haut du tableau filtre les entrées à mesure de la saisie. La recherche porte sur tous les champs de l'entrée (titre, identifiant, URL, notes, catégorie).

### Tri

**Menu** : Affichage > Trier par...

| Option | Comportement |
|---|---|
| Trier par nom | Ordre alphabétique sur le titre |
| Trier par date | Ordre chronologique sur la date de modification |
| Trier par catégorie | Regroupement par catégorie |

### Filtres de sécurité

**Menu** : Affichage

| Filtre | Description |
|---|---|
| Mots de passe faibles | Affiche les entrées dont le mot de passe est évalué comme faible |
| Mots de passe réutilisés | Affiche les entrées partageant le même mot de passe |

### Actualiser

**Menu** : Affichage > Actualiser | **Raccourci** : `F5`

---

## Catégories

Les catégories par défaut sont :

- **Email**
- **Bancaire**
- **Réseaux sociaux**
- **Travail**
- **Autre**

### Voir toutes les catégories

Cliquer sur **Toutes les catégories** dans le panneau gauche affiche l'ensemble des entrées.

### Filtrer par catégorie

Cliquer sur une catégorie dans le panneau gauche n'affiche que les entrées de cette catégorie.

### Ajouter une catégorie

Cliquer sur le bouton **Ajouter une catégorie** en bas du panneau gauche. Saisir le nom de la nouvelle catégorie et valider.

---

## Générateur de mots de passe

**Menu** : Outils > Générateur de mots de passe | **Barre d'outils** : bouton Générateur

Le générateur permet de créer des mots de passe aléatoires cryptographiquement sûrs.

### Options

| Option | Description | Valeurs |
|---|---|---|
| Longueur | Nombre de caractères du mot de passe | 8 à 128 |
| Majuscules (A-Z) | Inclure des lettres majuscules | Oui / Non |
| Minuscules (a-z) | Inclure des lettres minuscules | Oui / Non |
| Chiffres (0-9) | Inclure des chiffres | Oui / Non |
| Caractères spéciaux | Inclure `!@#$%^&*()-_=+[]{}` etc. | Oui / Non |
| Exclure caractères ambigus | Retirer 0/O, 1/l/I pour éviter les confusions visuelles | Oui / Non |

### Actions

- **Générer** : crée un nouveau mot de passe selon les options sélectionnées
- **Copier** : copie le mot de passe généré dans le presse-papiers
- **Utiliser** : insère le mot de passe dans le champ du formulaire d'entrée (disponible lorsque le générateur est ouvert depuis le formulaire)

Un indicateur de force est affiché en temps réel.

---

## Analyse de sécurité

**Menu** : Outils > Analyse de sécurité

L'audit de sécurité examine l'ensemble du coffre et génère un rapport détaillant :

### Mots de passe faibles

Liste les entrées dont le mot de passe est évalué comme faible (score insuffisant basé sur la longueur et la diversité des caractères).

### Mots de passe réutilisés

Identifie les groupes d'entrées partageant un même mot de passe. La réutilisation de mots de passe est un risque majeur : si un service est compromis, tous les comptes utilisant le même mot de passe sont vulnérables.

### Mots de passe anciens

Liste les entrées dont le mot de passe n'a pas été modifié depuis plus de 180 jours (configurable). La date de dernière modification de chaque entrée est affichée.

Le rapport indique le nombre total de problèmes détectés, ou confirme qu'aucun problème n'a été trouvé.

### Niveaux de force

| Niveau | Couleur | Description |
|---|---|---|
| Faible | Rouge | Mot de passe facilement devinable |
| Moyen | Orange | Protection minimale |
| Fort | Vert | Bonne sécurité |
| Très fort | Bleu | Sécurité optimale |

---

## Import / Export

### Import CSV

**Menu** : Fichier > Importer CSV

Sélectionnez un fichier CSV. L'import détecte automatiquement :

- **Le séparateur** : `,` (virgule) ou `;` (point-virgule), selon celui le plus présent dans l'en-tête
- **Les colonnes** : le nom des colonnes dans l'en-tête est reconnu grâce à un système d'alias multilingue

#### Noms de colonnes reconnus

| Champ | Alias acceptés |
|---|---|
| Titre | `title`, `organisme`, `name`, `nom`, `titre` |
| Identifiant | `username`, `identifiant`, `email`, `adresse mail`, `login`, `adresse mail / identifiant` |
| Mot de passe | `password`, `mdp`, `mot de passe`, `pass` |
| URL | `url`, `site`, `website`, `lien` |
| Notes | `notes`, `description`, `commentaire` |
| Catégorie | `category`, `catégorie`, `categorie`, `type` |
| Tags | `tags`, `étiquettes`, `etiquettes` |

La détection est insensible à la casse et aux accents.

#### Exemples de formats supportés

Format standard :
```
title,username,password,url,notes,category,tags
Gmail,user@example.com,MyP@ssword123,https://gmail.com,Compte principal,Email,google;mail
```

Format avec séparateur point-virgule et en-têtes français :
```
Organisme;URL;Adresse mail / Identifiant;Mdp;Description
Gmail;https://gmail.com;user@example.com;MyP@ssword123;Compte principal
```

#### Repli automatique

Si aucun en-tête n'est reconnu, l'import utilise l'ordre positionnel par défaut : titre, identifiant, mot de passe, URL, notes, catégorie, tags.

### Import JSON

**Menu** : Fichier > Importer JSON

Importe un fichier JSON au format d'export de l'application. Toutes les entrées du fichier sont ajoutées au coffre.

### Export CSV

**Menu** : Fichier > Exporter CSV

Exporte toutes les entrées au format CSV avec les colonnes : `title,username,password,url,notes,category,tags`.

> **Attention** : les données exportées ne sont **pas chiffrées**. Un avertissement est affiché avant l'export.

### Export JSON

**Menu** : Fichier > Exporter JSON

Exporte le coffre complet au format JSON.

> **Attention** : les données exportées ne sont **pas chiffrées**.

### Export sauvegarde chiffrée

**Menu** : Fichier > Exporter sauvegarde chiffrée

Crée une copie du fichier coffre chiffré (`.enc`). Ce fichier ne peut être ouvert qu'avec le mot de passe maître correspondant. C'est le moyen le plus sûr de sauvegarder vos données.

---

## Synchronisation distante

L'application supporte la synchronisation du coffre avec un serveur distant via SFTP.

### Mode local (par défaut)

Le coffre est stocké uniquement sur la machine locale. Aucune connexion réseau n'est nécessaire.

### Mode serveur distant

Le coffre est synchronisé avec un serveur SFTP. Cela permet :

- D'accéder au coffre depuis plusieurs machines
- De disposer d'une sauvegarde distante automatique

#### Configuration

**Menu** : Fichier > Paramètres > onglet Synchronisation

| Paramètre | Description |
|---|---|
| Mode de stockage | Local uniquement / Serveur distant |
| Hôte | Adresse du serveur SFTP |
| Port | Port SSH (22 par défaut) |
| Utilisateur SSH | Nom d'utilisateur sur le serveur |
| Clé privée SSH | Chemin vers la clé privée (authentification par clé uniquement, pas de mot de passe) |
| Répertoire distant | Chemin du dossier de stockage sur le serveur |

Le bouton **Tester la connexion** permet de vérifier que la configuration est valide.

> Pour la configuration du serveur, consultez le fichier `GUIDE_SERVEUR.md`.

#### Synchronisation manuelle

**Menu** : Outils > Synchroniser maintenant | **Barre d'outils** : bouton Synchroniser

#### Mode hors ligne

Si le serveur est inaccessible, les modifications sont enregistrées localement dans un cache. Elles seront synchronisées automatiquement lors de la prochaine connexion réussie.

#### Gestion des conflits

Lorsque le coffre a été modifié à la fois en local et sur le serveur, un dialogue propose trois options :

| Option | Comportement |
|---|---|
| Garder mes modifications locales | Le coffre local écrase la version distante |
| Utiliser la version du serveur | Le coffre distant remplace la version locale |
| Sauvegarder les deux versions | Une copie de sauvegarde est créée avant d'appliquer la version distante |

---

## Paramètres

**Menu** : Fichier > Paramètres

### Onglet Général

| Paramètre | Description | Valeurs |
|---|---|---|
| Langue | Langue de l'interface | Français / English |
| Thème | Apparence visuelle | Clair / Sombre |

Le changement de langue nécessite un redémarrage de l'application.

### Onglet Sécurité

| Paramètre | Description | Valeurs |
|---|---|---|
| Verrouillage automatique | Délai d'inactivité avant verrouillage automatique du coffre | 1 à 60 minutes (défaut : 15) |
| Effacement presse-papiers | Délai après lequel le presse-papiers est vidé après une copie de mot de passe | 5 à 120 secondes (défaut : 30) |

### Onglet Synchronisation

Voir la section [Synchronisation distante](#synchronisation-distante).

### Changer le mot de passe maître

**Menu** : Edition > Changer mot de passe maître

1. Saisir l'ancien mot de passe
2. Saisir le nouveau mot de passe (mêmes exigences que lors de la création)
3. Confirmer le nouveau mot de passe
4. Valider

Le coffre est re-chiffré avec le nouveau mot de passe.

---

## Sécurité

### Chiffrement

- Algorithme : **AES-256-GCM** (chiffrement authentifié)
- Dérivation de clé : **PBKDF2-HMAC-SHA256** avec 100 000 itérations et un sel aléatoire de 32 octets
- Chaque opération de sauvegarde utilise un vecteur d'initialisation (IV) unique

### Verrouillage automatique

Après une période d'inactivité configurable (aucune action clavier ou souris), le coffre se verrouille automatiquement et retourne à l'écran de connexion. Le mot de passe maître est effacé de la mémoire.

### Effacement du presse-papiers

Lorsqu'un mot de passe est copié dans le presse-papiers, celui-ci est automatiquement vidé après le délai configuré (par défaut 30 secondes).

### Masquage des mots de passe

Les mots de passe sont masqués par défaut dans le panneau de détails. Ils ne sont révélés que sur action explicite (case à cocher **Afficher**).

### Aucune récupération possible

Par conception, il n'existe aucun mécanisme de récupération du mot de passe maître. Cela garantit que seul le détenteur du mot de passe peut accéder aux données.

---

## Raccourcis clavier

| Raccourci | Action |
|---|---|
| `Ctrl+N` | Nouvelle entrée |
| `Suppr` | Supprimer l'entrée sélectionnée |
| `F5` | Actualiser l'affichage |
| `Entrée` (écran de connexion) | Se connecter |
| Double-clic sur une entrée | Modifier l'entrée |

---

## Structure des fichiers

```
~/.password-manager/
├── config.properties          # Configuration de l'application
└── vaults/
    ├── vault_alice.enc        # Coffre chiffré de l'utilisateur "alice"
    └── vault_bob.enc          # Coffre chiffré de l'utilisateur "bob"
```

### config.properties

Fichier de configuration en clair (ne contient aucune donnée sensible). Stocke la langue, le thème, les paramètres de sécurité et la configuration SFTP.

### Fichiers .enc

Fichiers coffre chiffrés. Chaque utilisateur dispose de son propre fichier. Le format interne est un enveloppe JSON contenant la version, le sel, le vecteur d'initialisation et les données chiffrées.
