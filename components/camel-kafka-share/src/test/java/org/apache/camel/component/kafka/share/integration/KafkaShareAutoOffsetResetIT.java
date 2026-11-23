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
package org.apache.camel.component.kafka.share.integration;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A new share group with shareAutoOffsetReset=earliest reads the records that were produced before the consumer
 * started: the consumer sets share.auto.offset.reset of the group with the Admin client (CAMEL-27379).
 */
@DisabledIfSystemProperty(named = "kafka.instance.type", matches = "(?!local-kafka-container$).+",
                          disabledReason = "Share groups are only configured on the Apache Kafka container")
class KafkaShareAutoOffsetResetIT extends CamelTestSupport {

    private static final String TOPIC = "share-earliest";
    private static final String GROUP = "share-earliest-group";

    @RegisterExtension
    static KafkaService service = KafkaServiceFactory.createSingletonService();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share:" + TOPIC + "?brokers=" + service.getBootstrapServers() + "&groupId=" + GROUP
                     + "&shareAutoOffsetReset=earliest")
                        .routeId("earliest").autoStartup(false)
                        .to("mock:earliest");
            }
        };
    }

    @Test
    void newShareGroupReadsTheRecordsProducedBeforeTheConsumerStarted() throws Exception {
        try (Admin admin = Admin.create(adminProps())) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get(30, TimeUnit.SECONDS);
            produce(3);

            MockEndpoint earliest = getMockEndpoint("mock:earliest");
            earliest.expectedBodiesReceived("message-0", "message-1", "message-2");

            context.getRouteController().startRoute("earliest");

            earliest.assertIsSatisfied(60000);

            ConfigResource group = new ConfigResource(ConfigResource.Type.GROUP, GROUP);
            Config config = admin.describeConfigs(List.of(group)).all().get(30, TimeUnit.SECONDS).get(group);
            assertThat(config.get("share.auto.offset.reset").value()).isEqualTo("earliest");
        }
    }

    private static Properties adminProps() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        return props;
    }

    private static void produce(int count) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, service.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < count; i++) {
                producer.send(new ProducerRecord<>(TOPIC, "key-" + i, "message-" + i));
            }
            producer.flush();
        }
    }
}
