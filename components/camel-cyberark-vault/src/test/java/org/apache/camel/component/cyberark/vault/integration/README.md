# CyberArk Vault Component Integration Tests

This directory contains integration tests for the Camel CyberArk Vault component.

## Overview

The tests run against a real CyberArk Conjur instance started automatically by
`camel-test-infra-cyberark-vault`. No manual setup and no system properties are required - a
working container runtime (Docker or Podman) is the only prerequisite.

Conjur cannot run standalone, so the test infra starts **two** containers wired together on a
private network:

- `mirror.gcr.io/cyberark/conjur` - the Conjur server
- `mirror.gcr.io/postgres` - the database Conjur stores its data in

On startup the infra also creates the Conjur account and captures the generated admin API key,
which is what the tests authenticate with.

> **Architecture note:** the `cyberark/conjur` image is only published for `amd64` and `arm64`.
> The ITs are therefore disabled on `ppc64le` and `s390x` via the `skipITs.ppc64le` and
> `skipITs.s390x` properties in the component `pom.xml`.

## Test Classes

All test classes extend `CyberArkTestSupport`, which registers the test infra service and exposes
the `loadPolicy`, `createSecret` and `configureVault` helpers.

### CyberArkVaultPropertiesSourceIT
Tests for the properties function (`{{cyberark:...}}`):
- Simple secret retrieval
- JSON field extraction (`{{cyberark:secret#field}}`)
- Default values (`{{cyberark:secret:defaultValue}}`)
- Complex paths (`{{cyberark:path/to/secret#field}}`)
- Error handling for missing secrets

### CyberArkVaultProducerIT
Tests for the component endpoint (`cyberark-vault://secretId`):
- Basic secret retrieval via component endpoint
- Dynamic secret ID via headers
- Header verification (SECRET_ID, SECRET_VALUE constants)

### CyberArkVaultMultipleSecretsIT
Tests for complex scenarios with multiple secrets:
- Multiple secrets in single route
- Multiple database configurations
- Mixed secret types
- Connection string construction

## Running the Tests

```bash
cd components/camel-cyberark-vault

# all integration tests
mvn clean verify

# a single test class
mvn clean verify -Dit.test=CyberArkVaultPropertiesSourceIT

# a single test method
mvn clean verify -Dit.test=CyberArkVaultPropertiesSourceIT#testSimpleSecretRetrieval
```

## Declaring Secrets

A Conjur secret is two distinct things:

1. a `variable` resource **declared by a policy**, and
2. a value assigned to that variable through the Secrets API.

The Secrets API can only update the value - it cannot create the variable. Setting a value for a
variable that was never declared returns `404 Variable not found`, so every test must load a
policy before creating its secrets:

```java
@BeforeAll
static void init() throws Exception {
    // 1. declare the variables
    loadPolicy("""
            - !variable simple-secret
            - !variable database
            """);

    // 2. then assign their values
    createSecret("simple-secret", "my-simple-value");
    createSecret("database", "{\"username\":\"dbuser\",\"password\":\"dbpass\"}");
}
```

## Adding New Tests

```java
class MyNewIT extends CyberArkTestSupport {

    @BeforeAll
    static void setup() throws Exception {
        loadPolicy("- !variable my/secret");
        createSecret("my/secret", "value");
    }

    @Test
    void testSomething() throws Exception {
        // for the properties function, point the vault configuration at the test infra
        configureVault();
        ...
    }
}
```

Use unique secret IDs per test class to avoid clashes, since the Conjur instance is shared across
the test classes running in the same JVM.

When the component endpoint is used directly, the connection details come from the service:

```java
service.url()       // e.g. http://localhost:32768
service.account()   // myConjurAccount
service.username()  // admin
service.apiKey()    // generated on startup
```

## Troubleshooting

### Tests fail to start the containers

- Verify the container runtime is running and reachable by Testcontainers
- The first run pulls both images, which can take a while on a slow connection

### Secret creation fails with 404

The variable was not declared - add it to the `loadPolicy` call as described above.

## References

- [CyberArk Conjur Documentation](https://docs.cyberark.com/conjur-open-source/latest/en/content/resources/_topnav/cc_home.htm)
- [Conjur Policy Reference](https://docs.cyberark.com/conjur-open-source/latest/en/content/operations/policy/policy-syntax.htm)
- [Camel Testing Guide](https://camel.apache.org/manual/testing.html)
