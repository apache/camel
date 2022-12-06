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
package org.apache.camel.component.kafka.integration;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.test.infra.kafka.services.ContainerLocalAuthKafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tags({ @Tag("non-abstract") })
public abstract class BaseEmbeddedKafkaAuthTestSupport extends CamelTestSupport {
    @RegisterExtension
    public static ContainerLocalAuthKafkaService service = new ContainerLocalAuthKafkaService("/kafka-jaas.config");

    protected static AdminClient kafkaAdminClient;

    private static final Logger LOG = LoggerFactory.getLogger(BaseEmbeddedKafkaAuthTestSupport.class);

    @BeforeAll
    public static void beforeClass() {
        LOG.info("Embedded Kafka cluster broker list: {}", service.getBootstrapServers());
        System.setProperty("bootstrapServers", service.getBootstrapServers());
    }

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = createAdminClient();
        }
    }

    public static Properties getDefaultProperties(KafkaService service) {
        LOG.info("Connecting to Kafka {}", service.getBootstrapServers());

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_PARTITIONER);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return props;
    }

    protected Properties getDefaultProperties() {
        return getDefaultProperties(service);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:prop");

        KafkaComponent kafka = new KafkaComponent(context);
        kafka.init();
        kafka.getConfiguration().setBrokers(service.getBootstrapServers());
        context.addComponent("kafka", kafka);

        return context;
    }

    protected static String getBootstrapServers() {
        return service.getBootstrapServers();
    }

    private static AdminClient createAdminClient() {
        return createAdminClient(service);
    }

    public static AdminClient createAdminClient(KafkaService service) {
        final Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());

        return KafkaAdminClient.create(properties);
    }
}
