/**
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
package org.apache.camel.component.kafka;

import java.util.Properties;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.MemoryStateRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Test;

public class KafkaConsumerOffsetRepositoryEmptyTest extends BaseEmbeddedKafkaTest {
    private static final String TOPIC = "offset-initialize";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    private MemoryStateRepository stateRepository;

    // FIXME lifecycle issue here
    @Override
    protected void doPreSetup() throws Exception {
        // Create the topic with 2 partitions + send 10 messages (5 in each partitions)
        kafkaBroker.createTopic(TOPIC, 2);

        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<>(TOPIC, i % 2, "key", "message-" + i));
        }

        stateRepository = new MemoryStateRepository();
    }

    @After
    public void after() {
        if (producer != null) {
            producer.close();
        }
        stateRepository = null;
    }

    /**
     * Given an empty offset repository
     * When consuming with this repository
     * Then we consume according to the {@code autoOffsetReset} setting
     */
    @Test
    public void shouldStartFromBeginningWithEmptyOffsetRepository() throws InterruptedException {
        result.expectedMessageCount(10);
        result.expectedBodiesReceivedInAnyOrder("message-0", "message-1", "message-2", "message-3", "message-4", "message-5", "message-6", "message-7",
                                                "message-8", "message-9");

        result.assertIsSatisfied(3000);

        assertEquals("partition-0", "4", stateRepository.getState(TOPIC + "/0"));
        assertEquals("partition-1", "4", stateRepository.getState(TOPIC + "/1"));
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("offset", stateRepository);
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("kafka:" + TOPIC
                             + "?groupId=A"
                             + "&autoOffsetReset=earliest"             // Ask to start from the beginning if we have unknown offset
                             + "&consumersCount=2"                     // We have 2 partitions, we want 1 consumer per partition
                             + "&offsetRepository=#offset")            // Keep the offset in our repository
                        .to("mock:result");
            }
        };
    }
}
