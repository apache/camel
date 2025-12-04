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

package org.apache.camel.component.kafka.integration.batching;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaBatchingProcessingBreakOnFirstErrorManualCommitIT extends BatchingProcessingITSupport {
    private static final Logger LOG =
            LoggerFactory.getLogger(KafkaBatchingProcessingBreakOnFirstErrorManualCommitIT.class);

    public static final String TOPIC = "testBatchingProcessingBreakOnFirstErrorManualCommit";
    private volatile boolean errorThrown = false;

    @AfterEach
    public void after() {
        cleanupKafka(TOPIC);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String from = "kafka:" + TOPIC
                + "?groupId=KafkaBatchingProcessingBreakOnFirstErrorManualCommitIT"
                + "&pollTimeoutMs=1000"
                + "&batching=true"
                + "&maxPollRecords=10"
                + "&autoOffsetReset=earliest"
                + "&breakOnFirstError=true"
                + "&autoCommitEnable=false" // Test manual commit mode
                + "&allowManualCommit=true";

        return new RouteBuilder() {

            @Override
            public void configure() {
                onException(Exception.class)
                        .handled(false)
                        // Process the error and manually commit if needed
                        .process(exchange -> {
                            errorThrown = true;
                            LOG.info("Error occurred, performing manual commit");
                            doCommitOffset(exchange);
                        });

                from(from)
                        .routeId("batching")
                        .process(e -> {
                            // The received records are stored as exchanges in a list. This gets the list of those
                            // exchanges
                            final List<?> exchanges = e.getMessage().getBody(List.class);

                            // Ensure we are actually receiving what we are asking for
                            if (exchanges == null || exchanges.isEmpty()) {
                                return;
                            }

                            // The records from the batch are stored in a list of exchanges in the original exchange.
                            int i = 0;
                            for (Object o : exchanges) {
                                if (o instanceof Exchange exchange) {
                                    i++;
                                    String body = exchange.getMessage().getBody(String.class);
                                    LOG.info("Processing exchange with body {}", body);

                                    // Throw exception on message-3 to test breakOnFirstError
                                    if ("message-3".equals(body)) {
                                        throw new RuntimeException(
                                                "ERROR TRIGGERED BY TEST for breakOnFirstError in batching mode with manual commit");
                                    }
                                }
                            }
                        })
                        .to(KafkaTestUtil.MOCK_RESULT)
                        .process(exchange -> {
                            // Manual commit on success
                            doCommitOffset(exchange);
                        });
            }
        };
    }

    private void doCommitOffset(Exchange exchange) {
        LOG.debug("Performing manual commit");
        KafkaManualCommit manual =
                exchange.getMessage().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
        assertNotNull(manual);
        manual.commit();
    }

    @Test
    public void kafkaBreakOnFirstErrorInBatchingModeWithManualCommit() throws Exception {
        to.reset();
        to.expectedMinimumMessageCount(1);

        // Send 6 messages where message-3 will cause an error
        sendRecords(0, 6, TOPIC);

        // Wait for the error to be thrown
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> errorThrown);

        // With breakOnFirstError=true in batching mode with manual commit,
        // the processing should stop when an error occurs and the error handler should commit
        assertTrue(errorThrown, "Error should have been thrown on message-3");

        // Reset error flag and send more messages to test reconnection
        errorThrown = false;
        to.reset();
        to.expectedMinimumMessageCount(1);

        // Send additional messages that should be processed after reconnection
        // These messages don't contain "message-3" so should process successfully
        sendRecords(20, 23, TOPIC); // message-20, message-21, message-22

        // Wait for the new messages to be processed, indicating successful reconnection
        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> to.getReceivedCounter() >= 1);

        to.assertIsSatisfied(3000);

        // Verify that no error was thrown for the second batch (reconnection successful)
        assertFalse(errorThrown, "No error should be thrown for the second batch after reconnection");

        // The route should have encountered the error, triggered breakOnFirstError,
        // committed via the error handler, reconnected automatically, and continued processing new messages
        // This verifies that the breakOnFirstError functionality works in batching mode with manual commit
        // and that automatic reconnection occurs as expected
    }
}
