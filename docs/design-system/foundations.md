# Fondations

Détail des tokens et de leur justification. Toutes les valeurs proviennent de
[`tokens.json`](./tokens.json) — ce document explique le *pourquoi* et documente les
contrôles d'accessibilité.

## Couleur

### Accent de marque

`#4F46E5` (indigo, `accent.40`) est le **seed** de la palette tonale. Vif sans être
criard : il porte la direction « expressive » tout en restant crédible pour une app de
sécurité. Décliné en 11 tons (`accent.10` → `accent.95`).

- **Light** : accent = `accent.40` ; texte blanc dessus.
- **Dark** : accent = `accent.60` (`#818CF8`) pour rester lisible sur fond sombre.
- **Android ≥ API 31** : `dynamicColor` (Material You) prend le dessus et dérive le
  schéma du fond d'écran ; le seed indigo est le repli (< API 31 et desktop).
  **Les couleurs de statut sécurité restent fixes** quelle que soit la teinte dynamique
  (un rouge « compromis » ne doit jamais virer au bleu).

### Couleurs de statut (signal sécurité)

Seul usage « couleur signifiante » de l'app. Mappées sur les 4 niveaux de
`PasswordStrengthAnalyzer` du `:core` et sur l'état HIBP :

| Niveau core | Token | Light | Usage |
|---|---|---|---|
| `VERY_STRONG` / `STRONG` | `color.status.strong` | `#16A34A` | jauge, badge « fort » |
| `MEDIUM` | `color.status.medium` | `#F59E0B` | jauge, badge « moyen » |
| `WEAK` / breached | `color.status.weak` | `#DC2626` | jauge, badge « faible/compromis » |

> Remplace les **3 verts incohérents** du desktop et les **couleurs codées en dur**
> d'Android (étoile, swipe, avatar). Un seul jeu, themable, validé contraste.

### Contraste WCAG 2.2 AA (vérifié)

Ratios calculés sur les couples critiques (cible : texte 4.5:1, large 3:1, UI 3:1) :

| Couple | Ratio | Verdict |
|---|---|---|
| Blanc sur `accent.40` (#4F46E5) | **6.36:1** | ✅ texte |
| Blanc sur `red.50` (#DC2626) | **4.83:1** | ✅ texte |
| Blanc sur `green.50` (#16A34A) | 3.30:1 | ⚠️ **UI/icône seulement** — pour du texte, utiliser `green.40` (#15803D) → `*.text` |
| `amber.50` (#F59E0B) | clair | ⚠️ fill ; texte foncé obligatoire, ou `amber.40` pour du texte |

Conséquence encodée dans les tokens : chaque statut a une variante `*.text` plus foncée
(`color.status.strong.text` = `green.40`, etc.) pour le **texte coloré sur fond clair**,
distincte du **fill** utilisé pour barres/icônes. À l'implémentation, je repasse au crible
tous les couples surface/texte et j'ajuste tout token qui n'atteint pas le seuil.

### Catégories

Palette de 10 teintes (`category.indigo` … `category.slate`) attribuées par **hash
déterministe** du nom de catégorie → avatar/chip coloré stable. Unifie le violet unique
d'Android et le gris du desktop. Les 10 teintes sont choisies pour rester distinguables
en light comme en dark et pour les déficiences de vision des couleurs (on ne s'appuie
jamais sur la seule couleur — toujours doublée d'un libellé/initiale).

## Typographie

Échelle Material 3 + **monospace réservé aux secrets** (corrige le détournement de
`bodySmall` en mono côté Android).

| Rôle | Taille | Line-height | Poids | Usage |
|---|---|---|---|---|
| display | 36 | 44 | bold | score santé, chiffres bento |
| headline | 28 | 36 | bold | titres d'écran |
| title | 22 | 28 | semibold | titres de section |
| titleSm | 16 | 24 | medium | titre d'entrée (carte) |
| body | 16 | 24 | regular | texte courant |
| bodySm | 14 | 20 | regular | sous-titres, métadonnées |
| label | 12 | 16 | medium | chips, badges, libellés |
| **mono** | 14–16 | — | regular | **mots de passe, clés, empreintes, PIN** |

Familles : `Inter` / défauts plateforme en sans ; `JetBrains Mono` / défauts en mono.
Repli système complet listé dans les tokens (pas de dépendance bloquante à une police
embarquée).

## Forme (rayons)

| Token | Valeur | Usage |
|---|---|---|
| xs | 4 | détails, séparateurs arrondis |
| sm | 8 | petits éléments |
| **md** | **12** | **boutons, champs** |
| **lg** | **16** | **cartes (entrées, bento)** |
| **xl** | **24** | dialogs, bottom sheets |
| full | pilule | chips, badges, FAB |

## Espacement

Grille **base 8** (4 toléré pour les ajustements fins) :
`2 · 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64`.

Alias sémantiques : `space.cardPadding=16`, `space.cardGap=16`, `space.screenEdge=16`,
`space.fieldGap=12`, `space.sectionGap=24`. **Fin des 8/12/16/24 codés en dur** des deux
côtés.

## Tailles & cibles

- **Cible tactile/clic : 44–48px** (satisfait simultanément WCAG 2.2 « 24px », Apple
  « 44pt » et Material « 48dp »). Les icônes-boutons font 24px d'icône dans une zone 48px.
- Avatar 40 · icône 24 (small 20) · FAB 56 · barre de force 6 · anneau de focus 2.

## Motion

Schéma **Standard** par défaut (utilitaire), schéma **Expressive** réservé à quelques
moments : succès de déverrouillage, confirmation « Copié », morph de l'icône favori.

- **Durées** : desktop 150ms ; mobile base 200 / emphasized 300 / large 375. Plafond 400.
- **Easings** (cubic-bézier) : standard `(.4,0,.2,1)`, decelerate/enter `(0,0,.2,1)`,
  accelerate/exit `(.4,0,1,1)`, sharp `(.4,0,.6,1)`.
- **Springs** (Compose) : standard amorti, expressive avec léger rebond.
- **Reduced-motion** : chemin complet. Android respecte « Supprimer les animations »
  (`ANIMATOR_DURATION_SCALE`) ; desktop expose un flag de config — on tombe sur des
  fondus d'opacité seuls, durées vers 0. (Aussi exigence WCAG 2.3.3.)

## Élévation

Profondeur douce, 4 niveaux (`level0`–`level3`) : ombres diffuses à faible opacité
(0.10–0.14) + tint tonal de surface. Pas de glassmorphism lourd (illisible sur des
listes de secrets, et coûteux/instable en Swing).
