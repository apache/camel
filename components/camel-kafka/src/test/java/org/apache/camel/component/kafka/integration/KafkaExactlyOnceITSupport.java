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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;

/**
 * Shared real-Kafka and real-Camel test infrastructure for the exactly-once integration tests.
 */
abstract class KafkaExactlyOnceITSupport {
    @RegisterExtension
    protected static final KafkaService service = KafkaServiceFactory.createSingletonService();

    protected final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    protected final List<String> failures = new CopyOnWriteArrayList<>();
    protected final List<CamelContext> contexts = new ArrayList<>();
    protected AdminClient admin;
    protected String id;

    @BeforeEach
    protected void setUp(TestInfo info) {
        id = info.getTestMethod().orElseThrow().getName() + "-" + Long.toHexString(System.nanoTime());
        admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, boot()));
    }

    @AfterEach
    protected void tearDown() {
        for (CamelContext context : contexts) {
            context.stop();
        }
        if (admin != null) {
            admin.close();
        }
    }

    protected static String boot() {
        return service.getBootstrapServers();
    }

    protected String source(String topic) {
        return source(topic, id);
    }

    protected static String source(String topic, String group) {
        return "kafka:" + topic + "?groupId=" + group + "&exactlyOnce=true&autoOffsetReset=earliest"
               + "&autoCommitEnable=false&allowManualCommit=true&breakOnFirstError=true&pollTimeoutMs=1000";
    }

    protected String sink(String topic, String txSuffix) {
        return "kafka:" + topic + "?exactlyOnce=true&transactionalId=" + id + "-" + txSuffix
               + "&requestRequiredAcks=-1";
    }

    protected static Map<String, String> poisonRecords() {
        Map<String, String> records = new LinkedHashMap<>();
        records.put("small-0", "small");
        records.put("big-1", "x".repeat(4000));
        records.put("small-2", "small");
        return records;
    }

    protected CamelContext newContext() {
        CamelContext context = new DefaultCamelContext();
        KafkaComponent kafka = new KafkaComponent(context);
        kafka.getConfiguration().setBrokers(boot());
        context.addComponent("kafka", kafka);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.ExchangeFailedEvent failed) {
                    failures.add(String.valueOf(failed.getExchange().getException()));
                }
            }
        });
        return context;
    }

    protected CamelContext start(RouteBuilder routes) throws Exception {
        CamelContext context = newContext();
        context.addRoutes(routes);
        context.start();
        contexts.add(context);
        return context;
    }

    protected static String key(Exchange e) {
        return e.getMessage().getHeader(KafkaConstants.KEY, String.class);
    }

    protected Processor countAttempt() {
        return e -> attempts.computeIfAbsent(key(e), k -> new AtomicInteger()).incrementAndGet();
    }

    protected static Processor failOnce(String key, AtomicBoolean failed) {
        return e -> {
            if (key.equals(key(e)) && failed.compareAndSet(false, true)) {
                throw new IllegalStateException("failure injected by the test after the send");
            }
        };
    }

    protected int attempts(String key) {
        AtomicInteger count = attempts.get(key);
        return count == null ? 0 : count.get();
    }

    protected List<String> sample() {
        return failures.stream().distinct().limit(3).toList();
    }

    protected static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    protected static boolean awaitQuietly(Callable<Boolean> condition, int seconds) {
        try {
            await().atMost(seconds, TimeUnit.SECONDS).until(condition);
            return true;
        } catch (ConditionTimeoutException e) {
            return false;
        }
    }

    protected void awaitCommitted(String topic, long offset) {
        await().atMost(60, TimeUnit.SECONDS).until(() -> Long.valueOf(offset).equals(committed(id, topic)));
    }

    /** Creates the topics id-name and returns their names. */
    protected String[] topics(String... names) throws Exception {
        String[] topics = new String[names.length];
        try (AdminClient client = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, boot()))) {
            List<NewTopic> newTopics = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                topics[i] = id + "-" + names[i];
                newTopics.add(new NewTopic(topics[i], 1, (short) 1));
            }
            client.createTopics(newTopics).all().get(30, TimeUnit.SECONDS);
        }
        return topics;
    }

    protected static void produce(String topic, String... keys) {
        Map<String, String> records = new LinkedHashMap<>();
        for (String key : keys) {
            records.put(key, key);
        }
        produce(topic, records);
    }

    protected static void produce(String topic, Map<String, String> records) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, boot());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            records.forEach((k, v) -> producer.send(new ProducerRecord<>(topic, k, v)));
            producer.flush();
        }
    }

    protected static List<String> readCommittedKeys(String topic) {
        return readCommitted(topic, true);
    }

    protected static List<String> readCommittedValues(String topic) {
        return readCommitted(topic, false);
    }

    protected static List<String> readCommitted(String topic, boolean keys) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, boot());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<String> result = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic, Duration.ofSeconds(10)).stream()
                    .map(p -> new TopicPartition(topic, p.partition())).toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline
                    && partitions.stream().anyMatch(tp -> consumer.position(tp) < end.get(tp))) {
                consumer.poll(Duration.ofMillis(200)).forEach(r -> result.add(keys ? r.key() : r.value()));
            }
        }
        return result;
    }

    protected Long committed(String group, String topic) throws Exception {
        OffsetAndMetadata offset = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
                .get(30, TimeUnit.SECONDS).get(new TopicPartition(topic, 0));
        return offset == null ? null : offset.offset();
    }
}
