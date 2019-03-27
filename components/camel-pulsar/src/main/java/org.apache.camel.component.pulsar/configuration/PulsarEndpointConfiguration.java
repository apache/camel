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
package org.apache.camel.component.pulsar.configuration;

import org.apache.camel.component.pulsar.utils.consumers.SubscriptionType;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.impl.ClientBuilderImpl;

import static org.apache.camel.component.pulsar.utils.consumers.SubscriptionType.EXCLUSIVE;

@UriParams
public class PulsarEndpointConfiguration {

    @UriParam(label = "consumer", description = "Name of the subscription to use", defaultValue = "subscription")
    private String subscriptionName = "subs";
    @UriParam(label = "consumer", description = "Type of the subscription", enums = "EXCLUSIVE, SHARED, FAILOVER", defaultValue = "EXCLUSIVE")
    private SubscriptionType subscriptionType = EXCLUSIVE;
    @UriParam(label = "consumer", description = "Number of consumers", defaultValue = "1")
    private int numberOfConsumers = 1;
    @UriParam(label = "consumer", description = "Size of the consumer queue", defaultValue = "10")
    private int consumerQueueSize = 10;
    @UriParam(label = "consumer", description = "Name of the consumer when subscription is EXCLUSIVE", defaultValue = "sole-consumer")
    private String consumerName = "sole-consumer";
    @UriParam(label = "producer", description = "Name of the producer", defaultValue = "default-producer")
    private String producerName = "default-producer";
    @UriParam(label = "consumer", description = "Prefix to add to consumer names when a SHARED or FAILOVER subscription is used", defaultValue = "cons")
    private String consumerNamePrefix = "cons";
    @UriParam(label = "consumer, producer", description = "The pulsar client")
    private PulsarClient pulsarClient;
    @UriParam(label = "consumer, producer", description = "Url for the Pulsar Broker")
    private String pulsarBrokerUrl;

    public String getSubscriptionName() {
        return subscriptionName;
    }

    public void setSubscriptionName(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public int getNumberOfConsumers() {
        return numberOfConsumers;
    }

    public void setNumberOfConsumers(int numberOfConsumers) {
        this.numberOfConsumers = numberOfConsumers;
    }

    public int getConsumerQueueSize() {
        return consumerQueueSize;
    }

    public void setConsumerQueueSize(int consumerQueueSize) {
        this.consumerQueueSize = consumerQueueSize;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public String getProducerName() {
        return producerName;
    }

    public void setProducerName(String producerName) {
        this.producerName = producerName;
    }

    public String getConsumerNamePrefix() {
        return consumerNamePrefix;
    }

    public void setConsumerNamePrefix(String consumerNamePrefix) {
        this.consumerNamePrefix = consumerNamePrefix;
    }

    public PulsarClient getPulsarClient() throws PulsarClientException {
        if (pulsarClient == null) {
            pulsarClient = new ClientBuilderImpl().serviceUrl("pulsar://localhost:6650").build();
        }
        return pulsarClient;
    }

    public void setPulsarClient(PulsarClient pulsarClient) {
        this.pulsarClient = pulsarClient;
    }

    public String getPulsarBrokerUrl() {
        return pulsarBrokerUrl;
    }

    public void setPulsarBrokerUrl(String pulsarBrokerUrl) {
        this.pulsarBrokerUrl = pulsarBrokerUrl;
    }
}
