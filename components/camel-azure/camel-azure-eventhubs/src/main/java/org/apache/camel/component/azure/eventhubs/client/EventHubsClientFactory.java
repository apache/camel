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
package org.apache.camel.component.azure.eventhubs.client;

import java.util.Locale;
import java.util.function.Consumer;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.component.azure.eventhubs.CredentialType;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.util.ObjectHelper;

public final class EventHubsClientFactory {

    private static final String SERVICE_URI_SEGMENT = "servicebus.windows.net";
    private static final String BLOB_SERVICE_URI_SEGMENT = ".blob.core.windows.net";

    private EventHubsClientFactory() {
    }

    public static EventHubProducerAsyncClient createEventHubProducerAsyncClient(final EventHubsConfiguration configuration) {
        EventHubClientBuilder eventHubClientBuilder = new EventHubClientBuilder()
                .transportType(configuration.getAmqpTransportType())
                .retryOptions(configuration.getAmqpRetryOptions());

        if (configuration.getCredentialType().equals(CredentialType.CONNECTION_STRING)) {
            return eventHubClientBuilder
                    .connectionString(buildConnectionString(configuration))
                    .buildAsyncProducerClient();
        } else if (configuration.getCredentialType().equals(CredentialType.TOKEN_CREDENTIAL)) {

            checkTokenCredentialConfiguration(configuration);
            return eventHubClientBuilder
                    .fullyQualifiedNamespace(getFullyQualifiedNamespace(configuration))
                    .eventHubName(configuration.getEventHubName())
                    .credential(configuration.getTokenCredential())
                    .buildAsyncProducerClient();
        }
        return eventHubClientBuilder
                .fullyQualifiedNamespace(getFullyQualifiedNamespace(configuration))
                .eventHubName(configuration.getEventHubName())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncProducerClient();
    }

    public static EventHubConsumerAsyncClient createEventHubConsumerAsyncClient(final EventHubsConfiguration configuration) {
        EventHubClientBuilder eventHubClientBuilder = new EventHubClientBuilder()
                .consumerGroup(configuration.getConsumerGroupName())
                .prefetchCount(configuration.getPrefetchCount())
                .transportType(configuration.getAmqpTransportType())
                .retryOptions(configuration.getAmqpRetryOptions());

        if (configuration.getCredentialType().equals(CredentialType.CONNECTION_STRING)) {
            return eventHubClientBuilder
                    .connectionString(buildConnectionString(configuration))
                    .buildAsyncConsumerClient();
        } else if (configuration.getCredentialType().equals(CredentialType.TOKEN_CREDENTIAL)) {

            checkTokenCredentialConfiguration(configuration);
            return eventHubClientBuilder
                    .fullyQualifiedNamespace(getFullyQualifiedNamespace(configuration))
                    .eventHubName(configuration.getEventHubName())
                    .credential(configuration.getTokenCredential())
                    .buildAsyncConsumerClient();
        }
        return eventHubClientBuilder
                .fullyQualifiedNamespace(getFullyQualifiedNamespace(configuration))
                .eventHubName(configuration.getEventHubName())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncConsumerClient();
    }

    public static EventProcessorClient createEventProcessorClient(
            final EventHubsConfiguration configuration, final Consumer<EventContext> processEvent,
            final Consumer<ErrorContext> processError) {
        EventProcessorClientBuilder eventProcessorClientBuilder = new EventProcessorClientBuilder()
                .initialPartitionEventPosition(configuration.getEventPosition())
                .checkpointStore(createCheckpointStore(configuration))
                .consumerGroup(configuration.getConsumerGroupName())
                .retryOptions(configuration.getAmqpRetryOptions())
                .transportType(configuration.getAmqpTransportType())
                .processError(processError)
                .processEvent(processEvent);

        if (configuration.getCredentialType().equals(CredentialType.CONNECTION_STRING)) {
            return eventProcessorClientBuilder
                    .connectionString(buildConnectionString(configuration))
                    .buildEventProcessorClient();
        } else if (configuration.getCredentialType().equals(CredentialType.TOKEN_CREDENTIAL)) {

            checkTokenCredentialConfiguration(configuration);
            return eventProcessorClientBuilder
                    .fullyQualifiedNamespace(getFullyQualifiedNamespace(configuration))
                    .eventHubName(configuration.getEventHubName())
                    .credential(configuration.getTokenCredential())
                    .buildEventProcessorClient();
        }
        return eventProcessorClientBuilder
                .fullyQualifiedNamespace(getFullyQualifiedNamespace(configuration))
                .eventHubName(configuration.getEventHubName())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildEventProcessorClient();
    }

    // public for testing purposes
    public static BlobContainerAsyncClient createBlobContainerClient(final EventHubsConfiguration configuration) {
        return new BlobContainerClientBuilder()
                .endpoint(buildAzureEndpointUri(configuration))
                .containerName(configuration.getBlobContainerName())
                .credential(getCredentialForClient(configuration))
                .buildAsyncClient();
    }

    private static void checkTokenCredentialConfiguration(final EventHubsConfiguration configuration) {
        if (ObjectHelper.isEmpty(configuration.getNamespace()) || ObjectHelper.isEmpty(configuration.getEventHubName())) {
            throw new IllegalArgumentException("EventHub's namespace and name is required for the Azure-AD authentication");
        }
    }

    private static CheckpointStore createCheckpointStore(final EventHubsConfiguration configuration) {
        if (ObjectHelper.isNotEmpty(configuration.getCheckpointStore())) {
            return configuration.getCheckpointStore();
        }
        // so we have no checkpoint store, we fallback to default BlobCheckpointStore
        // first we check if we have all required params for BlobCheckpointStore
        if (ObjectHelper.isEmpty(configuration.getBlobContainerName()) || !isCredentialsSet(configuration)) {
            throw new IllegalArgumentException(
                    "Since there is no provided CheckpointStore, you will need to set blobAccountName, blobAccessName"
                                               + " or blobContainerName in order to use the default BlobCheckpointStore");
        }

        // second build the BlobContainerAsyncClient
        return new BlobCheckpointStore(createBlobContainerClient(configuration));
    }

    private static boolean isCredentialsSet(final EventHubsConfiguration configuration) {
        if (ObjectHelper.isNotEmpty(configuration.getBlobStorageSharedKeyCredential())) {
            return true;
        }

        return ObjectHelper.isNotEmpty(configuration.getBlobAccessKey())
                && ObjectHelper.isNotEmpty(configuration.getBlobAccountName());
    }

    private static String buildConnectionString(final EventHubsConfiguration configuration) {
        if (ObjectHelper.isNotEmpty(configuration.getConnectionString())) {
            return configuration.getConnectionString();
        }

        return String.format(Locale.ROOT, "Endpoint=sb://%s.%s/;SharedAccessKeyName=%s;SharedAccessKey=%s;EntityPath=%s",
                configuration.getNamespace(), SERVICE_URI_SEGMENT, configuration.getSharedAccessName(),
                configuration.getSharedAccessKey(),
                configuration.getEventHubName());
    }

    private static String buildAzureEndpointUri(final EventHubsConfiguration configuration) {
        return String.format(Locale.ROOT, "https://%s" + BLOB_SERVICE_URI_SEGMENT, getAccountName(configuration));
    }

    private static StorageSharedKeyCredential getCredentialForClient(final EventHubsConfiguration configuration) {
        final StorageSharedKeyCredential storageSharedKeyCredential = configuration.getBlobStorageSharedKeyCredential();

        if (storageSharedKeyCredential != null) {
            return storageSharedKeyCredential;
        }

        return new StorageSharedKeyCredential(configuration.getBlobAccountName(), configuration.getBlobAccessKey());
    }

    private static String getAccountName(final EventHubsConfiguration configuration) {
        return ObjectHelper.isNotEmpty(configuration.getBlobStorageSharedKeyCredential())
                ? configuration.getBlobStorageSharedKeyCredential().getAccountName()
                : configuration.getBlobAccountName();
    }

    private static String getFullyQualifiedNamespace(EventHubsConfiguration configuration) {
        return configuration.getNamespace() + "." + SERVICE_URI_SEGMENT;
    }
}
