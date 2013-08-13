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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.util.ServiceHelper;

public class LimitedPollingConsumerPollStrategyTest extends ContextTestSupport {

    private LimitedPollingConsumerPollStrategy strategy;

    public void testLimitedPollingConsumerPollStrategy() throws Exception {
        Exception expectedException = new Exception("Hello");

        strategy = new LimitedPollingConsumerPollStrategy();
        strategy.setLimit(3);

        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);
        consumer.setPollStrategy(strategy);

        consumer.start();

        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should be suspended", consumer.isSuspended());

        consumer.stop();
    }

    public void testLimitAtTwoLimitedPollingConsumerPollStrategy() throws Exception {
        Exception expectedException = new Exception("Hello");

        strategy = new LimitedPollingConsumerPollStrategy();
        strategy.setLimit(2);

        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);
        consumer.setPollStrategy(strategy);

        consumer.start();

        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should be suspended", consumer.isSuspended());

        consumer.stop();
    }

    public void testLimitedPollingConsumerPollStrategySuccess() throws Exception {
        Exception expectedException = new Exception("Hello");

        strategy = new LimitedPollingConsumerPollStrategy();
        strategy.setLimit(3);

        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);
        consumer.setPollStrategy(strategy);

        consumer.start();

        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());

        // now force success
        consumer.setExceptionToThrowOnPoll(null);
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());

        consumer.stop();
    }

    public void testLimitedPollingConsumerPollStrategySuccessThenFail() throws Exception {
        Exception expectedException = new Exception("Hello");

        strategy = new LimitedPollingConsumerPollStrategy();
        strategy.setLimit(3);

        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);
        consumer.setPollStrategy(strategy);

        consumer.start();

        // fail 2 times
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());

        // now force success 2 times
        consumer.setExceptionToThrowOnPoll(null);
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());

        // now fail again, after hitting limit at 3
        consumer.setExceptionToThrowOnPoll(expectedException);
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should be suspended", consumer.isSuspended());

        consumer.stop();
    }

    public void testTwoConsumersLimitedPollingConsumerPollStrategy() throws Exception {
        Exception expectedException = new Exception("Hello");

        strategy = new LimitedPollingConsumerPollStrategy();
        strategy.setLimit(3);

        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);
        consumer.setPollStrategy(strategy);

        MockScheduledPollConsumer consumer2 = new MockScheduledPollConsumer(endpoint, null);
        consumer2.setPollStrategy(strategy);

        consumer.start();
        consumer2.start();

        consumer.run();
        consumer2.run();
        assertTrue("Should still be started", consumer.isStarted());
        assertTrue("Should still be started", consumer2.isStarted());
        consumer.run();
        consumer2.run();
        assertTrue("Should still be started", consumer.isStarted());
        assertTrue("Should still be started", consumer2.isStarted());
        consumer.run();
        consumer2.run();
        assertTrue("Should be suspended", consumer.isSuspended());
        assertTrue("Should still be started", consumer2.isStarted());

        consumer.stop();
        consumer2.stop();
    }

    public void testRestartManuallyLimitedPollingConsumerPollStrategy() throws Exception {
        Exception expectedException = new Exception("Hello");

        strategy = new LimitedPollingConsumerPollStrategy();
        strategy.setLimit(3);

        final Endpoint endpoint = getMockEndpoint("mock:foo");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(endpoint, expectedException);
        consumer.setPollStrategy(strategy);

        consumer.start();

        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should be suspended", consumer.isSuspended());

        // now start the consumer again
        ServiceHelper.resumeService(consumer);

        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should be suspended", consumer.isSuspended());

        // now start the consumer again
        ServiceHelper.resumeService(consumer);
        // and let it succeed
        consumer.setExceptionToThrowOnPoll(null);
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());
        consumer.run();
        assertTrue("Should still be started", consumer.isStarted());

        consumer.stop();
    }

}
