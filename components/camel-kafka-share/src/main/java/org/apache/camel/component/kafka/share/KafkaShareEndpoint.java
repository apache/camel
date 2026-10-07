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
package org.apache.camel.component.kafka.share;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consume messages from Apache Kafka topics as a queue, using a share group.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "kafka-share", title = "Kafka Share", syntax = "kafka-share:topic",
             consumerOnly = true, category = { Category.MESSAGING }, headersClass = KafkaShareConstants.class)
public class KafkaShareEndpoint extends DefaultEndpoint implements MultipleConsumersSupport, EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaShareEndpoint.class);

    private static final String CALLBACK_HANDLER_CLASS_CONFIG = "sasl.login.callback.handler.class";

    @UriParam
    private KafkaShareConfiguration configuration = new KafkaShareConfiguration();
    @UriParam(label = "advanced")
    private KafkaShareClientFactory kafkaShareClientFactory;

    public KafkaShareEndpoint() {
    }

    public KafkaShareEndpoint(String endpointUri, KafkaShareComponent component) {
        super(endpointUri, component);
    }

    @Override
    public KafkaShareComponent getComponent() {
        return (KafkaShareComponent) super.getComponent();
    }

    @Override
    public String getServiceUrl() {
        return configuration.getBrokers();
    }

    @Override
    public String getServiceProtocol() {
        return "kafka";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getClientId() != null) {
            return Map.of("clientId", configuration.getClientId());
        }
        return null;
    }

    public KafkaShareConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(KafkaShareConfiguration configuration) {
        this.configuration = configuration;
    }

    public KafkaShareClientFactory getKafkaShareClientFactory() {
        return kafkaShareClientFactory;
    }

    /**
     * Factory to use for creating {@link org.apache.kafka.clients.consumer.KafkaShareConsumer} instances. This allows
     * configuring a custom factory to create instances with logic that extends the vanilla Kafka clients.
     */
    public void setKafkaShareClientFactory(KafkaShareClientFactory kafkaShareClientFactory) {
        this.kafkaShareClientFactory = kafkaShareClientFactory;
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        if (kafkaShareClientFactory == null) {
            kafkaShareClientFactory = getComponent().getKafkaShareClientFactory();
        }
        if (kafkaShareClientFactory == null) {
            kafkaShareClientFactory = new DefaultKafkaShareClientFactory();
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        KafkaShareConsumer consumer = new KafkaShareConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException(
                "The kafka-share endpoint does not support producers, use the kafka component to send messages");
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    /**
     * Replaces the class names of the deserializers and of the SASL login callback handler with the classes, loaded
     * with the class resolver of the CamelContext.
     */
    void updateClassProperties(Properties props) {
        try {
            if (getCamelContext() != null) {
                ClassResolver resolver = getCamelContext().getClassResolver();
                replaceWithClass(props, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, resolver, Deserializer.class);
                replaceWithClass(props, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, resolver, Deserializer.class);
                // because the property is not available in Kafka client, use a static string
                replaceWithClass(props, CALLBACK_HANDLER_CLASS_CONFIG, resolver, AuthenticateCallbackHandler.class);
            }
        } catch (Exception e) {
            // can ignore and Kafka itself might be able to handle it, if not, it will throw an exception
            LOG.debug("Problem loading classes for Deserializers", e);
        }
    }

    private void replaceWithClass(Properties props, String key, ClassResolver resolver, Class<?> type) {
        Object value = props.get(key);
        if (value == null || value instanceof Class) {
            return;
        }
        String name = value.toString();
        Class<?> c = resolver.resolveClass(name, type);
        if (c == null) {
            c = resolver.resolveClass(name, type, getClass().getClassLoader());
        }
        if (c == null) {
            c = resolver.resolveClass(name, type, org.apache.kafka.clients.consumer.KafkaShareConsumer.class.getClassLoader());
        }
        if (c != null) {
            props.put(key, c);
        }
    }

    ExecutorService createExecutor(Object source) {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(source,
                "KafkaShareConsumer[" + configuration.getTopic() + "]", configuration.getConsumersCount());
    }
}
