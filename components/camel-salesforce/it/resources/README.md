# Salesforce Integration Test Setup

This directory contains the Salesforce configuration and setup scripts for the Camel Salesforce component integration tests.

## Overview

The integration tests use the **Salesforce CLI (sf)** to deploy metadata to a Salesforce org. The metadata is stored in **source format** (not metadata API format) for easier version control and compatibility with modern Salesforce development tools.

## Prerequisites

1. **Salesforce CLI** must be installed

   **Install via npm (recommended):**
   ```bash
   npm install -g @salesforce/cli
   ```

   **Or download installer:**
   - Download from: https://developer.salesforce.com/tools/salesforcecli

   **Verify installation:**
   ```bash
   sf --version
   ```

2. **jq** (JSON processor) - **Required** for parsing SF CLI JSON output

   **Installation:**
   ```bash
   # macOS
   brew install jq

   # Ubuntu/Debian
   sudo apt-get install jq

   # RHEL/CentOS
   sudo yum install jq
   ```

   Download: https://jqlang.github.io/jq/download/

## Running Integration Tests

When integration tests are run via maven, `setup-salesforce.sh` is executed and automatically detects which mode to use with the following priority:

1. **Existing Scratch Org**: If a `target-org` is configured locally (in `sfdx-project/.sf/config.json`), it will be reused. You can run this command to see the current local target-org:
   ```bash
   sf config get target-org --verbose
   ```
2. **Auto-Create Scratch Org**: If no credentials provided and no local org exists, a new scratch org is created
3. **Traditional Org**: If username/password credentials are provided, authenticate with those

All operations use the **Salesforce CLI's local default org** mechanism, scoped to the project directory.

To reset and create a fresh org:
```bash
sf config unset target-org
```

### Option A: Scratch Orgs

If you have a Dev Hub authenticated and don't provide any credentials, a scratch org is automatically created on the first run and reused on subsequent runs:

```bash
# One-time setup: Authenticate to Dev Hub
sf org login web --set-default-dev-hub

# First run - scratch org is automatically created and set as local default
mvn clean verify -Pintegration

# Subsequent runs - reuses the same org (no new org creation)
mvn clean verify -Pintegration
```

**How it works:**
- **First run**: Creates a new scratch org with a unique name (e.g., `camel-sf-test-1733123456`)
- Sets it as the **local default org** (stored in `sfdx-project/.sf/config.json`)
- Deploys the source metadata to the org
- Extracts the **refresh token** from SF CLI and stores it for test authentication
- Runs the integration tests
- **Subsequent runs**: Detects existing local target-org and reuses it (skips org creation)
- The scratch org remains available for 7 days by default

**Authentication:**
The integration tests use the **refresh token flow** with `PlatformCLI` (SF CLI's built-in connected app). This approach:
- Uses the refresh token already stored by SF CLI (no additional OAuth flow needed)
- Does not require a security token
- Does not require username/password
- Works immediately after scratch org creation

**After tests run:**
The scratch org is configured as the local default. You can:
- View org details: `sf org display`
- Open in browser: `sf org open`
- List all orgs: `sf org list`
- Delete when done: `sf org delete scratch --target-org <alias> --no-prompt`
- Reset to create a new org: `sf config unset target-org`

### Option B: Traditional Salesforce Orgs

**Important:** Before using username-password authentication, you must enable this OAuth flow in your Salesforce org:

1. Go to **Setup** → **OAuth and OIDC Settings**
   - Direct URL: `/lightning/setup/OauthOidcSettings/home`
2. Enable **"Allow OAuth Username-Password Flows"**
3. Save your changes

This setting is disabled by default in newer Salesforce orgs for security reasons. Scratch orgs created by the setup script have this setting enabled automatically via the scratch org definition.

Set the following environment variables:

```bash
export SALESFORCE_USERNAME="your-username@example.com"
export SALESFORCE_PASSWORD="your-password-and-security-token"
export SALESFORCE_LOGIN_URL="https://login.salesforce.com"  # or https://test.salesforce.com for sandbox

mvn clean verify -Pintegration
```

**How it works:**
- **First run**: Authenticates to your org with username/password
- Sets it as the **local default org** (alias: `camel-sf-integration`)
- Deploys the source metadata
- Runs the integration tests
- **Subsequent runs**: Detects existing local target-org and reuses it (skips authentication)

### Additional Configuration Options

#### Custom Scratch Org Duration             
```bash
export SALESFORCE_SCRATCH_ORG_DURATION=14  # Default: 7 days (max: 30)
mvn clean verify -Pintegration
```

#### Skip Source Deployment (Reuse Existing Org)
If the org already has the metadata deployed:
```bash
export SALESFORCE_DEPLOY_SOURCE=false
mvn clean verify -Pintegration
```

#### Specify Dev Hub Explicitly
If you want to specify which Dev Hub to use (instead of the default):
```bash
export SALESFORCE_DEV_HUB="mydevhub@example.com"
mvn clean verify -Pintegration
```

#### Use a Specific Existing Org
Set any org as the default before running tests:
```bash
# Login to a new org and set as default
sf org login web --alias my-test-org --set-default

# Or set an already-authenticated org as default
sf config set target-org my-existing-org

mvn clean verify -Pintegration
```

## Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SALESFORCE_USERNAME` | Username for traditional org | - | No* |
| `SALESFORCE_PASSWORD` | Password + security token for traditional org | - | No* |
| `SALESFORCE_LOGIN_URL` | Login URL for traditional org | `https://login.salesforce.com` | No |
| `SALESFORCE_DEV_HUB` | Dev Hub username (skips auto-detection) | Auto-detected | No |
| `SALESFORCE_DEPLOY_SOURCE` | Deploy source to org | `true` | No |
| `SALESFORCE_SCRATCH_ORG_DURATION` | Scratch org lifetime in days (1-30) | `7` | No |

**How SF CLI Tracks Orgs:**
- All org authentication is stored **locally** in `sfdx-project/.sf/` directory (git-ignored)
- The script sets the authenticated/created org as the **local default** (not global)
- Subsequent runs automatically detect and reuse the local target-org
- This keeps the project isolated from other SF CLI usage on your machine
- Use `sf org display` to see which org is currently the local default
- Use `sf config get target-org` to see where the org is configured (Local vs Global)

## Manual Setup Script Execution

You can run the setup script manually, outside of a maven execution:

```bash
cd it/resources
./setup-salesforce.sh
```

## Additional Resources

- [Salesforce CLI Documentation](https://developer.salesforce.com/docs/atlas.en-us.sfdx_cli_reference.meta/sfdx_cli_reference/)
- [Salesforce DX Developer Guide](https://developer.salesforce.com/docs/atlas.en-us.sfdx_dev.meta/sfdx_dev/)
- [Scratch Orgs](https://developer.salesforce.com/docs/atlas.en-us.sfdx_dev.meta/sfdx_dev/sfdx_dev_scratch_orgs.htm)
