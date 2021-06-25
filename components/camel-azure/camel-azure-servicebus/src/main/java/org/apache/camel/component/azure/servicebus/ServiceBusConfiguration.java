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
package org.apache.camel.component.azure.servicebus;

import java.time.Duration;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.amqp.ProxyOptions;
import com.azure.core.util.ClientOptions;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class ServiceBusConfiguration implements Cloneable {

    @UriPath
    private String topicOrQueueName;
    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String connectionString;
    @UriParam(label = "common", defaultValue = "queue")
    @Metadata(required = true)
    private ServiceBusType serviceBusType = ServiceBusType.queue;
    @UriParam(label = "common")
    private ClientOptions clientOptions;
    @UriParam(label = "common")
    private ProxyOptions proxyOptions;
    @UriParam(label = "common")
    private AmqpRetryOptions amqpRetryOptions;
    @UriParam(label = "common", defaultValue = "AMQP")
    private AmqpTransportType amqpTransportType = AmqpTransportType.AMQP;
    @UriParam(label = "consumer")
    private ServiceBusConsumerOperationDefinition consumerOperation = ServiceBusConsumerOperationDefinition.receiveMessages;
    @UriParam(label = "consumer")
    @Metadata(autowired = true)
    private ServiceBusReceiverAsyncClient receiverAsyncClient;
    @UriParam(label = "consumer")
    private String subscriptionName;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean disableAutoComplete = false;
    @UriParam(label = "consumer", defaultValue = "PEER_LOCK")
    private ServiceBusReceiveMode serviceBusReceiveMode = ServiceBusReceiveMode.PEEK_LOCK;
    @UriParam(label = "consumer", defaultValue = "5min")
    private Duration maxAutoLockRenewDuration = Duration.ofMinutes(5);
    @UriParam(label = "consumer", defaultValue = "0")
    private int prefetchCount = 0;
    @UriParam(label = "consumer")
    private SubQueue subQueue;
    @UriParam(label = "producer")
    private ServiceBusProducerOperationDefinition producerOperation = ServiceBusProducerOperationDefinition.sendMessages;
    @UriParam(label = "producer")
    @Metadata(autowired = true)
    private ServiceBusSenderAsyncClient senderAsyncClient;

    /**
     * d
     */
    public String getTopicOrQueueName() {
        return topicOrQueueName;
    }

    public void setTopicOrQueueName(String topicOrQueueName) {
        this.topicOrQueueName = topicOrQueueName;
    }

    /**
     * d
     */
    public ServiceBusType getServiceBusType() {
        return serviceBusType;
    }

    public void setServiceBusType(ServiceBusType serviceBusType) {
        this.serviceBusType = serviceBusType;
    }

    /**
     * d
     */
    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * d
     */
    public String getSubscriptionName() {
        return subscriptionName;
    }

    public void setSubscriptionName(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    /**
     * dd
     */
    public ClientOptions getClientOptions() {
        return clientOptions;
    }

    public void setClientOptions(ClientOptions clientOptions) {
        this.clientOptions = clientOptions;
    }

    /**
     * dd
     */
    public ProxyOptions getProxyOptions() {
        return proxyOptions;
    }

    public void setProxyOptions(ProxyOptions proxyOptions) {
        this.proxyOptions = proxyOptions;
    }

    /**
     * dd
     */
    public AmqpRetryOptions getAmqpRetryOptions() {
        return amqpRetryOptions;
    }

    public void setAmqpRetryOptions(AmqpRetryOptions amqpRetryOptions) {
        this.amqpRetryOptions = amqpRetryOptions;
    }

    /**
     * dd
     */
    public AmqpTransportType getAmqpTransportType() {
        return amqpTransportType;
    }

    public void setAmqpTransportType(AmqpTransportType amqpTransportType) {
        this.amqpTransportType = amqpTransportType;
    }

    /**
     * dd
     */
    public ServiceBusReceiverAsyncClient getReceiverAsyncClient() {
        return receiverAsyncClient;
    }

    public void setReceiverAsyncClient(ServiceBusReceiverAsyncClient receiverAsyncClient) {
        this.receiverAsyncClient = receiverAsyncClient;
    }

    /**
     * dd
     */
    public boolean isDisableAutoComplete() {
        return disableAutoComplete;
    }

    public void setDisableAutoComplete(boolean disableAutoComplete) {
        this.disableAutoComplete = disableAutoComplete;
    }

    /**
     * dd
     */
    public ServiceBusReceiveMode getServiceBusReceiveMode() {
        return serviceBusReceiveMode;
    }

    public void setServiceBusReceiveMode(ServiceBusReceiveMode serviceBusReceiveMode) {
        this.serviceBusReceiveMode = serviceBusReceiveMode;
    }

    /**
     * dd
     */
    public Duration getMaxAutoLockRenewDuration() {
        return maxAutoLockRenewDuration;
    }

    public void setMaxAutoLockRenewDuration(Duration maxAutoLockRenewDuration) {
        this.maxAutoLockRenewDuration = maxAutoLockRenewDuration;
    }

    /**
     * dd
     */
    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    /**
     * dd
     */
    public SubQueue getSubQueue() {
        return subQueue;
    }

    public void setSubQueue(SubQueue subQueue) {
        this.subQueue = subQueue;
    }

    /**
     * dd
     */
    public ServiceBusSenderAsyncClient getSenderAsyncClient() {
        return senderAsyncClient;
    }

    public void setSenderAsyncClient(ServiceBusSenderAsyncClient senderAsyncClient) {
        this.senderAsyncClient = senderAsyncClient;
    }

    /**
     * dd
     */
    public ServiceBusConsumerOperationDefinition getConsumerOperation() {
        return consumerOperation;
    }

    public void setConsumerOperation(ServiceBusConsumerOperationDefinition consumerOperation) {
        this.consumerOperation = consumerOperation;
    }

    /**
     * dd
     */
    public ServiceBusProducerOperationDefinition getProducerOperation() {
        return producerOperation;
    }

    public void setProducerOperation(ServiceBusProducerOperationDefinition producerOperation) {
        this.producerOperation = producerOperation;
    }

    // *************************************************
    //
    // *************************************************

    public org.apache.camel.component.azure.servicebus.ServiceBusConfiguration copy() {
        try {
            return (org.apache.camel.component.azure.servicebus.ServiceBusConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
