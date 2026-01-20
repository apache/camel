# Google Vertex AI Integration Tests

This directory contains integration tests for the Google Vertex AI component. These tests are **disabled by default** and only run when the required system properties are provided.

## Prerequisites

1. A Google Cloud Platform account with Vertex AI API enabled
2. A service account key file (JSON format) with appropriate permissions
3. Access to Vertex AI models in your GCP project

## Running the Integration Tests

The integration tests use **system properties** for configuration. This is the standard approach used across all Camel Google components.

### Required System Properties

- `google.vertexai.serviceAccountKey` - Path to your service account JSON key file
- `google.vertexai.project` - Your Google Cloud project ID

### Optional System Properties

- `google.vertexai.location` - GCP region (default: `us-central1`)
- `google.vertexai.model` - Vertex AI model to use (default: `gemini-2.5-flash`)

### Running the Tests

```bash
mvn test -Dtest=GoogleVertexAIIT \
  -Dgoogle.vertexai.serviceAccountKey=/path/to/service-account-key.json \
  -Dgoogle.vertexai.project=your-project-id
```

### Running with Optional Parameters

```bash
mvn test -Dtest=GoogleVertexAIIT \
  -Dgoogle.vertexai.serviceAccountKey=/path/to/service-account-key.json \
  -Dgoogle.vertexai.project=your-project-id \
  -Dgoogle.vertexai.location=europe-west1 \
  -Dgoogle.vertexai.model=gemini-3-pro
```

### Running All Integration Tests During Maven Verify

```bash
mvn verify \
  -Dgoogle.vertexai.serviceAccountKey=/path/to/service-account-key.json \
  -Dgoogle.vertexai.project=your-project-id
```

## Test Coverage

The integration tests (`GoogleVertexAIIT.java`) cover:

1. **Text Generation** - Basic text generation with the `generateText` operation
2. **Chat Generation** - Conversational responses with the `generateChat` operation
3. **Code Generation** - Code generation with the `generateCode` operation
4. **Header-based Configuration** - Overriding parameters via message headers (temperature, maxOutputTokens)
5. **Multiple Requests** - Sequential requests to test stability

## Available Locations

Common Google Cloud regions for Vertex AI:
- `us-central1` (default)
- `us-east1`
- `us-west1`
- `europe-west1`
- `europe-west4`
- `asia-southeast1`
- `asia-northeast1`

## Available Models

### Recommended Models (2025)

- `gemini-3-pro` - Latest reasoning-first model (high capability)
- `gemini-2.5-pro` - Advanced pro model
- `gemini-2.5-flash` - Fast and cost-effective (default)
- `gemini-2.5-flash-lite` - Lightweight variant
- `gemini-2.0-flash` - Previous generation

### Legacy Models (Deprecated)

- `text-bison` - Legacy PaLM text model
- `chat-bison` - Legacy PaLM chat model
- `code-bison` - Legacy Codey code model

## Service Account Permissions

Your service account needs the following IAM role:

```
roles/aiplatform.user
```

Or the specific permission:
```
aiplatform.endpoints.predict
```

To grant the permission:

```bash
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:YOUR_SERVICE_ACCOUNT@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"
```

## Troubleshooting

### Tests are Skipped

If you see a message like:
```
Tests run: 0, Failures: 0, Errors: 0, Skipped: 0
```

**Solution**: Ensure the system property `google.vertexai.serviceAccountKey` is set:
```bash
mvn test -Dtest=GoogleVertexAIIT -Dgoogle.vertexai.serviceAccountKey=/path/to/key.json
```

### Authentication Errors

**Error**: `The Application Default Credentials are not available`

**Solution**: Verify your service account key file:
- Check that the file path is correct
- Ensure the file is valid JSON
- Verify the file is readable

### Permission Denied

**Error**: `PERMISSION_DENIED` or `403 Forbidden`

**Solutions**:
1. Ensure Vertex AI API is enabled:
   ```bash
   gcloud services enable aiplatform.googleapis.com --project=YOUR_PROJECT_ID
   ```

2. Grant the correct IAM role to your service account (see above)

3. Verify the service account key is for the correct project

### Model Not Found

**Error**: `Model not found` or `404 Not Found`

**Solutions**:
- Verify the model ID is correct
- Some models are only available in specific regions
- Check the model is available in your region using GCP Console

### Quota Exceeded

**Error**: `Resource exhausted` or `429 Too Many Requests`

**Solutions**:
- Wait before retrying (rate limits)
- Request a quota increase in GCP Console
- Use a different model with higher quota
- Spread tests over time

## Cost Considerations

**Warning**: These integration tests make real API calls to Google Vertex AI and **will incur costs**.

### Approximate Costs (as of 2025)

- **Gemini 2.5 Flash**: ~$0.075 per 1M input tokens, ~$0.30 per 1M output tokens
- **Gemini 3 Pro**: Higher pricing tier

### Cost Optimization Tips

1. **Run tests sparingly** - Only run when needed, not on every build
2. **Use smaller models** - Default is `gemini-2.5-flash` which is cost-effective
3. **Limit output tokens** - Tests use `maxOutputTokens=512` or less
4. **Use test projects** - Create a separate GCP project for testing
5. **Monitor usage** - Check GCP Console for usage and costs

### Estimated Test Run Cost

A single test run (5 test methods) typically costs:
- Input: ~500 tokens (~$0.00004)
- Output: ~2000 tokens (~$0.0006)
- **Total per run**: ~$0.0007 (less than 1 cent)

## More Information

- [Vertex AI Documentation](https://cloud.google.com/vertex-ai/docs)
- [Vertex AI Generative AI](https://cloud.google.com/vertex-ai/generative-ai/docs)
- [Service Account Authentication](https://cloud.google.com/docs/authentication/production)
- [Vertex AI Pricing](https://cloud.google.com/vertex-ai/pricing)
- [Vertex AI Quotas](https://cloud.google.com/vertex-ai/docs/quotas)
