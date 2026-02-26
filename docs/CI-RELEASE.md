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
| `test-core-desktop` | ubuntu-latest | 21 | Tests core + compilation desktop + tests desktop |
| `test-android` | ubuntu-latest | 17 | Compilation Android + tests unitaires Android |

Les deux jobs s'executent **en parallele** et sont independants.

### Job : test-core-desktop

1. Checkout du code
2. Setup JDK 21 (Temurin) + Gradle
3. `./gradlew :core:test` — execute tous les tests unitaires du module core
4. `./gradlew :desktop:compileJava` — verifie que le desktop compile
5. `./gradlew :desktop:test` — execute les tests du module desktop

### Job : test-android

1. Checkout du code
2. Setup JDK 17 (Temurin) + Android SDK + Gradle
3. `./gradlew :android:compileDebugKotlin` — verifie la compilation Kotlin Android
4. `./gradlew :android:testDebugUnitTest` — execute les tests unitaires Android

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
        +---------------------------+
        |     Job: Release          |
        | needs: [build-desktop,    |
        |         build-android]    |
        +---------------------------+
        | 1. Download all artifacts |
        | 2. Generate SHA256SUMS    |
        | 3. gh release create      |
        |    + attach 5 files       |
        +---------------------------+
                  |
                  v
        +------------------------------------+
        |   GitHub Release v1.0.0            |
        |   - 1.0.0-linux-x64.tar.gz        |
        |   - 1.0.0-windows-x64.zip         |
        |   - 1.0.0-macos-aarch64.tar.gz    |
        |   - 1.0.0-android.apk             |
        |   - SHA256SUMS.txt                 |
        +------------------------------------+
```

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

Le workflow contient **trois jobs** :

| Job             | Runs-on              | JDK | Role                                          |
|-----------------|----------------------|-----|-----------------------------------------------|
| `build-desktop` | matrix (3 OS)        | 21  | Compile core+desktop, teste, package avec JRE  |
| `build-android` | ubuntu-latest        | 17  | Compile core+android, produit l'APK            |
| `release`       | ubuntu-latest        | -   | Cree la release GitHub et attache les archives  |

### Dependance entre jobs

```
build-desktop (3 runners en parallele) --+
                                         +--> release (attend que tous finissent)
build-android (1 runner) ----------------+
```

Le job `release` declare `needs: [build-desktop, build-android]`, il ne demarre que lorsque les 4 builds ont reussi.

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

Execute les 233 tests unitaires et d'integration (core + desktop). Si les tests echouent,
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
      --add-modules java.base,java.desktop,java.logging,java.naming,java.security.jgss,java.sql,java.xml \
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

#### 6. Assemblage et archivage

```yaml
- run: |
    cp desktop/build/libs/password-manager.jar dist/
    cp scripts/run.sh dist/
    cp scripts/run.bat dist/
```

La distribution finale contient :
```
dist/
  password-manager.jar    # Fat JAR avec toutes les dependances
  runtime/                # JRE minimal (jlink)
  run.sh                  # Script de lancement (Linux/macOS)
  run.bat                 # Script de lancement (Windows)
```

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

#### 2. Decodage du keystore et build APK signe

```yaml
- name: Decode keystore
  run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > android/build/keystore.p12

- name: Build signed release APK
  env:
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: ./gradlew :android:assembleRelease
```

Le keystore est decode depuis un secret GitHub (base64). L'APK release est signe avec les identifiants fournis via des variables d'environnement.

Produit `android/build/outputs/apk/release/android-release.apk`.

#### 3. Renommage et upload

```yaml
- run: mv android/build/outputs/apk/release/android-release.apk password-manager-<version>-android.apk

- uses: actions/upload-artifact@v4
  with:
    name: password-manager-<version>-android
    path: password-manager-<version>-android.apk
    retention-days: 1
```

---

## Job 3 : Release

Ce job s'execute sur `ubuntu-latest` apres que les 4 builds (3 desktop + 1 Android) aient reussi.

### Etapes

#### 1. Telechargement des artifacts

```yaml
- uses: actions/download-artifact@v4
  with:
    path: release-assets/
    merge-multiple: true
```

Telecharge les 4 archives produites par les jobs `build-desktop` et `build-android`.

#### 2. Generation des checksums SHA-256

```yaml
- name: Generate checksums
  run: |
    cd release-assets
    sha256sum *.tar.gz *.zip *.apk > SHA256SUMS.txt
```

Genere un fichier `SHA256SUMS.txt` contenant les empreintes SHA-256 de chaque archive. Ce fichier permet aux utilisateurs de verifier l'integrite des telechargements.

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
      release-assets/SHA256SUMS.txt
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
2. Les 3 builds tournent en parallele (~3-5 min)
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
| **Build system**    | Gradle 8.11 (wrapper), multi-module `:core`/`:desktop`/`:android` |
| **Tests desktop**   | `:core:test` + `:desktop:test` sur chaque OS desktop (233 tests) |
| **Taille desktop**  | ~20-25 Mo (JAR 2 Mo + JRE compresse ~57 Mo / par OS)        |
| **Taille APK**      | ~5-10 Mo (release, signe)                                    |
| **Checksums**       | SHA256SUMS.txt genere et publie avec chaque release          |
| **Retention**       | Artifacts temporaires : 1 jour / Release : permanente        |
| **fail-fast**       | `false` — les builds continuent meme si l'un echoue         |
| **Permissions**     | `contents: write` pour creer la release                      |
