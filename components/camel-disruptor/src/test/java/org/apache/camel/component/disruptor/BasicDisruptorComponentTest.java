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

package org.apache.camel.component.disruptor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Tests some of the basic disruptor functionality
 */
public class BasicDisruptorComponentTest extends CamelTestSupport {
    private static final Integer VALUE = Integer.valueOf(42);
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @Produce(uri = "disruptor:test")
    private ProducerTemplate template;

    

    private final ThreadCounter threadCounter = new ThreadCounter();

    @Test
    public void testProduce() throws InterruptedException {
        resultEndpoint.expectedBodiesReceived(VALUE);
        resultEndpoint.setExpectedMessageCount(1);

        template.asyncSendBody("disruptor:test", VALUE);

        resultEndpoint.await(5, TimeUnit.SECONDS);
        resultEndpoint.assertIsSatisfied();
    }


    @Test
    public void testAsynchronous() throws InterruptedException {
        threadCounter.reset();

        final int messagesSent = 1000;

        resultEndpoint.setExpectedMessageCount(messagesSent);

        final long currentThreadId = Thread.currentThread().getId();

        for (int i = 0; i < messagesSent; ++i) {
            template.asyncSendBody("disruptor:testAsynchronous", VALUE);
        }

        resultEndpoint.await(20, TimeUnit.SECONDS);
        resultEndpoint.assertIsSatisfied();

        assertTrue(threadCounter.getThreadIdCount() > 0);
        assertFalse(threadCounter.getThreadIds().contains(currentThreadId));
    }

    @Test
    public void testMultipleConsumers() throws InterruptedException {
        threadCounter.reset();

        final int messagesSent = 1000;

        resultEndpoint.setExpectedMessageCount(messagesSent);

        for (int i = 0; i < messagesSent; ++i) {
            template.asyncSendBody("disruptor:testMultipleConsumers?concurrentConsumers=4", VALUE);
        }

        resultEndpoint.await(20, TimeUnit.SECONDS);

        //sleep for another second to check for duplicate messages in transit
        Thread.sleep(1000);

        resultEndpoint.assertIsSatisfied();

        assertEquals(4, threadCounter.getThreadIdCount());
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:test").to("mock:result");
                from("disruptor:testAsynchronous").process(threadCounter).to("mock:result");
                from("disruptor:testMultipleConsumers?concurrentConsumers=4").process(threadCounter)
                        .to("mock:result");
            }
        };
    }

    private static final class ThreadCounter implements Processor {

        private final Set<Long> threadIds = new HashSet<Long>();

        public void reset() {
            threadIds.clear();
        }

        @Override
        public void process(final Exchange exchange) throws Exception {
            threadIds.add(Thread.currentThread().getId());
        }

        public Set<Long> getThreadIds() {
            return Collections.unmodifiableSet(threadIds);
        }

        public int getThreadIdCount() {
            return threadIds.size();
        }
    }
}
