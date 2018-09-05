/**
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
package org.apache.camel.component.azure.queue;

import com.microsoft.azure.storage.queue.CloudQueue;

import org.apache.camel.component.azure.common.AbstractConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
@UriParams
public class QueueServiceConfiguration extends AbstractConfiguration {

    private String queueName;
    @UriParam
    private CloudQueue azureQueueClient;

    @UriParam(label = "producer", defaultValue = "listQueues")
    private QueueServiceOperations operation = QueueServiceOperations.listQueues;

    @UriParam(label = "producer")
    private int messageTimeToLive;

    @UriParam(label = "producer")
    private int messageVisibilityDelay;

    @UriParam(label = "producer")
    private String queuePrefix;

    public String getQueueName() {
        return queueName;
    }

    /**
     * The queue resource name
     */
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public CloudQueue getAzureQueueClient() {
        return azureQueueClient;
    }

    /**
     * The queue service client
     */
    public void setAzureQueueClient(CloudQueue azureQueueClient) {
        this.azureQueueClient = azureQueueClient;
    }

    public QueueServiceOperations getOperation() {
        return operation;
    }

    /**
     * Queue service operation hint to the producer
     */
    public void setOperation(QueueServiceOperations operation) {
        this.operation = operation;
    }

    public int getMessageTimeToLive() {
        return messageTimeToLive;
    }

    /**
     * Message Time To Live in seconds
     */
    public void setMessageTimeToLive(int messageTimeToLive) {
        this.messageTimeToLive = messageTimeToLive;
    }

    public int getMessageVisibilityDelay() {
        return messageVisibilityDelay;
    }

    /**
     * Message Visibility Delay in seconds
     */
    public void setMessageVisibilityDelay(int messageVisibilityDelay) {
        this.messageVisibilityDelay = messageVisibilityDelay;
    }

    public String getQueuePrefix() {
        return queuePrefix;
    }

    /**
     * Set a prefix which can be used for listing the queues
     */
    public void setQueuePrefix(String queuePrefix) {
        this.queuePrefix = queuePrefix;
    }
}
