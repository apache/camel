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
package org.apache.camel.component.azure.storage.blob.client;

import com.azure.core.credential.AzureSasCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;

import static java.lang.String.format;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.Set.of;
import static org.apache.camel.component.azure.storage.blob.CredentialType.*;
import static org.apache.camel.util.ObjectHelper.isEmpty;

public final class BlobClientFactory {

    private static final String SERVICE_URI_SEGMENT = ".blob.core.windows.net";

    private BlobClientFactory() {
    }

    public static BlobServiceClient createBlobServiceClient(final BlobConfiguration configuration) {
        BlobServiceClientBuilder blobServiceClientBuilder
                = new BlobServiceClientBuilder().endpoint(buildAzureEndpointUri(configuration));

        if (of(SHARED_KEY_CREDENTIAL, SHARED_ACCOUNT_KEY).contains(configuration.getCredentialType())) {
            blobServiceClientBuilder.credential(getSharedKeyCredential(configuration));
        } else if (AZURE_SAS.equals(configuration.getCredentialType())) {
            blobServiceClientBuilder.credential(getAzureSasCredential(configuration));
        } else {
            blobServiceClientBuilder.credential(new DefaultAzureCredentialBuilder().build());
        }
        return blobServiceClientBuilder.buildClient();
    }

    private static String buildAzureEndpointUri(final BlobConfiguration configuration) {
        return format(ROOT, "https://%s" + SERVICE_URI_SEGMENT, getAccountName(configuration));
    }

    private static StorageSharedKeyCredential getSharedKeyCredential(final BlobConfiguration configuration) {
        return ofNullable(configuration.getCredentials())
                .orElseGet(() -> new StorageSharedKeyCredential(configuration.getAccountName(), configuration.getAccessKey()));
    }

    private static String getAccountName(final BlobConfiguration configuration) {
        return !isEmpty(configuration.getCredentials())
                ? configuration.getCredentials().getAccountName() : configuration.getAccountName();
    }

    private static AzureSasCredential getAzureSasCredential(final BlobConfiguration configuration) {
        return new AzureSasCredential(configuration.getSasToken());
    }
}
