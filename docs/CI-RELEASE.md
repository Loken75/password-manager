# CI/CD - Releases Multi-Plateformes avec GitHub Actions

## Vue d'ensemble

Ce document decrit le workflow GitHub Actions pour construire et publier
automatiquement des releases du Password Manager pour **Linux**, **Windows**
et **macOS** a chaque creation d'un tag de version.

```
                         git tag v1.0.0 && git push --tags
                                      |
                                      v
                        +---------------------------+
                        |   GitHub Actions Trigger   |
                        |   on: push tags: v*.*.*    |
                        +---------------------------+
                                      |
                    +-----------------+-----------------+
                    |                 |                 |
                    v                 v                 v
            +-------------+  +---------------+  +-------------+
            |   Ubuntu     |  |   Windows     |  |   macOS      |
            |   latest     |  |   latest      |  |   latest     |
            +-------------+  +---------------+  +-------------+
            | 1. Checkout  |  | 1. Checkout   |  | 1. Checkout  |
            | 2. Setup JDK |  | 2. Setup JDK  |  | 2. Setup JDK |
            | 3. mvn build |  | 3. mvn build  |  | 3. mvn build |
            | 4. jlink     |  | 4. jlink      |  | 4. jlink     |
            | 5. Package   |  | 5. Package    |  | 5. Package   |
            |    .tar.gz   |  |    .zip       |  |    .tar.gz   |
            +------+------+  +-------+-------+  +------+------+
                    |                 |                 |
                    v                 v                 v
              Upload artifact   Upload artifact   Upload artifact
                    |                 |                 |
                    +-----------------+-----------------+
                                      |
                                      v
                        +---------------------------+
                        |     Job: Release          |
                        |  needs: [build]           |
                        +---------------------------+
                        | 1. Download artifacts     |
                        | 2. gh release create      |
                        |    + attach 3 archives    |
                        +---------------------------+
                                      |
                                      v
                        +---------------------------+
                        |   GitHub Release v1.0.0   |
                        |   - linux-x64.tar.gz      |
                        |   - windows-x64.zip       |
                        |   - macos-x64.tar.gz      |
                        +---------------------------+
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

Le workflow contient **deux jobs** :

| Job       | Runs-on              | Role                                          |
|-----------|----------------------|-----------------------------------------------|
| `build`   | matrix (3 OS)        | Compile, package et upload les artifacts       |
| `release` | ubuntu-latest        | Cree la release GitHub et attache les archives |

### Dependance entre jobs

```
build (3 runners en parallele) --> release (attend que les 3 finissent)
```

Le job `release` declare `needs: [build]`, il ne demarre que lorsque les
3 builds de la matrice ont reussi.

---

## Job 1 : Build (matrice multi-OS)

### Strategie matricielle

```yaml
strategy:
  matrix:
    include:
      - os: ubuntu-latest
        label: linux-x64
        archive_cmd: tar -czf
        archive_ext: tar.gz
      - os: windows-latest
        label: windows-x64
        archive_cmd: powershell Compress-Archive
        archive_ext: zip
      - os: macos-latest
        label: macos-x64
        archive_cmd: tar -czf
        archive_ext: tar.gz
```

GitHub Actions lance **3 runners en parallele**, un par OS. Chaque runner
execute exactement les memes etapes mais sur son systeme natif, ce qui
garantit une distribution native pour chaque plateforme.

### Etapes du job build

#### 1. Checkout du code

```yaml
- uses: actions/checkout@v4
```

Clone le repository sur le runner.

#### 2. Installation du JDK

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: 'temurin'
    java-version: '21'
```

Installe Temurin JDK 21 (Eclipse Adoptium). Cette distribution est choisie
car elle est gratuite, maintenue, et disponible sur les 3 OS. Le JDK (pas
le JRE) est necessaire car `jlink` n'est disponible que dans le JDK.

#### 3. Build Maven

```yaml
- run: mvn clean package -DskipTests=false
```

- Compile le code source
- Execute les 150 tests unitaires et d'integration
- Produit le fat JAR (`target/password-manager.jar`) via maven-assembly-plugin

Si les tests echouent, le build s'arrete et la release n'est pas creee.

#### 4. Creation du JRE minimal avec jlink

```yaml
# Linux/macOS
- run: |
    jlink \
      --add-modules java.base,java.desktop,java.logging,java.naming,java.security.jgss,java.sql,java.xml \
      --strip-debug \
      --no-man-pages \
      --compress zip-6 \
      --output dist/runtime

# Windows
- run: |
    jlink `
      --add-modules java.base,java.desktop,java.logging,java.naming,java.security.jgss,java.sql,java.xml `
      --strip-debug `
      --no-man-pages `
      --compress zip-6 `
      --output dist/runtime
```

`jlink` analyse les modules Java requis et cree un JRE minimal (~57 Mo)
contenant uniquement ces modules, au lieu du JDK complet (~300 Mo).

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

#### 5. Assemblage de la distribution

```yaml
- run: |
    cp target/password-manager.jar dist/
    # Copier le script de lancement adapte a l'OS
```

La distribution finale contient :
```
dist/
  password-manager.jar    # Fat JAR avec toutes les dependances
  runtime/                # JRE minimal (jlink)
  password-manager        # Script de lancement (Linux/macOS)
  password-manager.bat    # Script de lancement (Windows)
```

**Scripts de lancement :**

Linux/macOS (`password-manager`) :
```bash
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"$SCRIPT_DIR/runtime/bin/java" -jar "$SCRIPT_DIR/password-manager.jar"
```

Windows (`password-manager.bat`) :
```batch
@echo off
set SCRIPT_DIR=%~dp0
"%SCRIPT_DIR%runtime\bin\java.exe" -jar "%SCRIPT_DIR%password-manager.jar"
```

#### 6. Archivage

```yaml
# Linux/macOS
- run: tar -czf password-manager-${{ matrix.label }}.${{ matrix.archive_ext }} -C dist .

# Windows
- run: Compress-Archive -Path dist\* -DestinationPath password-manager-windows-x64.zip
```

#### 7. Upload de l'artifact

```yaml
- uses: actions/upload-artifact@v4
  with:
    name: password-manager-${{ matrix.label }}
    path: password-manager-${{ matrix.label }}.${{ matrix.archive_ext }}
```

Les artifacts sont stockes temporairement par GitHub Actions et seront
telecharges par le job `release`.

---

## Job 2 : Release

Ce job s'execute sur `ubuntu-latest` apres que les 3 builds aient reussi.

### Etapes

#### 1. Telechargement des artifacts

```yaml
- uses: actions/download-artifact@v4
  with:
    path: release-assets/
    merge-multiple: true
```

Telecharge les 3 archives produites par le job `build` dans `release-assets/`.

#### 2. Creation de la release GitHub

```yaml
- uses: softprops/action-gh-release@v2
  with:
    name: Password Manager ${{ github.ref_name }}
    draft: false
    prerelease: false
    generate_release_notes: true
    files: |
      release-assets/password-manager-linux-x64.tar.gz
      release-assets/password-manager-windows-x64.zip
      release-assets/password-manager-macos-x64.tar.gz
```

**`generate_release_notes: true`** genere automatiquement les notes de
release a partir des commits et PR depuis le dernier tag.

---

## Workflow YAML complet

Voici le fichier qui sera place dans `.github/workflows/release.yml` :

```yaml
name: Build & Release

on:
  push:
    tags:
      - 'v*.*.*'

permissions:
  contents: write

jobs:
  build:
    name: Build - ${{ matrix.label }}
    runs-on: ${{ matrix.os }}
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
            label: macos-x64
            archive_ext: tar.gz

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Build with Maven
        run: mvn clean package -DskipTests=false

      - name: Create minimal JRE (jlink)
        shell: bash
        run: |
          jlink \
            --add-modules java.base,java.desktop,java.logging,java.naming,java.security.jgss,java.sql,java.xml \
            --strip-debug \
            --no-man-pages \
            --compress zip-6 \
            --output dist/runtime

      - name: Assemble distribution
        shell: bash
        run: |
          cp target/password-manager.jar dist/

          # Script de lancement Linux/macOS
          cat > dist/password-manager << 'LAUNCHER'
          #!/bin/bash
          SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
          "$SCRIPT_DIR/runtime/bin/java" -jar "$SCRIPT_DIR/password-manager.jar"
          LAUNCHER
          chmod +x dist/password-manager

          # Script de lancement Windows
          cat > dist/password-manager.bat << 'LAUNCHER'
          @echo off
          set SCRIPT_DIR=%~dp0
          "%SCRIPT_DIR%runtime\bin\java.exe" -jar "%SCRIPT_DIR%password-manager.jar"
          LAUNCHER

      - name: Package archive (Linux/macOS)
        if: runner.os != 'Windows'
        run: tar -czf password-manager-${{ matrix.label }}.${{ matrix.archive_ext }} -C dist .

      - name: Package archive (Windows)
        if: runner.os == 'Windows'
        shell: pwsh
        run: Compress-Archive -Path dist\* -DestinationPath password-manager-${{ matrix.label }}.${{ matrix.archive_ext }}

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: password-manager-${{ matrix.label }}
          path: password-manager-${{ matrix.label }}.${{ matrix.archive_ext }}
          retention-days: 1

  release:
    name: Create GitHub Release
    needs: build
    runs-on: ubuntu-latest

    steps:
      - name: Download all artifacts
        uses: actions/download-artifact@v4
        with:
          path: release-assets/
          merge-multiple: true

      - name: Create release
        uses: softprops/action-gh-release@v2
        with:
          name: Password Manager ${{ github.ref_name }}
          draft: false
          prerelease: false
          generate_release_notes: true
          files: |
            release-assets/password-manager-linux-x64.tar.gz
            release-assets/password-manager-windows-x64.zip
            release-assets/password-manager-macos-x64.tar.gz
```

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

1. Aller sur la page **Releases** du repository
2. Telecharger l'archive correspondant a son OS
3. Extraire l'archive
4. Lancer `password-manager` (Linux/macOS) ou `password-manager.bat` (Windows)

Aucune installation de Java n'est requise : le JRE est embarque.

---

## Points cles

| Aspect              | Detail                                                   |
|---------------------|----------------------------------------------------------|
| **Declencheur**     | Push d'un tag `v*.*.*`                                   |
| **OS supportes**    | Linux x64, Windows x64, macOS x64                        |
| **JDK**             | Temurin 21                                                |
| **Tests**           | Executes sur chaque OS avant packaging                   |
| **Taille archive**  | ~20-25 Mo (JAR 2 Mo + JRE compresse ~57 Mo / par OS)    |
| **Retention**       | Artifacts temporaires : 1 jour / Release : permanente    |
| **fail-fast**       | `false` — les 3 builds continuent meme si l'un echoue   |
| **Permissions**     | `contents: write` pour creer la release                  |
