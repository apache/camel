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

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The group.instance.id of each Kafka consumer of an endpoint. A static member id is allowed once in a consumer group,
 * so with consumersCount greater than one each consumer gets its own id. A {@link KafkaClientFactory} records the
 * properties of each Kafka consumer instead of connecting to a broker.
 */
class KafkaConsumerGroupInstanceIdTest extends CamelTestSupport {

    private final List<Properties> consumerProps = new CopyOnWriteArrayList<>();

    @BindToRegistry("recordingFactory")
    private final KafkaClientFactory factory = new KafkaClientFactory() {
        @Override
        public Producer getProducer(Properties kafkaProps) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer getConsumer(Properties kafkaProps) {
            consumerProps.add(kafkaProps);
            return new IdleConsumer();
        }

        @Override
        public String getBrokers(KafkaConfiguration configuration) {
            return "localhost:9092";
        }
    };

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @Test
    void eachConsumerGetsItsOwnGroupInstanceId() throws Exception {
        startRoute("&groupInstanceId=pod-1&consumersCount=3", 3);

        assertEquals(Set.of("pod-1-0", "pod-1-1", "pod-1-2"), groupInstanceIds());
    }

    @Test
    void eachConsumerGetsItsOwnGroupInstanceIdFromAdditionalProperties() throws Exception {
        startRoute("&additionalProperties.group.instance.id=pod-1&consumersCount=2", 2);

        assertEquals(Set.of("pod-1-0", "pod-1-1"), groupInstanceIds());
    }

    @Test
    void oneConsumerKeepsTheGroupInstanceId() throws Exception {
        startRoute("&groupInstanceId=pod-1", 1);

        assertEquals("pod-1", consumerProps.get(0).getProperty(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG));
    }

    @Test
    void dynamicMembersHaveNoGroupInstanceId() throws Exception {
        startRoute("&consumersCount=3", 3);

        for (Properties props : consumerProps) {
            assertNull(props.getProperty(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG));
        }
    }

    private Set<String> groupInstanceIds() {
        return consumerProps.stream()
                .map(props -> props.getProperty(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG))
                .collect(Collectors.toSet());
    }

    private void startRoute(String options, int consumers) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:instances?brokers=localhost:9092&kafkaClientFactory=#recordingFactory&groupId=group"
                     + options)
                        .routeId("kafka")
                        .to("mock:result");
            }
        });
        context.start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> consumerProps.size() == consumers);
        assertEquals(consumers, consumerProps.size());
    }

    /**
     * A consumer of an idle topic: a poll waits up to its timeout, unless the consumer is woken up.
     */
    private static final class IdleConsumer extends MockConsumer<Object, Object> {
        private final CountDownLatch wokenUp = new CountDownLatch(1);

        IdleConsumer() {
            super("earliest");
        }

        @Override
        public ConsumerRecords<Object, Object> poll(Duration timeout) {
            try {
                wokenUp.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ConsumerRecords.empty();
        }

        @Override
        public void wakeup() {
            wokenUp.countDown();
        }
    }
}
