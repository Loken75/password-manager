# Fondations

Détail des tokens et de leur justification. Toutes les valeurs proviennent de
[`tokens.json`](./tokens.json) — ce document explique le *pourquoi* et documente les
contrôles d'accessibilité.

## Couleur

### Accent de marque

`#3B66C9` (bleu acier, `accent.40`) est le **seed** de la palette tonale. Sobre et
rassurant : il porte la direction « Calme & confiance » et reste crédible pour une app
de sécurité. Décliné en 11 tons (`accent.10` → `accent.95`).

- **Light** : accent = `accent.40` ; texte blanc dessus.
- **Dark** : accent = `accent.60` (`#6E92E8`) pour rester lisible sur fond sombre.
- **Android ≥ API 31** : `dynamicColor` (Material You) prend le dessus et dérive le
  schéma du fond d'écran ; le seed indigo est le repli (< API 31 et desktop).
  **Les couleurs de statut sécurité restent fixes** quelle que soit la teinte dynamique
  (un rouge « compromis » ne doit jamais virer au bleu).

### Couleurs de statut (signal sécurité)

Seul usage « couleur signifiante » de l'app. Mappées sur les 4 niveaux de
`PasswordStrengthAnalyzer` du `:core` et sur l'état HIBP :

| Niveau core | Token | Light | Usage |
|---|---|---|---|
| `VERY_STRONG` | `color.status.veryStrong` | `#2F6FB0` | jauge, badge « très fort » (signal positif fort) |
| `STRONG` | `color.status.strong` | `#2F9E68` | jauge, badge « fort » |
| `MEDIUM` | `color.status.medium` | `#B97D08` | jauge, badge « moyen » |
| `WEAK` / breached | `color.status.weak` | `#CF4747` | jauge, badge « faible/compromis » |

> Teintes **adoucies** (vs vives) pour la direction calme. Les badges s'affichent en
> style **doux** (fond teinté clair + texte/point de la couleur), pas en pastilles
> pleines. Favori = or sobre (`#C79A33`), distinct des statuts.

> Conserve le « Très fort = bleu » documenté du README (4 niveaux distincts) tout en
> remplaçant les **3 verts incohérents** du desktop et les **couleurs codées en dur**
> d'Android (étoile, swipe, avatar). Un seul jeu, themable, validé contraste. Ces 4
> couleurs sont **fixes** (non soumises au dynamic color). Comme l'app affiche toujours
> aussi le **libellé** du niveau, la couleur n'est jamais le seul signal (a11y).

### Contraste WCAG 2.2 AA (vérifié)

Ratios calculés sur les couples critiques (cible : texte 4.5:1, large 3:1, UI 3:1) :

| Couple | Ratio | Verdict |
|---|---|---|
| Blanc sur `accent.40` (#3B66C9) | **5.34:1** | ✅ texte (bouton primaire) |
| `status.*.text` (`*.40`) sur `*.90` (fond doux clair) | ≥ 4.5:1 | ✅ texte de badge doux |
| `accent.40` sur surface claire | ≥ 4.5:1 | ✅ liens / segment actif |

Style **badge doux** : fond = teinte `*.90` (très clair), texte/point = `*.40` (foncé).
Chaque statut garde une variante `*.text` (`*.40`) pour le texte coloré, distincte du
**fill** (`*.50`) des barres de force. À l'implémentation, je repasse au crible tous les
couples surface/texte et j'ajuste tout token sous le seuil.

### Catégories

En direction « Calme & confiance », **les avatars sont neutres** (cercle `surface`
sombre/clair + initiale en `text.secondary`) : la couleur reste réservée aux signaux de
sécurité. La catégorie s'affiche en **chip neutre** (texte secondaire) et, dans la
sidebar desktop, en simple puce grise. La palette `category.*` (10 teintes) est
**conservée dans les tokens** mais non utilisée pour les avatars — réserve pour un
éventuel mode « couleur par catégorie » optionnel plus tard.

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
