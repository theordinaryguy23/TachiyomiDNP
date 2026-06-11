#!/bin/bash
# Generate google-services.json from environment variable
# Usage: ./scripts/setup-firebase.sh
#
# Set GOOGLE_SERVICES_JSON environment variable with the content of google-services.json
# For CI/CD, set it as a GitHub Secret

set -e

GOOGLE_SERVICES_FILE="app/src/standard/google-services.json"

if [ -z "$GOOGLE_SERVICES_JSON" ]; then
    if [ -f "$GOOGLE_SERVICES_FILE" ]; then
        # Check if it's a dummy file
        if grep -q "DummyKey" "$GOOGLE_SERVICES_FILE" 2>/dev/null; then
            echo "WARNING: google-services.json is a dummy file. Firebase features will not work."
            echo "Set GOOGLE_SERVICES_JSON environment variable with your real Firebase config."
        else
            echo "google-services.json already exists, skipping..."
        fi
        exit 0
    else
        echo "ERROR: GOOGLE_SERVICES_JSON environment variable is not set"
        echo "Please set it with the content of your google-services.json file"
        echo ""
        echo "Example:"
        echo "  export GOOGLE_SERVICES_JSON='{\"project_info\":...}'"
        echo ""
        echo "Or create app/src/standard/google-services.json manually"
        exit 1
    fi
fi

echo "Generating google-services.json..."
mkdir -p "$(dirname "$GOOGLE_SERVICES_FILE")"
echo "$GOOGLE_SERVICES_JSON" > "$GOOGLE_SERVICES_FILE"
echo "Done! google-services.json created at $GOOGLE_SERVICES_FILE"
