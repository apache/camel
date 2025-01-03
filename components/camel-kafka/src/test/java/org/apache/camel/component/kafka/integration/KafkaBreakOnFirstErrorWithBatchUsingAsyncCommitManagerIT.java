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

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.kafka.testutil.CamelKafkaUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Uuid;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * this will test basic breakOnFirstError functionality uses allowManualCommit and set Sync Commit Manager this allows
 * Camel to handle when to commit an offset
 */
@Tags({ @Tag("breakOnFirstError") })

class KafkaBreakOnFirstErrorWithBatchUsingAsyncCommitManagerIT extends BaseKafkaTestSupport {
    public static final String ROUTE_ID = "breakOnFirstErrorBatchIT";
    public static final String TOPIC = "breakOnFirstErrorBatchIT" + Uuid.randomUuid();

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBreakOnFirstErrorWithBatchUsingAsyncCommitManagerIT.class);

    private final List<String> errorPayloads = new CopyOnWriteArrayList<>();

    @EndpointInject("mock:result")
    private MockEndpoint to;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeEach
    public void before() {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        MockConsumerInterceptor.recordsCaptured.clear();
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        DeleteTopicsResult r = kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));

        // wait added to ensure the topic is actually deleted, and avoid chance of clash in unrelated tests
        Awaitility.await()
                .timeout(180, TimeUnit.SECONDS)
                .pollDelay(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(r.all().isDone()));

    }

    /**
     * will continue to retry the message that is in error
     */
    @Test
    public void kafkaBreakOnFirstErrorBasicCapability() throws Exception {
        to.reset();
        to.expectedMessageCount(3);
        // message-3 causes an error
        // and breakOnFirstError will cause it to be retried forever
        // we will never get to message-4
        to.expectedBodiesReceived("message-0", "message-1", "message-2");

        contextExtension.getContext().getRouteController().stopRoute(ROUTE_ID);

        this.publishMessagesToKafka();

        contextExtension.getContext().getRouteController().startRoute(ROUTE_ID);

        Awaitility.await()
                .atMost(180, TimeUnit.SECONDS) // increased to 180 sec for CAMEL-20722 due to failure on ppc64le
                .pollDelay(5, TimeUnit.SECONDS)
                .until(() -> errorPayloads.size() > 3);

        to.assertIsSatisfied();

        for (String payload : errorPayloads) {
            assertEquals("message-3", payload);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("kafka:" + TOPIC
                     + "?groupId=" + ROUTE_ID
                     + "&autoOffsetReset=earliest"
                     + "&autoCommitEnable=false"
                     + "&allowManualCommit=true"
                     + "&breakOnFirstError=true"
                     + "&maxPollRecords=3"
                     + "&pollTimeoutMs=1000"
                     + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                     + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                // asynch commit factory
                     + "&kafkaManualCommitFactory=#class:org.apache.camel.component.kafka.consumer.DefaultKafkaManualAsyncCommitFactory"
                     + "&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor")
                        .routeId(ROUTE_ID)
                        .process(exchange -> {
                            LOG.debug(CamelKafkaUtil.buildKafkaLogMessage("Consuming", exchange, true));
                        })
                        .process(exchange -> {
                            ifIsPayloadWithErrorThrowException(exchange);
                        })
                        .to(to)
                        .end();
            }
        };
    }

    private void publishMessagesToKafka() {
        for (int i = 0; i < 5; i++) {
            String msg = "message-" + i;
            ProducerRecord<String, String> data = new ProducerRecord<>(TOPIC, null, msg);
            producer.send(data);
        }
    }

    private void ifIsPayloadWithErrorThrowException(Exchange exchange) {
        String payload = exchange.getMessage().getBody(String.class);
        if (payload.equals("message-3")) {
            errorPayloads.add(payload);
            throw new RuntimeException("ERROR TRIGGERED BY TEST");
        }
    }

}
