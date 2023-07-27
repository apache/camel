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
package org.apache.camel.component.kafka.integration.pause;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.camel.component.kafka.integration.BaseEmbeddedKafkaTestSupport;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KafkaPausableConsumerIT extends BaseEmbeddedKafkaTestSupport {
    public static final String SOURCE_TOPIC = "pause-source";
    private static final Logger LOG = LoggerFactory.getLogger(KafkaPausableConsumerIT.class);
    private static final int RETRY_COUNT = 10;
    private static final LongAdder count = new LongAdder();
    private static final TestListener testConsumerListener = new TestListener();
    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private static boolean canContinue() {
        // First one should go through ...
        if (count.intValue() <= 1) {
            return true;
        }

        if (count.intValue() >= RETRY_COUNT) {
            return true;
        }

        return false;
    }

    public static int getCount() {
        return count.intValue();
    }

    @BeforeEach
    public void before() {
        Properties props = KafkaTestUtil.getDefaultProperties(service);
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        MockConsumerInterceptor.recordsCaptured.clear();

        executorService.scheduleAtFixedRate(this::increment, 5, 1, TimeUnit.SECONDS);
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        KafkaTestUtil.createAdminClient(service)
                .deleteTopics(Collections.singletonList(SOURCE_TOPIC)).all();

        executorService.shutdownNow();
    }

    public void increment() {
        count.increment();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("kafka:" + SOURCE_TOPIC
                     + "?groupId=KafkaPausableConsumerIT&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                     + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                     + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
                        .pausable(testConsumerListener, o -> canContinue())
                        .routeId("pausable-it")
                        .process(exchange -> LOG.info("Got record from Kafka: {}", exchange.getMessage().getBody()))
                        .to("direct:intermediate");

                from("direct:intermediate")
                        .process(exchange -> {
                            LOG.info("Got record on the intermediate processor: {}", exchange.getMessage().getBody());

                            if (getCount() <= RETRY_COUNT) {
                                throw new RuntimeCamelException("Error");
                            }
                        })
                        .to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void kafkaMessageIsConsumedByCamel() throws InterruptedException {
        String propagatedHeaderKey = "PropagatedCustomHeader";
        byte[] propagatedHeaderValue = "propagated header value".getBytes();
        String skippedHeaderKey = "CamelSkippedHeader";

        // Although all messages will be sent more than once to the exception only 5 messages should reach the final
        // destination, because sending them on the first few tries should fail

        MockEndpoint to = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");

        // The LAST_RECORD_BEFORE_COMMIT header should not be configured on any
        // exchange because autoCommitEnable=true
        to.expectedHeaderValuesReceivedInAnyOrder(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, null, null, null, null, null);
        to.expectedHeaderReceived(propagatedHeaderKey, propagatedHeaderValue);

        for (int k = 0; k < 5; k++) {
            String msg = "message-" + k;
            ProducerRecord<String, String> data = new ProducerRecord<>(SOURCE_TOPIC, "1", msg);
            data.headers().add(new RecordHeader("CamelSkippedHeader", "skipped header value".getBytes()));
            data.headers().add(new RecordHeader(propagatedHeaderKey, propagatedHeaderValue));
            producer.send(data);
        }

        await().atMost(30, TimeUnit.SECONDS).untilAdder(count, greaterThan(10L));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(testConsumerListener.afterConsumeCalled,
                        "The afterConsume method should have been called"));
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(testConsumerListener.afterProcessCalled,
                        "The afterProcess method should have been called"));

        to.assertIsSatisfied();
        assertEquals(5, to.getExchanges().size(), "Did not receive the expected amount of messages");

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertFalse(headers.containsKey(skippedHeaderKey), "Should not receive skipped header");
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
    }

    // Just a wrapper for us to check if the expected methods are being called
    private static class TestListener extends KafkaConsumerListener {
        volatile boolean afterConsumeCalled;
        volatile boolean afterProcessCalled;

        @Override
        public boolean afterConsume(Object ignored) {
            afterConsumeCalled = true;
            return super.afterConsume(ignored);
        }

        @Override
        public boolean afterProcess(ProcessingResult result) {
            afterProcessCalled = true;
            return super.afterProcess(result);
        }
    }
}
