# Secrets

Shows secrets resolved from cloud vault providers. Camel can load
configuration values from external secret managers instead of
hardcoding them in properties files.

Supported providers: AWS Secrets Manager, Azure Key Vault,
GCP Secret Manager, HashiCorp Vault, IBM Secrets Manager,
Kubernetes Secrets, and Kubernetes ConfigMaps.

## Table Columns

- **VAULT** — The cloud provider name (AWS, Azure, GCP, Hashicorp, IBM, Kubernetes, Kubernetes-cm)
- **REGION** — Cloud region (AWS only, blank for other providers)
- **SECRET** — The secret name/key as referenced in the route configuration
- **AGE** — How long ago the secret value was last updated
- **UPDATE** — How long ago the last automatic reload of secrets occurred
- **CHECK** — How long ago the last check for secret changes was performed

## Automatic Refresh

Camel can automatically detect secret changes and reload them:
- **AWS** — monitors via CloudTrail events
- **Azure** — monitors via Event Hubs
- **GCP** — monitors via Pub/Sub
- **IBM** — monitors via Event Streams (Kafka)
- **Kubernetes** — watches the Kubernetes API

When refresh is enabled, the UPDATE and CHECK columns show the
timing of these automatic operations.

## Keys

- `Up/Down` — select secret
- `f` — force reload secrets from vault providers
- `s` — cycle sort column
- `S` — reverse sort order
