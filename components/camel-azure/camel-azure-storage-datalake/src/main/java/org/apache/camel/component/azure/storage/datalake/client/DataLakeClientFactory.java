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
package org.apache.camel.component.azure.storage.datalake.client;

import java.util.Locale;

import com.azure.core.credential.AzureSasCredential;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import org.apache.camel.component.azure.storage.datalake.CredentialType;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DataLakeClientFactory {
    private static final String SERVICE_URI_SEGMENT = ".dfs.core.windows.net";

    private static final Logger LOG = LoggerFactory.getLogger(DataLakeClientFactory.class);

    private DataLakeClientFactory() {
    }

    public static DataLakeServiceClient createDataLakeServiceClient(final DataLakeConfiguration configuration) {
        DataLakeServiceClient client = null;
        if (configuration.getServiceClient() != null
                && configuration.getCredentialType().equals(CredentialType.SERVICE_CLIENT_INSTANCE)) {
            LOG.trace("Using configured service client instance");
            client = configuration.getServiceClient();
        } else if (configuration.getCredentialType().equals(CredentialType.AZURE_IDENTITY)) {
            client = createDataLakeServiceClientWithDefaultIdentity(configuration);
        } else if (configuration.getCredentialType().equals(CredentialType.SHARED_KEY_CREDENTIAL)) {
            if (configuration.getAccountKey() != null || configuration.getSharedKeyCredential() != null) {
                client = createDataLakeServiceClientWithSharedKey(configuration);
            }
        } else if (configuration.getCredentialType().equals(CredentialType.AZURE_SAS)) {
            if (configuration.getSasSignature() != null || configuration.getSasCredential() != null) {
                client = createDataLakeServiceClientWithSas(configuration);
            }
        } else {
            if (configuration.getCredentialType().equals(CredentialType.CLIENT_SECRET)) {
                client = createDataLakeServiceClientWithClientSecret(configuration);
            }
        }

        return client;
    }

    private static DataLakeServiceClient createDataLakeServiceClientWithSharedKey(final DataLakeConfiguration configuration) {
        StorageSharedKeyCredential sharedKeyCredential = configuration.getSharedKeyCredential();
        if (sharedKeyCredential == null) {
            LOG.trace("Using account name and account key to instantiate service client");
            sharedKeyCredential = new StorageSharedKeyCredential(configuration.getAccountName(), configuration.getAccountKey());
        } else {
            LOG.trace("Using configured shared key instance to instantiate service client");
        }

        return new DataLakeServiceClientBuilder()
                .credential(sharedKeyCredential)
                .endpoint(buildAzureUri(configuration))
                .buildClient();
    }

    private static DataLakeServiceClient createDataLakeServiceClientWithClientSecret(
            final DataLakeConfiguration configuration) {
        ClientSecretCredential clientSecretCredential = configuration.getClientSecretCredential();
        if (clientSecretCredential == null) {
            LOG.trace("Using client id, client secret, tenant id to instantiate service client");
            clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId(configuration.getClientId())
                    .clientSecret(configuration.getClientSecret())
                    .tenantId(configuration.getTenantId())
                    .build();
        } else {
            LOG.trace("Using configured client secret instance to instantiate service client");
        }

        return new DataLakeServiceClientBuilder()
                .credential(clientSecretCredential)
                .endpoint(buildAzureUri(configuration))
                .buildClient();
    }

    private static DataLakeServiceClient createDataLakeServiceClientWithSas(
            final DataLakeConfiguration configuration) {
        AzureSasCredential sasCredential = configuration.getSasCredential();
        if (sasCredential == null) {
            LOG.trace("Using SAS signature to instantiate service client");
            sasCredential = new AzureSasCredential(configuration.getSasSignature());
        } else {
            LOG.trace("Using configured SAS instance to instantiate service client");
        }

        return new DataLakeServiceClientBuilder()
                .credential(sasCredential)
                .endpoint(buildAzureUri(configuration))
                .buildClient();
    }

    private static DataLakeServiceClient createDataLakeServiceClientWithDefaultIdentity(
            final DataLakeConfiguration configuration) {
        LOG.trace("Using default identity to instantiate service client");
        final DefaultAzureCredentialBuilder defaultAzureCredentialBuilder = new DefaultAzureCredentialBuilder();
        if (configuration.getTenantId() != null) {
            defaultAzureCredentialBuilder.tenantId(configuration.getTenantId());
        }
        return new DataLakeServiceClientBuilder()
                .credential(defaultAzureCredentialBuilder.build())
                .endpoint(buildAzureUri(configuration))
                .buildClient();
    }

    private static String buildAzureUri(final DataLakeConfiguration configuration) {
        return String.format(Locale.ROOT, "https://%s" + SERVICE_URI_SEGMENT, getAccountName(configuration));
    }

    private static String getAccountName(final DataLakeConfiguration configuration) {
        String accountName;
        if (ObjectHelper.isNotEmpty(configuration.getSharedKeyCredential())) {
            accountName = configuration.getSharedKeyCredential().getAccountName();
        } else {
            accountName = configuration.getAccountName();
        }
        return accountName;
    }
}
