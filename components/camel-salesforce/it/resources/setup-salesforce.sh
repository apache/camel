#!/bin/bash

#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

# Script to setup Salesforce environment for Camel Salesforce component integration tests
# This script supports two modes:
# 1. Scratch org creation and deployment
# 2. Traditional org with username/password authentication

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/sfdx-project"

echo "=== Salesforce Integration Test Setup ==="
echo ""

# Check if SF CLI is installed
if ! command -v sf &> /dev/null; then
    echo "ERROR: Salesforce CLI (sf) is not installed or not in PATH"
    echo "Please install from: https://developer.salesforce.com/tools/salesforcecli"
    exit 1
fi

echo "✓ Salesforce CLI found: $(sf version --json | grep '"cliVersion"' | cut -d'"' -f4)"
echo ""

# Check if jq is available for JSON parsing (required for org detection)
if ! command -v jq >/dev/null 2>&1; then
    echo "ERROR: jq is required for JSON parsing but not found in PATH"
    echo ""
    echo "Please install jq:"
    echo "  - macOS: brew install jq"
    echo "  - Ubuntu/Debian: apt-get install jq"
    echo "  - RHEL/CentOS: yum install jq"
    echo ""
    echo "Download: https://jqlang.github.io/jq/download/"
    exit 1
fi

echo "✓ jq found: $(jq --version)"
echo ""

# Determine mode of operation based on provided credentials
DEPLOY_SOURCE="${SALESFORCE_DEPLOY_SOURCE:-true}"
USERNAME="${SALESFORCE_USERNAME}"
PASSWORD="${SALESFORCE_PASSWORD}"

# Change to project directory to check for local target-org
cd "$PROJECT_DIR"

# Check if there's already a target-org configured locally (not globally)
EXISTING_TARGET_ORG=$(sf config get target-org --json 2>/dev/null | jq -r '.result[] | select(.location == "Local") | .value // empty')

if [ -n "$EXISTING_TARGET_ORG" ]; then
    echo "Mode: Using Existing Target Org"
    echo "--------------------------------"
    echo "✓ Found existing local target-org: $EXISTING_TARGET_ORG"
    echo ""
    # Skip org creation, proceed to deployment

# Auto-detect mode: if no username/password, try creating a scratch org
elif [ -z "$USERNAME" ] && [ -z "$PASSWORD" ]; then
    echo "Mode: Auto-Create Scratch Org (no credentials provided)"
    echo "------------------------------------------------"

    # Get Dev Hub username - check if explicitly provided first
    DEV_HUB="${SALESFORCE_DEV_HUB}"

    if [ -z "$DEV_HUB" ]; then
        # Get default Dev Hub username from SF CLI
        DEV_HUB=$(sf org list --json 2>/dev/null | jq -r '(.result.devHubs // []) + (.result.nonScratchOrgs // []) + (.result.scratchOrgs // []) + (.result.sandboxes // []) + (.result.other // []) | .[] | select(.isDefaultDevHubUsername == true) | .username' | head -1)
    fi

    if [ -z "$DEV_HUB" ]; then
        echo "ERROR: No credentials or scratch org alias provided, and no default Dev Hub set"
        echo ""
        echo "To run integration tests, you must either:"
        echo ""
        echo "1. Provide traditional org credentials:"
        echo "   export SALESFORCE_USERNAME=\"your-username@example.com\""
        echo "   export SALESFORCE_PASSWORD=\"your-password-and-token\""
        echo ""
        echo "2. Authenticate to a Dev Hub and set it as default (for automatic scratch org creation):"
        echo "   sf org login web --set-default-dev-hub"
        exit 1
    fi
    echo "✓ Using Dev Hub: $DEV_HUB"

    # Generate unique scratch org alias
    SCRATCH_ORG_ALIAS="camel-sf-test-$(date +%s)"
    SCRATCH_ORG_DURATION="${SALESFORCE_SCRATCH_ORG_DURATION:-7}"

    echo "Creating scratch org: $SCRATCH_ORG_ALIAS (${SCRATCH_ORG_DURATION} days)..."

    sf org create scratch \
        --definition-file "config/scratch-org-def.json" \
        --alias "$SCRATCH_ORG_ALIAS" \
        --duration-days "$SCRATCH_ORG_DURATION" \
        --set-default \
        --target-dev-hub "$DEV_HUB"

    echo "✓ Scratch org created: $SCRATCH_ORG_ALIAS"
    echo ""

    # SF CLI automatically sets the created scratch org as default, no need to specify TARGET_ORG

else
    echo "Mode: Traditional Org"
    echo "--------------------"

    LOGIN_URL="${SALESFORCE_LOGIN_URL:-https://login.salesforce.com}"

    if [ -z "$USERNAME" ] || [ -z "$PASSWORD" ]; then
        echo "ERROR: SALESFORCE_USERNAME and SALESFORCE_PASSWORD environment variables must be set"
        echo ""
        echo "Set environment variables:"
        echo "  export SALESFORCE_USERNAME=\"your-username@example.com\""
        echo "  export SALESFORCE_PASSWORD=\"your-password-and-token\""
        exit 1
    fi

    echo "Authenticating to: $LOGIN_URL"
    echo "Username: $USERNAME"

    # Authenticate using username/password
    ORG_ALIAS="camel-sf-integration"

    # Use sfdx auth command for username/password flow
    sf org login sfdx-url \
        --sfdx-url-stdin \
        --alias "$ORG_ALIAS" \
        --set-default <<< "force://$USERNAME:$PASSWORD@$(echo $LOGIN_URL | sed 's|https://||')"

    echo "✓ Authenticated successfully"
    echo ""
fi

# Deploy source if requested
if [ "$DEPLOY_SOURCE" = "true" ]; then
    echo "Deploying source to org..."
    echo "-------------------------"

    # Generate OAuth credentials for the connected app from template
    CONNECTED_APP_TEMPLATE="config/CamelSalesforceIntegrationTests.connectedApp-template.xml"
    CONNECTED_APP_FILE="salesforce-source/main/default/connectedApps/CamelSalesforceIntegrationTests.connectedApp-meta.xml"
    CONNECTED_APP_CREDENTIALS=".local/connected-app-credentials.json"

    # Ensure .local directory exists
    mkdir -p .local

    # Check if we have existing valid connected app credentials (separate from auth credentials)
    EXISTING_CLIENT_ID=""
    EXISTING_CLIENT_SECRET=""
    if [ -f "$CONNECTED_APP_CREDENTIALS" ]; then
        EXISTING_CLIENT_ID=$(jq -r '.clientId // empty' "$CONNECTED_APP_CREDENTIALS")
        EXISTING_CLIENT_SECRET=$(jq -r '.clientSecret // empty' "$CONNECTED_APP_CREDENTIALS")
    fi

    if [ -n "$EXISTING_CLIENT_ID" ] && [ -n "$EXISTING_CLIENT_SECRET" ]; then
        # Valid credentials exist - connected app was previously created
        # Deploy without consumerKey/consumerSecret (can't update these)
        echo "Existing connected app credentials found - deploying without regenerating keys..."

        # Generate connected app file without credentials (for update)
        sed -e '/{{CONSUMER_KEY}}/d' \
            -e '/{{CONSUMER_SECRET}}/d' \
            "$CONNECTED_APP_TEMPLATE" > "$CONNECTED_APP_FILE"

        echo "✓ Using existing connected app credentials"

    elif [ -f "$CONNECTED_APP_TEMPLATE" ]; then
        # First time setup - generate new credentials
        echo "Generating OAuth credentials for connected app..."

        # Generate random credentials using /dev/urandom
        # Consumer Key: alphanumeric only (per Salesforce docs)
        CONSUMER_KEY=$(head -c 48 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 64)
        # Consumer Secret: uppercase hex (matching Salesforce's own format)
        CONSUMER_SECRET=$(head -c 32 /dev/urandom | xxd -p | tr -d '\n' | tr 'a-f' 'A-F' | head -c 64)

        # Generate connected app file from template with credentials
        sed -e "s|{{CONSUMER_KEY}}|${CONSUMER_KEY}|g" \
            -e "s|{{CONSUMER_SECRET}}|${CONSUMER_SECRET}|g" \
            "$CONNECTED_APP_TEMPLATE" > "$CONNECTED_APP_FILE"

        # Save connected app credentials for future deployments
        cat > "$CONNECTED_APP_CREDENTIALS" << EOF
{
    "clientId": "$CONSUMER_KEY",
    "clientSecret": "$CONSUMER_SECRET"
}
EOF

        echo "✓ OAuth credentials generated and saved"
    else
        echo "ERROR: Connected app template not found: $CONNECTED_APP_TEMPLATE"
        exit 1
    fi

    # Deploy the source (SF CLI uses the default org automatically)
    sf project deploy start \
        --source-dir salesforce-source \
        --wait 10

    echo "✓ Source deployed successfully"

    # Assign permission set to the current user (ignore if already assigned)
    echo "Assigning permission set to user..."
    sf org assign permset --name Hard_Delete_Permission_Set 2>&1 || true
    echo "✓ Permission set assigned"
else
    echo "Skipping source deployment (SALESFORCE_DEPLOY_SOURCE=false)"
fi

# Get org information including refresh token
echo ""
echo "Retrieving org information..."
ORG_INFO=$(sf org display --verbose --json)
INSTANCE_URL=$(echo "$ORG_INFO" | jq -r '.result.instanceUrl')
ORG_USERNAME=$(echo "$ORG_INFO" | jq -r '.result.username')
SFDX_AUTH_URL=$(echo "$ORG_INFO" | jq -r '.result.sfdxAuthUrl // empty')

# Extract refresh token from sfdxAuthUrl (format: force://clientId::refreshToken@instanceUrl)
if [ -n "$SFDX_AUTH_URL" ]; then
    REFRESH_TOKEN=$(echo "$SFDX_AUTH_URL" | sed -n 's|force://[^:]*::\([^@]*\)@.*|\1|p')
fi

echo "✓ Instance URL: $INSTANCE_URL"
echo "✓ Username: $ORG_USERNAME"
if [ -n "$REFRESH_TOKEN" ]; then
    echo "✓ Refresh token available"
fi

# Store credentials for integration tests
# Using refresh token flow with PlatformCLI (SF CLI's built-in connected app)
CREDENTIALS_FILE="$PROJECT_DIR/.local/test-credentials.json"

# Ensure .local directory exists
mkdir -p "$PROJECT_DIR/.local"
echo ""
echo "Storing credentials for integration tests..."

cat > "$CREDENTIALS_FILE" << EOF
{
    "clientId": "PlatformCLI",
    "refreshToken": "${REFRESH_TOKEN:-}",
    "instanceUrl": "$INSTANCE_URL"
}
EOF

echo "✓ Credentials stored in: $CREDENTIALS_FILE"

echo ""
echo "=== Setup Complete ==="

# Display org info (uses default org)
echo ""
echo "Default Org Information:"
sf org display
