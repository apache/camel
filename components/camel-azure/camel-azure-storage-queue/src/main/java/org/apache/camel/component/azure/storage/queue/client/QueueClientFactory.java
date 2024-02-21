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
package org.apache.camel.component.azure.storage.queue.client;

import java.util.Locale;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.QueueServiceClientBuilder;
import org.apache.camel.component.azure.storage.queue.CredentialType;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.util.ObjectHelper;

public final class QueueClientFactory {

    private static final String SERVICE_URI_SEGMENT = ".queue.core.windows.net";

    private QueueClientFactory() {
    }

    public static QueueServiceClient createQueueServiceClient(final QueueConfiguration configuration) {
        if (configuration.getCredentialType().equals(CredentialType.SHARED_KEY_CREDENTIAL)
                || configuration.getCredentialType().equals(CredentialType.SHARED_ACCOUNT_KEY)) {
            return new QueueServiceClientBuilder()
                    .endpoint(buildAzureEndpointUri(configuration))
                    .credential(getCredentialForClient(configuration))
                    .buildClient();
        } else {
            return new QueueServiceClientBuilder()
                    .endpoint(buildAzureEndpointUri(configuration))
                    .credential(new DefaultAzureCredentialBuilder().build())
                    .buildClient();
        }
    }

    private static String buildAzureEndpointUri(final QueueConfiguration configuration) {
        return String.format(Locale.ROOT, "https://%s" + SERVICE_URI_SEGMENT, getAccountName(configuration));
    }

    private static StorageSharedKeyCredential getCredentialForClient(final QueueConfiguration configuration) {
        final StorageSharedKeyCredential storageSharedKeyCredential = configuration.getCredentials();

        if (storageSharedKeyCredential != null) {
            return storageSharedKeyCredential;
        }

        return new StorageSharedKeyCredential(configuration.getAccountName(), configuration.getAccessKey());
    }

    private static String getAccountName(final QueueConfiguration configuration) {
        return !ObjectHelper.isEmpty(configuration.getCredentials())
                ? configuration.getCredentials().getAccountName() : configuration.getAccountName();
    }
}
