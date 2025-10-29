# IBM COS Integration Tests

This directory contains integration tests for the IBM Cloud Object Storage (COS) component. These tests run against a real IBM COS instance and are disabled by default.

## Prerequisites

1. An IBM Cloud account
2. An IBM Cloud Object Storage instance
3. IBM Cloud API Key with access to the COS instance
4. COS Service Instance ID (CRN)

## Setting Up IBM COS

### 1. Create an IBM Cloud Object Storage instance

```bash
# Using IBM Cloud CLI
ibmcloud resource service-instance-create my-cos-instance \
  cloud-object-storage standard global
```

### 2. Create Service Credentials

```bash
# Get the CRN of your COS instance
ibmcloud resource service-instance my-cos-instance --output json | jq -r '.[0].id'

# Create service credentials with HMAC keys
ibmcloud resource service-key-create my-cos-credentials Writer \
  --instance-name my-cos-instance \
  --parameters '{"HMAC":true}'
```

### 3. Get Your Credentials

```bash
# View your credentials
ibmcloud resource service-key my-cos-credentials --output json
```

From the output, you'll need:
- `apikey` - Your IBM Cloud API Key
- `resource_instance_id` - Your Service Instance ID (CRN)

### 4. Determine Your Endpoint URL

IBM COS endpoints vary by region and access type. Examples:
- Public endpoint (US South): `https://s3.us-south.cloud-object-storage.appdomain.cloud`
- Public endpoint (EU GB): `https://s3.eu-gb.cloud-object-storage.appdomain.cloud`
- Private endpoint (US South): `https://s3.private.us-south.cloud-object-storage.appdomain.cloud`

Full list: https://cloud.ibm.com/docs/cloud-object-storage?topic=cloud-object-storage-endpoints

## Running Integration Tests

### Option 1: Command Line Properties

Run all integration tests:

```bash
mvn verify \
  -Dcamel.ibm.cos.apiKey="YOUR_API_KEY" \
  -Dcamel.ibm.cos.serviceInstanceId="YOUR_SERVICE_INSTANCE_ID" \
  -Dcamel.ibm.cos.endpointUrl="https://s3.us-south.cloud-object-storage.appdomain.cloud" \
  -Dcamel.ibm.cos.location="us-south"
```

Run a specific integration test:

```bash
mvn verify \
  -Dcamel.ibm.cos.apiKey="YOUR_API_KEY" \
  -Dcamel.ibm.cos.serviceInstanceId="YOUR_SERVICE_INSTANCE_ID" \
  -Dcamel.ibm.cos.endpointUrl="https://s3.us-south.cloud-object-storage.appdomain.cloud" \
  -Dcamel.ibm.cos.location="us-south" \
  -Dit.test=IBMCOSProducerPutGetDeleteIT
```

### Option 2: Environment Variables

Set environment variables and reference them:

```bash
export IBM_COS_API_KEY="YOUR_API_KEY"
export IBM_COS_SERVICE_INSTANCE_ID="YOUR_SERVICE_INSTANCE_ID"
export IBM_COS_ENDPOINT_URL="https://s3.us-south.cloud-object-storage.appdomain.cloud"
export IBM_COS_LOCATION="us-south"

mvn verify \
  -Dcamel.ibm.cos.apiKey="${IBM_COS_API_KEY}" \
  -Dcamel.ibm.cos.serviceInstanceId="${IBM_COS_SERVICE_INSTANCE_ID}" \
  -Dcamel.ibm.cos.endpointUrl="${IBM_COS_ENDPOINT_URL}" \
  -Dcamel.ibm.cos.location="${IBM_COS_LOCATION}"
```

### Option 3: Maven Settings

Add to your `~/.m2/settings.xml`:

```xml
<settings>
  <profiles>
    <profile>
      <id>ibm-cos-tests</id>
      <properties>
        <camel.ibm.cos.apiKey>YOUR_API_KEY</camel.ibm.cos.apiKey>
        <camel.ibm.cos.serviceInstanceId>YOUR_SERVICE_INSTANCE_ID</camel.ibm.cos.serviceInstanceId>
        <camel.ibm.cos.endpointUrl>https://s3.us-south.cloud-object-storage.appdomain.cloud</camel.ibm.cos.endpointUrl>
        <camel.ibm.cos.location>us-south</camel.ibm.cos.location>
      </properties>
    </profile>
  </profiles>
</settings>
```

Then run:

```bash
mvn verify -Pibm-cos-tests
```

## Required Properties

| Property | Description | Example |
|----------|-------------|---------|
| `camel.ibm.cos.apiKey` | IBM Cloud API Key | `abc123...` |
| `camel.ibm.cos.serviceInstanceId` | COS Service Instance ID (CRN) | `crn:v1:bluemix:public:cloud-object-storage:global:a/...` |
| `camel.ibm.cos.endpointUrl` | COS Endpoint URL | `https://s3.us-south.cloud-object-storage.appdomain.cloud` |
| `camel.ibm.cos.location` | COS Location/Region (optional) | `us-south`, `eu-gb`, `eu-de`, etc. |

**Important:** Test buckets are automatically created in the region specified by your `endpointUrl`. Choose the endpoint that matches your desired region:
- `https://s3.us-south.cloud-object-storage.appdomain.cloud` - US South region
- `https://s3.eu-gb.cloud-object-storage.appdomain.cloud` - UK region
- `https://s3.eu-de.cloud-object-storage.appdomain.cloud` - Germany region

See [IBM COS Endpoints](https://cloud.ibm.com/docs/cloud-object-storage?topic=cloud-object-storage-endpoints) for a complete list.

## Test Coverage

The integration tests cover:

### Producer Operations
- **IBMCOSProducerPutGetDeleteIT**: putObject, getObject, deleteObject operations
- **IBMCOSProducerListOperationsIT**: listObjects, listBuckets with prefix filtering
- **IBMCOSProducerCopyObjectIT**: copyObject operation
- **IBMCOSProducerAdditionalOperationsIT**: deleteObjects (batch delete), getObjectRange (partial retrieval), headBucket, createBucket, deleteBucket

### Consumer Operations
- **IBMCOSConsumerIT**: Basic consume with deleteAfterRead
- **IBMCOSConsumerMoveAfterReadIT**: moveAfterRead with prefix support

## Test Buckets

Each integration test class creates its own isolated bucket to ensure test independence:
- `camel-test-<random-12-char-id>` (e.g., `camel-test-a1b2c3d4e5f6`)
- `camel-test-<random-12-char-id>-dest` (destination bucket for move operations)

Buckets are created before the test class runs (`@BeforeAll`) and automatically deleted after all tests in the class complete (`@AfterAll`), ensuring:
- Complete isolation between test classes
- No bucket name conflicts when running tests in parallel
- Proper cleanup even if tests fail
- Tests within the same class share the same bucket for efficiency

## Troubleshooting

### Tests are skipped

Make sure all required properties are provided. Check Maven output for disabled test reasons.

### Authentication errors

Verify your API key and service instance ID are correct:

```bash
# Test your credentials using IBM Cloud CLI
ibmcloud login --apikey YOUR_API_KEY
ibmcloud cos config crn --crn YOUR_SERVICE_INSTANCE_ID --force
ibmcloud cos buckets
```

### Network/endpoint errors

- Ensure your endpoint URL matches your region
- Check if you need to use private endpoints
- Verify firewall/proxy settings

### Permission errors

Ensure your service credentials have "Writer" or "Manager" role on the COS instance.

## Cost Considerations

Running integration tests will incur minimal IBM Cloud costs:
- Storage: Temporary objects (deleted after tests)
- Requests: API calls for put/get/delete/list operations
- Data transfer: Minimal (objects are small test files)

Estimated cost per test run: < $0.01 USD
