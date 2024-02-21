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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaBatchingProcessingAutoCommitErrorHandlingIT extends BatchingProcessingITSupport {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaBatchingProcessingManualCommitIT.class);

    public static final String TOPIC = "testBatchingProcessingAutoCommitErrorHandling";
    private volatile boolean invalidExchangeFormat = false;

    @AfterEach
    public void after() {
        cleanupKafka(TOPIC);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // allowManualCommit=true&autoOffsetReset=earliest
        String from = "kafka:" + TOPIC
                      + "?groupId=KafkaBatchingProcessingIT&pollTimeoutMs=1000&batching=true&maxPollRecords=10&autoOffsetReset=earliest";

        return new RouteBuilder() {

            @Override
            public void configure() {

                /*
                 We want to use continued here, so that Camel auto-commits the batch even though part of it has failed. In a
                 production scenario, applications should probably send these records to a separate topic or fix the condition
                 that lead to the failure
                 */
                onException(IllegalArgumentException.class).process(exchange -> {
                    LOG.warn("Failed to process batch: {}", exchange.getMessage().getBody());
                    LOG.warn("Failed to process due to: {}",
                            exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class).getMessage());
                }).continued(true);

                from(from).routeId("batching").process(e -> {
                    // The received records are stored as exchanges in a list. This gets the list of those exchanges
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
                            LOG.info("Processing exchange with body {}", exchange.getMessage().getBody(String.class));

                            if (i == 4) {
                                throw new IllegalArgumentException("Failed to process record");
                            }
                        }
                    }
                }).to(KafkaTestUtil.MOCK_RESULT);
            }
        };
    }

    @Test
    public void kafkaAutoCommit() throws Exception {
        kafkaManualCommitTest(TOPIC);

        Assertions.assertFalse(invalidExchangeFormat, "The exchange list should be composed of exchanges");
    }
}
