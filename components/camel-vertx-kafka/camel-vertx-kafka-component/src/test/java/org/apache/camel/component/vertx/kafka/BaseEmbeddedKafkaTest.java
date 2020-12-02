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
package org.apache.camel.component.vertx.kafka;

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.internals.DefaultPartitioner;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseEmbeddedKafkaTest extends CamelTestSupport {
    @RegisterExtension
    public static KafkaService service = KafkaServiceFactory.createService();

    protected static AdminClient kafkaAdminClient;

    private static final Logger LOG = LoggerFactory.getLogger(BaseEmbeddedKafkaTest.class);

    @BeforeAll
    public static void beforeClass() {
        LOG.info("### Embedded Kafka cluster broker list: " + service.getBootstrapServers());
        System.setProperty("bootstrapServers", service.getBootstrapServers());
    }

    @BeforeEach
    public void setKafkaAdminClient() {
        if (kafkaAdminClient == null) {
            kafkaAdminClient = createAdminClient();
        }
    }

    protected Properties getDefaultProperties() {
        LOG.info("Connecting to Kafka {}", service.getBootstrapServers());

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, DefaultPartitioner.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return props;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:prop");

        VertxKafkaComponent kafka = new VertxKafkaComponent(context);
        kafka.init();
        kafka.getConfiguration().setBootstrapServers(service.getBootstrapServers());
        context.addComponent("vertx-kafka", kafka);

        return context;
    }

    private static AdminClient createAdminClient() {
        final Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());

        return KafkaAdminClient.create(properties);
    }
}
