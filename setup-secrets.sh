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
echo "This script will create empty secrets. You'll need to update them with actual values."
echo ""

# Function to create a secret with empty value
create_secret() {
    local name=$1
    local description=$2
    echo "Creating secret: $name"
    echo "  Description: $description"
    echo "" | gh secret set "$name" --repo "$REPO" 2>/dev/null || echo "  (already exists or failed)"
}

echo "=== Nonprod Environment Secrets ==="
create_secret "CF_API_NONPROD" "Cloud Foundry API endpoint for nonprod (e.g., https://api.cf.example.com)"
create_secret "CF_USERNAME_NONPROD" "Cloud Foundry username for nonprod"
create_secret "CF_PASSWORD_NONPROD" "Cloud Foundry password for nonprod"
create_secret "CF_ORG_NONPROD" "Cloud Foundry organization for nonprod"
create_secret "CF_SPACE_NONPROD" "Cloud Foundry space for nonprod"
create_secret "CF_DOMAIN_NONPROD" "App domain for nonprod (e.g., apps.nonprod.example.com)"
create_secret "APP_ROUTE_NONPROD" "App hostname for nonprod (e.g., cf-llama-chat-dev)"

echo ""
echo "=== Production Environment Secrets ==="
create_secret "CF_API_PROD" "Cloud Foundry API endpoint for production"
create_secret "CF_USERNAME_PROD" "Cloud Foundry username for production"
create_secret "CF_PASSWORD_PROD" "Cloud Foundry password for production"
create_secret "CF_ORG_PROD" "Cloud Foundry organization for production"
create_secret "CF_SPACE_PROD" "Cloud Foundry space for production"
create_secret "CF_DOMAIN_PROD" "App domain for production (e.g., apps.example.com)"
create_secret "APP_ROUTE_PROD" "App hostname for production (e.g., cf-llama-chat)"

echo ""
echo "=============================================="
echo "Secrets created successfully!"
echo ""
echo "Next steps:"
echo "1. Go to https://github.com/$REPO/settings/secrets/actions"
echo "2. Update each secret with the actual values"
echo ""
echo "Also create GitHub Environments:"
echo "1. Go to https://github.com/$REPO/settings/environments"
echo "2. Create 'nonprod' environment"
echo "3. Create 'production' environment with 'Required reviewers' enabled"
