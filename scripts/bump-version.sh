#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Get new version from argument or prompt
if [ -z "$1" ]; then
    CURRENT_VERSION=$(cat "$ROOT_DIR/VERSION")
    echo "Current version: $CURRENT_VERSION"
    read -p "Enter new version: " NEW_VERSION
else
    NEW_VERSION="$1"
fi

# Validate version format (semver with optional suffix like -SNAPSHOT)
if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9]+)?$ ]]; then
    echo "Error: Version must be in format X.Y.Z or X.Y.Z-SUFFIX (e.g., 1.0.0, 1.1.0-SNAPSHOT)"
    exit 1
fi

echo "Updating version to $NEW_VERSION..."

# Update VERSION file
echo "$NEW_VERSION" > "$ROOT_DIR/VERSION"
echo "  - VERSION file"

# Update frontend/package.json
npm pkg set version="$NEW_VERSION" --prefix "$ROOT_DIR/frontend"
echo "  - frontend/package.json"

# Update backend/pom.xml using Maven (only changes project version)
mvn -f "$ROOT_DIR/backend/pom.xml" versions:set -DnewVersion="$NEW_VERSION" -DgenerateBackupPoms=false -q
echo "  - backend/pom.xml"

echo ""
echo "Version updated to $NEW_VERSION"
echo ""
echo "Next steps:"
echo "  1. Review changes: git diff"
echo "  2. Commit: git commit -am \"Release $NEW_VERSION\""
echo "  3. Tag (optional): git tag v$NEW_VERSION"
echo "  4. Trigger Docker build from GitHub Actions"
