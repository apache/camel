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

import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.consumer.KafkaManualCommitFactory;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.util.CastUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sent and receive messages to/from an Apache Kafka broker.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = "kafka", title = "Kafka", syntax = "kafka:topic",
             category = { Category.MESSAGING }, headersClass = KafkaConstants.class)
public class KafkaEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaEndpoint.class);

    private static final String CALLBACK_HANDLER_CLASS_CONFIG = "sasl.login.callback.handler.class";

    @UriParam
    private KafkaConfiguration configuration = new KafkaConfiguration();
    @UriParam(label = "advanced")
    private KafkaClientFactory kafkaClientFactory;
    @UriParam(label = "consumer,advanced")
    private KafkaManualCommitFactory kafkaManualCommitFactory;

    public KafkaEndpoint() {
    }

    public KafkaEndpoint(String endpointUri, KafkaComponent component) {
        super(endpointUri, component);
    }

    @Override
    public KafkaComponent getComponent() {
        return (KafkaComponent) super.getComponent();
    }

    public KafkaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(KafkaConfiguration configuration) {
        this.configuration = configuration;
    }

    public KafkaClientFactory getKafkaClientFactory() {
        return this.kafkaClientFactory;
    }

    /**
     * Factory to use for creating {@link org.apache.kafka.clients.consumer.KafkaConsumer} and
     * {@link org.apache.kafka.clients.producer.KafkaProducer} instances. This allows to configure a custom factory to
     * create instances with logic that extends the vanilla Kafka clients.
     */
    public void setKafkaClientFactory(KafkaClientFactory kafkaClientFactory) {
        this.kafkaClientFactory = kafkaClientFactory;
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

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        if (kafkaClientFactory == null) {
            kafkaClientFactory = getComponent().getKafkaClientFactory();
        }
        if (kafkaManualCommitFactory == null) {
            kafkaManualCommitFactory = getComponent().getKafkaManualCommitFactory();
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        KafkaConsumer consumer = new KafkaConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        KafkaProducer producer = createProducer(this);
        if (getConfiguration().isSynchronous()) {
            return new SynchronousDelegateProducer(producer);
        } else {
            return producer;
        }
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    <T> Class<T> loadClass(Object o, ClassResolver resolver, Class<T> type) {
        if (o == null || o instanceof Class) {
            return CastUtils.cast((Class<?>) o);
        }
        String name = o.toString();
        Class<T> c = resolver.resolveClass(name, type);
        if (c == null) {
            c = resolver.resolveClass(name, type, getClass().getClassLoader());
        }
        if (c == null) {
            c = resolver.resolveClass(name, type, org.apache.kafka.clients.producer.KafkaProducer.class.getClassLoader());
        }
        return c;
    }

    void replaceWithClass(Properties props, String key, ClassResolver resolver, Class<?> type) {
        Class<?> c = loadClass(props.get(key), resolver, type);
        if (c != null) {
            props.put(key, c);
        }
    }

    public void updateClassProperties(Properties props) {
        try {
            if (getCamelContext() != null) {
                ClassResolver resolver = getCamelContext().getClassResolver();
                replaceWithClass(props, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, resolver, Serializer.class);
                replaceWithClass(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, resolver, Serializer.class);
                replaceWithClass(props, ProducerConfig.PARTITIONER_CLASS_CONFIG, resolver, Partitioner.class);
                replaceWithClass(props, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, resolver, Deserializer.class);
                replaceWithClass(props, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, resolver, Deserializer.class);

                // because he property is not available in Kafka client, use a static string
                replaceWithClass(props, CALLBACK_HANDLER_CLASS_CONFIG, resolver, AuthenticateCallbackHandler.class);
            }
        } catch (Exception t) {
            // can ignore and Kafka itself might be able to handle it, if not,
            // it will throw an exception
            LOG.debug("Problem loading classes for Serializers", t);
        }
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                "KafkaConsumer[" + configuration.getTopic() + "]", configuration.getConsumersCount());
    }

    public ExecutorService createProducerExecutor() {
        int core = getConfiguration().getWorkerPoolCoreSize();
        int max = getConfiguration().getWorkerPoolMaxSize();
        return getCamelContext().getExecutorServiceManager().newThreadPool(this,
                "KafkaProducer[" + configuration.getTopic() + "]", core, max);
    }

    protected KafkaProducer createProducer(KafkaEndpoint endpoint) {
        return new KafkaProducer(endpoint);
    }

}
