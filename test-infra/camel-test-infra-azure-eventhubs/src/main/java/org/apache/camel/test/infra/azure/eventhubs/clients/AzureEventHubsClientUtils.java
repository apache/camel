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

package org.apache.camel.test.infra.azure.eventhubs.clients;

import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.test.infra.azure.eventhubs.services.AzureEventHubsInfraService;

public final class AzureEventHubsClientUtils {

    private AzureEventHubsClientUtils() {

    }

    /**
     * A client of a Blob container in the storage account of the service, for example for the
     * {@code BlobCheckpointStore} of an event processor. A camel-azure-eventhubs consumer builds the Blob client of its
     * checkpoint store for the endpoint of an account in Azure only, so a local service needs a checkpoint store built
     * on this client.
     */
    public static BlobContainerAsyncClient createBlobContainerClient(AzureEventHubsInfraService service, String containerName) {
        return new BlobContainerClientBuilder()
                .endpoint(service.blobEndpoint())
                .containerName(containerName)
                .credential(new StorageSharedKeyCredential(service.blobAccountName(), service.blobAccessKey()))
                .buildAsyncClient();
    }
}
