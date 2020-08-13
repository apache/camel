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
import com.azure.messaging.eventhubs.CheckpointStore;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

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
    @UriParam(label = "common", defaultValue = "Amqp")
    private AmqpTransportType amqpTransportType = AmqpTransportType.AMQP;
    @UriParam(label = "common")
    private AmqpRetryOptions amqpRetryOptions;
    @UriParam(label = "common", defaultValue = "true")
    private boolean autoDiscoverClient = true;
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
    @UriParam(label = "producer")
    private EventHubProducerAsyncClient producerAsyncClient;
    @UriParam(label = "producer")
    private String partitionKey;
    @UriParam(label = "producer")
    private String partitionId;

    /**
     * test
     */
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * test
     */
    public String getEventHubName() {
        return eventHubName;
    }

    public void setEventHubName(String eventHubName) {
        this.eventHubName = eventHubName;
    }

    /**
     * test
     */
    public String getSharedAccessName() {
        return sharedAccessName;
    }

    public void setSharedAccessName(String sharedAccessName) {
        this.sharedAccessName = sharedAccessName;
    }

    /**
     * test
     */
    public String getSharedAccessKey() {
        return sharedAccessKey;
    }

    public void setSharedAccessKey(String sharedAccessKey) {
        this.sharedAccessKey = sharedAccessKey;
    }

    /**
     * test
     */
    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * test
     */
    public AmqpTransportType getAmqpTransportType() {
        return amqpTransportType;
    }

    public void setAmqpTransportType(AmqpTransportType amqpTransportType) {
        this.amqpTransportType = amqpTransportType;
    }

    /**
     * test
     */
    public AmqpRetryOptions getAmqpRetryOptions() {
        return amqpRetryOptions;
    }

    public void setAmqpRetryOptions(AmqpRetryOptions amqpRetryOptions) {
        this.amqpRetryOptions = amqpRetryOptions;
    }

    /**
     * test
     */
    public String getConsumerGroupName() {
        return consumerGroupName;
    }

    public void setConsumerGroupName(String consumerGroupName) {
        this.consumerGroupName = consumerGroupName;
    }

    /**
     * test
     */
    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    /**
     * test
     */
    public EventHubProducerAsyncClient getProducerAsyncClient() {
        return producerAsyncClient;
    }

    public void setProducerAsyncClient(EventHubProducerAsyncClient producerAsyncClient) {
        this.producerAsyncClient = producerAsyncClient;
    }

    /**
     * Setting the autoDiscoverClient mechanism, if true, the component will
     * look for a client instance in the registry automatically otherwise it
     * will skip that checking.
     */
    public boolean isAutoDiscoverClient() {
        return autoDiscoverClient;
    }

    public void setAutoDiscoverClient(boolean autoDiscoverClient) {
        this.autoDiscoverClient = autoDiscoverClient;
    }

    /**
     * test
     */
    public String getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(String partitionId) {
        this.partitionId = partitionId;
    }

    /**
     * test
     */
    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    /**
     * test
     */
    public CheckpointStore getCheckpointStore() {
        return checkpointStore;
    }

    public void setCheckpointStore(CheckpointStore checkpointStore) {
        this.checkpointStore = checkpointStore;
    }

    /**
     * test
     */
    public String getBlobAccountName() {
        return blobAccountName;
    }

    public void setBlobAccountName(String blobAccountName) {
        this.blobAccountName = blobAccountName;
    }

    /**
     * test
     */
    public String getBlobAccessKey() {
        return blobAccessKey;
    }

    public void setBlobAccessKey(String blobAccessKey) {
        this.blobAccessKey = blobAccessKey;
    }

    /**
     * test
     */
    public String getBlobContainerName() {
        return blobContainerName;
    }

    public void setBlobContainerName(String blobContainerName) {
        this.blobContainerName = blobContainerName;
    }

    /**
     * test
     */
    public StorageSharedKeyCredential getBlobStorageSharedKeyCredential() {
        return blobStorageSharedKeyCredential;
    }

    public void setBlobStorageSharedKeyCredential(StorageSharedKeyCredential blobStorageSharedKeyCredential) {
        this.blobStorageSharedKeyCredential = blobStorageSharedKeyCredential;
    }

    /**
     * test
     */
    public Map<String, EventPosition> getEventPosition() {
        return eventPosition;
    }

    public void setEventPosition(Map<String, EventPosition> eventPosition) {
        this.eventPosition = eventPosition;
    }

    // *************************************************
    //
    // *************************************************

    public EventHubsConfiguration copy() {
        try {
            return (EventHubsConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
