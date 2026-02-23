#!/bin/bash
# Password Manager - Linux/macOS launcher
# Looks for an embedded JRE in runtime/, falls back to system java.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/password-manager.jar"

if [ ! -f "$JAR" ]; then
    echo "Error: password-manager.jar not found in $SCRIPT_DIR" >&2
    exit 1
fi

# Use embedded JRE if available, otherwise system java
if [ -x "$SCRIPT_DIR/runtime/bin/java" ]; then
    JAVA="$SCRIPT_DIR/runtime/bin/java"
else
    JAVA="java"
    if ! command -v "$JAVA" >/dev/null 2>&1; then
        echo "Error: Java not found. Install Java 17+ or place a JRE in runtime/" >&2
        exit 1
    fi
fi

exec "$JAVA" -jar "$JAR" "$@"
