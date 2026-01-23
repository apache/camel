# Camel IBM Watsonx AI Integration tests

The following properties are needed to execute all the integration tests of this component:

* `camel.ibm.watsonx.ai.apiKey` - IBM Cloud API key. Generate one at **Manage > Access (IAM) > API keys** in the IBM Cloud console.
* `camel.ibm.watsonx.ai.projectId` - Watsonx project ID. Found in your project's **Manage > General > Details** section.
* `camel.ibm.watsonx.ai.baseUrl` - Watsonx.ai API endpoint (e.g., `https://eu-de.ml.cloud.ibm.com`). Depends on your provisioned region.
* `camel.ibm.watsonx.ai.deploymentId` - ID of a deployed model. Found in the **Deployments** section of your project or space.
* `camel.ibm.watsonx.ai.wxUrl` - Watsonx platform URL (e.g., `https://api.eu-de.dataplatform.cloud.ibm.com/wx`). Depends on your region. Refer to the API spec for more information https://cloud.ibm.com/apidocs/watsonx-ai 
* `camel.ibm.watsonx.ai.cosUrl` - Cloud Object Storage endpoint (e.g., `https://s3.eu-de.cloud-object-storage.appdomain.cloud`). Found in your COS instance under **Endpoints**.
* `camel.ibm.watsonx.ai.documentConnectionId` - Connection asset ID for COS. Create a connection in your project under **Assets > Connections**, then view its details.
* `camel.ibm.watsonx.ai.documentBucket` - COS bucket name containing input documents.
* `camel.ibm.watsonx.ai.resultConnectionId` - Connection asset ID for results storage. Can be the same as `documentConnectionId`.
* `camel.ibm.watsonx.ai.resultBucket` - COS bucket name for output results. Can be the same as `documentBucket`.
* `camel.ibm.watsonx.ai.spaceId` - Deployment space ID. Found in your space's **Manage > General** section.

For example:

```bash
mvn clean verify \
  -Dcamel.ibm.watsonx.ai.apiKey=xyz \
  -Dcamel.ibm.watsonx.ai.projectId=xyz \
  -Dcamel.ibm.watsonx.ai.baseUrl=https://eu-de.ml.cloud.ibm.com \
  -Dcamel.ibm.watsonx.ai.deploymentId=xyz \
  -Dcamel.ibm.watsonx.ai.wxUrl=https://api.eu-de.dataplatform.cloud.ibm.com/wx \
  -Dcamel.ibm.watsonx.ai.cosUrl=https://s3.eu-de.cloud-object-storage.appdomain.cloud \
  -Dcamel.ibm.watsonx.ai.documentConnectionId=xyz \
  -Dcamel.ibm.watsonx.ai.documentBucket=xyz \
  -Dcamel.ibm.watsonx.ai.resultConnectionId=xyz \
  -Dcamel.ibm.watsonx.ai.resultBucket=xyz \
  -Dcamel.ibm.watsonx.ai.spaceId=xyz
```