#!/bin/bash
# Build a portable distribution of Password Manager with an embedded JRE.
# Output: dist/PasswordManager/
#
# Requirements: JDK 17+ with jlink, Gradle (via wrapper)
# Note: jlink produces a JRE for the current OS. Cross-platform builds
# require running this script on each target OS (or using CI).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="$PROJECT_DIR/dist/PasswordManager"

echo "=== Building fat JAR ==="
cd "$PROJECT_DIR"
./gradlew :desktop:fatJar -q

echo "=== Creating distribution directory ==="
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

echo "=== Building minimal JRE with jlink ==="
JAVA_HOME_DIR="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
JMODS_DIR="$JAVA_HOME_DIR/jmods"

if [ ! -d "$JMODS_DIR" ]; then
    echo "Warning: jmods directory not found at $JMODS_DIR"
    echo "jlink requires a JDK (not just a JRE). Skipping JRE embedding."
    echo "The distribution will use system Java."
else
    jlink \
        --add-modules java.base,java.desktop,java.logging,java.naming,java.security.jgss,java.sql,java.xml \
        --output "$DIST_DIR/runtime" \
        --strip-debug \
        --no-man-pages \
        --no-header-files \
        --compress zip-6
    echo "Embedded JRE created ($(du -sh "$DIST_DIR/runtime" | cut -f1))"
fi

echo "=== Copying files ==="
cp "$PROJECT_DIR/desktop/build/libs/password-manager.jar" "$DIST_DIR/"
cp "$SCRIPT_DIR/run.sh" "$DIST_DIR/"
cp "$SCRIPT_DIR/run.bat" "$DIST_DIR/"

chmod +x "$DIST_DIR/run.sh"
if [ -d "$DIST_DIR/runtime" ]; then
    chmod +x "$DIST_DIR/runtime/bin/"*
fi

echo "=== Distribution ready ==="
echo "Location: $DIST_DIR"
ls -la "$DIST_DIR"
echo ""
echo "To run: $DIST_DIR/run.sh"
