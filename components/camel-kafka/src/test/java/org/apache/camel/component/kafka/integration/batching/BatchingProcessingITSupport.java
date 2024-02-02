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

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.integration.BaseEmbeddedKafkaTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class BatchingProcessingITSupport extends BaseEmbeddedKafkaTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BatchingProcessingITSupport.class);

    @EndpointInject("mock:result")
    protected MockEndpoint to;

    @EndpointInject("mock:resultBar")
    protected MockEndpoint toBar;

    protected org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeEach
    public void createClient() {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
    }

    public void cleanupKafka(String topic) {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(topic));
    }

    public void kafkaManualCommitTest(String topic) throws Exception {
        setupPreExecutionExpectations();

        LOG.debug("Starting the first step");
        sendRecords(0, 5, topic);

        to.assertIsSatisfied(3000);
        to.expectedMessageCount(1);

        final List<Exchange> firstExchangeBatch = to.getExchanges();

        validateReceivedExchanges(5, firstExchangeBatch);

        to.reset();

        LOG.debug("Starting the second step");
        // Second step: We shut down our route, we expect nothing will be recovered by our route
        contextExtension.getContext().getRouteController().stopRoute("batching");

        // Third step: While our route is stopped, we send 3 records more to a Kafka test topic.
        // We should receive NO messages
        LOG.debug("Starting the third step");
        to.expectedMessageCount(0);
        sendRecords(5, 8, topic);

        to.assertIsSatisfied(3000);

        to.reset();

        // Fourth step: We start again our route, since we have been committing the offsets from the first step,
        // we will expect to consume from the latest committed offset (e.g.: from offset 5()
        LOG.debug("Starting the fourth step");
        contextExtension.getContext().getRouteController().startRoute("batching");
        setupPostExecutionExpectations();

        to.assertIsSatisfied(3000);

        final List<Exchange> secondExchangeBatch = to.getExchanges();
        validateReceivedExchanges(3, secondExchangeBatch);
    }

    private static void validateReceivedExchanges(int expectedCount, List<Exchange> exchanges) {
        assertNotNull(exchanges, "The exchange should not be null");

        final Exchange parentExchange = exchanges.get(0);
        final Message message = parentExchange.getMessage();

        assertNotNull(message, "The message body should not be null");

        final Object body = message.getBody();
        final List<?> list = assertInstanceOf(List.class, body, "The body should be a list");

        assertEquals(expectedCount, list.size(), "It should have received " + expectedCount + " instead of " + list.size());

        for (var object : list) {
            final Exchange exchange = assertInstanceOf(Exchange.class, object, "The list content should be an exchange");

            final Message messageInList = exchange.getMessage();
            LOG.info("Received message {}", messageInList);

            final Object bodyInMessage = messageInList.getBody();
            assertNotNull(bodyInMessage, "The body in message should not be null");

            final String messageBodyStr = assertInstanceOf(String.class, bodyInMessage, "The body should be a string");
            LOG.info("Received message body {}", messageBodyStr);

            assertTrue(messageBodyStr.contains("message-"), "The message body should start with message-");
            assertTrue(messageInList.hasHeaders(), "The message in list should have headers");
            assertNotNull(messageInList.getHeader(KafkaConstants.PARTITION, Integer.class),
                    "The message in list should have the partition information");
            assertNotNull(messageInList.getHeader(KafkaConstants.TOPIC, String.class),
                    "The message in list should have the correct topic information");
        }
    }

    protected void setupPostExecutionExpectations() {
        to.expectedMessageCount(1);
    }

    protected void sendRecords(int startIndex, int lastIndex, String topic) {
        for (int position = startIndex; position < lastIndex; position++) {
            String msg = "message-" + position;
            ProducerRecord<String, String> data = new ProducerRecord<>(topic, "1", msg);
            producer.send(data);
        }
    }

    protected void setupPreExecutionExpectations() {
        to.expectedMessageCount(1);
    }
}
