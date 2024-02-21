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
package org.apache.camel.component.azure.eventhubs;

import java.util.HashMap;
import java.util.Map;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.credential.TokenCredential;
import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.azure.eventhubs.CredentialType.CONNECTION_STRING;

@UriParams
public class EventHubsConfiguration implements Cloneable {

    @UriPath
    private String namespace;
    @UriPath
    private String eventHubName;
    @UriParam(label = "security")
    private String sharedAccessName;
    @UriParam(label = "security", secret = true)
    private String sharedAccessKey;
    @UriParam(label = "security", secret = true)
    private String connectionString;
    @UriParam(label = "security", secret = true)
    private TokenCredential tokenCredential;
    @UriParam(label = "common", defaultValue = "AMQP")
    private AmqpTransportType amqpTransportType = AmqpTransportType.AMQP;
    @UriParam(label = "common")
    private AmqpRetryOptions amqpRetryOptions;
    @UriParam(label = "consumer", defaultValue = "$Default")
    private String consumerGroupName = "$Default";
    @UriParam(label = "consumer", defaultValue = "500")
    private int prefetchCount = 500;
    @UriParam(label = "consumer", defaultValue = "BlobCheckpointStore")
    private CheckpointStore checkpointStore;
    @UriParam(label = "consumer")
    private String blobAccountName;
    @UriParam(label = "consumer", secret = true)
    private String blobAccessKey;
    @UriParam(label = "consumer")
    private String blobContainerName;
    @UriParam(label = "consumer", secret = true)
    private StorageSharedKeyCredential blobStorageSharedKeyCredential;
    @UriParam(label = "consumer")
    private Map<String, EventPosition> eventPosition = new HashMap<>();
    @UriParam(label = "consumer", defaultValue = "500")
    private int checkpointBatchSize = 500;
    @UriParam(label = "consumer", defaultValue = "5000")
    private int checkpointBatchTimeout = 5000;
    @UriParam(label = "producer")
    @Metadata(autowired = true)
    private EventHubProducerAsyncClient producerAsyncClient;
    @UriParam(label = "producer")
    private String partitionKey;
    @UriParam(label = "producer")
    private String partitionId;
    @UriParam(label = "security", enums = "AZURE_IDENTITY,CONNECTION_STRING,TOKEN_CREDENTIAL",
              defaultValue = "CONNECTION_STRING")
    private CredentialType credentialType = CONNECTION_STRING;

    /**
     * EventHubs namespace created in Azure Portal.
     */
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * EventHubs name under a specific namespace.
     */
    public String getEventHubName() {
        return eventHubName;
    }

    public void setEventHubName(String eventHubName) {
        this.eventHubName = eventHubName;
    }

    /**
     * The name you chose for your EventHubs SAS keys.
     */
    public String getSharedAccessName() {
        return sharedAccessName;
    }

    public void setSharedAccessName(String sharedAccessName) {
        this.sharedAccessName = sharedAccessName;
    }

    /**
     * The generated value for the SharedAccessName.
     */
    public String getSharedAccessKey() {
        return sharedAccessKey;
    }

    public void setSharedAccessKey(String sharedAccessKey) {
        this.sharedAccessKey = sharedAccessKey;
    }

    /**
     * Instead of supplying namespace, sharedAccessKey, sharedAccessName ... etc, you can just supply the connection
     * string for your eventHub. The connection string for EventHubs already include all the necessary information to
     * connection to your EventHub. To learn on how to generate the connection string, take a look at this
     * documentation: https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-get-connection-string
     */
    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * Still another way of authentication (beside supplying namespace, sharedAccessKey, sharedAccessName or connection
     * string) is through Azure-AD authentication using an implementation instance of {@link TokenCredential}.
     */
    public TokenCredential getTokenCredential() {
        return tokenCredential;
    }

    public void setTokenCredential(TokenCredential tokenCredential) {
        this.tokenCredential = tokenCredential;
    }

    /**
     * Sets the transport type by which all the communication with Azure Event Hubs occurs. Default value is
     * {@link AmqpTransportType#AMQP}.
     */
    public AmqpTransportType getAmqpTransportType() {
        return amqpTransportType;
    }

    public void setAmqpTransportType(AmqpTransportType amqpTransportType) {
        this.amqpTransportType = amqpTransportType;
    }

    /**
     * Sets the retry policy for {@link EventHubAsyncClient}. If not specified, the default retry options are used.
     */
    public AmqpRetryOptions getAmqpRetryOptions() {
        return amqpRetryOptions;
    }

    public void setAmqpRetryOptions(AmqpRetryOptions amqpRetryOptions) {
        this.amqpRetryOptions = amqpRetryOptions;
    }

    /**
     * Sets the name of the consumer group this consumer is associated with. Events are read in the context of this
     * group. The name of the consumer group that is created by default is {@code "$Default"}.
     */
    public String getConsumerGroupName() {
        return consumerGroupName;
    }

    public void setConsumerGroupName(String consumerGroupName) {
        this.consumerGroupName = consumerGroupName;
    }

    /**
     * Sets the count used by the receiver to control the number of events the Event Hub consumer will actively receive
     * and queue locally without regard to whether a receive operation is currently active.
     */
    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    /**
     * Sets the {@link EventHubProducerAsyncClient}.An asynchronous producer responsible for transmitting
     * {@link EventData} to a specific Event Hub, grouped together in batches. Depending on the
     * {@link CreateBatchOptions options} specified when creating an {@linkEventDataBatch}, the events may be
     * automatically routed to an available partition or specific to a partition. Use by this component to produce the
     * data in camel producer.
     */
    public EventHubProducerAsyncClient getProducerAsyncClient() {
        return producerAsyncClient;
    }

    public void setProducerAsyncClient(EventHubProducerAsyncClient producerAsyncClient) {
        this.producerAsyncClient = producerAsyncClient;
    }

    /**
     * Sets the identifier of the Event Hub partition that the {@link EventData events} will be sent to. If the
     * identifier is not specified, the Event Hubs service will be responsible for routing events that are sent to an
     * available partition.
     */
    public String getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(String partitionId) {
        this.partitionId = partitionId;
    }

    /**
     * Sets a hashing key to be provided for the batch of events, which instructs the Event Hubs service to map this key
     * to a specific partition.
     *
     * The selection of a partition is stable for a given partition hashing key. Should any other batches of events be
     * sent using the same exact partition hashing key, the Event Hubs service will route them all to the same
     * partition.
     *
     * This should be specified only when there is a need to group events by partition, but there is flexibility into
     * which partition they are routed. If ensuring that a batch of events is sent only to a specific partition, it is
     * recommended that the {@link #setPartitionId(String) identifier of the position be specified directly} when
     * sending the batch.
     */
    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    /**
     * Sets the {@link CheckpointStore} the {@link EventProcessorClient} will use for storing partition ownership and
     * checkpoint information.
     *
     * <p>
     * Users can, optionally, provide their own implementation of {@link CheckpointStore} which will store ownership and
     * checkpoint information.
     * </p>
     *
     * By default it set to use {@link com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore} which
     * stores all checkpoint offsets into Azure Blob Storage.
     */
    public CheckpointStore getCheckpointStore() {
        return checkpointStore;
    }

    public void setCheckpointStore(CheckpointStore checkpointStore) {
        this.checkpointStore = checkpointStore;
    }

    /**
     * In case you chose the default BlobCheckpointStore, this sets Azure account name to be used for authentication
     * with azure blob services.
     */
    public String getBlobAccountName() {
        return blobAccountName;
    }

    public void setBlobAccountName(String blobAccountName) {
        this.blobAccountName = blobAccountName;
    }

    /**
     * In case you chose the default BlobCheckpointStore, this sets access key for the associated azure account name to
     * be used for authentication with azure blob services.
     */
    public String getBlobAccessKey() {
        return blobAccessKey;
    }

    public void setBlobAccessKey(String blobAccessKey) {
        this.blobAccessKey = blobAccessKey;
    }

    /**
     * In case you chose the default BlobCheckpointStore, this sets the blob container that shall be used by the
     * BlobCheckpointStore to store the checkpoint offsets.
     */
    public String getBlobContainerName() {
        return blobContainerName;
    }

    public void setBlobContainerName(String blobContainerName) {
        this.blobContainerName = blobContainerName;
    }

    /**
     * In case you chose the default BlobCheckpointStore, StorageSharedKeyCredential can be injected to create the azure
     * client, this holds the important authentication information.
     */
    public StorageSharedKeyCredential getBlobStorageSharedKeyCredential() {
        return blobStorageSharedKeyCredential;
    }

    public void setBlobStorageSharedKeyCredential(StorageSharedKeyCredential blobStorageSharedKeyCredential) {
        this.blobStorageSharedKeyCredential = blobStorageSharedKeyCredential;
    }

    /**
     * Sets the map containing the event position to use for each partition if a checkpoint for the partition does not
     * exist in {@link CheckpointStore}. This map is keyed off of the partition id. If there is no checkpoint in
     * {@link CheckpointStore} and there is no entry in this map, the processing of the partition will start from
     * {@link EventPosition#latest() latest} position.
     */
    public Map<String, EventPosition> getEventPosition() {
        return eventPosition;
    }

    public void setEventPosition(Map<String, EventPosition> eventPosition) {
        this.eventPosition = eventPosition;
    }

    public int getCheckpointBatchSize() {
        return checkpointBatchSize;
    }

    /**
     * Sets the batch size between each checkpoint updates. Works jointly with {@link #checkpointBatchTimeout}.
     */
    public void setCheckpointBatchSize(int checkpointBatchSize) {
        this.checkpointBatchSize = checkpointBatchSize;
    }

    public int getCheckpointBatchTimeout() {
        return checkpointBatchTimeout;
    }

    /**
     * Sets the batch timeout between each checkpoint updates. Works jointly with {@link #checkpointBatchSize}.
     */
    public void setCheckpointBatchTimeout(int checkpointBatchTimeout) {
        this.checkpointBatchTimeout = checkpointBatchTimeout;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    /**
     * Determines the credential strategy to adopt
     */
    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    public EventHubsConfiguration copy() {
        try {
            return (EventHubsConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
