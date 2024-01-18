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
import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.kafka.integration.BaseEmbeddedKafkaTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;

abstract class BatchingProcessingITSupport extends BaseEmbeddedKafkaTestSupport {

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
