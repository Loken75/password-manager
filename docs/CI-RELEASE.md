# CI/CD - Integration Continue et Releases Multi-Plateformes avec GitHub Actions

## Vue d'ensemble

Ce document decrit les workflows GitHub Actions du Password Manager :

1. **CI** (`.github/workflows/ci.yml`) — Integration continue : tests automatiques sur chaque push/PR vers `main`
2. **Release** (`.github/workflows/release.yml`) — Releases multi-plateformes : construction et publication automatiques a chaque tag de version

---

## Workflow CI (Integration continue)

Le workflow CI s'execute automatiquement sur chaque **push** et **pull request** vers la branche `main`.

### Declenchement

```yaml
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
```

### Jobs

| Job | Runs-on | JDK | Role |
|-----|---------|-----|------|
| `test-core-desktop` | ubuntu-latest | 21 | Tests core + porte de couverture JaCoCo + compilation et tests desktop |
| `test-android` | ubuntu-latest | 17 | Compilation Android + tests unitaires + Android Lint |
| `test-android-instrumented` | ubuntu-latest | 17 | Tests instrumentes Android sur emulateur (KVM) |
| `dependency-scan` | ubuntu-latest | 21 | SBOM CycloneDX + scan de vulnerabilites Trivy |

Les quatre jobs s'executent **en parallele** et sont independants.

### Job : test-core-desktop

1. Checkout du code
2. Setup JDK 21 (Temurin) + Gradle
3. `./gradlew :core:test` — execute tous les tests unitaires du module core
4. `./gradlew :core:jacocoTestCoverageVerification` — **porte de couverture** : echoue si la couverture LINE de `:core` passe sous 70 % (actuellement ~75 %)
5. `./gradlew :desktop:compileJava` — verifie que le desktop compile
6. `./gradlew :desktop:test` — execute les tests du module desktop

### Job : test-android

1. Checkout du code
2. Setup JDK 17 (Temurin) + Android SDK + Gradle
3. `./gradlew :android:compileDebugKotlin` — verifie la compilation Kotlin Android
4. `./gradlew :android:testDebugUnitTest` — execute les tests unitaires Android
5. `./gradlew :android:lintDebug` — **Android Lint** : echoue sur tout nouveau probleme par rapport a `android/lint-baseline.xml` (les problemes existants sont grandfathered)

### Job : test-android-instrumented

1. Checkout du code
2. Setup JDK 17 (Temurin) + Gradle
3. **Activation de KVM** — expose `/dev/kvm` au runner pour l'acceleration materielle de l'emulateur
4. `reactivecircus/android-emulator-runner@v2` (API 34, `google_apis`, `x86_64`) execute `./gradlew :android:connectedDebugAndroidTest` — valide le vrai Android Keystore, `EncryptedSharedPreferences` et l'UI Compose sur emulateur. Le chemin du prompt biometrique est exclu (QA manuelle, voir `docs/manual-qa-biometric.md`).

### Job : dependency-scan

1. Checkout du code
2. Setup JDK 21 (Temurin) + Android SDK (le SBOM agrege resout le classpath release `:android`) + Gradle
3. `./gradlew cyclonedxBom` — genere un SBOM CycloneDX agrege (`build/reports/bom.json`)
4. Scan Trivy du SBOM (`aquasecurity/trivy-action`, `scan-type: sbom`) — **echoue** (`exit-code: 1`) sur toute vulnerabilite **HIGH/CRITICAL**
5. Upload du SBOM en artifact (`sbom-cyclonedx`, `if: always()`)

### Mises a jour de dependances (Dependabot)

`.github/dependabot.yml` ouvre des PRs hebdomadaires pour les dependances Gradle (`:core`/`:desktop`/`:android`) et les actions GitHub, alimentant aussi les alertes de securite (CVE) du depot.

### Permissions

Le workflow CI ne necessite que la permission `contents: read` (lecture seule du repository).

---

## Workflow Release

Ce document decrit le workflow GitHub Actions pour construire et publier
automatiquement des releases du Password Manager pour **Linux**, **Windows**,
**macOS** et **Android** a chaque creation d'un tag de version.

```
                         git tag v1.0.0 && git push --tags
                                      |
                                      v
                        +---------------------------+
                        |   GitHub Actions Trigger   |
                        |   on: push tags: v*.*.*    |
                        +---------------------------+
                                      |
              +-----------+-----------+-----------+
              |           |           |           |
              v           v           v           v
      +----------+ +----------+ +----------+ +----------+
      |  Ubuntu  | | Windows  | |  macOS   | | Android  |
      |  latest  | | latest   | | latest   | | (Ubuntu) |
      +----------+ +----------+ +----------+ +----------+
      | JDK 21   | | JDK 21   | | JDK 21   | | JDK 17   |
      | Gradle   | | Gradle   | | Gradle   | | Gradle   |
      | core+dsk | | core+dsk | | core+dsk | | Android  |
      | test     | | test     | | test     | | SDK      |
      | fatJar   | | fatJar   | | fatJar   | | assemble |
      | jlink    | | jlink    | | jlink    | | Debug    |
      | .tar.gz  | | .zip     | | .tar.gz  | | .apk     |
      +----+-----+ +----+-----+ +----+-----+ +----+-----+
           |             |            |            |
           v             v            v            v
      Upload art.   Upload art.  Upload art.  Upload art.
           |             |            |            |
           +------+------+------+-----+
                  |
                  v
        +-----------------------------+
        |     Job: Release            |
        | needs: [build-desktop,      |
        |         build-android,      |
        |         build-sbom]         |
        +-----------------------------+
        | 1. Download all artifacts   |
        | 2. Generate SHA256SUMS      |
        | 3. Sign artifacts (GPG)     |
        | 4. gh release create        |
        |    + attach archives, SBOM, |
        |      SHA256SUMS, .asc       |
        +-----------------------------+
                  |
                  v
        +------------------------------------+
        |   GitHub Release v1.0.0            |
        |   - 1.0.0-linux-x64.tar.gz        |
        |   - 1.0.0-windows-x64.zip         |
        |   - 1.0.0-macos-aarch64.tar.gz    |
        |   - 1.0.0-android.apk             |
        |   - 1.0.0-sbom.json               |
        |   - SHA256SUMS.txt                 |
        |   - *.asc (signatures GPG)         |
        +------------------------------------+
```

> Un cinquieme job `build-sbom` (Ubuntu, JDK 21) genere le SBOM CycloneDX du projet en parallele des builds desktop/Android ; il alimente le job `release`.

---

## Declenchement

Le workflow se declenche **uniquement** sur un push de tag correspondant au
pattern `v*.*.*` (ex: `v1.0.0`, `v2.1.3`).

```yaml
on:
  push:
    tags:
      - 'v*.*.*'
```

**Pour lancer une release :**
```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## Structure du workflow

Le workflow contient **quatre jobs** :

| Job             | Runs-on              | JDK | Role                                          |
|-----------------|----------------------|-----|-----------------------------------------------|
| `build-desktop` | matrix (3 OS)        | 21  | Compile core+desktop, teste, package avec JRE  |
| `build-android` | ubuntu-latest        | 17  | Compile core+android, produit l'APK signe      |
| `build-sbom`    | ubuntu-latest        | 21  | Genere le SBOM CycloneDX du projet             |
| `release`       | ubuntu-latest        | -   | Cree la release GitHub et attache les archives  |

### Dependance entre jobs

```
build-desktop (3 runners en parallele) --+
build-android (1 runner) ----------------+--> release (attend que tous finissent)
build-sbom (1 runner) -------------------+
```

Le job `release` declare `needs: [build-desktop, build-android, build-sbom]`, il ne demarre que lorsque tous les builds (3 desktop + Android + SBOM) ont reussi.

---

## Job 1 : Build Desktop (matrice multi-OS)

### Strategie matricielle

```yaml
strategy:
  fail-fast: false
  matrix:
    include:
      - os: ubuntu-latest
        label: linux-x64
        archive_ext: tar.gz
      - os: windows-latest
        label: windows-x64
        archive_ext: zip
      - os: macos-latest
        label: macos-aarch64
        archive_ext: tar.gz
```

GitHub Actions lance **3 runners en parallele**, un par OS. Chaque runner
execute exactement les memes etapes mais sur son systeme natif, ce qui
garantit une distribution native pour chaque plateforme.

### Etapes du job build-desktop

#### 1. Checkout du code

```yaml
- uses: actions/checkout@v4
```

#### 1b. Extraction et injection de la version

```yaml
- name: Extract version
  id: version
  shell: bash
  run: echo "VERSION=${GITHUB_REF_NAME#v}" >> "$GITHUB_OUTPUT"

- name: Inject version into build
  shell: bash
  run: |
    sed -i.bak "s/^appVersion=.*/appVersion=${{ steps.version.outputs.VERSION }}/" gradle.properties
    rm -f gradle.properties.bak
```

La version est derivee du tag (`v1.4.2` -> `1.4.2`) puis injectee dans `gradle.properties` (`appVersion`). Cette etape est presente dans les quatre jobs (`build-desktop`, `build-android`, `build-sbom`, `release`) et sert a nommer les artefacts et a versionner le build. Pour `build-desktop`, `build-android` et `build-sbom`, l'injection met aussi a jour `appVersion` avant compilation.

#### 2. Installation du JDK + Gradle

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: 'temurin'
    java-version: '21'

- uses: gradle/actions/setup-gradle@v4
```

Temurin JDK 21 (Eclipse Adoptium). Le JDK (pas le JRE) est necessaire car
`jlink` n'est disponible que dans le JDK.

#### 3. Tests (core + desktop)

```yaml
- run: ./gradlew :core:test :desktop:test
```

Execute les ~390 tests unitaires et d'integration (core + desktop, dont l'integration SFTP reelle). Si les tests echouent,
le build s'arrete et la release n'est pas creee.

> **Note** : on specifie `:core:test :desktop:test` et non `test` pour eviter
> de declencher la configuration du module `:android` (qui necessite le SDK Android).

#### 4. Build fat JAR

```yaml
- run: ./gradlew :desktop:fatJar
```

Produit `desktop/build/libs/password-manager.jar` — fat JAR avec toutes les dependances (~2 Mo).

#### 5. Creation du JRE minimal avec jlink

```yaml
- run: |
    jlink \
      --add-modules java.base,java.desktop,java.logging,java.naming,java.security.jgss,java.sql,java.xml,jdk.crypto.ec \
      --strip-debug \
      --no-man-pages \
      --no-header-files \
      --compress zip-6 \
      --output dist/runtime
```

`jlink` cree un JRE minimal (~57 Mo) contenant uniquement les modules requis.

**Modules inclus :**

| Module                  | Raison                                        |
|-------------------------|-----------------------------------------------|
| `java.base`             | Fondation (crypto, IO, collections, etc.)     |
| `java.desktop`          | Swing (AWT, javax.swing)                      |
| `java.logging`          | java.util.logging                             |
| `java.naming`           | JNDI (dependance transitive)                  |
| `java.security.jgss`    | Kerberos/GSSAPI (dependance SSH)              |
| `java.sql`              | Dependance transitive                         |
| `java.xml`              | Parsing XML (dependance transitive)           |
| `jdk.crypto.ec`         | Cryptographie sur courbes elliptiques (EC/ECDSA) |

> **Note** : le script local `scripts/build-dist.sh` utilise une liste de modules plus courte (sans `jdk.crypto.ec`). La liste de reference pour les artefacts publies est celle du workflow `release.yml`.

#### 6. Assemblage de la distribution

```yaml
- name: Assemble distribution
  shell: bash
  run: |
    cp desktop/build/libs/password-manager.jar dist/
    cp README.md dist/
    if [ "${{ runner.os }}" = "Windows" ]; then
      cp scripts/run.bat dist/
    else
      cp scripts/run.sh dist/
      chmod +x dist/run.sh
    fi
    chmod +x dist/runtime/bin/*
```

Le script de lancement copie est **conditionnel a l'OS** du runner (`run.bat` sous Windows, `run.sh` sinon). Le `README.md` est egalement inclus. La distribution finale contient :
```
dist/
  password-manager.jar    # Fat JAR avec toutes les dependances
  runtime/                # JRE minimal (jlink)
  run.sh OU run.bat       # Script de lancement (selon l'OS du runner)
  README.md               # Documentation
```

#### 6b. Packaging de l'archive (conditionnel a l'OS)

```yaml
- name: Package archive (Linux/macOS)
  if: runner.os != 'Windows'
  run: tar -czf password-manager-<version>-<label>.tar.gz -C dist .

- name: Package archive (Windows)
  if: runner.os == 'Windows'
  shell: pwsh
  run: Compress-Archive -Path dist\* -DestinationPath password-manager-<version>-<label>.zip
```

L'archive est produite nativement par OS : `tar -czf` (Linux/macOS) ou `Compress-Archive` PowerShell (Windows).

#### 7. Upload de l'artifact

```yaml
- uses: actions/upload-artifact@v4
  with:
    name: password-manager-<version>-${{ matrix.label }}
    path: password-manager-<version>-${{ matrix.label }}.${{ matrix.archive_ext }}
    retention-days: 1
```

---

## Job 2 : Build Android

Ce job s'execute en parallele du build desktop, sur `ubuntu-latest`.

### Etapes

Le job commence par le **Checkout** puis les memes etapes **Extract version** / **Inject version** que `build-desktop` (voir Job 1, etape 1b).

#### 1. Setup JDK 17 + Android SDK + Gradle

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: 'temurin'
    java-version: '17'

- uses: android-actions/setup-android@v3

- uses: gradle/actions/setup-gradle@v4
```

Le JDK 17 est suffisant pour la compilation Android (AGP 8.7.3 le requiert).
L'action `android-actions/setup-android@v3` installe le SDK Android et les
build tools necessaires.

#### 2. Tests (core + android)

```yaml
- run: ./gradlew :core:test :android:testDebugUnitTest
```

Execute les tests `:core` et les tests unitaires Android (JVM locale) avant de construire l'APK. Si les tests echouent, l'APK n'est pas produit et la release n'est pas creee.

#### 3. Decodage du keystore et build APK signe

```yaml
- name: Decode keystore
  run: |
    mkdir -p android/build
    echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > android/build/keystore.p12

- name: Build signed release APK
  env:
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: ./gradlew :android:assembleRelease
```

Le keystore est decode depuis un secret GitHub (base64). L'APK release est signe avec les identifiants fournis via des variables d'environnement.

Produit `android/build/outputs/apk/release/android-release.apk`.

#### 4. Renommage et upload

```yaml
- run: mv android/build/outputs/apk/release/android-release.apk password-manager-<version>-android.apk

- uses: actions/upload-artifact@v4
  with:
    name: password-manager-<version>-android
    path: password-manager-<version>-android.apk
    retention-days: 1
```

---

## Job 3 : Build SBOM

Ce job s'execute en parallele des builds desktop et Android, sur `ubuntu-latest` (JDK 21 + Android SDK).

### Etapes

1. Checkout + **Extract version** / **Inject version** (meme logique que les autres jobs)
2. Setup JDK 21 + Android SDK + Gradle
3. `./gradlew cyclonedxBom` — genere le SBOM CycloneDX agrege du projet
4. Renommage : `mv build/reports/bom.json password-manager-<version>-sbom.json`
5. Upload de l'artifact `password-manager-<version>-sbom` (le SBOM est attache a la release)

---

## Job 4 : Release

Ce job s'execute sur `ubuntu-latest` apres que tous les builds (3 desktop + 1 Android + SBOM) aient reussi.

### Etapes

Le job demarre par l'etape **Extract version** (meme logique que les autres jobs) pour nommer les fichiers d'assets a attacher.

#### 1. Telechargement des artifacts

```yaml
- uses: actions/download-artifact@v4
  with:
    path: release-assets/
    merge-multiple: true
```

Telecharge les artefacts produits par les jobs `build-desktop`, `build-android` et `build-sbom` (archives desktop, APK et SBOM).

#### 2. Generation des checksums SHA-256

```yaml
- name: Generate checksums
  run: |
    cd release-assets
    sha256sum *.tar.gz *.zip *.apk *-sbom.json > SHA256SUMS.txt
```

Genere un fichier `SHA256SUMS.txt` contenant les empreintes SHA-256 de chaque archive. Ce fichier permet aux utilisateurs de verifier l'integrite des telechargements.

#### 2b. Signature GPG detachee (authenticite)

Les checksums prouvent l'**integrite** mais pas l'**authenticite** (qui a produit le binaire). Une signature GPG detachee (`.asc`) est generee pour chaque artefact, y compris `SHA256SUMS.txt`. La signature est **optionnelle** : si le secret `GPG_PRIVATE_KEY` n'est pas configure, la release se poursuit **non signee** avec un avertissement (elle n'echoue pas).

Secrets GitHub Actions requis : `GPG_PRIVATE_KEY` (cle privee ASCII-armored) et `GPG_PASSPHRASE`.

```yaml
- name: Sign artifacts (GPG detached)
  if: steps.gpg.outputs.enabled == 'true'
  env:
    GPG_PRIVATE_KEY: ${{ secrets.GPG_PRIVATE_KEY }}
    GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
  run: |
    echo "$GPG_PRIVATE_KEY" | gpg --batch --import
    cd release-assets
    for f in *.tar.gz *.zip *.apk *-sbom.json SHA256SUMS.txt; do
      [ -e "$f" ] || continue
      gpg --batch --yes --pinentry-mode loopback \
          --passphrase "$GPG_PASSPHRASE" --armor --detach-sign "$f"
    done
```

**Verification cote utilisateur** (apres avoir importe la cle publique du projet) :

```bash
# 1. Importer la cle publique (une seule fois)
gpg --import password-manager-public-key.asc

# 2. Verifier la signature d'un artefact
gpg --verify password-manager-1.5.0-linux-x64.tar.gz.asc \
             password-manager-1.5.0-linux-x64.tar.gz

# 3. Verifier l'integrite (et, via SHA256SUMS.txt.asc, son authenticite)
gpg --verify SHA256SUMS.txt.asc SHA256SUMS.txt
sha256sum -c SHA256SUMS.txt
```

> Note : la signature GPG ne supprime pas les avertissements OS (« editeur inconnu » SmartScreen/Gatekeeper) — cela necessiterait une signature de code OS avec certificat payant. Elle fournit une authenticite verifiable pour qui fait confiance a la cle du projet.

#### 3. Creation de la release GitHub

```yaml
- uses: softprops/action-gh-release@v2
  with:
    name: Password Manager ${{ github.ref_name }}
    draft: false
    prerelease: false
    generate_release_notes: true
    files: |
      release-assets/password-manager-<version>-linux-x64.tar.gz
      release-assets/password-manager-<version>-windows-x64.zip
      release-assets/password-manager-<version>-macos-aarch64.tar.gz
      release-assets/password-manager-<version>-android.apk
      release-assets/password-manager-<version>-sbom.json
      release-assets/SHA256SUMS.txt
      release-assets/*.asc
```

**`generate_release_notes: true`** genere automatiquement les notes de
release a partir des commits et PR depuis le dernier tag.

---

## Workflow YAML complet

Le fichier `.github/workflows/release.yml` est la source de verite.
Consulter directement le fichier pour la version a jour du workflow.

---

## Utilisation

### Creer une release

```bash
# S'assurer d'etre sur main avec tout commit
git checkout main
git pull

# Creer et pousser le tag
git tag v1.0.0
git push origin v1.0.0
```

### Suivre le build

1. Aller sur **GitHub > Actions** pour voir le workflow en cours
2. Les builds (3 desktop + Android + SBOM) tournent en parallele (~3-5 min)
3. Une fois termines, la release apparait dans **GitHub > Releases**

### Telecharger une release (utilisateur final)

**Desktop :**
1. Aller sur la page **Releases** du repository
2. Telecharger l'archive correspondant a son OS
3. Extraire l'archive
4. Lancer `run.sh` (Linux/macOS) ou `run.bat` (Windows)

Aucune installation de Java n'est requise : le JRE est embarque.

**Android :**
1. Telecharger le fichier `.apk` depuis la page Releases
2. Installer l'APK sur le telephone (activer "Sources inconnues" si necessaire)

---

## Points cles

| Aspect              | Detail                                                      |
|---------------------|-------------------------------------------------------------|
| **Declencheur**     | Push d'un tag `v*.*.*`                                      |
| **Plateformes**     | Linux x64, Windows x64, macOS aarch64, Android (APK)        |
| **JDK desktop**     | Temurin 21 (build + jlink)                                   |
| **JDK Android**     | Temurin 17 (AGP 8.7.3)                                       |
| **Build system**    | Gradle 8.12 (wrapper), multi-module `:core`/`:desktop`/`:android` |
| **Tests desktop**   | `:core:test` + `:desktop:test` sur chaque OS desktop (~390 tests) |
| **Taille desktop**  | ~20-25 Mo (JAR 2 Mo + JRE compresse ~57 Mo / par OS)        |
| **Taille APK**      | ~5-10 Mo (release, signe)                                    |
| **Checksums**       | SHA256SUMS.txt genere et publie avec chaque release          |
| **Retention**       | Artifacts temporaires : 1 jour / Release : permanente        |
| **fail-fast**       | `false` — les builds continuent meme si l'un echoue         |
| **Permissions**     | `contents: write` pour creer la release                      |
