# Bibliothèque de composants

Spécifications des composants partagés (lot 1). Chaque composant existe sur **les deux
plateformes** avec le même comportement et la même apparence (aux contraintes de
toolkit près). Tous les chiffres viennent des tokens `component.*` / `semantic.*`.

> Convention : sur Android = composables Kotlin sous `ui/components/` ; sur desktop =
> factories/sous-classes Swing sous `ui/components/` (paquet à créer). Les secrets
> restent en `char[]`/`byte[]`, copies défensives et wipe préservés — aucun changement
> de posture sécurité.

---

## EntryCard

Carte de liste unifiée pour les 3 types d'entrées (remplace les `JTable` denses du
desktop et harmonise les cartes Android).

```
┌──────────────────────────────────────────────┐
│ │ ◉  github.com                  ●fort    ★   │   ← barre force (gauche) + avatar
│ │    alice@example.com           Dev          │     + titre/sous-titre + badge + étoile
└──────────────────────────────────────────────┘
```

- **Avatar** 40px : favicon si disponible (token `faviconsEnabled`), sinon initiale sur
  fond `category.{hash}`.
- **Titre** `titleSm`, **sous-titre** `bodySm secondary` (username/email/type selon entrée).
- **Badge de force** (`StatusBadge`) — mots de passe uniquement.
- **Étoile favori** : zone 48px, icône 20px, `color.favorite`, morph expressif au toggle.
- **Barre de force** verticale 6px à gauche (couleur statut) — mots de passe uniquement.
- **États** : repos / hover (desktop) / pressed / **selected** (mode sélection multiple,
  `accent.container`) / swipe (Android : copier ↔ supprimer avec couleurs statut).
- **A11y** : carte = 1 cible, libellé accessible « <titre>, <type>, force <niveau>,
  favori » ; actions secondaires exposées séparément.

## BentoCard

Brique du dashboard d'accueil. Conteneur arrondi `lg`, padding 24, ombre `level1`.

- Variantes : **Santé du coffre** (score `display` + ventilation), **Alertes HIBP**
  (compte + CTA), **Activité/Récents**.
- Grille responsive : 3 colonnes en large, empilées et scrollables en étroit.
- Repliable, préférence persistée (réutilise `AppConfig`, pas de nouveau champ core si
  évitable — sinon un simple booléen UI local).
- Données dérivées de l'audit `:core` existant + `HibpChecker` — **aucune logique
  nouvelle**.

## SecretField

Champ d'affichage/édition d'un secret (mot de passe, PIN, clé privée).

- Police **mono**, masqué par défaut (`••••••••`).
- **Révélation explicite** : bouton œil → texte clair, **auto-masquage à 30s**
  (token `secretField.revealAutoHideMs`), annoncé aux lecteurs d'écran. Jamais exposé à
  l'arbre d'accessibilité tant que masqué.
- **Copier** : bouton dédié → presse-papiers sécurisé existant (`SecureClipboard` /
  équivalent Android) + micro-confirmation « Copié » (motion expressive courte).
- En édition : jauge `StrengthMeter` inline + bouton générateur intégré.

## StrengthMeter

Barre de force. Hauteur 6px, rayon `full`, couleur = `color.status.*` selon le score
0–100 de `PasswordStrengthAnalyzer`. Largeur animée (`animateFloat`), couleur animée
(`animateColor`), désactivé si reduced-motion. Libellé `label` à droite (Faible/Moyen/
Fort/Très fort) — **jamais la couleur seule** (a11y).

## StatusBadge

Pastille d'état sécurité (rayon `full`, padding 8×2). Trois variantes : strong / medium /
weak. Texte en `*.text` (variante foncée, contraste ≥ 4.5:1) sur fond `*.container`, ou
fill plein + texte `onStatus` selon le contexte. Toujours libellé + icône, pas que la
couleur.

## SearchField

Champ de recherche persistant en tête de liste. Rayon `md`, icône loupe, bouton clear.
Filtre en direct (debounce léger côté UI pour les gros coffres). Remplace les barres de
recherche custom hétérogènes.

## SegmentedTabs

Sélecteur **Mots de passe / Applications / Clés SSH** visible sur les deux plateformes —
**unifie enfin l'accès aux 3 types** (aujourd'hui desktop = 3 onglets, Android = 2 + SSH
caché dans les réglages). Style « segmented control » arrondi, indicateur animé,
navigable au clavier (desktop) et accessible (rôle tab).

## Boutons

- **PrimaryButton** : fond `accent`, texte `onAccent`, rayon `md`, hauteur min 48,
  anneau focus 2px.
- **TonalButton** : fond `accent.container`, texte `onContainer` (actions secondaires).
- **IconButton** : zone 48px, icône 24px, libellé accessible obligatoire (corrige les
  `contentDescription` manquants Android et l'absence de noms accessibles Swing).
- États repos/hover/pressed/disabled/focus tous dérivés des tokens.

## EmptyState

État vide standardisé (liste vide, recherche sans résultat, coffre neuf) : icône 64px
`text.secondary`, titre `title`, sous-titre `bodySm secondary`, CTA optionnel. Style
unique partagé (aujourd'hui incohérent d'un écran à l'autre).

## AppDialog

Conteneur de dialogue/sheet unifié : rayon `xl`, padding 24, titre `title`, zone
actions alignée à droite (Annuler tonal / Valider primary). Sur Android : `ModalBottom
Sheet` ou `AlertDialog` selon le cas ; sur desktop : `JDialog` stylé tokens. Gère le
focus (WCAG 2.4.11 « focus non masqué ») et la fermeture clavier (Échap).

---

## Galerie de démo

Avant de refondre les vrais écrans (lots 2+), je livre une **galerie de composants**
(un écran/fenêtre listant tous les composants ci-dessus dans leurs états, en light/dark)
sur chaque plateforme — pour validation visuelle rapide de la direction.
