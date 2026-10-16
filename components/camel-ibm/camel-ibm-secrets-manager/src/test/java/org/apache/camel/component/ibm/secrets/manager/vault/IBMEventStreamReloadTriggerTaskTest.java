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
package org.apache.camel.component.ibm.secrets.manager.vault;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.vault.IBMSecretsManagerVaultConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IBMEventStreamReloadTriggerTaskTest {

    private static final String TOPIC = "secrets-events";
    private static final String ROTATED = "{\"data\":{\"event_type\":\"secret_rotated\",\"secrets\":"
                                          + "[{\"secret_name\":\"database-password\",\"event_time\":\"2026-10-16T10:00:00Z\"}]}}";

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"data\":{\"secrets\":[]}}",
            "{\"data\":{\"event_type\":\"secret_rotated\"}}" })
    void rotationAfterARecordThatCannotBeReadStillTriggersTheReload(String unreadable) throws Exception {
        CountDownLatch reloaded = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.addService(new DefaultContextReloadStrategy() {
                @Override
                public void onReload(Object source) {
                    reloaded.countDown();
                }
            });
            IBMSecretsManagerVaultConfiguration ibm = context.getVaultConfiguration().ibmSecretsManager();
            // the reload trigger task looks up the IBM properties function, which needs these
            ibm.setToken("api-key");
            ibm.setServiceUrl("https://localhost:9443");
            ibm.setSecrets("database-password");
            ibm.setEventStreamBootstrapServers("localhost:9092");
            ibm.setEventStreamTopic(TOPIC);
            ibm.setEventStreamGroupId("camel");
            ibm.setEventStreamPassword("secret");
            ibm.setEventStreamConsumerPollTimeout(100);
            context.start();

            IBMEventStreamReloadTriggerTask task = new IBMEventStreamReloadTriggerTask();
            task.setCamelContext(context);
            task.start();
            // replace the client that doStart created (it never connected) by one that returns the given records
            KafkaConsumer<String, String> created = task.kafkaConsumer;
            ScriptedConsumer scripted = new ScriptedConsumer();
            task.kafkaConsumer = scripted;
            created.close(Duration.ZERO);

            // an event that the task cannot read, then the rotation of a secret that the task watches
            scripted.batches.add(unreadable);
            scripted.batches.add(ROTATED);

            // the task runs once, on its own thread, for as long as Camel runs (as the period task scheduler runs it)
            Future<?> running = executor.submit(task);
            boolean reloadTriggered = reloaded.await(10, TimeUnit.SECONDS);
            boolean taskEnded = running.isDone();

            task.stop();
            assertTrue(reloadTriggered,
                    "the rotation of a watched secret did not trigger a reload after the record [" + unreadable + "]");
            assertFalse(taskEnded, "the reload trigger task ended while Camel was running");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "the reload trigger task did not end");
        }
    }

    /**
     * A Kafka consumer that is never connected: each poll returns the next queued record value, or no record when none
     * is queued within the poll timeout.
     */
    private static final class ScriptedConsumer extends KafkaConsumer<String, String> {

        private final BlockingQueue<String> batches = new LinkedBlockingQueue<>();
        private long offset;

        ScriptedConsumer() {
            super(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                    ConsumerConfig.GROUP_ID_CONFIG, "camel",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        }

        @Override
        public ConsumerRecords<String, String> poll(Duration timeout) {
            String value;
            try {
                value = batches.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ConsumerRecords.empty();
            }
            if (value == null) {
                return ConsumerRecords.empty();
            }
            TopicPartition partition = new TopicPartition(TOPIC, 0);
            return new ConsumerRecords<>(
                    Map.of(partition, List.of(new ConsumerRecord<>(TOPIC, 0, offset++, "key", value))), Map.of());
        }
    }
}
