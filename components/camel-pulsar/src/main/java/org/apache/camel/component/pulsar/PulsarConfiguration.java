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
package org.apache.camel.component.pulsar;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.pulsar.utils.consumers.SubscriptionInitialPosition;
import org.apache.camel.component.pulsar.utils.consumers.SubscriptionType;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.pulsar.client.api.BatcherBuilder;
import org.apache.pulsar.client.api.CompressionType;
import org.apache.pulsar.client.api.MessageRouter;
import org.apache.pulsar.client.api.MessageRoutingMode;

import static org.apache.camel.component.pulsar.utils.consumers.SubscriptionInitialPosition.LATEST;
import static org.apache.camel.component.pulsar.utils.consumers.SubscriptionType.EXCLUSIVE;

@UriParams
public class PulsarConfiguration implements Cloneable {

    @UriParam(label = "consumer", defaultValue = "subs")
    private String subscriptionName = "subs";
    @UriParam(label = "consumer", defaultValue = "EXCLUSIVE")
    private SubscriptionType subscriptionType = EXCLUSIVE;
    @UriParam(label = "consumer", defaultValue = "1")
    private int numberOfConsumers = 1;
    @UriParam(label = "consumer", defaultValue = "10")
    private int consumerQueueSize = 10;
    @UriParam(label = "consumer", defaultValue = "sole-consumer")
    private String consumerName = "sole-consumer";
    @UriParam(label = "producer")
    private String producerName;
    @UriParam(label = "consumer", defaultValue = "cons")
    private String consumerNamePrefix = "cons";
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean allowManualAcknowledgement;
    @UriParam(label = "consumer", defaultValue = "10000")
    private long ackTimeoutMillis = 10000;
    @UriParam(label = "consumer", defaultValue = "60000000")
    private long negativeAckRedeliveryDelayMicros = 60000000;
    @UriParam(label = "consumer", defaultValue = "100")
    private long ackGroupTimeMillis = 100;
    @UriParam(label = "consumer", defaultValue = "LATEST")
    private SubscriptionInitialPosition subscriptionInitialPosition = LATEST;
    @UriParam(label = "consumer", description = "Maximum number of times that a message will be redelivered before being sent to the dead letter queue. If this value is not set, no Dead Letter Policy will be created")
    private Integer maxRedeliverCount;
    @UriParam(label = "consumer", description = "Name of the topic where the messages which fail maxRedeliverCount times will be sent. Note: if not set, default topic name will be topicName-subscriptionName-DLQ")
    private String deadLetterTopic;
    @UriParam(label = "producer", description = "Send timeout in milliseconds", defaultValue = "30000")
    private int sendTimeoutMs = 30000;
    @UriParam(label = "producer", description = "Whether to block the producing thread if pending messages queue is full or to throw a ProducerQueueIsFullError", defaultValue = "false")
    private boolean blockIfQueueFull;
    @UriParam(label = "producer", description = "Size of the pending massages queue. When the queue is full, by default, any further sends will fail unless blockIfQueueFull=true", defaultValue = "1000")
    private int maxPendingMessages = 1000;
    @UriParam(label = "producer", description = "The maximum number of pending messages for partitioned topics. The maxPendingMessages value will be reduced if "
                                                + "(number of partitions * maxPendingMessages) exceeds this value. Partitioned topics have a pending message queue for each partition.", defaultValue = "50000")
    private int maxPendingMessagesAcrossPartitions = 50000;
    @UriParam(label = "producer", description = "The maximum time period within which the messages sent will be batched if batchingEnabled is true.", defaultValue = "1000")
    private long batchingMaxPublishDelayMicros = TimeUnit.MILLISECONDS.toMicros(1);
    @UriParam(label = "producer", description = "The maximum size to batch messages.", defaultValue = "1000")
    private int batchingMaxMessages = 1000;
    @UriParam(label = "producer", description = "Control whether automatic batching of messages is enabled for the producer.", defaultValue = "true")
    private boolean batchingEnabled = true;
    @UriParam(label = "producer", description = "Control batching method used by the producer.", defaultValue = "DEFAULT")
    private BatcherBuilder batcherBuilder = BatcherBuilder.DEFAULT;
    @UriParam(label = "producer", description = "The first message published will have a sequence Id of initialSequenceId  1.", defaultValue = "-1")
    private long initialSequenceId = -1;
    @UriParam(label = "producer", description = "Compression type to use", defaultValue = "NONE")
    private CompressionType compressionType = CompressionType.NONE;
    @UriParam(label = "producer", description = "Message Routing Mode to use", defaultValue = "RoundRobinPartition")
    private MessageRoutingMode messageRoutingMode = MessageRoutingMode.RoundRobinPartition;
    @UriParam(label = "producer", description = "Custom Message Router to use")
    private MessageRouter messageRouter;

    /**
     * Returns a copy of this configuration
     */
    public PulsarConfiguration copy() {
        try {
            PulsarConfiguration copy = (PulsarConfiguration)clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getSubscriptionName() {
        return subscriptionName;
    }

    /**
     * Name of the subscription to use
     */
    public void setSubscriptionName(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    /**
     * Type of the subscription [EXCLUSIVE|SHARED|FAILOVER|KEY_SHARED], defaults to
     * EXCLUSIVE
     */
    public void setSubscriptionType(SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public int getNumberOfConsumers() {
        return numberOfConsumers;
    }

    /**
     * Number of consumers - defaults to 1
     */
    public void setNumberOfConsumers(int numberOfConsumers) {
        this.numberOfConsumers = numberOfConsumers;
    }

    public int getConsumerQueueSize() {
        return consumerQueueSize;
    }

    /**
     * Size of the consumer queue - defaults to 10
     */
    public void setConsumerQueueSize(int consumerQueueSize) {
        this.consumerQueueSize = consumerQueueSize;
    }

    public String getConsumerName() {
        return consumerName;
    }

    /**
     * Name of the consumer when subscription is EXCLUSIVE
     */
    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public String getProducerName() {
        return producerName;
    }

    /**
     * Name of the producer. If unset, lets Pulsar select a unique identifier.
     */
    public void setProducerName(String producerName) {
        this.producerName = producerName;
    }

    public String getConsumerNamePrefix() {
        return consumerNamePrefix;
    }

    /**
     * Prefix to add to consumer names when a SHARED or FAILOVER subscription is
     * used
     */
    public void setConsumerNamePrefix(String consumerNamePrefix) {
        this.consumerNamePrefix = consumerNamePrefix;
    }

    public boolean isAllowManualAcknowledgement() {
        return allowManualAcknowledgement;
    }

    /**
     * Whether to allow manual message acknowledgements.
     * <p/>
     * If this option is enabled, then messages are not acknowledged automatically
     * after successful route completion. Instead, an instance of
     * {@link PulsarMessageReceipt} is stored as a header on the
     * {@link org.apache.camel.Exchange}. Messages can then be acknowledged
     * using {@link PulsarMessageReceipt} at any time before the ackTimeout
     * occurs.
     */
    public void setAllowManualAcknowledgement(boolean allowManualAcknowledgement) {
        this.allowManualAcknowledgement = allowManualAcknowledgement;
    }

    public long getAckTimeoutMillis() {
        return ackTimeoutMillis;
    }

    /**
     * Timeout for unacknowledged messages in milliseconds - defaults to 10000
     */
    public void setAckTimeoutMillis(long ackTimeoutMillis) {
        this.ackTimeoutMillis = ackTimeoutMillis;
    }

    public long getAckGroupTimeMillis() {
        return ackGroupTimeMillis;
    }

    /**
     * Group the consumer acknowledgments for the specified time in milliseconds
     * - defaults to 100
     */
    public void setAckGroupTimeMillis(long ackGroupTimeMillis) {
        this.ackGroupTimeMillis = ackGroupTimeMillis;
    }

    /**
     * Send timeout in milliseconds. Defaults to 30,000ms (30 seconds)
     */
    public void setSendTimeoutMs(int sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public int getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    /**
     * Set whether the send and asyncSend operations should block when the
     * outgoing message queue is full. If set to false, send operations will
     * immediately fail with ProducerQueueIsFullError when there is no space
     * left in the pending queue. Default is false.
     */
    public void setBlockIfQueueFull(boolean blockIfQueueFull) {
        this.blockIfQueueFull = blockIfQueueFull;
    }

    public boolean isBlockIfQueueFull() {
        return blockIfQueueFull;
    }

    /**
     * Set the max size of the queue holding the messages pending to receive an
     * acknowledgment from the broker. Default is 1000.
     */
    public void setMaxPendingMessages(int maxPendingMessages) {
        this.maxPendingMessages = maxPendingMessages;
    }

    public int getMaxPendingMessages() {
        return maxPendingMessages;
    }

    /**
     * Set the number of max pending messages across all the partitions. Default
     * is 50000.
     */
    public void setMaxPendingMessagesAcrossPartitions(int maxPendingMessagesAcrossPartitions) {
        this.maxPendingMessagesAcrossPartitions = maxPendingMessagesAcrossPartitions;
    }

    public int getMaxPendingMessagesAcrossPartitions() {
        return maxPendingMessagesAcrossPartitions;
    }

    /**
     * Set the time period within which the messages sent will be batched if
     * batch messages are enabled. If set to a non zero value, messages will be
     * queued until either:
     * <ul>
     * <li>this time interval expires</li>
     * <li>the max number of messages in a batch is reached
     * </ul>
     * Default is 1ms.
     */
    public void setBatchingMaxPublishDelayMicros(long batchingMaxPublishDelayMicros) {
        this.batchingMaxPublishDelayMicros = batchingMaxPublishDelayMicros;
    }

    public long getBatchingMaxPublishDelayMicros() {
        return batchingMaxPublishDelayMicros;
    }

    /**
     * Set the maximum number of messages permitted in a batch. Default 1,000.
     */
    public void setBatchingMaxMessages(int batchingMaxMessages) {
        this.batchingMaxMessages = batchingMaxMessages;
    }

    public int getBatchingMaxMessages() {
        return batchingMaxMessages;
    }

    /**
     * Control whether automatic batching of messages is enabled for the
     * producer. Default is true.
     */
    public void setBatchingEnabled(boolean batchingEnabled) {
        this.batchingEnabled = batchingEnabled;
    }

    public boolean isBatchingEnabled() {
        return batchingEnabled;
    }

    /**
     * Control batching method of the Pulsar producer.
     * KEY_BASED batches based on the Pulsar message key.
     * DEFAULT batches all messages together regardless of key;
     * this may cause only a single consumer to work when consuming using a KEY_SHARED subscription.
     * Default is DEFAULT.
     */
    public void setBatcherBuilder(BatcherBuilder batcherBuilder) {
        this.batcherBuilder = batcherBuilder;
    }

    public BatcherBuilder getBatcherBuilder() {
        return batcherBuilder;
    }

    /**
     * Control the initial position in the topic of a newly created subscription. Default is latest message.
     */
    public void setSubscriptionInitialPosition(SubscriptionInitialPosition subscriptionInitialPosition) {
        this.subscriptionInitialPosition = subscriptionInitialPosition;
    }

    public SubscriptionInitialPosition getSubscriptionInitialPosition() {
        return subscriptionInitialPosition;
    }

    /**
     * Set the baseline for the sequence ids for messages published by the
     * producer. First message will be using (initialSequenceId 1) as its
     * sequence id and subsequent messages will be assigned incremental sequence
     * ids, if not otherwise specified.
     */
    public void setInitialSequenceId(long initialSequenceId) {
        this.initialSequenceId = initialSequenceId;
    }

    public long getInitialSequenceId() {
        return initialSequenceId;
    }

    /**
     * Set the compression type for the producer.
     */
    public void setCompressionType(String compressionType) {
        this.compressionType = CompressionType.valueOf(compressionType.toUpperCase());
    }

    /**
     * Set the compression type for the producer.
     */
    public void setCompressionType(CompressionType compressionType) {
        this.compressionType = compressionType;
    }

    public CompressionType getCompressionType() {
        return compressionType;
    }

    /**
     * Set the message routing mode for the producer.
     */
    public MessageRoutingMode getMessageRoutingMode() {
        return messageRoutingMode;
    }

    public void setMessageRoutingMode(MessageRoutingMode messageRoutingMode) {
        this.messageRoutingMode = messageRoutingMode;
    }

    /**
     * Set a custom Message Router.
     */
    public MessageRouter getMessageRouter() {
        return messageRouter;
    }

    public void setMessageRouter(MessageRouter messageRouter) {
        this.messageRouter = messageRouter;
    }

    public long getNegativeAckRedeliveryDelayMicros() {
        return negativeAckRedeliveryDelayMicros;
    }

    /**
     * Set the negative acknowledgement delay
     */
    public void setNegativeAckRedeliveryDelayMicros(long negativeAckRedeliveryDelayMicros) {
        this.negativeAckRedeliveryDelayMicros = negativeAckRedeliveryDelayMicros;
    }
    public Integer getMaxRedeliverCount() {
        return maxRedeliverCount;
    }

    public void setMaxRedeliverCount(Integer maxRedeliverCount) {
        this.maxRedeliverCount = maxRedeliverCount;
    }

    public String getDeadLetterTopic() {
        return deadLetterTopic;
    }

    public void setDeadLetterTopic(String deadLetterTopic) {
        this.deadLetterTopic = deadLetterTopic;
    }
}
