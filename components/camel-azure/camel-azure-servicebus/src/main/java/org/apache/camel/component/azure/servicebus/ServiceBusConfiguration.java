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
import java.time.OffsetDateTime;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.amqp.ProxyOptions;
import com.azure.core.credential.TokenCredential;
import com.azure.core.util.ClientOptions;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import com.azure.messaging.servicebus.ServiceBusTransactionContext;
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
    @UriParam(label = "common", defaultValue = "queue")
    @Metadata(required = true)
    private ServiceBusType serviceBusType = ServiceBusType.queue;
    @UriParam(label = "security", secret = true)
    private String connectionString;
    @UriParam(label = "security")
    private String fullyQualifiedNamespace;
    @UriParam(label = "security", secret = true)
    private TokenCredential tokenCredential;
    @UriParam(label = "common")
    private ClientOptions clientOptions;
    @UriParam(label = "common")
    private ProxyOptions proxyOptions;
    @UriParam(label = "common")
    private AmqpRetryOptions amqpRetryOptions;
    @UriParam(label = "common", defaultValue = "AMQP")
    private AmqpTransportType amqpTransportType = AmqpTransportType.AMQP;
    @UriParam(label = "consumer", defaultValue = "receiveMessages")
    private ServiceBusConsumerOperationDefinition consumerOperation = ServiceBusConsumerOperationDefinition.receiveMessages;
    @UriParam(label = "consumer")
    @Metadata(autowired = true)
    private ServiceBusReceiverAsyncClient receiverAsyncClient;
    @UriParam(label = "consumer")
    private String subscriptionName;
    @UriParam(label = "consumer")
    private boolean disableAutoComplete;
    @UriParam(label = "consumer", defaultValue = "PEEK_LOCK")
    private ServiceBusReceiveMode serviceBusReceiveMode = ServiceBusReceiveMode.PEEK_LOCK;
    @UriParam(label = "consumer", defaultValue = "5m")
    private Duration maxAutoLockRenewDuration = Duration.ofMinutes(5);
    @UriParam(label = "consumer")
    private int prefetchCount;
    @UriParam(label = "consumer")
    private SubQueue subQueue;
    @UriParam(label = "consumer")
    private Integer peekNumMaxMessages;
    @UriParam(label = "producer", defaultValue = "sendMessages")
    private ServiceBusProducerOperationDefinition producerOperation = ServiceBusProducerOperationDefinition.sendMessages;
    @UriParam(label = "producer")
    @Metadata(autowired = true)
    private ServiceBusSenderAsyncClient senderAsyncClient;
    @UriParam(label = "producer")
    private ServiceBusTransactionContext serviceBusTransactionContext;
    @UriParam(label = "producer")
    private OffsetDateTime scheduledEnqueueTime;

    /**
     * Selected topic name or the queue name, that is depending on serviceBusType config. For example if
     * serviceBusType=queue, then this will be the queue name and if serviceBusType=topic, this will be the topic name.
     */
    public String getTopicOrQueueName() {
        return topicOrQueueName;
    }

    public void setTopicOrQueueName(String topicOrQueueName) {
        this.topicOrQueueName = topicOrQueueName;
    }

    /**
     * The service bus type of connection to execute. Queue is for typical queue option and topic for subscription based
     * model.
     */
    public ServiceBusType getServiceBusType() {
        return serviceBusType;
    }

    public void setServiceBusType(ServiceBusType serviceBusType) {
        this.serviceBusType = serviceBusType;
    }

    /**
     * Sets the connection string for a Service Bus namespace or a specific Service Bus resource.
     */
    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    /**
     * Sets the name of the subscription in the topic to listen to. topicOrQueueName and serviceBusType=topic must also
     * be set. This property is required if serviceBusType=topic and the consumer is in use.
     */
    public String getSubscriptionName() {
        return subscriptionName;
    }

    public void setSubscriptionName(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    /**
     * Sets the ClientOptions to be sent from the client built from this builder, enabling customization of certain
     * properties, as well as support the addition of custom header information.
     */
    public ClientOptions getClientOptions() {
        return clientOptions;
    }

    public void setClientOptions(ClientOptions clientOptions) {
        this.clientOptions = clientOptions;
    }

    /**
     * Sets the proxy configuration to use for ServiceBusSenderAsyncClient. When a proxy is configured, AMQP_WEB_SOCKETS
     * must be used for the transport type.
     */
    public ProxyOptions getProxyOptions() {
        return proxyOptions;
    }

    public void setProxyOptions(ProxyOptions proxyOptions) {
        this.proxyOptions = proxyOptions;
    }

    /**
     * Sets the retry options for Service Bus clients. If not specified, the default retry options are used.
     */
    public AmqpRetryOptions getAmqpRetryOptions() {
        return amqpRetryOptions;
    }

    public void setAmqpRetryOptions(AmqpRetryOptions amqpRetryOptions) {
        this.amqpRetryOptions = amqpRetryOptions;
    }

    /**
     * Sets the transport type by which all the communication with Azure Service Bus occurs. Default value is AMQP.
     */
    public AmqpTransportType getAmqpTransportType() {
        return amqpTransportType;
    }

    public void setAmqpTransportType(AmqpTransportType amqpTransportType) {
        this.amqpTransportType = amqpTransportType;
    }

    /**
     * Sets the receiverAsyncClient in order to consume messages by the consumer
     */
    public ServiceBusReceiverAsyncClient getReceiverAsyncClient() {
        return receiverAsyncClient;
    }

    public void setReceiverAsyncClient(ServiceBusReceiverAsyncClient receiverAsyncClient) {
        this.receiverAsyncClient = receiverAsyncClient;
    }

    /**
     * Disables auto-complete and auto-abandon of received messages. By default, a successfully processed message is
     * completed. If an error happens when the message is abandoned.
     */
    public boolean isDisableAutoComplete() {
        return disableAutoComplete;
    }

    public void setDisableAutoComplete(boolean disableAutoComplete) {
        this.disableAutoComplete = disableAutoComplete;
    }

    /**
     * Sets the receive mode for the receiver.
     */
    public ServiceBusReceiveMode getServiceBusReceiveMode() {
        return serviceBusReceiveMode;
    }

    public void setServiceBusReceiveMode(ServiceBusReceiveMode serviceBusReceiveMode) {
        this.serviceBusReceiveMode = serviceBusReceiveMode;
    }

    /**
     * Sets the amount of time to continue auto-renewing the lock. Setting ZERO disables auto-renewal. For ServiceBus
     * receive mode (RECEIVE_AND_DELETE RECEIVE_AND_DELETE), auto-renewal is disabled.
     */
    public Duration getMaxAutoLockRenewDuration() {
        return maxAutoLockRenewDuration;
    }

    public void setMaxAutoLockRenewDuration(Duration maxAutoLockRenewDuration) {
        this.maxAutoLockRenewDuration = maxAutoLockRenewDuration;
    }

    /**
     * Sets the prefetch count of the receiver. For both PEEK_LOCK PEEK_LOCK and RECEIVE_AND_DELETE RECEIVE_AND_DELETE
     * receive modes the default value is 1.
     *
     * Prefetch speeds up the message flow by aiming to have a message readily available for local retrieval when and
     * before the application asks for one using receive message. Setting a non-zero value will prefetch that number of
     * messages. Setting the value to zero turns prefetch off.
     */
    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    /**
     * Sets the type of the SubQueue to connect to.
     */
    public SubQueue getSubQueue() {
        return subQueue;
    }

    public void setSubQueue(SubQueue subQueue) {
        this.subQueue = subQueue;
    }

    /**
     * Sets SenderAsyncClient to be used in the producer.
     */
    public ServiceBusSenderAsyncClient getSenderAsyncClient() {
        return senderAsyncClient;
    }

    public void setSenderAsyncClient(ServiceBusSenderAsyncClient senderAsyncClient) {
        this.senderAsyncClient = senderAsyncClient;
    }

    /**
     * Fully Qualified Namespace of the service bus
     */
    public String getFullyQualifiedNamespace() {
        return fullyQualifiedNamespace;
    }

    public void setFullyQualifiedNamespace(String fullyQualifiedNamespace) {
        this.fullyQualifiedNamespace = fullyQualifiedNamespace;
    }

    /**
     * A TokenCredential for Azure AD authentication.
     */
    public TokenCredential getTokenCredential() {
        return tokenCredential;
    }

    public void setTokenCredential(TokenCredential tokenCredential) {
        this.tokenCredential = tokenCredential;
    }

    /**
     * Sets the desired operation to be used in the consumer
     */
    public ServiceBusConsumerOperationDefinition getConsumerOperation() {
        return consumerOperation;
    }

    public void setConsumerOperation(ServiceBusConsumerOperationDefinition consumerOperation) {
        this.consumerOperation = consumerOperation;
    }

    /**
     * Sets the desired operation to be used in the producer
     */
    public ServiceBusProducerOperationDefinition getProducerOperation() {
        return producerOperation;
    }

    public void setProducerOperation(ServiceBusProducerOperationDefinition producerOperation) {
        this.producerOperation = producerOperation;
    }

    /**
     * Represents transaction in service. This object just contains transaction id.
     */
    public ServiceBusTransactionContext getServiceBusTransactionContext() {
        return serviceBusTransactionContext;
    }

    public void setServiceBusTransactionContext(ServiceBusTransactionContext serviceBusTransactionContext) {
        this.serviceBusTransactionContext = serviceBusTransactionContext;
    }

    /**
     * Sets OffsetDateTime at which the message should appear in the Service Bus queue or topic.
     */
    public OffsetDateTime getScheduledEnqueueTime() {
        return scheduledEnqueueTime;
    }

    public void setScheduledEnqueueTime(OffsetDateTime scheduledEnqueueTime) {
        this.scheduledEnqueueTime = scheduledEnqueueTime;
    }

    /**
     * Set the max number of messages to be peeked during the peek operation.
     */
    public Integer getPeekNumMaxMessages() {
        return peekNumMaxMessages;
    }

    public void setPeekNumMaxMessages(Integer peekNumMaxMessages) {
        this.peekNumMaxMessages = peekNumMaxMessages;
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
