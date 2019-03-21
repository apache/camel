package org.apache.camel.component.pulsar.configuration;

import org.apache.camel.component.pulsar.PulsarUri;
import org.apache.camel.component.pulsar.utils.consumers.SubscriptionType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.pulsar.client.api.PulsarClient;

@UriParams
public class PulsarEndpointConfiguration {

    @UriPath(label = "consumer, producer", description = "Whether the topic is persistent or not", enums = "persistent, non-persistent")
    @Metadata(required = "true")
    private String topicType;
    @UriPath(label = "consumer, producer", description = "Pulsar tenant for the topic namespace to reside in")
    @Metadata(required = "true")
    private String tenant;
    @UriPath(label = "consumer, producer", description = "Pulsar namespace for the topic to reside in")
    @Metadata(required = "true")
    private String namespace;
    @UriPath(label = "consumer, producer", description = "Name of the Pulsar topic")
    @Metadata(required = "true")
    private String topic;
    @UriParam(label = "consumer", description = "Name of the subscription to use")
    private String subscriptionName;
    @UriParam(label = "consumer", description = "Type of the subscription", enums = "EXCLUSIVE, SHARED, FAILOVER")
    private SubscriptionType subscriptionType;
    @UriParam(label = "consumer", description = "Number of consumers")
    private int numberOfConsumers;
    @UriParam(label = "consumer", description = "Size of the consumer queue")
    private int consumerQueueSize;
    @UriParam(label = "consumer", description = "Name of the consumer")
    private String consumerName;
    @UriParam(label = "producer", description = "Name of the producer")
    private String producerName;
    @UriParam(label = "consumer", description = "Prefix to add to consumer names")
    private String consumerNamePrefix;
    @UriParam(label = "consumer, producer", description = "The pulsar client")
    private PulsarClient pulsarClient;

    public PulsarEndpointConfiguration(PulsarUri uri) {
        this.topicType = uri.getType();
        this.tenant = uri.getTenant();
        this.namespace = uri.getNamespace();
        this.topic = uri.getTopic();
    }

    public String getTopicType() {
        return topicType;
    }

    public void setTopicType(String topicType) {
        this.topicType = topicType;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

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

    public PulsarClient getPulsarClient() {
        return pulsarClient;
    }

    public void setPulsarClient(PulsarClient pulsarClient) {
        this.pulsarClient = pulsarClient;
    }
}
