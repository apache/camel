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

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
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
public class KafkaPausableConsumerCircuitBreakerIT extends BaseEmbeddedKafkaTestSupport {
    public static final String SOURCE_TOPIC = "pause-source-cb";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaPausableConsumerCircuitBreakerIT.class);

    private static final int SIMULATED_FAILURES = 5;
    private static final LongAdder count = new LongAdder();
    private static ScheduledExecutorService executorService;
    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    /*
     * This is used by pausable to determine whether to pause. If returning true, processing continues. If
     * returning false, processing pauses.
     */
    private static boolean canContinue() {
        // First one should go through ...
        if (count.intValue() <= 1) {
            LOG.info("Count is 1, allowing processing to proceed");
            return true;
        }

        if (count.intValue() >= SIMULATED_FAILURES) {
            LOG.info("Count is {}, allowing processing to proceed because it's greater than retry count {}",
                    count.intValue(), SIMULATED_FAILURES);
            return true;
        }

        LOG.info("Cannot proceed at the moment ... count is {}", count.intValue());
        return false;
    }

    public static void increment() {
        count.increment();
    }

    public static int getCount() {
        return count.intValue();
    }

    @BeforeEach
    public void before() {
        Properties props = KafkaTestUtil.getDefaultProperties(service);
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        MockConsumerInterceptor.recordsCaptured.clear();
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        KafkaTestUtil.createAdminClient(service)
                .deleteTopics(Collections.singletonList(SOURCE_TOPIC)).all();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("pausable");

                /*
                 * Set a watcher for the circuit breaker events. This watcher simulates a check for a downstream
                 * system availability. It watches for error events and, when they happen, it triggers a scheduled
                 * check (in this case, that simply increments a value). On success, it shuts down the scheduled check
                 */
                circuitBreaker.getEventPublisher()
                        .onSuccess(event -> {
                            LOG.info("Downstream call succeeded");
                            if (executorService != null) {
                                executorService.shutdownNow();
                                executorService = null;
                            }
                        })
                        .onError(event -> {
                            LOG.info(
                                    "Downstream call error. Starting a thread to simulate checking for the downstream availability");

                            if (executorService == null) {
                                executorService = Executors.newSingleThreadScheduledExecutor();
                                // In a real world scenario, instead of incrementing, it could be pinging a remote system or
                                // running a similar check to determine whether it's available. That
                                executorService.scheduleAtFixedRate(() -> increment(), 1, 1, TimeUnit.SECONDS);
                            }
                        });

                // Binds the configuration to the registry
                getCamelContext().getRegistry().bind("pausableCircuit", circuitBreaker);

                from("kafka:" + SOURCE_TOPIC
                        + "?groupId=KafkaPausableConsumerCircuitBreakerIT&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                        + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                        + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
                        .pausable(new KafkaConsumerListener(), o -> canContinue())
                        .routeId("pausable-it")
                        .process(exchange -> LOG.info("Got record from Kafka: {}", exchange.getMessage().getBody()))
                        .circuitBreaker()
                            .resilience4jConfiguration().circuitBreaker("pausableCircuit").end()
                        .to("direct:intermediate");

                from("direct:intermediate")
                        .process(exchange -> {
                            LOG.info("Got record on the intermediate processor: {}", exchange.getMessage().getBody());

                            if (getCount() <= SIMULATED_FAILURES) {
                                throw new RuntimeCamelException("Error");
                            }
                        })
                        .to(KafkaTestUtil.MOCK_RESULT)
                        .end();
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

        await().atMost(1, TimeUnit.MINUTES).untilAdder(count, greaterThan(5L));

        to.assertIsSatisfied();
        assertEquals(5, to.getExchanges().size(), "Did not receive the expected amount of messages");

        Map<String, Object> headers = to.getExchanges().get(0).getIn().getHeaders();
        assertFalse(headers.containsKey(skippedHeaderKey), "Should not receive skipped header");
        assertTrue(headers.containsKey(propagatedHeaderKey), "Should receive propagated header");
    }
}
