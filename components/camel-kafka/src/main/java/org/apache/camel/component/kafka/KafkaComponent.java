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
package org.apache.camel.component.kafka;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.KafkaManualCommitFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("kafka")
public class KafkaComponent extends AbstractKafkaComponent {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaComponent.class);

    @Metadata
    private KafkaConfiguration configuration = new KafkaConfiguration();
    @Metadata(autowired = true, label = "consumer,advanced")
    private KafkaManualCommitFactory kafkaManualCommitFactory;
    @Metadata(autowired = true, label = "advanced")
    private KafkaClientFactory kafkaClientFactory;
    @Deprecated
    @Metadata(label = "consumer,advanced")
    private int subscribeConsumerBackoffMaxAttempts;
    @Deprecated
    @Metadata(label = "consumer,advanced", defaultValue = "5000")
    private long subscribeConsumerBackoffInterval = 5000;
    @Metadata(label = "consumer,advanced")
    private boolean subscribeConsumerTopicMustExists;

    public KafkaComponent() {
    }

    public KafkaComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected KafkaEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("Topic must be configured on endpoint using syntax kafka:topic");
        }

        // extract the endpoint additional properties map
        final Map<String, Object> endpointAdditionalProperties
                = PropertiesHelper.extractProperties(parameters, "additionalProperties.");

        KafkaEndpoint endpoint = new KafkaEndpoint(uri, this);

        KafkaConfiguration copy = getConfiguration().copy();
        endpoint.setConfiguration(copy);

        setProperties(endpoint, parameters);

        configureEndpoint(endpoint.getConfiguration(), endpointAdditionalProperties);

        // If a topic is not defined in the KafkaConfiguration (set as option parameter) but only in the uri,
        // it can happen that it is not set correctly in the configuration of the endpoint.
        // Therefore, the topic is added after setProperties method
        // and a null check to avoid overwriting a value from the configuration.
        if (endpoint.getConfiguration().getTopic() == null) {
            endpoint.getConfiguration().setTopic(remaining);
        }

        return endpoint;
    }

    public KafkaConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Allows to pre-configure the Kafka component with common options that the endpoints will reuse.
     */
    public void setConfiguration(KafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    public KafkaManualCommitFactory getKafkaManualCommitFactory() {
        return kafkaManualCommitFactory;
    }

    /**
     * Factory to use for creating {@link KafkaManualCommit} instances. This allows to plugin a custom factory to create
     * custom {@link KafkaManualCommit} instances in case special logic is needed when doing manual commits that
     * deviates from the default implementation that comes out of the box.
     */
    public void setKafkaManualCommitFactory(KafkaManualCommitFactory kafkaManualCommitFactory) {
        this.kafkaManualCommitFactory = kafkaManualCommitFactory;
    }

    public KafkaClientFactory getKafkaClientFactory() {
        return kafkaClientFactory;
    }

    /**
     * Factory to use for creating {@link org.apache.kafka.clients.consumer.KafkaConsumer} and
     * {@link org.apache.kafka.clients.producer.KafkaProducer} instances. This allows configuring a custom factory to
     * create instances with logic that extends the vanilla Kafka clients.
     */
    public void setKafkaClientFactory(KafkaClientFactory kafkaClientFactory) {
        this.kafkaClientFactory = kafkaClientFactory;
    }

    /**
     * @deprecated Use {@link #getCreateConsumerBackoffMaxAttempts()} instead. Since Camel 4.22, the consumer creation
     *             and subscription are handled by a single reconnection task that uses the createConsumerBackoff*
     *             options.
     */
    @Deprecated
    public int getSubscribeConsumerBackoffMaxAttempts() {
        return subscribeConsumerBackoffMaxAttempts;
    }

    /**
     * Maximum number the kafka consumer will attempt to subscribe to the kafka broker, before eventually giving up and
     * failing.
     *
     * Error during subscribing the consumer to the kafka topic could be temporary errors due to network issues, and
     * could potentially be recoverable.
     *
     * Camel will by default retry forever, and therefore never give up. If you want to give up after many attempts,
     * then set this option and Camel will then when giving up terminate the consumer. You can manually restart the
     * consumer by stopping and starting the route, to try again.
     *
     * @deprecated Use {@link #setCreateConsumerBackoffMaxAttempts(int)} instead. Since Camel 4.22, the consumer
     *             creation and subscription are handled by a single reconnection task that uses the
     *             createConsumerBackoff* options.
     */
    @Deprecated
    public void setSubscribeConsumerBackoffMaxAttempts(int subscribeConsumerBackoffMaxAttempts) {
        this.subscribeConsumerBackoffMaxAttempts = subscribeConsumerBackoffMaxAttempts;
    }

    /**
     * @deprecated Use {@link #getCreateConsumerBackoffInterval()} instead. Since Camel 4.22, the consumer creation and
     *             subscription are handled by a single reconnection task that uses the createConsumerBackoff* options.
     */
    @Deprecated
    public long getSubscribeConsumerBackoffInterval() {
        return subscribeConsumerBackoffInterval;
    }

    /**
     * The delay in millis seconds to wait before trying again to subscribe to the kafka broker.
     *
     * @deprecated Use {@link #setCreateConsumerBackoffInterval(long)} instead. Since Camel 4.22, the consumer creation
     *             and subscription are handled by a single reconnection task that uses the createConsumerBackoff*
     *             options.
     */
    @Deprecated
    public void setSubscribeConsumerBackoffInterval(long subscribeConsumerBackoffInterval) {
        this.subscribeConsumerBackoffInterval = subscribeConsumerBackoffInterval;
    }

    public boolean isSubscribeConsumerTopicMustExists() {
        return subscribeConsumerTopicMustExists;
    }

    /**
     * Whether when a Camel Kafka consumer is subscribing to a Kafka broker then check whether a topic already exist on
     * the broker, and fail if it does not. Otherwise, the Camel Kafka consumer will keep attempt to consume from the
     * topic, until it's created on the Kafka broker; and until then the Camel Kafka consumer will fail and log a WARN
     * about UNKNOWN_TOPIC_OR_PARTITION.
     *
     * The option createConsumerBackoffMaxAttempts can be configured to give up trying to subscribe after a given number
     * of attempts.
     */
    public void setSubscribeConsumerTopicMustExists(boolean subscribeConsumerTopicMustExists) {
        this.subscribeConsumerTopicMustExists = subscribeConsumerTopicMustExists;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // if a factory was not autowired then create a default factory
        // NOTE: must be done in doStart() rather than doInit(), because when a component is
        // registered via addComponent() (the path used by Spring Boot), doInit() runs before
        // the autowiring lifecycle strategy has a chance to inject a custom factory.
        if (kafkaClientFactory == null) {
            kafkaClientFactory = new DefaultKafkaClientFactory();
        }
        if (configuration.isAllowManualCommit() && kafkaManualCommitFactory == null) {
            LOG.warn("The component was setup for allowing manual commits, but a manual commit factory was not set");
        }

        bindAdditionalProperties(configuration);
    }

}
