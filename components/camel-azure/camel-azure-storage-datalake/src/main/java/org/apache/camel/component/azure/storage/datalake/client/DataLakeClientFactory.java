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

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import org.apache.camel.component.azure.storage.datalake.DataLakeConfiguration;
import org.apache.camel.util.ObjectHelper;

public final class DataLakeClientFactory {
    private static final String SERVICE_URI_SEGMENT = ".dfs.core.windows.net";

    private DataLakeClientFactory() {
    }

    public static DataLakeServiceClient createDataLakeServiceClient(final DataLakeConfiguration configuration) {
        final DataLakeServiceClient client;
        if (configuration.getServiceClient() != null) {
            client = configuration.getServiceClient();
        } else if (configuration.getAccountKey() != null || configuration.getSharedKeyCredential() != null) {
            client = createDataLakeServiceClientWithSharedKey(configuration);
        } else {
            client = createDataLakeServiceClientWithClientSecret(configuration);
        }
        return client;
    }

    private static DataLakeServiceClient createDataLakeServiceClientWithSharedKey(final DataLakeConfiguration configuration) {
        StorageSharedKeyCredential sharedKeyCredential = configuration.getSharedKeyCredential();
        if (sharedKeyCredential == null) {
            sharedKeyCredential = new StorageSharedKeyCredential(configuration.getAccountName(), configuration.getAccountKey());
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
            clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId(configuration.getClientId())
                    .clientSecret(configuration.getClientSecret())
                    .tenantId(configuration.getTenantId())
                    .build();
        }

        return new DataLakeServiceClientBuilder()
                .credential(clientSecretCredential)
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
