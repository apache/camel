# CyberArk Vault Component Integration Tests

This directory contains integration tests for the Camel CyberArk Vault component.

## Overview

The integration tests require a running CyberArk Conjur instance and use system properties for configuration. Tests are disabled by default and only run when the required system properties are provided via command line.

## Test Classes

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
- Multiple secret retrieval

### CyberArkVaultMultipleSecretsIT
Tests for complex scenarios with multiple secrets:
- Multiple secrets in single route
- Multiple database configurations
- Mixed secret types
- Connection string construction

## Running the Tests

### Prerequisites

- A running CyberArk Conjur instance (see Setup section below)
- Maven 3.6+
- Java 11+
- CyberArk Conjur admin credentials

### Required System Properties

The tests require the following system properties:

- `camel.cyberark.url` - CyberArk Conjur URL (e.g., `http://localhost:8080`)
- `camel.cyberark.account` - Conjur account name (e.g., `myAccount`)
- `camel.cyberark.username` - Admin username (e.g., `admin`)
- `camel.cyberark.apiKey` - Admin API key

### Run All Integration Tests

```bash
cd components/camel-cyberark-vault
mvn clean verify \
  -Dcamel.cyberark.url=http://localhost:8080 \
  -Dcamel.cyberark.account=myAccount \
  -Dcamel.cyberark.username=admin \
  -Dcamel.cyberark.apiKey=your-api-key
```

### Run Specific Test Class

```bash
mvn clean verify -Dit.test=CyberArkVaultPropertiesSourceIT \
  -Dcamel.cyberark.url=http://localhost:8080 \
  -Dcamel.cyberark.account=myAccount \
  -Dcamel.cyberark.username=admin \
  -Dcamel.cyberark.apiKey=your-api-key
```

### Run Specific Test Method

```bash
mvn clean verify -Dit.test=CyberArkVaultPropertiesSourceIT#testSimpleSecretRetrieval \
  -Dcamel.cyberark.url=http://localhost:8080 \
  -Dcamel.cyberark.account=myAccount \
  -Dcamel.cyberark.username=admin \
  -Dcamel.cyberark.apiKey=your-api-key
```

### Skip Integration Tests (Default)

By default, integration tests are skipped if system properties are not provided:

```bash
mvn clean install
```

## Setting Up CyberArk Conjur for Testing

### Option 1: Using Docker (Recommended)

Use the official CyberArk Conjur quickstart setup:

```bash
# Clone the quickstart repository
git clone https://github.com/cyberark/conjur-quickstart.git
cd conjur-quickstart

# Start Conjur and database
docker-compose up -d

# Wait for services to be ready
sleep 10

# The admin API key will be displayed in the logs
docker-compose logs conjur | grep "admin API key"
```

Default connection details:
- **URL**: `http://localhost:8080`
- **Account**: `myConjurAccount`
- **Username**: `admin`
- **API Key**: Check container logs

### Option 2: Using Existing Conjur Instance

If you have an existing CyberArk Conjur instance:

1. Ensure you have admin access
2. Get your admin API key
3. Use your instance URL and account name

## Test Infrastructure

### Authentication

The tests authenticate using the provided credentials before running:

```java
@BeforeAll
public static void init() throws Exception {
    // Authenticate and get token
    authToken = authenticate();

    // Create test secrets
    createSecret("simple-secret", "my-simple-value");
    createSecret("database", "{\"username\":\"dbuser\",\"password\":\"dbpass\"}");
}
```

### Creating Test Secrets

The tests create secrets using the Conjur REST API:

```java
private static void createSecret(String secretId, String secretValue) {
    String url = String.format("%s/secrets/%s/variable/%s",
            System.getProperty("camel.cyberark.url"),
            System.getProperty("camel.cyberark.account"),
            secretId);

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Token token=\"" + encodedToken + "\"")
            .POST(HttpRequest.BodyPublishers.ofString(secretValue))
            .build();

    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
}
```

## Test Scenarios Covered

### Properties Function Tests

1. **Simple Secret Retrieval**
   ```java
   {{cyberark:simple-secret}} → "my-simple-value"
   ```

2. **JSON Field Extraction**
   ```java
   {{cyberark:database#username}} → "dbuser"
   {{cyberark:database#password}} → "dbpass"
   ```

3. **Default Values**
   ```java
   {{cyberark:nonexistent:defaultValue}} → "defaultValue"
   {{cyberark:database#missing:defaultUser}} → "defaultUser"
   ```

4. **Complex Paths**
   ```java
   {{cyberark:api/credentials#token}} → "secret-token"
   ```

5. **Error Handling**
   ```java
   {{cyberark:nonexistent}} → FailedToCreateRouteException
   ```

### Component Endpoint Tests

1. **Basic Retrieval**
   ```java
   from("direct:start")
       .to("cyberark-vault:test/secret?url=...&account=...&apiKey=...")
       .to("mock:result");
   ```

2. **Dynamic Secret ID**
   ```java
   exchange.getMessage().setHeader(CyberArkVaultConstants.SECRET_ID, "production/database");
   ```

3. **Header Verification**
   - `CyberArkVaultConstants.SECRET_ID`
   - `CyberArkVaultConstants.SECRET_VALUE`

## Troubleshooting

### Tests are Skipped

If you see messages like `CyberArk Conjur URL not provided`, ensure you're passing all required system properties:

```bash
mvn verify \
  -Dcamel.cyberark.url=http://localhost:8080 \
  -Dcamel.cyberark.account=myAccount \
  -Dcamel.cyberark.username=admin \
  -Dcamel.cyberark.apiKey=your-api-key
```

### Authentication Failures

If authentication fails:
- Verify the URL is accessible
- Check that the account name is correct
- Verify the username and API key are valid
- Ensure Conjur is fully started and accepting connections

### Connection Refused

If you get connection refused errors:
- Check that Conjur is running: `docker ps | grep conjur`
- Verify the URL and port are correct
- Check firewall settings if using remote instance

### Secret Creation Failures

The tests automatically create secrets they need. If secret creation fails:
- Ensure the API key has permission to create secrets
- Check Conjur logs for policy-related errors
- Verify the account name is correct

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Integration Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_PASSWORD: SuperSecretPg
        ports:
          - 5432:5432

      conjur:
        image: cyberark/conjur
        env:
          DATABASE_URL: postgres://postgres:SuperSecretPg@postgres/postgres
          CONJUR_DATA_KEY: W0BuL24xJMVfGNTKRxcC4xv76cKE7wNJh0AKXdvmnxk=
        ports:
          - 8080:80

    steps:
      - uses: actions/checkout@v2

      - name: Set up JDK 11
        uses: actions/setup-java@v2
        with:
          java-version: '11'

      - name: Initialize Conjur Account
        run: |
          # Wait for Conjur to start
          sleep 10
          # Create account and extract API key
          API_KEY=$(docker exec conjur conjurctl account create myAccount | grep "API key" | awk '{print $NF}')
          echo "CONJUR_API_KEY=$API_KEY" >> $GITHUB_ENV

      - name: Run Integration Tests
        run: |
          mvn clean verify \
            -Dcamel.cyberark.url=http://localhost:8080 \
            -Dcamel.cyberark.account=myAccount \
            -Dcamel.cyberark.username=admin \
            -Dcamel.cyberark.apiKey=${{ env.CONJUR_API_KEY }}
```

### Jenkins Example

```groovy
pipeline {
    agent any

    stages {
        stage('Setup Conjur') {
            steps {
                sh '''
                    docker-compose -f test-resources/conjur-docker-compose.yml up -d
                    sleep 10
                '''
            }
        }

        stage('Run Integration Tests') {
            steps {
                sh '''
                    API_KEY=$(docker exec conjur conjurctl account create myAccount | grep "API key" | awk '{print $NF}')
                    mvn clean verify \
                        -Dcamel.cyberark.url=http://localhost:8080 \
                        -Dcamel.cyberark.account=myAccount \
                        -Dcamel.cyberark.username=admin \
                        -Dcamel.cyberark.apiKey=$API_KEY
                '''
            }
        }
    }

    post {
        always {
            sh 'docker-compose -f test-resources/conjur-docker-compose.yml down'
        }
    }
}
```

## Test Data

### Created Secrets

The integration tests create the following secrets:

| Secret ID | Value | Purpose |
|-----------|-------|---------|
| `simple-secret` | `my-simple-value` | Basic retrieval test |
| `database` | `{"username":"dbuser",...}` | JSON field extraction |
| `api/credentials` | `{"token":"secret-token",...}` | Complex path test |
| `test/secret` | `mySecretValue` | Component endpoint test |
| `production/database` | `{"username":"prod-user",...}` | Dynamic retrieval test |
| `app/config` | `{"port":"8080",...}` | Multiple secrets test |
| `db/primary` | `{"host":"db1.example.com",...}` | Multiple configs test |
| `db/replica` | `{"host":"db2.example.com",...}` | Multiple configs test |
| `cache/redis` | `{"host":"redis.example.com",...}` | Mixed types test |

## Adding New Tests

To add new integration tests:

1. **Create Test Class**:
   ```java
   @EnabledIfSystemProperties({
       @EnabledIfSystemProperty(named = "camel.cyberark.url", matches = ".*",
                                disabledReason = "CyberArk Conjur URL not provided"),
       @EnabledIfSystemProperty(named = "camel.cyberark.account", matches = ".*",
                                disabledReason = "CyberArk Conjur account not provided"),
       @EnabledIfSystemProperty(named = "camel.cyberark.username", matches = ".*",
                                disabledReason = "CyberArk Conjur username not provided"),
       @EnabledIfSystemProperty(named = "camel.cyberark.apiKey", matches = ".*",
                                disabledReason = "CyberArk Conjur API key not provided")
   })
   public class MyNewIT extends CamelTestSupport {
       // Tests here
   }
   ```

2. **Create Secrets in @BeforeAll**:
   ```java
   @BeforeAll
   public static void setup() throws Exception {
       authToken = authenticate();
       createSecret("my/secret", "value");
   }
   ```

3. **Use System Properties for Connection**:
   ```java
   String url = System.getProperty("camel.cyberark.url");
   String account = System.getProperty("camel.cyberark.account");
   String username = System.getProperty("camel.cyberark.username");
   String apiKey = System.getProperty("camel.cyberark.apiKey");
   ```

## Best Practices

1. **Cleanup**: Secrets can be cleaned up manually or will be reset when Conjur restarts
2. **Isolation**: Use unique secret IDs for each test to avoid conflicts
3. **Logging**: Use SLF4J logger to debug issues
4. **Assertions**: Use MockEndpoint.assertIsSatisfied() for async verification
5. **Error Testing**: Use assertThrows() for expected failures
6. **System Properties**: Always use System.getProperty() to read configuration

## References

- [CyberArk Conjur Documentation](https://docs.cyberark.com/Product-Doc/OnlineHelp/AAM-DAP/Latest/en/Content/Resources/_TopNav/cc_Home.htm)
- [CyberArk Conjur Quickstart](https://github.com/cyberark/conjur-quickstart)
- [Camel Testing Guide](https://camel.apache.org/manual/testing.html)
- [JUnit 5 Conditional Test Execution](https://junit.org/junit5/docs/current/user-guide/#writing-tests-conditional-execution)
