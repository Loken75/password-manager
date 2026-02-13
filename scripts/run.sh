#!/bin/bash
# Password Manager - Linux/macOS launcher
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/../target/password-manager.jar"

if [ ! -f "$JAR" ]; then
    echo "JAR not found. Building..."
    cd "$SCRIPT_DIR/.." && mvn clean package -q
fi

java -jar "$JAR"
