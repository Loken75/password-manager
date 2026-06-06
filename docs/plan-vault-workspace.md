# Plan d'implémentation — « Dossier de travail » (workspace) configurable

> Objectif : permettre à l'utilisateur de **choisir le dossier où sont lus/écrits les
> vaults**, et le rendre **accessible** depuis les fichiers du téléphone (Android) —
> modèle « workspace » à la Obsidian / IDE. Sur desktop : n'importe quel dossier. Sur
> Android : un dossier choisi via le **Storage Access Framework (SAF)**, donc visible
> par les explorateurs de fichiers et survivant à la désinstallation.
>
> Statut : **✅ IMPLÉMENTÉ — toutes les phases sont livrées** (commit « Add configurable vault working folder (workspace) across all clients »). Document conservé comme référence de conception / archive.

---

## 1. Modèle cible

```
Écran « dossier de travail »
   → choix / changement de dossier (mémorisé, avec dossiers récents)
   → scan des vault_*.enc présents
   → liste des coffres disponibles dans ce dossier
   → sélection d'un coffre + mot de passe maître
   → déverrouillage → entrées
```

Le dossier de travail est une donnée **globale et pré-login** (l'énumération des
utilisateurs en dépend déjà aujourd'hui via `listUsers`). Le panneau de réglages
garde un bouton « Changer de dossier de travail » qui **déconnecte** et renvoie à cet
écran (pas de changement à chaud sur une session déverrouillée).

---

## 2. Principe d'architecture : abstraire le stockage du `:core`

Aujourd'hui `VaultManager` et `LocalRepository` raisonnent en **chemins de fichiers**
(`Paths.get`, `Files.*`, permissions POSIX, `.bak`). Sur Android-SAF il n'y a pas de
chemin, seulement une URI `content://` lue par flux. On introduit donc une interface
de stockage dans `:core`, avec deux implémentations par plateforme.

### 2.1 Nouvelle interface `VaultStore` (`core/.../vault/store/VaultStore.java`)

```java
public interface VaultStore {
    /** Noms logiques des fichiers présents (ex. "vault_alice.enc", "vault_alice.enc.bak"). */
    List<String> list() throws IOException;
    boolean exists(String name);
    long size(String name) throws IOException;
    byte[] read(String name) throws IOException;
    /** Écriture atomique + permissions, gérées par l'implémentation (temp+rename / doc SAF). */
    void writeAtomic(String name, byte[] data) throws IOException;   // implémenté sous ce nom
    void copy(String from, String to) throws IOException;   // pour les .bak
    void delete(String name) throws IOException;
    long lastModified(String name);
    /** Libellé lisible de l'emplacement, pour l'UI (chemin ou nom de dossier SAF). */
    String describe();
}
```

Règle de partage des responsabilités :
- **Le store** = mécanique d'I/O propre à la plateforme (atomicité, permissions, flux).
- **`VaultManager`** = politique métier (nom `vault_<user>.enc`, rotation `.bak`,
  cap 50 Mo, chiffrement). Il appelle uniquement des primitives `VaultStore`.

### 2.2 Implémentations

| Impl | Module | Usage | Mécanique |
|---|---|---|---|
| `FileVaultStore` | `:core` | Desktop + Android-interne (défaut/repli) | `Paths`/`Files`, temp+`ATOMIC_MOVE`, `FileSecurityUtils` (perms 600), repli non-atomique |
| `SafVaultStore` | `:android` | Android, dossier choisi par l'utilisateur | `DocumentFile` + `contentResolver` ; pas de perms POSIX (SAF gère l'accès) ; « atomique » = écriture `.tmp` doc puis remplacement |

`FileVaultStore` reçoit la logique d'I/O **actuelle** de `VaultManager` (déplacement,
pas réécriture) → comportement identique, tests existants verts.

### 2.3 Opérations « chemin arbitraire » — analyse affinée par l'audit

> **Affinage important issu de la re-lecture.** Toutes les méthodes « chemin externe »
> de `VaultManager` (`decryptVaultFile`, `importEncryptedVault`, `adoptEnvelopeFromFile`)
> sont en réalité appelées, **sur les deux plateformes**, avec un **fichier temporaire
> bien réel** (`cacheDir` côté Android, temp SFTP côté desktop) — jamais avec un
> `content://`. Détail vérifié :
> - `decryptVaultFile` : desktop `MainFrame:536` (`remoteTempPath`), Android
>   `VaultListViewModel:480` (`tempFile.absolutePath`).
> - `importEncryptedVault` : desktop `ImportExportController:146` (fichier choisi),
>   Android `VaultListViewModel:762` (`tempFile.absolutePath`).
> - `adoptEnvelopeFromFile` : sur **workspace path** uniquement côté desktop
>   (`MainFrame:515`, vrai fichier) ; Android utilise un temp (`VaultListViewModel:486`).
>
> **Conséquence : ces trois méthodes peuvent rester path-based** — elles ne touchent
> jamais le store SAF. Leur migration en `byte[]` devient **optionnelle** (nice-to-have),
> ce qui **réduit le périmètre et le risque de la Phase 0.**

Le **seul** accès qui pointe sur le **workspace** (et casse donc sur SAF) est la
lecture des octets du vault local pour l'**export** et l'**upload sync**. On l'adresse
par une unique nouvelle méthode :

| Besoin workspace | Avant (casse sur SAF) | Après |
|---|---|---|
| Octets du vault local | `getVaultPath(user)` + `new File(path).readBytes()` | `byte[] readVaultBytes(user)` (passe par le store) |

Points exacts à reconvertir vers `readVaultBytes` (audit) :
- Android export : `VaultListViewModel:300`.
- Android upload sync : `VaultListViewModel:418` et `:709` (puis *staging* vers temp
  pour `channel.put`, cf. Phase 3).
- Desktop : `getVaultPath` reste valide (FileVaultStore a un vrai chemin).

### 2.4 Nouvelles entrées d'API `VaultManager`

```java
VaultManager(VaultStore store, EncryptionService crypto)   // ctor principal
VaultManager(String dir)                                   // conserve : construit FileVaultStore(dir)
String  vaultFilename(String username)                     // "vault_<user>.enc" (sans chemin)
byte[]  readVaultBytes(String username)                    // pour sync (remplace getVaultPath+lecture)
VaultStore getStore()
```

Le contrat « chemin réel, sinon exception » a été réalisé via `VaultStore.pathOf(name)`
(et non `getVaultPath`) : il ne fonctionne que pour `FileVaultStore` (desktop) et lève
`UnsupportedOperationException` côté `SafVaultStore`. Pour Android-SAF, les appelants
passent par `readVaultBytes` + fichier temp.

---

## 3. Phase 0 — Fondation (aucun changement de comportement) — ✅ FAIT

> **Statut : implémentée et vérifiée (2026-06-05).** `:core` + `:desktop` : tous les
> tests verts (dont le nouveau `FileVaultStoreTest` et les tests d'intégration SFTP) ;
> `:android` compile sans modification. Fichiers : nouveaux
> `vault/store/VaultStore` + `FileVaultStore` (+ test) ; `VaultManager` délègue au store
> et expose `readVaultBytes`/`vaultFilename`/`getStore` (ctor `String` conservé) ;
> `SFTPRepository.validateLocalPath` paramétré via `setAllowedLocalDir`, câblé par
> `DesktopSyncFactory`.

**But : dé-risquer.** Introduire l'abstraction sans rien changer de visible. Les 431+
tests doivent rester verts.

> **Périmètre confirmé par l'audit.** `LocalRepository` / `LocalSyncRepository` /
> `SyncService` ne sont utilisés **que** côté desktop+core (Android a une sync inline,
> zéro référence). → **La Phase 0 ne touche QUE `VaultManager`** ; `LocalRepository`
> reste path-based (le desktop a toujours de vrais chemins). Et les méthodes
> « temp-path » restent telles quelles (cf. §2.3). Le refactor est donc plus petit que
> l'esquisse initiale.

1. Créer `vault/store/VaultStore` + `FileVaultStore` (extraction de l'I/O actuelle de
   `VaultManager` : constructeur `mkdirs`+perms, lecture taille/bytes, écriture
   temp+atomic+perms, `.bak`, `list`, `delete`, `cleanupOldBackups`).
2. Refactor `VaultManager` pour déléguer à `VaultStore` ; **garder le ctor `String dir`**
   (→ `FileVaultStore`). Audit : **les 11 sites de construction** (dont 8 en tests)
   utilisent ce ctor mono-argument → la compat suffit, aucun appelant cassé.
3. Ajouter `readVaultBytes(user)` + `vaultFilename(user)`. (Les surcharges `byte[]`
   des méthodes temp-path sont **optionnelles**, repoussées hors Phase 0.)
4. **Corriger le bug existant** `SFTPRepository.validateLocalPath`
   (`SFTPRepository.java:167`) : la garde est codée en dur sur `~/.passwordmanager`
   (sans tiret, ≠ défaut réel `~/.password-manager/data/vaults`) → la paramétrer sur
   le répertoire de travail configuré (canonicalisé). **Desktop uniquement** (Android
   n'appelle jamais `SFTPRepository` — il fait du JSch direct).
5. Tests : nouveau `FileVaultStoreTest` ; vérifier que les tests `VaultManager` /
   sync passent **sans modification**.

**Livrable : invisible, mais débloque tout le reste.**

---

## 4. Phase 1 — Workspace desktop (feature complète côté PC) — ✅ FAIT

> **Statut : implémentée et vérifiée (2026-06-05).** core+desktop tests verts (dont
> nouveaux tests `recentWorkspacesRoundTrip` / `addRecentWorkspaceDeduplicatesOrdersAndCaps`),
> Android compile. Réalisé : `AppConfig.recentWorkspaces` (+ `addRecentWorkspace`/getters,
> cap 8) ; `ConfigManager` persiste `workspace.recent.N` ; `LoginFrame` a un sélecteur de
> dossier (combo récents + bouton `...`) avec bascule, validation d'écriture et **migration**
> (déplacement des `vault_*` sans écrasement) ; `SettingsDialog` a une ligne « Dossier de
> travail » + bouton « Changer… » qui déclenche une reconnexion via `MainFrame.doLock()` ;
> clés i18n FR/EN ajoutées. *Non vérifié visuellement (pas d'affichage dans l'environnement) —
> logique couverte par tests unitaires, câblage UI compilé.*

1. **`AppConfig`** : ajouter `recentWorkspaces` (liste, max ~8) à côté de
   `localVaultDirectory` (déjà présent). Persisté par `ConfigManager` dans
   `config.properties` (clé `workspace.recent`).
2. **`LoginFrame`** : ajouter en haut un sélecteur « Dossier de travail » :
   - libellé du dossier courant + bouton « Changer… » (`JFileChooser`
     `DIRECTORIES_ONLY`, pattern déjà utilisé `SettingsDialog.java:225`),
   - menu déroulant des dossiers récents,
   - au changement : valider l'accès écriture → reconstruire `VaultManager(newDir)`
     (ligne `LoginFrame:42`) → re-scanner `listUsers()` (ligne `:186`) → rafraîchir la
     liste des comptes.
   - *Audit : `LoginFrame` possède déjà `appConfig` + `configManager` et les transmet
     à `MainFrame` (`LoginFrame:207-208`). Le sélecteur s'insère donc ici sans
     re-câbler `Main.java`.*
3. **`SettingsDialog`** (onglet Stockage, nouveau) : champ lecture seule du dossier
   courant + bouton « Changer de dossier de travail ». *Audit : il n'existe aucun
   mécanisme de re-login depuis `SettingsDialog`* → ajouter un **callback** que
   `MainFrame` fournit, déclenchant `doLock()` (`MainFrame:473`, qui wipe vault+session
   et rouvre `LoginFrame`). Le changement de dossier passe donc par un re-login propre,
   jamais à chaud.
4. **Migration** (option retenue : *proposer de déplacer*) : à la création/au
   changement, si des `vault_*.enc` existent à l'ancien emplacement, proposer une
   `JOptionPane` « Déplacer les coffres existants vers le nouveau dossier ? » →
   déplacer `vault_*.enc`, `*.bak`, `*.sync_meta`, `*.pending`.
5. Le moteur de sync (`DesktopSyncFactory.create(appConfig, …)`) lit déjà
   `config.getLocalVaultDirectory()` → automatiquement re-pointé.

**Livrable : desktop = choix de dossier libre, opérationnel.**

---

## 5. Phase 2 — Android : plomberie workspace (store `File`, sans SAF) — ✅ FAIT

> **Statut : implémentée et vérifiée (2026-06-05).** core+desktop+`:android:testDebugUnitTest`
> tous verts. Réalisé : `VaultManager(VaultStore)` (core) ; `AndroidVaultRepository` rend son
> `manager` swappable via `useStore(VaultStore)` (ctor `String` conservé pour les tests) ;
> nouvelle interface `WorkspaceManager` + impl `AndroidWorkspaceManager` (specs `internal`/
> `external` → `FileVaultStore`, spec opaque extensible au SAF) ; persistance via
> `ConfigRepository.get/setVaultWorkspace` (EncryptedSharedPreferences) ; `AppModule` fournit
> le `WorkspaceManager` et pointe le repository sur `currentStore()` ; `LoginViewModel` expose
> `workspaceSpec`/`workspaceOptions` + `switchWorkspace(spec)` (lock défensif → useStore →
> reload) ; sélecteur de dossier dans `LoginScreen` (affiché si >1 emplacement) ; strings
> EN/FR. `FakeWorkspaceManager` + `FakeConfigRepository`/`LoginViewModelTest` mis à jour.
> Autofill inchangé (lit `SessionHolder`).
>
> **Revue Phase 2 (haute-recall) → 7 correctifs appliqués (2026-06-05) :** (1) namespacing
> biométrique par workspace (`WorkspaceManager.biometricAccount`, défaut = username pour
> rétro-compat) sur Login/Settings/ChangeMasterPassword — corrige l'effacement d'enrôlement
> entre coffres homonymes ; (2) migration Android au switch via `VaultStoreMigrator` + dialogue
> Déplacer/Garder ; (3) `AndroidVaultRepository.readVaultBytes`/délégués + `manager` privé +
> `VaultListViewModel` upload/export par **staging temp** (SAF-safe) ; (4) cohérence
> `currentSpec`/`availableSpecs` (plus de spec « external » fantôme) ; (5) reset d'état périmé
> au switch (failedAttempts/rate-limit/biometricError) ; (6) constantes de nommage
> (`VAULT_BACKUP_SUFFIX`, `vaultFilename`) ; (7) `FakeWorkspaceManager` bi-dossier + 3 tests de
> switch/migration. core+desktop+android tests verts.

Étape de dé-risquage avant le SAF : faire fonctionner tout le workflow workspace
Android avec un store **fichier** (stockage interne), pour valider l'UI et le câblage
DI/ViewModel indépendamment du SAF.

1. **`AndroidVaultRepository`** : ne plus figer le dossier ; exposer
   `useStore(VaultStore)` (reconstruit le `VaultManager` interne). Le `@Singleton`
   Hilt fournit un repository « vide » re-pointé au choix du workspace.
2. **`WorkspaceManager`** (nouveau, `data/`) : source de vérité du workspace courant,
   persiste le choix dans `EncryptedSharedPreferences` (comme les autres réglages).
3. **`AppModule`** : injecter `WorkspaceManager` ; au démarrage, repointer le
   repository sur le dernier workspace connu (défaut = `filesDir/vaults`, comportement
   actuel → aucune régression pour l'existant).
4. **UI** : écran/section « Dossier de travail » (Compose) listant les coffres du
   workspace courant ; intégré au flux `ui/login`.
5. **Autofill** (`autofill/`) : vérifier que le service lit le vault via le même
   repository/workspace (point à confirmer en Phase de challenge — voir §9).

**Livrable : Android peut changer d'emplacement (encore interne), UI complète.**

---

## 6. Phase 3 — Android SAF (l'objectif réel) — ✅ FAIT

> **Statut : implémentée + revue + corrigée (2026-06-05).** core+desktop+android tests verts.
> Réalisé : `SafVaultStore` (DocumentFile/ContentResolver, `writeAtomic`/`copy`/`list`/… ; `pathOf`
> non supporté) ; `WorkspaceManager` gère le spec `saf:<treeUri>` (storeFor, availableSpecs
> incluant le dossier SAF courant si grant valide, currentSpec à repli internal, biometricAccount
> via tag stable) ; dépendance `androidx.documentfile` ; `LoginScreen` : picker
> `OpenDocumentTree` + `takePersistableUriPermission` + item « Choisir un dossier… », migration
> réutilisée. **Revue de code → correctifs appliqués :** guard `SecurityException` sur la prise de
> permission (anti-crash) ; libellé SAF résolu via `remember(spec)` (plus de requête par
> recomposition) ; I/O SAF (init/listUsers/switch/migration) déplacée hors du thread principal via
> dispatcher injecté (`@IoDispatcher`, `Dispatchers.Unconfined` en test). **Limites SAF documentées
> (non bloquantes) :** préservation du nom par le provider (OK ExternalStorageProvider),
> troncature `"wt"`, anciens grants SAF non libérés, égalité d'URI. À valider en test
> instrumenté/manuel (pas de test unitaire SAF sans Robolectric).

1. **`SafVaultStore`** (`:android`, implémente `VaultStore`) :
   - `list()` via `DocumentFile.fromTreeUri(ctx, treeUri).listFiles()` filtré
     `vault_*.enc`,
   - `read/write` via `contentResolver.openInputStream/openOutputStream`,
   - `write` « atomique » : écrire un doc `.tmp` puis remplacer (delete+rename via
     `DocumentsContract.renameDocument`) ; `copy` pour `.bak`,
   - pas de permissions POSIX.
2. **Sélection du dossier** : `ActivityResultContracts.OpenDocumentTree()` →
   `contentResolver.takePersistableUriPermission(uri, READ|WRITE)` ; stocker l'URI
   dans `WorkspaceManager`.
3. **Sync inline** (`VaultListViewModel`) : remplacer les accès chemin par un *staging*
   vers `cacheDir` (le download remote→temp existe déjà, `VaultListViewModel:468-470`).
   Points exacts (audit) :
   - `:300` (export) → `readVaultBytes(user)` puis écriture vers l'URI SAF de sortie.
   - `:418` / `:709` (check + `channel.put(localPath,…)`) → `readVaultBytes(user)` écrit
     dans un temp `cacheDir`, `channel.put(temp)`, puis suppression du temp.
   - `decryptVaultFile`/`importEncryptedVault`/`adoptEnvelopeFromFile` : **inchangés**
     (déjà sur des temp `cacheDir` réels, cf. §2.3).
4. **Restauration au démarrage** : revalider la permission URI persistée ; si révoquée
   (dossier supprimé/permission perdue), retomber sur l'écran de choix de dossier.
5. **Repli** : si aucun workspace SAF choisi → `FileVaultStore(filesDir/vaults)`
   (comportement historique).

**Livrable : Android = vault dans un dossier choisi, visible dans les fichiers du
téléphone, partageable avec d'autres apps de sync, persistant après désinstallation.**

---

## 7. Migration des données

- **Desktop** : déplacement de fichiers (`Files.move`) — `vault_*.enc`, `*.bak`,
  `*.sync_meta`, `*.pending`.
- **Android interne → SAF** : copie via flux (`read` interne → `write` SAF), puis
  suppression de l'interne après confirmation. Proposée, pas imposée.
- Toujours **proposer** (dialogue), jamais silencieux ; conserver les `.bak`.

---

## 8. Sécurité / arbitrages à acter

- **Perte de la protection POSIX 600** quand le vault sort du bac à sable : le fichier
  devient lisible/effaçable par ce qui a accès au dossier. **Confidentialité
  préservée** (AES-256-GCM) ; intégrité couverte par les `.bak` et la sync.
- **`SafVaultStore` n'applique pas `FileSecurityUtils`** — documenter ce choix
  (non applicable au SAF).
- **Validation d'accès écriture** avant d'accepter un nouveau dossier (desktop : test
  d'écriture ; Android : tentative `openOutputStream` sur un doc témoin).
- **Permission URI révocable** (Android) : gérer le cas « dossier inaccessible » au
  démarrage sans crash.
- Conserver les validations anti-traversal (`validateUsername`, `validateFilename`).
- **Non-atomicité du SAF** : le SAF n'offre pas de remplacement réellement atomique
  (pas d'équivalent `ATOMIC_MOVE`). `SafVaultStore.write` écrit un doc `.tmp` puis
  delete+rename → fenêtre de corruption possible si interruption. Le **`.bak` créé
  avant écriture est le filet de sécurité** ; en documenter la dépendance et tester le
  scénario d'écriture interrompue.

---

## 9. Résultats du challenge (re-lecture intégrale — vérifié)

| # | Question | Résultat vérifié |
|---|---|---|
| 1 | Appelants de `VaultManager(String)` | **11 sites, tous le ctor mono-arg** (8 en tests : `VaultManagerIntegrationTest`, `SecurityAuditTest`, `SyncConflictIntegrationTest`, `MasterPasswordSyncIntegrationTest`, `TestSessionHelper`). → compat ctor suffit. ✅ |
| 2 | `LocalRepository` desktop-only ? | **Oui, zéro référence Android.** Phase 0 ne l'abstrait pas. ✅ |
| 3 | Autofill Android | **Lit uniquement `SessionHolder.vaultService`** (vault en mémoire), aucun accès fichier/chemin. Aucun changement requis. ✅ |
| 4 | Cycle de vie `SessionHolder` | `lock()` wipe vault + détruit session ; **doit être appelé avant de repointer** le store/repository. ✅ |
| 5 | `.sync_meta` / `.pending` | Desktop-only, via `LocalRepository`, dans le même dossier → suivent automatiquement. Pas concernés par le store SAF. ✅ |
| 6 | Tests `@TempDir` | Tous via ctor `String` → compat OK. ✅ |
| 7 | Accès chemin workspace hors VaultManager (Android) | **Limité à 3 points** : export `VaultListViewModel:300`, upload sync `:418` et `:709` → tous couverts par `readVaultBytes`. ✅ |
| 8 | SAF déjà présent | `OpenDocument`/`CreateDocument`/`GetContent` utilisés (import/export) **mais aucun `takePersistableUriPermission`** → à ajouter pour le workspace. ✅ |
| 9 | Persistance du choix Android | `EncryptedSharedPreferences` (`AndroidConfigRepository`) → y stocker l'URI du workspace. ✅ |

**Aucun bloqueur découvert.** L'audit a au contraire permis de **réduire** la Phase 0
(LocalRepository et méthodes temp-path exclus) et de **cibler précisément** les 3 points
Android à reconvertir.

---

## 10. Ordre de livraison recommandé

`Phase 0` (fondation, 1 PR) → `Phase 1` (desktop, 1 PR, valeur immédiate) →
`Phase 2` (Android plomberie, 1 PR) → `Phase 3` (Android SAF, 1 PR, isolé car le plus
risqué). Chaque phase est indépendamment testable et n'introduit pas de régression
visible tant que l'UI n'est pas branchée.
