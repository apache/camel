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
package org.apache.camel.component.pulsar.configuration;

import org.apache.camel.component.pulsar.utils.consumers.SubscriptionType;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

import static org.apache.camel.component.pulsar.utils.consumers.SubscriptionType.EXCLUSIVE;

@UriParams
public class PulsarConfiguration {

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
    @UriParam(label = "producer", defaultValue = "default-producer")
    private String producerName = "default-producer";
    @UriParam(label = "consumer", defaultValue = "cons")
    private String consumerNamePrefix = "cons";
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean allowManualAcknowledgement;
    @UriParam(label = "consumer", defaultValue = "10000")
    private long ackTimeoutMillis = 10000;
    @UriParam(label = "consumer", defaultValue = "100")
    private long ackGroupTimeMillis = 100;

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
     * Type of the subscription [EXCLUSIVE|SHARED|FAILOVER], defaults to EXCLUSIVE
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
     * Name of the producer
     */
    public void setProducerName(String producerName) {
        this.producerName = producerName;
    }

    public String getConsumerNamePrefix() {
        return consumerNamePrefix;
    }

    /**
     * Prefix to add to consumer names when a SHARED or FAILOVER subscription is used
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
     * If this option is enabled, then messages are not immediately acknowledged after being consumed.
     * Instead, an instance of {@link PulsarMessageReceipt} is stored as a header on the {@link org.apache.camel.Exchange}.
     * Messages can then be acknowledged using {@link PulsarMessageReceipt} at any time before the ackTimeout occurs.
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
     * Group the consumer acknowledgments for the specified time in milliseconds - defaults to 100
     */
    public void setAckGroupTimeMillis(long ackGroupTimeMillis) {
        this.ackGroupTimeMillis = ackGroupTimeMillis;
    }
}
