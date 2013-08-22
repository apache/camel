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

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.PollingConsumerPollStrategy;

public class ScheduledPollConsumerBackoffTest extends ContextTestSupport {

    private static int commits;
    private static int errors;

    public void testBackoffIdle() throws Exception {

        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, null);
        consumer.setBackoffMultiplier(4);
        consumer.setBackoffIdleThreshold(2);

        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                commits++;
            }

            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) throws Exception {
                return false;
            }
        });

        consumer.start();

        consumer.run();
        consumer.run();
        assertEquals(2, commits);
        // now it should backoff 4 times
        consumer.run();
        consumer.run();
        consumer.run();
        consumer.run();
        assertEquals(2, commits);
        // and now we poll again
        consumer.run();
        consumer.run();
        assertEquals(4, commits);
        // now it should backoff 4 times
        consumer.run();
        consumer.run();
        consumer.run();
        consumer.run();
        assertEquals(4, commits);
        consumer.run();
        assertEquals(5, commits);

        consumer.stop();
    }

    public void testBackoffError() throws Exception {

        final Endpoint endpoint = getMockEndpoint("mock:foo");
        final Exception expectedException = new Exception("Hello, I should be thrown on shutdown only!");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);
        consumer.setBackoffMultiplier(4);
        consumer.setBackoffErrorThreshold(3);

        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public boolean begin(Consumer consumer, Endpoint endpoint) {
                return true;
            }

            public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
                commits++;
            }

            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) throws Exception {
                errors++;
                return false;
            }
        });

        consumer.start();

        consumer.run();
        consumer.run();
        consumer.run();
        assertEquals(3, errors);
        // now it should backoff 4 times
        consumer.run();
        consumer.run();
        consumer.run();
        consumer.run();
        assertEquals(3, errors);
        // and now we poll again
        consumer.run();
        consumer.run();
        consumer.run();
        assertEquals(6, errors);
        // now it should backoff 4 times
        consumer.run();
        consumer.run();
        consumer.run();
        consumer.run();
        assertEquals(6, errors);

        consumer.stop();
    }
}
