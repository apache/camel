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
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;

@Component("kafka")
public class KafkaComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata
    private KafkaConfiguration configuration = new KafkaConfiguration();
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "consumer,advanced")
    private KafkaManualCommitFactory kafkaManualCommitFactory = new DefaultKafkaManualCommitFactory();
    @Metadata(autowired = true, label = "advanced")
    private KafkaClientFactory kafkaClientFactory = new DefaultKafkaClientFactory();
    @Metadata(autowired = true, label = "consumer,advanced")
    private PollExceptionStrategy pollExceptionStrategy;

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
        endpoint.getConfiguration().setTopic(remaining);

        setProperties(endpoint, parameters);

        if (endpoint.getConfiguration().getSslContextParameters() == null) {
            endpoint.getConfiguration().setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        // overwrite the additional properties from the endpoint
        if (!endpointAdditionalProperties.isEmpty()) {
            endpoint.getConfiguration().getAdditionalProperties().putAll(endpointAdditionalProperties);
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
     * {@link org.apache.kafka.clients.producer.KafkaProducer} instances. This allows to configure a custom factory to
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

}
