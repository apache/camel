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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.MemoryStateRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Test;

public class KafkaConsumerRebalancePartitionRevokeTest extends BaseEmbeddedKafkaTest {
    private static final String TOPIC = "offset-rebalance";

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    private OffsetStateRepository stateRepository;
    private CountDownLatch messagesLatch;
    
    private org.apache.kafka.clients.producer.KafkaProducer<String, String> producer;

    @Override
    protected void doPreSetup() throws Exception {
        Properties props = getDefaultProperties();
        producer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);

        kafkaBroker.createTopic(TOPIC, 2);
        for (int i = 0; i < 2; i++) {
            producer.send(new ProducerRecord<>(TOPIC, i % 2, "key", "message-" + i));
        }
        messagesLatch = new CountDownLatch(1);
        stateRepository = new OffsetStateRepository(messagesLatch);
    }

    @After
    public void after() {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    public void ensurePartitionRevokeCallsWithLastProcessedOffset() throws Exception {
        boolean partitionRevokeCalled = messagesLatch.await(30000, TimeUnit.MILLISECONDS);
        assertTrue("StateRepository.setState should have been called with offset >= 0 for topic" + TOPIC + 
                ". Remaining count : " + messagesLatch.getCount(), partitionRevokeCalled);
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
                             + "?groupId=" + TOPIC + "_GROUP"
                             + "&autoCommitIntervalMs=1000"
                             + "&autoOffsetReset=earliest"
                             + "&consumersCount=1"
                             + "&offsetRepository=#offset")
                        .routeId("consumer-rebalance-route")
                        .to("mock:result");
            }
        };
    }

    public class OffsetStateRepository extends MemoryStateRepository {
        CountDownLatch messagesLatch = null;
        
        public OffsetStateRepository(CountDownLatch messagesLatch) {
            this.messagesLatch = messagesLatch;
        }

        @Override
        public void start() throws Exception {
        }

        @Override
        public void stop() throws Exception {
        }

        @Override
        public String getState(String key) {
            return super.getState(key);
        }

        @Override
        public void setState(String key, String value) {
            if (key.contains(TOPIC) && messagesLatch.getCount() > 0
            		&& Long.parseLong(value) >= 0) {
                messagesLatch.countDown();
            }
            super.setState(key, value);
        }
    }
}
