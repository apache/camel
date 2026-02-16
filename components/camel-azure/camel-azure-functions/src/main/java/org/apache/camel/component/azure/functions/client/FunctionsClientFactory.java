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
package org.apache.camel.component.azure.functions.client;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.appservice.AppServiceManager;
import org.apache.camel.component.azure.functions.CredentialType;
import org.apache.camel.component.azure.functions.FunctionsConfiguration;
import org.apache.camel.util.ObjectHelper;

/**
 * Factory for creating Azure Functions clients.
 */
public class FunctionsClientFactory {

    /**
     * Creates an AppServiceManager for management operations.
     *
     * @param  configuration the component configuration
     * @return               an AppServiceManager instance
     */
    public AppServiceManager createAppServiceManager(FunctionsConfiguration configuration) {
        // Check if a pre-configured manager is provided
        if (configuration.getAppServiceManager() != null) {
            return configuration.getAppServiceManager();
        }

        TokenCredential credential = resolveCredential(configuration);

        AzureProfile profile = new AzureProfile(
                configuration.getTenantId(),
                configuration.getSubscriptionId(),
                AzureEnvironment.AZURE);

        return AppServiceManager
                .authenticate(credential, profile);
    }

    /**
     * Creates an HTTP invocation client for invoking functions.
     *
     * @param  configuration the component configuration
     * @return               a FunctionsInvocationClient instance
     */
    public FunctionsInvocationClient createInvocationClient(FunctionsConfiguration configuration) {
        return new FunctionsInvocationClient(
                configuration.getFunctionKey(),
                configuration.getHostKey(),
                configuration.getConnectionTimeout(),
                configuration.getReadTimeout());
    }

    /**
     * Resolves the TokenCredential based on configuration.
     */
    private TokenCredential resolveCredential(FunctionsConfiguration configuration) {
        CredentialType credentialType = configuration.getCredentialType();
        if (credentialType == null) {
            credentialType = CredentialType.AZURE_IDENTITY;
        }

        switch (credentialType) {
            case AZURE_IDENTITY:
                return buildDefaultCredential(configuration);
            case TOKEN_CREDENTIAL:
                if (configuration.getTokenCredential() == null) {
                    throw new IllegalArgumentException(
                            "TokenCredential must be provided when using TOKEN_CREDENTIAL type");
                }
                return configuration.getTokenCredential();
            case FUNCTION_KEY:
                // For FUNCTION_KEY, we use Azure Identity for management operations
                // and the key for invocation
                return buildDefaultCredential(configuration);
            default:
                return buildDefaultCredential(configuration);
        }
    }

    private TokenCredential buildDefaultCredential(FunctionsConfiguration configuration) {
        // If client credentials are provided, use ClientSecretCredential
        if (ObjectHelper.isNotEmpty(configuration.getClientId())
                && ObjectHelper.isNotEmpty(configuration.getClientSecret())
                && ObjectHelper.isNotEmpty(configuration.getTenantId())) {
            return new ClientSecretCredentialBuilder()
                    .clientId(configuration.getClientId())
                    .clientSecret(configuration.getClientSecret())
                    .tenantId(configuration.getTenantId())
                    .build();
        }

        // Otherwise use DefaultAzureCredential which tries multiple auth methods
        return new DefaultAzureCredentialBuilder().build();
    }
}
