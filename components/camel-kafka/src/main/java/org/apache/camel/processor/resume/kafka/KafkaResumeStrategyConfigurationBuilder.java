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

package org.apache.camel.processor.resume.kafka;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import org.apache.camel.resume.Cacheable;
import org.apache.camel.support.resume.BasicResumeStrategyConfigurationBuilder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configuration builder appropriate for building configurations for the {@link SingleNodeKafkaResumeStrategy}
 */
public class KafkaResumeStrategyConfigurationBuilder
        extends
        BasicResumeStrategyConfigurationBuilder<KafkaResumeStrategyConfigurationBuilder, KafkaResumeStrategyConfiguration> {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaResumeStrategyConfigurationBuilder.class);

    private Properties producerProperties;
    private Properties consumerProperties;
    private String topic;
    private Duration maxInitializationDuration = Duration.ofSeconds(10);
    private int maxInitializationRetries = 5;

    private KafkaResumeStrategyConfigurationBuilder() {
    }

    public KafkaResumeStrategyConfigurationBuilder(Properties producerProperties, Properties consumerProperties) {
        this.producerProperties = ObjectHelper.notNull(producerProperties, "producerProperties");
        this.consumerProperties = ObjectHelper.notNull(consumerProperties, "consumerProperties");
    }

    @Override
    public KafkaResumeStrategyConfigurationBuilder withCacheFillPolicy(Cacheable.FillPolicy cacheFillPolicy) {
        if (cacheFillPolicy == Cacheable.FillPolicy.MINIMIZING) {
            consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        } else {
            consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        }

        return super.withCacheFillPolicy(cacheFillPolicy);
    }

    public KafkaResumeStrategyConfigurationBuilder withProducerProperty(String key, Object value) {
        producerProperties.put(key, value);

        return this;
    }

    public KafkaResumeStrategyConfigurationBuilder withConsumerProperty(String key, Object value) {
        consumerProperties.put(key, value);

        return this;
    }

    public KafkaResumeStrategyConfigurationBuilder withGroupId(String value) {
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, value);

        return this;
    }

    public KafkaResumeStrategyConfigurationBuilder withEnableAutoCommit(boolean value) {
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.valueOf(value));

        return this;
    }

    public KafkaResumeStrategyConfigurationBuilder withBootstrapServers(String value) {
        final String bootstrapServers = StringHelper.notEmpty(value, "bootstrapServers");

        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        return this;
    }

    public KafkaResumeStrategyConfigurationBuilder withTopic(String value) {
        this.topic = value;

        return this;
    }

    public KafkaResumeStrategyConfigurationBuilder withMaxInitializationDuration(Duration duration) {
        this.maxInitializationDuration = duration;

        return this;
    }

    public KafkaResumeStrategyConfigurationBuilder withMaxInitializationRetries(int retries) {
        this.maxInitializationRetries = retries;

        return this;
    }

    /**
     * Creates a basic consumer
     *
     * @return A set of default properties for consuming byte-based key/pair records from Kafka
     */
    public static Properties createConsumerProperties() {
        Properties config = new Properties();

        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return config;
    }

    /**
     * Creates a basic producer
     *
     * @return A set of default properties for producing byte-based key/pair records from Kafka
     */
    public static Properties createProducerProperties() {
        Properties config = new Properties();

        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        return config;
    }

    @Override
    public KafkaResumeStrategyConfiguration build() {
        KafkaResumeStrategyConfiguration resumeStrategyConfiguration = new KafkaResumeStrategyConfiguration();

        buildCommonConfiguration(resumeStrategyConfiguration);

        resumeStrategyConfiguration.setConsumerProperties(consumerProperties);
        resumeStrategyConfiguration.setProducerProperties(producerProperties);
        resumeStrategyConfiguration.setTopic(topic);
        resumeStrategyConfiguration.setMaxInitializationDuration(maxInitializationDuration);
        resumeStrategyConfiguration.setMaxInitializationRetries(maxInitializationRetries);

        return resumeStrategyConfiguration;
    }

    /**
     * Creates the most basic builder possible
     *
     * @return a pre-configured basic builder
     */
    public static KafkaResumeStrategyConfigurationBuilder newBuilder() {
        final Properties producerProperties = KafkaResumeStrategyConfigurationBuilder.createProducerProperties();
        final Properties consumerProperties = KafkaResumeStrategyConfigurationBuilder.createConsumerProperties();

        KafkaResumeStrategyConfigurationBuilder builder = new KafkaResumeStrategyConfigurationBuilder(
                producerProperties,
                consumerProperties);

        String groupId = UUID.randomUUID().toString();
        LOG.debug("Creating consumer with {}[{}]", ConsumerConfig.GROUP_ID_CONFIG, groupId);
        builder.withGroupId(groupId);
        builder.withEnableAutoCommit(true);
        builder.withCacheFillPolicy(Cacheable.FillPolicy.MAXIMIZING);

        return builder;
    }

    /**
     * Creates an empty builder
     *
     * @return an empty configuration builder
     */
    public static KafkaResumeStrategyConfigurationBuilder newEmptyBuilder() {
        final Properties producerProperties = new Properties();
        final Properties consumerProperties = new Properties();

        KafkaResumeStrategyConfigurationBuilder builder = new KafkaResumeStrategyConfigurationBuilder(
                producerProperties,
                consumerProperties);

        String groupId = UUID.randomUUID().toString();
        LOG.debug("Creating consumer with {}[{}]", ConsumerConfig.GROUP_ID_CONFIG, groupId);
        builder.withGroupId(groupId);
        builder.withEnableAutoCommit(true);

        return builder;
    }
}
