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

package org.apache.camel.component.kafka.integration.common;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.requests.CreateTopicsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public final class KafkaTestUtil {
    public static final String MOCK_RESULT = "mock:result";
    public static final String MOCK_RESULT_BAR = "mock:resultBar";
    public static final String MOCK_DLQ = "mock:dlq";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaTestUtil.class);

    private KafkaTestUtil() {

    }

    public static void setServiceProperties(KafkaService service) {
        LOG.info("### Kafka cluster broker list: {}", service.getBootstrapServers());
        System.setProperty("bootstrapServers", service.getBootstrapServers());
    }

    public static AdminClient createAdminClient(KafkaService service) {
        final Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());

        return KafkaAdminClient.create(properties);
    }

    public static Properties getDefaultProperties(String bootstrapService) {
        LOG.info("Connecting to Kafka {}", bootstrapService);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapService);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaConstants.KAFKA_DEFAULT_SERIALIZER);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return props;
    }

    public static Properties getDefaultProperties(KafkaService service) {
        return getDefaultProperties(service.getBootstrapServers());
    }

    public static void configureKafkaComponent(CamelContext context, String bootstrapServers) {
        LOG.info("Configuring Kafka using bootstrap address {}", bootstrapServers);

        context.getPropertiesComponent().setLocation("ref:prop");

        KafkaComponent kafka = new KafkaComponent(context);
        kafka.init();
        kafka.getConfiguration().setBrokers(bootstrapServers);
        context.addComponent("kafka", kafka);
    }

    public static void createTopic(KafkaService service, String topic, int numPartitions) {
        AdminClient kafkaAdminClient = createAdminClient(service);
        NewTopic testTopic = new NewTopic(topic, numPartitions, CreateTopicsRequest.NO_REPLICATION_FACTOR);
        kafkaAdminClient.createTopics(Collections.singleton(testTopic));
        KafkaFuture<TopicDescription> tdFuture
                = kafkaAdminClient.describeTopics(Collections.singletonList(topic)).topicNameValues().get(topic);

        try {
            TopicDescription td = tdFuture.get(5L, TimeUnit.SECONDS);
            List<TopicPartitionInfo> pi = td.partitions();
            assertEquals(numPartitions, pi.size());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
