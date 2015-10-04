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

import org.apache.camel.AsyncCallback;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.Throttler.TimeSlot;

import static org.apache.camel.builder.Builder.constant;

public class ThrottlerTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    protected int messageCount = 9;

    protected boolean canTest() {
        // skip test on windows as it does not run well there
        return !isPlatform("windows");
    }

    public void testSendLotsOfMessagesButOnly3GetThrough() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(3);
        resultEndpoint.setResultWaitTime(2000);

        for (int i = 0; i < messageCount; i++) {
            template.sendBody("seda:a", "<message>" + i + "</message>");
        }

        // lets pause to give the requests time to be processed
        // to check that the throttle really does kick in
        resultEndpoint.assertIsSatisfied();
    }
    
    public void testSendLotsOfMessagesWithRejctExecution() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.setResultWaitTime(2000);
        
        MockEndpoint errorEndpoint = resolveMandatoryEndpoint("mock:error", MockEndpoint.class);
        errorEndpoint.expectedMessageCount(4);
        errorEndpoint.setResultWaitTime(2000);

        for (int i = 0; i < 6; i++) {
            template.sendBody("direct:start", "<message>" + i + "</message>");
        }

        // lets pause to give the requests time to be processed
        // to check that the throttle really does kick in
        resultEndpoint.assertIsSatisfied();
        errorEndpoint.assertIsSatisfied();
    }

    public void testSendLotsOfMessagesSimultaneouslyButOnly3GetThrough() throws Exception {
        if (!canTest()) {
            return;
        }

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
        long delta = System.currentTimeMillis() - start + 750;
        assertTrue("Should take at least " + minimumTime + "ms, was: " + delta, delta >= minimumTime);
        executor.shutdownNow();
    }

    public void testTimeSlotCalculus() throws Exception {
        if (!canTest()) {
            return;
        }

        Throttler throttler = new Throttler(context, null, constant(3), 1000, null, false, false);
        // calculate will assign a new slot
        throttler.calculateDelay(new DefaultExchange(context));
        TimeSlot slot = throttler.nextSlot();
        // start a new time slot
        assertNotNull(slot);
        // make sure the same slot is used (3 exchanges per slot)
        assertSame(slot, throttler.nextSlot());
        assertTrue(slot.isFull());
        assertTrue(slot.isActive());

        TimeSlot next = throttler.nextSlot();
        // now we should have a new slot that starts somewhere in the future
        assertNotSame(slot, next);
        assertFalse(next.isActive());
    }

    public void testTimeSlotCalculusForPeriod() throws InterruptedException {
        if (!canTest()) {
            return;
        }

        Throttler throttler = new Throttler(context, null, constant(3), 1000, null, false, false);
        throttler.calculateDelay(new DefaultExchange(context));

        TimeSlot slot = throttler.getSlot();
        assertNotNull(slot);
        assertSame(slot, throttler.nextSlot());

        // we've only used up two of three slots, but now we introduce a time delay
        // so to make the slot not valid anymore
        Thread.sleep((long) (1.5 * 1000));
        assertFalse(slot.isActive());
        assertNotSame(slot, throttler.nextSlot());
    }

    public void testConfigurationWithConstantExpression() throws Exception {
        if (!canTest()) {
            return;
        }

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
        if (!canTest()) {
            return;
        }

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

    public void testConfigurationWithChangingHeaderExpression() throws Exception {
        if (!canTest()) {
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        sendMessagesWithHeaderExpression(executor, resultEndpoint, 1);

        resultEndpoint.reset();
        sendMessagesWithHeaderExpression(executor, resultEndpoint, 10);

        resultEndpoint.reset();
        sendMessagesWithHeaderExpression(executor, resultEndpoint, 1);

        executor.shutdownNow();
    }

    private void sendMessagesWithHeaderExpression(ExecutorService executor, MockEndpoint resultEndpoint, final int
            throttle) throws InterruptedException {

        resultEndpoint.expectedMessageCount(messageCount);

        long start = System.currentTimeMillis();
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBodyAndHeader("direct:expressionHeader", "<message>payload</message>", "throttleValue",
                            throttle);
                }
            });
        }

        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();

        // now assert that they have actually been throttled
        long minimumTime = (messageCount - 1) * INTERVAL / throttle;
        // add a little slack
        long delta = System.currentTimeMillis() - start + 200;
        assertTrue("Should take at least " + minimumTime + "ms, was: " + delta, delta >= minimumTime);
        long maxTime = (messageCount - 1) * INTERVAL / throttle * 3;
        assertTrue("Should take at most " + maxTime + "ms, was: " + delta, delta <= maxTime);
    }

    /*
    Given: you have a throttler which rejects messages
    Then:
    Throttler should return an exception when calculating the delay or set the exception
    on the exchange when processing the delay.
     */
    public void testWhenTimeSlotIsFullShouldReturnThrottlerRejectedExecutionException() {
        if (!canTest()) {
            return;
        }
        Throttler throttler = new Throttler(context, null, constant(1), 1000, null, false, true);
        AsyncCallback callback = new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {

            }
        };
        throttler.calculateDelay(new DefaultExchange(context));

        boolean exceptionThrown = false;
        DefaultExchange exchange = null;
        try {
            long delay = throttler.calculateDelay(new DefaultExchange(context));
            exchange = new DefaultExchange(context);
            throttler.processDelay(exchange,
                    callback,
                    delay);
        } catch (ThrottlerRejectedExecutionException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown || exchange.getException().getClass() == ThrottlerRejectedExecutionException.class);
    }

    /*
       Given: you have a throttler which rejects messages after the first message
       Then: the timeslot should be the original timeslot or the new timeslot should not be full
     */
    public void testRejectionOfExecutionShouldNotFillNextTimeSlot() {
        if (!canTest()) {
            return;
        }
        Throttler throttler = new Throttler(context, null, constant(1), 10000, null, false, true);
        AsyncCallback callback = new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {

            }
        };
        throttler.calculateDelay(new DefaultExchange(context));
        TimeSlot currentSlot = throttler.getSlot();
        DefaultExchange exchange;
        try {
            long delay = throttler.calculateDelay(new DefaultExchange(context));
            exchange = new DefaultExchange(context);
            throttler.processDelay(exchange,
                    callback,
                    delay);
        } catch (ThrottlerRejectedExecutionException ignore) {
        }
        assertTrue(currentSlot == throttler.getSlot() || !throttler.getSlot().isFull());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                
                onException(ThrottlerRejectedExecutionException.class)
                    .handled(true)
                    .to("mock:error");
                
                // START SNIPPET: ex
                from("seda:a").throttle(3).timePeriodMillis(10000).to("log:result", "mock:result");
                // END SNIPPET: ex

                from("direct:a").throttle(1).timePeriodMillis(INTERVAL).to("log:result", "mock:result");

                from("direct:expressionConstant").throttle(constant(1)).timePeriodMillis(INTERVAL).to("log:result", "mock:result");

                from("direct:expressionHeader").throttle(header("throttleValue")).timePeriodMillis(INTERVAL).to("log:result", "mock:result");

                from("direct:start").throttle(2).timePeriodMillis(10000).rejectExecution(true).to("log:result", "mock:result");
            }
        };
    }
}