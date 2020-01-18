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
package org.apache.camel.component.kafka;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.StateRepository;
import org.junit.Test;

public class KafkaConsumerRebalanceTest extends BaseEmbeddedKafkaTest {
    private static final String TOPIC = "offset-rebalance";

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BindToRegistry("offset")
    private OffsetStateRepository stateRepository;
    private CountDownLatch messagesLatch;

    @Override
    protected void doPreSetup() throws Exception {
        messagesLatch = new CountDownLatch(2);
        stateRepository = new OffsetStateRepository(messagesLatch);
    }

    @Test
    public void offsetGetStateMustHaveBeenCalledTwice() throws Exception {
        boolean offsetGetStateCalled = messagesLatch.await(30000, TimeUnit.MILLISECONDS);
        assertTrue("StateRepository.getState should have been called twice for topic " + TOPIC + ". Remaining count : " + messagesLatch.getCount(), offsetGetStateCalled);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("kafka:" + TOPIC + "?groupId=" + TOPIC + "_GROUP" + "&autoCommitIntervalMs=1000" + "&autoOffsetReset=latest" + "&consumersCount=1"
                     + "&offsetRepository=#offset").routeId("consumer-rebalance-route").to("mock:result");
            }
        };
    }

    public class OffsetStateRepository implements StateRepository<String, String> {
        CountDownLatch messagesLatch;

        public OffsetStateRepository(CountDownLatch messagesLatch) {
            this.messagesLatch = messagesLatch;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public String getState(String key) {
            if (key.contains(TOPIC)) {
                messagesLatch.countDown();
            }
            return "-1";
        }

        @Override
        public void setState(String key, String value) {
        }
    }
}
