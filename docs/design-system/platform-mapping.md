# Mapping plateforme

Comment [`tokens.json`](./tokens.json) devient un thème concret sur chaque client.
La source de vérité est unique ; chaque plateforme en dérive sa représentation native.

## Stratégie de génération

Au lot 1, je génère les artefacts de thème **à partir de `tokens.json`** plutôt que de
recopier les valeurs à la main (évite la dérive). Approche pragmatique pour ce repo
Gradle multi-module :

- Un petit script de génération (Gradle task ou script autonome) lit `tokens.json` et
  écrit (a) le `.properties` FlatLaf et (b) les fichiers `theme/*.kt` Compose.
- Les fichiers générés sont **commités** (pas de génération obligatoire au build) et
  portent un en-tête « généré depuis tokens.json — ne pas éditer à la main ».
- Tout changement de design = éditer `tokens.json` puis régénérer.

> Si la mise en place du générateur s'avère disproportionnée pour le périmètre, repli
> assumé : fichiers de thème écrits à la main mais **référençant 1:1** les tokens, avec
> un test de cohérence. La décision sera prise au début du lot 1 et notée ici.

## Desktop — FlatLaf

FlatLaf se thème via `.properties` (UIManager keys) → slot direct dans le pipeline.

| Token | Clé FlatLaf | Note |
|---|---|---|
| `color.accent` | `@accentColor` / `Component.accentColor` | accent global |
| `color.surface` | `@background`, `Panel.background` | |
| `color.text.primary` | `@foreground` | |
| `color.outline` | `Component.borderColor` | |
| `radius.button` (12) | `Button.arc` | défaut 6 → 12 |
| `radius.input` (12) | `Component.arc` | combos/spinners/text |
| `size.focusRing` (2) | `Component.focusWidth` | + `Component.innerFocusWidth` ; ≥ 3:1 |
| — | `ScrollBar.width=10`, `ScrollBar.showButtons=false` | scrollbars fines |
| — | `Component.arrowType=chevron` | finition |
| `font.family.sans` | `defaultFont` | Inter si dispo, repli système |

Leviers retenus pour la « refonte complète » : titlebar/décorations natives intégrées,
cartes arrondies (au lieu de `JTable`), layout responsive (remplacement des largeurs en
pixels durs), motion minimal (fondus courts, gated reduced-motion). **Pas** de
glassmorphism/blur en Swing (se bat avec le toolkit, instable HiDPI).

Light/dark : deux jeux de `.properties` (ou un fichier + variantes) alignés sur
`semantic.light` / `semantic.dark`. FlatLaf bascule déjà selon `ThemeMode` (System/
Light/Dark) — on conserve ce câblage existant.

## Android — Compose / Material 3

| Token | Cible Compose | Note |
|---|---|---|
| `semantic.light/dark` couleurs | `lightColorScheme()` / `darkColorScheme()` dans `theme/Color.kt` | mappées sur les rôles M3 (`primary`, `surface`, `error`…) + couleurs custom pour statuts/catégories |
| `color.status.*` | objets couleur dédiés (hors `colorScheme`) | **fixes**, non soumis au dynamic color |
| `color.category.*` | table + `categoryColor(name)` | hash déterministe |
| `font.*` | `Typography` dans `theme/Type.kt` | mono = style dédié `secret`, plus de détournement de `bodySmall` |
| `radius.*` | `Shapes` dans `theme/Shape.kt` | |
| `space.*` | `object Spacing` dans `theme/Spacing.kt` | fin des dp codés en dur |
| `motion.*` | `object Motion` (durées, easings, springs) dans `theme/Motion.kt` | + lecture `ANIMATOR_DURATION_SCALE` |

- **Dynamic color** (Material You) conservé ≥ API 31, seed `#4F46E5` en repli ; statuts
  sécurité et couleurs catégorie restent fixes.
- **Material 3 stable 1.4.0** maintenant. Composants structurés pour bascule **M3
  Expressive** (toolbars/`DockedToolbar`, boutons expressifs, `WavyProgressIndicator`)
  dès que `compose-material3 1.5.0` passe stable — sans refonte des écrans.

## Parité

La table de fonctionnalités du `README` racine suit déjà les écarts desktop/Android.
Le design system ajoute une **parité visuelle** : mêmes composants, mêmes tokens, même
vocabulaire (dont `SegmentedTabs` qui rend les 3 types accessibles des deux côtés). Le
lot 5 inclut une revue de parité finale.
