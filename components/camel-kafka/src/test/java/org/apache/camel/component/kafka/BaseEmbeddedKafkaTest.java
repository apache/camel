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

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class BaseEmbeddedKafkaTest extends CamelTestSupport {
    private static final String CONFLUENT_PLATFORM_VERSION = "5.3.1";

    @ClassRule
    public static KafkaContainer kafkaBroker = new KafkaContainer(CONFLUENT_PLATFORM_VERSION)
        .withEmbeddedZookeeper()
        .waitingFor(Wait.forListeningPort());

    private static final Logger LOG = LoggerFactory.getLogger(BaseEmbeddedKafkaTest.class);

    @BeforeClass
    public static void beforeClass() {
        LOG.info("### Embedded Kafka cluster broker list: " + kafkaBroker.getBootstrapServers());
    }

    protected Properties getDefaultProperties() {
        LOG.info("Connecting to Kafka {}", kafkaBroker.getBootstrapServers());

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_PARTITIONER);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return props;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:prop");

        KafkaComponent kafka = new KafkaComponent(context);
        kafka.init();
        kafka.getConfiguration().setBrokers(kafkaBroker.getBootstrapServers());
        context.addComponent("kafka", kafka);

        return context;
    }

    protected static String getBootstrapServers() {
        return kafkaBroker.getBootstrapServers();
    }
}
