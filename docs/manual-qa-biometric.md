# QA manuelle — Déverrouillage biométrique (Android)

Le chemin biométrique de bout en bout ne peut pas être automatisé de façon fiable
(il exige un vrai prompt d'empreinte + un verrou d'écran configuré). Il est donc
validé **manuellement sur un appareil de test**, en complément des tests
instrumentés automatisés (Keystore AES-GCM, EncryptedSharedPreferences, smoke
Compose) qui tournent en CI.

## Pré-requis
- Téléphone/émulateur avec **au moins une empreinte enrôlée** et un verrou d'écran.
- APK debug installé :
  ```bash
  ./gradlew :android:installDebug
  # ou build seul : ./gradlew :android:assembleDebug
  ```

## Scénarios à dérouler

### 1. Activation de la biométrie
1. Créer/ouvrir un coffre avec le mot de passe maître.
2. Paramètres → activer « Déverrouillage biométrique » → s'authentifier.
3. **Attendu** : activation confirmée, aucune erreur.

### 2. Déverrouillage par empreinte
1. Verrouiller l'app (auto-lock ou extinction d'écran), puis rouvrir.
2. Choisir le déverrouillage biométrique, présenter l'empreinte.
3. **Attendu** : le coffre s'ouvre sans saisir le mot de passe maître.

### 3. Rejet d'une empreinte non reconnue
1. Sur l'écran de déverrouillage biométrique, présenter un doigt **non enrôlé**.
2. **Attendu** : échec, le coffre reste verrouillé, repli sur le mot de passe maître possible.

### 4. Invalidation au changement de mot de passe maître
1. Biométrie activée, changer le mot de passe maître.
2. Verrouiller puis tenter le déverrouillage biométrique.
3. **Attendu** : la biométrie ne déverrouille plus (donnée biométrique invalidée) ;
   le mot de passe maître **nouveau** fonctionne. Ré-activer la biométrie est possible.

### 5. Invalidation à la ré-enrôle d'empreinte (`setInvalidatedByBiometricEnrollment`)
1. Biométrie activée. Dans les réglages système Android, **ajouter/supprimer une empreinte**.
2. Tenter le déverrouillage biométrique dans l'app.
3. **Attendu** : la clé est invalidée (`KeyPermanentlyInvalidatedException` interceptée),
   message clair, repli sur le mot de passe maître, ré-activation possible.

### 6. Désactivation
1. Paramètres → désactiver la biométrie.
2. **Attendu** : plus de proposition biométrique au déverrouillage ; les données
   biométriques chiffrées sont effacées (`clearBiometricData`).

### 7. Cloisonnement par dossier de travail (workspace)
Le nommage des clés biométriques est cloisonné par dossier de travail
(`WorkspaceManager.biometricAccount(username)`) : deux coffres homonymes dans
deux dossiers différents n'ont pas la même clé biométrique.
1. Activer la biométrie pour un coffre `alice` dans le **dossier interne**.
2. Basculer vers un **autre dossier** (SAF) contenant un coffre `alice` distinct.
3. **Attendu** : la biométrie n'est pas proposée / ne déverrouille pas l'autre `alice` ;
   chaque dossier conserve son propre enrôlement biométrique.

## Traçabilité
À cocher avant chaque release contenant un changement touchant
`BiometricHelper`, `LoginViewModel`, `ChangeMasterPasswordViewModel`,
`ConfigRepository`/`AndroidConfigRepository` ou `WorkspaceManager`
(le nommage des clés biométriques est désormais cloisonné par dossier de travail) :

- [ ] Scénario 1 — Activation
- [ ] Scénario 2 — Déverrouillage empreinte
- [ ] Scénario 3 — Empreinte non reconnue
- [ ] Scénario 4 — Invalidation changement master password
- [ ] Scénario 5 — Invalidation ré-enrôle
- [ ] Scénario 6 — Désactivation
- [ ] Scénario 7 — Cloisonnement par dossier de travail

Appareil testé : __________________  Version Android : ______  Date : __________
