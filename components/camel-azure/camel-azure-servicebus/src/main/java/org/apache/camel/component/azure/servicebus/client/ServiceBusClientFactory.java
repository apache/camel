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
package org.apache.camel.component.azure.servicebus.client;

public final class ServiceBusClientFactory {

    private static final String SERVICE_URI_SEGMENT = "servicebus.windows.net";
    private static final String BLOB_SERVICE_URI_SEGMENT = ".blob.core.windows.net";

    private ServiceBusClientFactory() {
    }

    /*
    public static EventHubProducerAsyncClient createEventHubProducerAsyncClient(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        return new EventHubClientBuilder()
                .connectionString(buildConnectionString(configuration))
                .transportType(configuration.getAmqpTransportType())
                .retry(configuration.getAmqpRetryOptions())
                .buildAsyncProducerClient();
    }
    
    public static EventHubConsumerAsyncClient createEventHubConsumerAsyncClient(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        return new EventHubClientBuilder()
                .connectionString(buildConnectionString(configuration))
                .consumerGroup(configuration.getConsumerGroupName())
                .prefetchCount(configuration.getPrefetchCount())
                .transportType(configuration.getAmqpTransportType())
                .retry(configuration.getAmqpRetryOptions())
                .buildAsyncConsumerClient();
    }
    
    public static EventProcessorClient createEventProcessorClient(
            final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration, final Consumer<EventContext> processEvent,
            final Consumer<ErrorContext> processError) {
        return new EventProcessorClientBuilder()
                .initialPartitionEventPosition(configuration.getEventPosition())
                .connectionString(buildConnectionString(configuration))
                .checkpointStore(createCheckpointStore(configuration))
                .consumerGroup(configuration.getConsumerGroupName())
                .retry(configuration.getAmqpRetryOptions())
                .transportType(configuration.getAmqpTransportType())
                .processError(processError)
                .processEvent(processEvent)
                .buildEventProcessorClient();
    
    }
    
    // public for testing purposes
    public static BlobContainerAsyncClient createBlobContainerClient(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        return new BlobContainerClientBuilder()
                .endpoint(buildAzureEndpointUri(configuration))
                .containerName(configuration.getBlobContainerName())
                .credential(getCredentialForClient(configuration))
                .buildAsyncClient();
    }
    
    private static CheckpointStore createCheckpointStore(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        if (ObjectHelper.isNotEmpty(configuration.getCheckpointStore())) {
            return configuration.getCheckpointStore();
        }
        // so we have no checkpoint store, we fallback to default BlobCheckpointStore
        // first we check if we have all required params for BlobCheckpointStore
        if (ObjectHelper.isEmpty(configuration.getBlobContainerName())
                || !isCredentialsSet(configuration)) {
            throw new IllegalArgumentException(
                    "Since there is no provided CheckpointStore, you will need to set blobAccountName, blobAccessName"
                                               + " or blobContainerName in order to use the default BlobCheckpointStore");
        }
    
        // second build the BlobContainerAsyncClient
        return new BlobCheckpointStore(createBlobContainerClient(configuration));
    }
    
    private static boolean isCredentialsSet(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        if (ObjectHelper.isNotEmpty(configuration.getBlobStorageSharedKeyCredential())) {
            return true;
        }
    
        return ObjectHelper.isNotEmpty(configuration.getBlobAccessKey())
                && ObjectHelper.isNotEmpty(configuration.getBlobAccountName());
    }
    
    private static String buildConnectionString(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        if (ObjectHelper.isNotEmpty(configuration.getConnectionString())) {
            return configuration.getConnectionString();
        }
    
        return String.format(Locale.ROOT, "Endpoint=sb://%s.%s/;SharedAccessKeyName=%s;SharedAccessKey=%s;EntityPath=%s",
                configuration.getNamespace(), SERVICE_URI_SEGMENT, configuration.getSharedAccessName(),
                configuration.getSharedAccessKey(),
                configuration.getEventHubName());
    }
    
    private static String buildAzureEndpointUri(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        return String.format(Locale.ROOT, "https://%s" + BLOB_SERVICE_URI_SEGMENT, getAccountName(configuration));
    }
    
    private static StorageSharedKeyCredential getCredentialForClient(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        final StorageSharedKeyCredential storageSharedKeyCredential = configuration.getBlobStorageSharedKeyCredential();
    
        if (storageSharedKeyCredential != null) {
            return storageSharedKeyCredential;
        }
    
        return new StorageSharedKeyCredential(configuration.getBlobAccountName(), configuration.getBlobAccessKey());
    }
    
    private static String getAccountName(final org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration) {
        return ObjectHelper.isNotEmpty(configuration.getBlobStorageSharedKeyCredential())
                ? configuration.getBlobStorageSharedKeyCredential().getAccountName()
                : configuration.getBlobAccountName();
    }
     */
}
