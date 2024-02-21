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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KafkaConsumerLastRecordHeaderIT extends BaseEmbeddedKafkaTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerLastRecordHeaderIT.class);
    private static final String TOPIC = "last-record";

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @BeforeEach
    public void before() {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
    }

    @AfterEach
    public void after() {
        if (producer != null) {
            producer.close();
        }
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));
    }

    /**
     * When consuming data with autoCommitEnable=false Then the LAST_RECORD_BEFORE_COMMIT header must be always defined
     * And it should be true only for the last one of batch of polled records
     */
    @Test
    public void shouldStartFromBeginningWithEmptyOffsetRepository() throws InterruptedException {
        MockEndpoint result = contextExtension.getMockEndpoint(KafkaTestUtil.MOCK_RESULT);
        result.expectedMessageCount(5);
        result.expectedBodiesReceived("message-0", "message-1", "message-2", "message-3", "message-4");

        for (int i = 0; i < 5; i++) {
            producer.send(new ProducerRecord<>(TOPIC, "1", "message-" + i));
        }

        result.assertIsSatisfied(5000);

        List<Exchange> exchanges = result.getExchanges();
        LOG.debug("There are {} exchanges in the result", exchanges.size());

        for (int i = 0; i < exchanges.size(); i++) {
            final Boolean lastRecordCommit
                    = exchanges.get(i).getIn().getHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, Boolean.class);
            final Boolean lastPollRecord = exchanges.get(i).getIn().getHeader(KafkaConstants.LAST_POLL_RECORD, Boolean.class);

            LOG.debug("Processing LAST_RECORD_BEFORE_COMMIT header for {}: {} ", i, lastRecordCommit);
            LOG.debug("Processing LAST_POLL_RECORD header for {}: {} ", i, lastPollRecord);

            assertNotNull(lastRecordCommit, "Header not set for #" + i);
            assertEquals(lastRecordCommit, i == exchanges.size() - 1 || lastPollRecord.booleanValue(),
                    "Header invalid for #" + i);

            assertNotNull(lastPollRecord, "Last record header not set for #" + i);

            if (i == exchanges.size() - 1) {
                assertEquals(lastPollRecord, i == exchanges.size() - 1, "Last record header invalid for #" + i);
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka:" + TOPIC + "?groupId=A&autoOffsetReset=earliest&autoCommitEnable=false")
                        .to("mock:result");
            }
        };
    }
}
