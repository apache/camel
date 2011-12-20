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
package org.apache.camel.processor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.Throttler.TimeSlot;

import static org.apache.camel.builder.Builder.constant;

/**
 * @version 
 */
public class ThrottlerTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    protected int messageCount = 9;

    public void testSendLotsOfMessagesButOnly3GetThrough() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(3);
        resultEndpoint.setResultWaitTime(5000);

        for (int i = 0; i < messageCount; i++) {
            template.sendBody("seda:a", "<message>" + i + "</message>");
        }

        // lets pause to give the requests time to be processed
        // to check that the throttle really does kick in
        resultEndpoint.assertIsSatisfied();
    }
    
    public void testSendLotsOfMessagesSimultaneouslyButOnly3GetThrough() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);

        long start = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBody("direct:a", "<message>payload</message>");
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();

        // now assert that they have actually been throttled
        long minimumTime = (messageCount - 1) * INTERVAL;
        // add a little slack
        long delta = System.currentTimeMillis() - start + 200;
        assertTrue("Should take at least " + minimumTime + "ms, was: " + delta, delta >= minimumTime);
        executor.shutdownNow();
    }

    public void testTimeSlotCalculus() throws Exception {
        Throttler throttler = new Throttler(null, constant(3), 1000, null);
        // calculate will assign a new slot
        throttler.calculateDelay(new DefaultExchange(context));
        TimeSlot slot = throttler.nextSlot();
        // start a new time slot
        assertNotNull(slot);
        // make sure the same slot is used (3 exchanges per slot)
        assertSame(slot, throttler.nextSlot());
        assertTrue(slot.isFull());
        
        TimeSlot next = throttler.nextSlot();
        // now we should have a new slot that starts somewhere in the future
        assertNotSame(slot, next);
        assertFalse(next.isActive());
    }

    public void testConfigurationWithConstantExpression() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);

        long start = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBody("direct:expressionConstant", "<message>payload</message>");
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();

        // now assert that they have actually been throttled
        long minimumTime = (messageCount - 1) * INTERVAL;
        // add a little slack
        long delta = System.currentTimeMillis() - start + 200;
        assertTrue("Should take at least " + minimumTime + "ms, was: " + delta, delta >= minimumTime);
        executor.shutdownNow();
    }

    public void testConfigurationWithHeaderExpression() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);

        long start = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBodyAndHeader("direct:expressionHeader", "<message>payload</message>", "throttleValue", 1);
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();

        // now assert that they have actually been throttled
        long minimumTime = (messageCount - 1) * INTERVAL;
        // add a little slack
        long delta = System.currentTimeMillis() - start + 200;
        assertTrue("Should take at least " + minimumTime + "ms, was: " + delta, delta >= minimumTime);
        executor.shutdownNow();
    }
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ex
                from("seda:a").throttle(3).timePeriodMillis(10000).to("log:result", "mock:result");
                // END SNIPPET: ex
                
                from("direct:a").throttle(1).timePeriodMillis(INTERVAL).to("log:result", "mock:result");
                
                from("direct:expressionConstant").throttle(constant(1)).timePeriodMillis(INTERVAL).to("log:result", "mock:result");
                
                from("direct:expressionHeader").throttle(header("throttleValue")).timePeriodMillis(INTERVAL).to("log:result", "mock:result");
            }
        };
    }
}