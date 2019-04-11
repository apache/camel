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

    @UriParam(label = "consumer", defaultValue = "subscription")
    private String subscriptionName = "subs";
    @UriParam(label = "consumer", enums = "EXCLUSIVE, SHARED, FAILOVER", defaultValue = "EXCLUSIVE")
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
}
