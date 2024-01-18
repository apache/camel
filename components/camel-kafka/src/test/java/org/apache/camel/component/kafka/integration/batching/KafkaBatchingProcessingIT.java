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
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KafkaBatchingProcessingIT extends BatchingProcessingITSupport {

    public static final String TOPIC = "testManualCommitSyncTest";

    @AfterEach
    public void after() {
        cleanupKafka(TOPIC);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String from = "kafka:" + TOPIC
                      + "?groupId=KafkaBatchingProcessingIT&pollTimeoutMs=1000&batching=true"
                      + "&maxPollRecords=10&autoOffsetReset=earliest&kafkaManualCommitFactory=#class:org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommitFactory";

        return new RouteBuilder() {

            @Override
            public void configure() {
                from(from).routeId("foo").to(KafkaTestUtil.MOCK_RESULT).process(e -> {
                    KafkaManualCommit manual = e.getIn().getHeader(KafkaConstants.MANUAL_COMMIT, KafkaManualCommit.class);
                    final Message message = e.getMessage();

                    assertNotNull(message, "The message body should not be null");

                    final Object body = message.getBody();
                    final List<?> list = assertInstanceOf(List.class, body, "The body should be a list");

                    assertEquals(1, list.size(), "The should be just one message on the list");

                    for (var object : list) {
                        final Exchange exchange =
                                assertInstanceOf(Exchange.class, object, "The list content should be an exchange");

                        final Message messageInList = exchange.getMessage();

                        final Object bodyInMessage = messageInList.getBody();
                        assertNotNull(bodyInMessage, "The body in message should not be null");
                        final String s = assertInstanceOf(String.class, bodyInMessage, "The body should be a string");
                        assertTrue(s.contains("message-"), "The message body should start with message-");
                        assertTrue(messageInList.hasHeaders(), "The message in list should have headers");
                        assertNotNull(messageInList.getHeader(KafkaConstants.PARTITION, Integer.class),
                                "The message in list should have the partition information");
                        assertEquals(TOPIC, messageInList.getHeader(KafkaConstants.PARTITION, String.class),
                                "The message in list should have the correct topic information");
                    }


                    manual.commit();
                });
                from(from).routeId("bar").autoStartup(false).to(KafkaTestUtil.MOCK_RESULT_BAR);
            }
        };
    }

    @Test
    public void kafkaManualCommit() throws Exception {
        kafkaManualCommitTest(TOPIC);
    }

}
