#!/bin/bash
# GitHub Release Script for TachiyomiDNP
# Usage: ./release.sh <version> <apk_path>

set -e

VERSION="${1:-v1.8.0}"
APK_PATH="${2:-/home/rere/TachiyomiDNP/app/build/outputs/apk/standard/release/app-standard-arm64-v8a-release.apk}"
REPO="theordinaryguy23/TachiyomiDNP"
TAG="${VERSION}"

echo "=== TachiyomiDNP Release Script ==="
echo "Version: ${VERSION}"
echo "APK: ${APK_PATH}"
echo ""

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo "ERROR: APK not found at ${APK_PATH}"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "APK size: ${APK_SIZE}"

# Rename APK with version
APK_DIR=$(dirname "$APK_PATH")
APK_NAME="TachiyomiDNP-${VERSION}-arm64-v8a.apk"
APK_RENAMED="${APK_DIR}/${APK_NAME}"
cp "$APK_PATH" "$APK_RENAMED"
echo "Renamed APK to: ${APK_NAME}"

# Create GitHub release using API
echo ""
echo "Creating GitHub release ${TAG}..."

# Create release
RELEASE_RESPONSE=$(curl -s -X POST \
    -H "Authorization: token ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github.v3+json" \
    "https://api.github.com/repos/${REPO}/releases" \
    -d "{
        \"tag_name\": \"${TAG}\",
        \"name\": \"TachiyomiDNP ${VERSION}\",
        \"body\": \"## TachiyomiDNP ${VERSION}\\n\\n### Changes\\n- See CHANGELOG.md for details\\n\\n### APK\\n- arm64-v8a: ${APK_NAME} (${APK_SIZE})\\n\\n### Minimum Requirements\\n- Android 6.0 (API 23)+\\n- 2 GB RAM\\n- ARM64/ARMv7/x86_64\",
        \"draft\": false,
        \"prerelease\": false
    }")

RELEASE_ID=$(echo "$RELEASE_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('id', ''))" 2>/dev/null)

if [ -z "$RELEASE_ID" ]; then
    echo "ERROR: Failed to create release"
    echo "$RELEASE_RESPONSE"
    exit 1
fi

echo "Release created: ID ${RELEASE_ID}"

# Upload APK
echo "Uploading APK..."
UPLOAD_URL="https://uploads.github.com/repos/${REPO}/releases/${RELEASE_ID}/assets?name=${APK_NAME}"

curl -s -X POST \
    -H "Authorization: token ${GITHUB_TOKEN}" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"${APK_RENAMED}" \
    "${UPLOAD_URL}"

echo ""
echo "=== Release Complete ==="
echo "URL: https://github.com/${REPO}/releases/tag/${TAG}"
