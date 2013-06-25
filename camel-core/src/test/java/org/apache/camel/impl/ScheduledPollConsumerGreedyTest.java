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
package org.apache.camel.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;

public class ScheduledPollConsumerGreedyTest extends ContextTestSupport {

    private final AtomicInteger polled = new AtomicInteger();

    public void test321Greedy() throws Exception {
        polled.set(0);

        MockScheduledPollConsumer consumer = new Mock321ScheduledPollConsumer(getMockEndpoint("mock:foo"), null);
        consumer.setGreedy(true);

        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                polled.addAndGet(polledMessages);
            }

            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) throws Exception {
                return false;
            }
        });

        consumer.start();
        consumer.run();

        assertEquals(6, polled.get());

        consumer.stop();
    }

    public void test321NotGreedy() throws Exception {
        polled.set(0);

        MockScheduledPollConsumer consumer = new Mock321ScheduledPollConsumer(getMockEndpoint("mock:foo"), null);
        consumer.setGreedy(false);

        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                polled.addAndGet(polledMessages);
            }

            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) throws Exception {
                return false;
            }
        });

        consumer.start();

        consumer.run();
        assertEquals(3, polled.get());
        consumer.run();
        assertEquals(5, polled.get());
        consumer.run();
        assertEquals(6, polled.get());
        consumer.run();
        assertEquals(6, polled.get());

        consumer.stop();
    }

}
