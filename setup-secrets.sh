#!/bin/bash
# Setup script for GitHub secrets required by the CF deployment workflow
# Run this script with: ./setup-secrets.sh
#
# Prerequisites:
# - GitHub CLI (gh) installed and authenticated
# - Run from the repository root directory

set -e

REPO="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner -q '.nameWithOwner')}"

echo "Setting up GitHub secrets for repository: $REPO"
echo "=============================================="
echo ""
echo "You will be prompted to enter values for each secret."
echo "Press Enter to skip a secret (leave it unchanged)."
echo "Passwords will be hidden as you type."
echo ""

# Function to prompt and set a secret
set_secret() {
    local name=$1
    local description=$2
    local is_password=${3:-false}

    echo "----------------------------------------"
    echo "Secret: $name"
    echo "Description: $description"

    if [ "$is_password" = "true" ]; then
        read -s -p "Enter value (hidden): " value
        echo ""
    else
        read -p "Enter value: " value
    fi

    if [ -n "$value" ]; then
        echo "$value" | gh secret set "$name" --repo "$REPO"
        echo "  ✓ Secret '$name' has been set"
    else
        echo "  ⊘ Skipped (no value entered)"
    fi
    echo ""
}

echo ""
echo "=== Nonprod Environment Secrets ==="
echo ""

set_secret "CF_API_NONPROD" "Cloud Foundry API endpoint (e.g., https://api.cf.example.com)"
set_secret "CF_USERNAME_NONPROD" "Cloud Foundry username"
set_secret "CF_PASSWORD_NONPROD" "Cloud Foundry password" true
set_secret "CF_ORG_NONPROD" "Cloud Foundry organization"
set_secret "CF_SPACE_NONPROD" "Cloud Foundry space"
set_secret "CF_DOMAIN_NONPROD" "App domain (e.g., apps.nonprod.example.com)"
set_secret "APP_ROUTE_NONPROD" "App hostname (e.g., cf-llama-chat-dev)"

echo ""
echo "=== Production Environment Secrets ==="
echo ""

set_secret "CF_API_PROD" "Cloud Foundry API endpoint"
set_secret "CF_USERNAME_PROD" "Cloud Foundry username"
set_secret "CF_PASSWORD_PROD" "Cloud Foundry password" true
set_secret "CF_ORG_PROD" "Cloud Foundry organization"
set_secret "CF_SPACE_PROD" "Cloud Foundry space"
set_secret "CF_DOMAIN_PROD" "App domain (e.g., apps.example.com)"
set_secret "APP_ROUTE_PROD" "App hostname (e.g., cf-llama-chat)"

echo ""
echo "=============================================="
echo "Setup complete!"
echo ""
echo "You can view/edit secrets at:"
echo "  https://github.com/$REPO/settings/secrets/actions"
echo ""
echo "Don't forget to create GitHub Environments:"
echo "  1. Go to https://github.com/$REPO/settings/environments"
echo "  2. Create 'nonprod' environment"
echo "  3. Create 'production' environment with 'Required reviewers' enabled"
echo ""
