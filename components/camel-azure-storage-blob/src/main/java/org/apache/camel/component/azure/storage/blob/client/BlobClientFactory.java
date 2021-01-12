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

import java.util.Locale;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.component.azure.storage.blob.BlobConfiguration;
import org.apache.camel.util.ObjectHelper;

public final class BlobClientFactory {

    private static final String SERVICE_URI_SEGMENT = ".blob.core.windows.net";

    private BlobClientFactory() {
    }

    public static BlobServiceClient createBlobServiceClient(final BlobConfiguration configuration) {
        return new BlobServiceClientBuilder()
                .endpoint(buildAzureEndpointUri(configuration))
                .credential(getCredentialForClient(configuration))
                .buildClient();
    }

    private static String buildAzureEndpointUri(final BlobConfiguration configuration) {
        return String.format(Locale.ROOT, "https://%s" + SERVICE_URI_SEGMENT, getAccountName(configuration));
    }

    private static StorageSharedKeyCredential getCredentialForClient(final BlobConfiguration configuration) {
        final StorageSharedKeyCredential storageSharedKeyCredential = configuration.getCredentials();

        if (storageSharedKeyCredential != null) {
            return storageSharedKeyCredential;
        }

        return new StorageSharedKeyCredential(configuration.getAccountName(), configuration.getAccessKey());
    }

    private static String getAccountName(final BlobConfiguration configuration) {
        return !ObjectHelper.isEmpty(configuration.getCredentials())
                ? configuration.getCredentials().getAccountName() : configuration.getAccountName();
    }

}
