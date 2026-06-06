# Design System — Password Manager

Direction : **« Expressif & moderne »**. Système de design partagé qui pilote l'UI
des **deux clients** (desktop Swing + FlatLaf, Android Compose + Material 3) depuis
une **source de vérité unique** : [`tokens.json`](./tokens.json).

> Objectif : cohérence visuelle desktop ↔ Android (la parité que suit déjà la table
> de fonctionnalités du `README` racine), accessibilité WCAG 2.2 AA dès la première
> itération, et zéro valeur codée en dur dans l'UI.

## Principes

1. **Calme, structuré, expressif.** Surfaces neutres généreuses, cartes arrondies sur
   grille 8px, profondeur douce (ombres diffuses + tint tonal). La personnalité vient
   des **avatars/chips colorés par catégorie**, du **dashboard bento** et de quelques
   moments de motion expressifs — jamais au détriment de la lisibilité.
2. **La couleur est un signal de sécurité.** Vert = fort/sain, ambre = faible/réutilisé,
   rouge = compromis. Le reste est neutre + un seul accent de marque (`#4F46E5`).
3. **Monospace pour les secrets.** Mots de passe, clés, empreintes, PIN s'affichent en
   police mono (alignement tabulaire, 0/O et 1/l/I non ambigus). Révélation explicite
   et annoncée aux lecteurs d'écran, jamais exposée par défaut.
4. **Accessibilité comme socle.** Contrastes WCAG 2.2 AA, cibles 44–48px, focus visible
   (anneau 2px ≥ 3:1), navigation clavier complète, `reduced-motion` respecté partout.
5. **Tokens, pas de magie.** L'UI ne référence que des tokens *sémantiques* ou
   *composant*. Les valeurs brutes vivent uniquement dans le niveau *primitive*.

## Structure des tokens (3 niveaux)

| Niveau | Rôle | Exemple |
|---|---|---|
| **primitive** | Valeurs brutes, sans intention | `color.accent.40 = #4F46E5`, `space.5 = 16` |
| **semantic** | Intentions, déclinées `light`/`dark`/`shared` | `color.surface`, `color.status.weak`, `radius.card` |
| **component** | Portée d'un composant | `entryCard.padding`, `secretField.fontFamily` |

## Pipeline de génération

```
docs/design-system/tokens.json   (source de vérité, ce dossier)
        │
        ├─► desktop  : thème FlatLaf .properties
        │              (arc boutons 12, focusWidth 2px, scrollbars fines, accent indigo)
        │
        └─► android  : ui/theme/{Color,Type,Shape,Spacing,Motion}.kt
                       Material 3 stable 1.4.0 ; structuré pour bascule M3 Expressive
                       (toolbars, boutons, wavy progress) dès 1.5.0 stable.
```

Détail du mapping dans [`platform-mapping.md`](./platform-mapping.md).

## Documents

- [`tokens.json`](./tokens.json) — **source de vérité** (couleurs, type, espacement, rayons, motion).
- [`foundations.md`](./foundations.md) — fondations détaillées + notes de contraste WCAG.
- [`components.md`](./components.md) — spécifications de la bibliothèque de composants.
- [`platform-mapping.md`](./platform-mapping.md) — comment les tokens deviennent un thème FlatLaf et un thème Compose.

## Statut

| Lot | Contenu | Statut |
|---|---|---|
| 0 | Design system documenté (ce dossier) | ✅ |
| 1 | Tokens → thèmes générés + bibliothèque de composants (desktop + Android) | À faire |
| 2 | Écrans à fort impact : Login, Vault/liste, Détail | À faire |
| 3 | Édition + Générateur + Audit | À faire |
| 4 | Réglages + Sync/Conflits + Import-Export | À faire |
| 5 | Passe accessibilité + motion + revue de parité | À faire |

> Contrainte transverse : **aucune modification du `:core`/backend** dans cette refonte.
> L'UI consomme les capacités existantes (audit, HIBP, favicons, sync, filtres…).
