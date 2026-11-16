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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer group of a Kafka consumer. Without the groupId option the consumer uses a generated group id, a new one
 * at each start of the application, and logs a WARN that says so. A {@link KafkaClientFactory} records the properties
 * of each Kafka consumer instead of connecting to a broker, and each start of the application is a new CamelContext.
 */
class KafkaConsumerGroupIdTest {

    private final String topic = "group-id-" + UUID.randomUUID();
    private final List<Properties> consumerProps = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Logger logger = (Logger) LogManager.getLogger(KafkaConsumer.class);
    private final AbstractAppender appender
            = new AbstractAppender("KafkaConsumerGroupIdTest", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    String message = event.getMessage().getFormattedMessage();
                    // only the events of the consumers of this test, as other tests may run at the same time
                    if (event.getLevel().isMoreSpecificThan(Level.WARN) && message.contains(topic)) {
                        warnings.add(message);
                    }
                }
            };

    @BeforeEach
    void captureWarnings() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void stopCapture() {
        logger.removeAppender(appender);
        appender.stop();
    }

    @Test
    void withoutGroupIdEachStartUsesANewGeneratedGroupAndWarns() throws Exception {
        String uri = "kafka:" + topic + "?brokers=localhost:9092&kafkaClientFactory=#recordingFactory";

        String first = startAndStopApplication(uri, 1).get(0);
        String second = startAndStopApplication(uri, 1).get(0);

        assertDoesNotThrow(() -> UUID.fromString(first));
        assertDoesNotThrow(() -> UUID.fromString(second));
        assertNotEquals(first, second);

        assertEquals(2, warnings.size(), "one warning at each start: " + warnings);
        assertWarnsAbout(warnings.get(0), first);
        assertWarnsAbout(warnings.get(1), second);
    }

    @Test
    void withoutGroupIdTheConsumersOfTheEndpointShareTheGeneratedGroupAndWarnOnce() throws Exception {
        String uri = "kafka:" + topic + "?brokers=localhost:9092&kafkaClientFactory=#recordingFactory&consumersCount=3";

        List<String> groups = startAndStopApplication(uri, 3);

        assertEquals(List.of(groups.get(0), groups.get(0), groups.get(0)), groups);
        assertEquals(1, warnings.size(), "one warning for the endpoint: " + warnings);
        assertWarnsAbout(warnings.get(0), groups.get(0));
    }

    @Test
    void withGroupIdEachStartUsesTheGroupWithoutWarning() throws Exception {
        String uri = "kafka:" + topic + "?brokers=localhost:9092&kafkaClientFactory=#recordingFactory&groupId=orders";

        assertEquals(List.of("orders"), startAndStopApplication(uri, 1));
        assertEquals(List.of("orders"), startAndStopApplication(uri, 1));

        assertEquals(List.of(), warnings);
    }

    private static void assertWarnsAbout(String warning, String group) {
        assertTrue(warning.contains(group), "the warning names the generated group " + group + ": " + warning);
        assertTrue(warning.contains("groupId"), "the warning names the groupId option: " + warning);
    }

    /**
     * Starts a CamelContext with a route from the given uri, waits for its Kafka consumers and stops it.
     *
     * @return the group.id of each Kafka consumer of the route
     */
    private List<String> startAndStopApplication(String uri, int consumers) throws Exception {
        int before = consumerProps.size();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("recordingFactory", new RecordingFactory());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(uri).routeId("kafka").to("mock:result");
                }
            });
            context.start();
            await().atMost(20, TimeUnit.SECONDS).until(() -> consumerProps.size() == before + consumers);
            context.stop();
        }
        return consumerProps.subList(before, before + consumers).stream()
                .map(props -> props.getProperty(ConsumerConfig.GROUP_ID_CONFIG))
                .toList();
    }

    private final class RecordingFactory implements KafkaClientFactory {
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
