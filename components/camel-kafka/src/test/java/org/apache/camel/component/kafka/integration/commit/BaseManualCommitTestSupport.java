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

package org.apache.camel.component.kafka.integration.commit;

import java.util.Collections;
import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.component.kafka.integration.BaseEmbeddedKafkaTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class BaseManualCommitTestSupport extends BaseEmbeddedKafkaTestSupport {

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

        sendRecords(0, 5, topic);

        to.assertIsSatisfied(3000);

        to.reset();

        // Second step: We shut down our route, we expect nothing will be recovered by our route
        contextExtension.getContext().getRouteController().stopRoute("foo");
        to.expectedMessageCount(0);

        // Third step: While our route is stopped, we send 3 records more to a Kafka test topic
        sendRecords(5, 8, topic);

        to.assertIsSatisfied(3000);

        to.reset();

        // Fourth step: We start again our route, since we have been committing the offsets from the first step,
        // we will expect to consume from the latest committed offset (e.g.: from offset 5()
        contextExtension.getContext().getRouteController().startRoute("foo");
        setupPostExecutionExpectations();

        to.assertIsSatisfied(3000);
    }

    public void kafkaManualCommitTestWithStateRepository(String topic, StateRepository<String, String> stateRepository)
            throws Exception {
        setupPreExecutionExpectations();

        sendRecords(0, 5, topic);

        to.assertIsSatisfied(3000);

        to.reset();

        final String state = Awaitility.await().until(() -> stateRepository.getState(topic + "/0"),
                Matchers.notNullValue());
        // We send 5 records initially, so we expect the offset to be 5 after first step execution
        assertEquals("5", state, "5 messages were sent in the first step, therefore the offset should be 5");

        // Second step: We shut down our route, we expect nothing will be recovered by our route
        contextExtension.getContext().getRouteController().stopRoute("foo");
        to.expectedMessageCount(0);

        // Third step: While our route is stopped, we send 3 records more to a Kafka test topic
        sendRecords(5, 8, topic);

        to.assertIsSatisfied(3000);

        to.reset();

        // Fourth step: We start again our route, since we have been committing the offsets from the first step,
        // we will expect to consume from the latest committed offset (e.g.: from offset 5)
        contextExtension.getContext().getRouteController().startRoute("foo");
        setupPostExecutionExpectations();

        to.assertIsSatisfied(3000);
    }

    protected void setupPostExecutionExpectations() {
        to.expectedMessageCount(3);
        to.expectedBodiesReceivedInAnyOrder("message-5", "message-6", "message-7");
    }

    protected void sendRecords(int startIndex, int lastIndex, String topic) {
        for (int position = startIndex; position < lastIndex; position++) {
            String msg = "message-" + position;
            ProducerRecord<String, String> data = new ProducerRecord<>(topic, "1", msg);
            producer.send(data);
        }
    }

    protected void setupPreExecutionExpectations() {
        to.expectedMessageCount(5);
        to.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4");

        // The LAST_RECORD_BEFORE_COMMIT header should include a value as we use
        // manual commit
        to.allMessages().header(KafkaConstants.LAST_RECORD_BEFORE_COMMIT).isNotNull();
    }
}
