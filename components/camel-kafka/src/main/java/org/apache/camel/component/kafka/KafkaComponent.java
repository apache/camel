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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.KafkaManualCommitFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("kafka")
public class KafkaComponent extends HealthCheckComponent implements SSLContextParametersAware {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaComponent.class);

    @Metadata
    private KafkaConfiguration configuration = new KafkaConfiguration();
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(autowired = true, label = "consumer,advanced")
    private KafkaManualCommitFactory kafkaManualCommitFactory;
    @Metadata(autowired = true, label = "advanced")
    private KafkaClientFactory kafkaClientFactory;
    @Metadata(autowired = true, label = "consumer,advanced")
    private PollExceptionStrategy pollExceptionStrategy;
    @Metadata(label = "consumer,advanced")
    private int createConsumerBackoffMaxAttempts;
    @Metadata(label = "consumer,advanced", defaultValue = "5000")
    private long createConsumerBackoffInterval = 5000;
    @Metadata(label = "consumer,advanced")
    private int subscribeConsumerBackoffMaxAttempts;
    @Metadata(label = "consumer,advanced", defaultValue = "5000")
    private long subscribeConsumerBackoffInterval = 5000;

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

        if (endpoint.getConfiguration().getSslContextParameters() == null) {
            endpoint.getConfiguration().setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        if (!endpointAdditionalProperties.isEmpty()) {
            Map<String, Object> map = new HashMap<>();
            // resolve parameter values from the values (#bean / #class etc)
            PropertyBindingSupport.bindProperties(getCamelContext(), map, endpointAdditionalProperties);
            // overwrite the additional properties from the endpoint
            endpoint.getConfiguration().getAdditionalProperties().putAll(map);
        }

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

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
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

    public PollExceptionStrategy getPollExceptionStrategy() {
        return pollExceptionStrategy;
    }

    /**
     * To use a custom strategy with the consumer to control how to handle exceptions thrown from the Kafka broker while
     * pooling messages.
     */
    public void setPollExceptionStrategy(PollExceptionStrategy pollExceptionStrategy) {
        this.pollExceptionStrategy = pollExceptionStrategy;
    }

    public int getCreateConsumerBackoffMaxAttempts() {
        return createConsumerBackoffMaxAttempts;
    }

    /**
     * Maximum attempts to create the kafka consumer (kafka-client), before eventually giving up and failing.
     *
     * Error during creating the consumer may be fatal due to invalid configuration and as such recovery is not
     * possible. However, one part of the validation is DNS resolution of the bootstrap broker hostnames. This may be a
     * temporary networking problem, and could potentially be recoverable. While other errors are fatal, such as some
     * invalid kafka configurations. Unfortunately, kafka-client does not separate this kind of errors.
     *
     * Camel will by default retry forever, and therefore never give up. If you want to give up after many attempts then
     * set this option and Camel will then when giving up terminate the consumer. To try again, you can manually restart
     * the consumer by stopping, and starting the route.
     */
    public void setCreateConsumerBackoffMaxAttempts(int createConsumerBackoffMaxAttempts) {
        this.createConsumerBackoffMaxAttempts = createConsumerBackoffMaxAttempts;
    }

    public long getCreateConsumerBackoffInterval() {
        return createConsumerBackoffInterval;
    }

    /**
     * The delay in millis seconds to wait before trying again to create the kafka consumer (kafka-client).
     */
    public void setCreateConsumerBackoffInterval(long createConsumerBackoffInterval) {
        this.createConsumerBackoffInterval = createConsumerBackoffInterval;
    }

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
     */
    public void setSubscribeConsumerBackoffMaxAttempts(int subscribeConsumerBackoffMaxAttempts) {
        this.subscribeConsumerBackoffMaxAttempts = subscribeConsumerBackoffMaxAttempts;
    }

    public long getSubscribeConsumerBackoffInterval() {
        return subscribeConsumerBackoffInterval;
    }

    /**
     * The delay in millis seconds to wait before trying again to subscribe to the kafka broker.
     */
    public void setSubscribeConsumerBackoffInterval(long subscribeConsumerBackoffInterval) {
        this.subscribeConsumerBackoffInterval = subscribeConsumerBackoffInterval;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // if a factory was not autowired then create a default factory
        if (kafkaClientFactory == null) {
            kafkaClientFactory = new DefaultKafkaClientFactory();
        }
        if (configuration.isAllowManualCommit() && kafkaManualCommitFactory == null) {
            LOG.warn("The component was setup for allowing manual commits, but a manual commit factory was not set");
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        Map<String, Object> map = new HashMap<>();
        // resolve parameter values from the values (#bean / #class etc)
        PropertyBindingSupport.bindProperties(getCamelContext(), map, configuration.getAdditionalProperties());
        configuration.setAdditionalProperties(map);
    }
}
