/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.azure.functions;

import com.azure.core.credential.TokenCredential;
import com.azure.resourcemanager.appservice.AppServiceManager;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Configuration for Azure Functions component.
 */
@UriParams
public class FunctionsConfiguration implements Cloneable {

    // URI Path parameters
    @UriPath(description = "The Azure Function App name")
    @Metadata(required = true)
    private String functionApp;

    @UriPath(description = "The function name within the app (required for invokeFunction operation)")
    private String functionName;

    // Security parameters
    @UriParam(label = "security", secret = true,
              description = "The Azure subscription ID (required for management operations)")
    private String subscriptionId;

    @UriParam(label = "security",
              description = "The resource group name containing the function app (required for management operations)")
    private String resourceGroup;

    @UriParam(label = "security", secret = true,
              description = "The function key for direct HTTP invocation")
    private String functionKey;

    @UriParam(label = "security", secret = true,
              description = "The host key for the function app (used if function key is not provided)")
    private String hostKey;

    @UriParam(label = "security", secret = true,
              description = "Azure AD Client ID for service principal authentication")
    private String clientId;

    @UriParam(label = "security", secret = true,
              description = "Azure AD Client Secret for service principal authentication")
    private String clientSecret;

    @UriParam(label = "security",
              description = "Azure AD Tenant ID")
    private String tenantId;

    @UriParam(label = "security",
              description = "A TokenCredential instance for Azure AD authentication")
    @Metadata(autowired = true)
    private TokenCredential tokenCredential;

    @UriParam(label = "security",
              enums = "AZURE_IDENTITY,FUNCTION_KEY,TOKEN_CREDENTIAL",
              defaultValue = "AZURE_IDENTITY",
              description = "Determines the credential strategy to adopt")
    private CredentialType credentialType = CredentialType.AZURE_IDENTITY;

    // Producer parameters
    @UriParam(label = "producer", defaultValue = "invokeFunction",
              description = "The operation to be performed")
    private FunctionsOperations operation = FunctionsOperations.invokeFunction;

    @UriParam(label = "producer", defaultValue = "POST",
              description = "HTTP method for function invocation (GET, POST, PUT, DELETE, etc.)")
    private String httpMethod = "POST";

    @UriParam(label = "producer", defaultValue = "30000",
              description = "Connection timeout in milliseconds for HTTP invocation")
    private int connectionTimeout = 30000;

    @UriParam(label = "producer", defaultValue = "60000",
              description = "Read timeout in milliseconds for HTTP invocation")
    private int readTimeout = 60000;

    // Function app creation parameters
    @UriParam(label = "producer",
              description = "Azure region for creating function app (e.g., eastus, westeurope)")
    private String location;

    @UriParam(label = "producer",
              description = "Runtime stack (java, node, python, dotnet)")
    private String runtime;

    @UriParam(label = "producer",
              description = "Runtime version")
    private String runtimeVersion;

    @UriParam(label = "producer", secret = true,
              description = "Storage account connection string for function app")
    private String storageAccountConnectionString;

    // Advanced parameters
    @UriParam(label = "advanced",
              description = "An AppServiceManager instance for management operations")
    @Metadata(autowired = true)
    private AppServiceManager appServiceManager;

    // Getters and setters

    public String getFunctionApp() {
        return functionApp;
    }

    public void setFunctionApp(String functionApp) {
        this.functionApp = functionApp;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public String getFunctionKey() {
        return functionKey;
    }

    public void setFunctionKey(String functionKey) {
        this.functionKey = functionKey;
    }

    public String getHostKey() {
        return hostKey;
    }

    public void setHostKey(String hostKey) {
        this.hostKey = hostKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public TokenCredential getTokenCredential() {
        return tokenCredential;
    }

    public void setTokenCredential(TokenCredential tokenCredential) {
        this.tokenCredential = tokenCredential;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    public FunctionsOperations getOperation() {
        return operation;
    }

    public void setOperation(FunctionsOperations operation) {
        this.operation = operation;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getStorageAccountConnectionString() {
        return storageAccountConnectionString;
    }

    public void setStorageAccountConnectionString(String storageAccountConnectionString) {
        this.storageAccountConnectionString = storageAccountConnectionString;
    }

    public AppServiceManager getAppServiceManager() {
        return appServiceManager;
    }

    public void setAppServiceManager(AppServiceManager appServiceManager) {
        this.appServiceManager = appServiceManager;
    }

    /**
     * Creates a copy of this configuration.
     */
    public FunctionsConfiguration copy() {
        try {
            return (FunctionsConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
