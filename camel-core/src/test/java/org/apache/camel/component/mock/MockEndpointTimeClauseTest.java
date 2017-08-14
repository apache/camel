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
package org.apache.camel.component.mock;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class MockEndpointTimeClauseTest extends ContextTestSupport {

    public void testReceivedTimestamp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.message(0).exchangeProperty(Exchange.CREATED_TIMESTAMP).isNotNull();
        mock.message(0).exchangeProperty(Exchange.CREATED_TIMESTAMP).isInstanceOf(Date.class);
        mock.message(0).exchangeProperty(Exchange.RECEIVED_TIMESTAMP).isNotNull();
        mock.message(0).exchangeProperty(Exchange.RECEIVED_TIMESTAMP).isInstanceOf(Date.class);

        template.sendBody("direct:a", "A");

        assertMockEndpointsSatisfied();
    }

    public void testAssertPeriod() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.setAssertPeriod(10);

        template.sendBody("direct:a", "A");

        assertMockEndpointsSatisfied();
    }

    public void testAssertPeriodNot() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.setAssertPeriod(10);

        template.sendBody("direct:a", "A");
        template.sendBody("direct:a", "B");

        // we got 2 messages so the assertion is not
        mock.assertIsNotSatisfied();
    }

    public void testAssertPeriodSecondMessageArrives() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        // wait a bit after preliminary assertion to ensure its still correct
        mock.setAssertPeriod(250);

        template.sendBody("direct:a", "A");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                    // ignore
                }
                if (isStarted(template)) {
                    template.sendBody("direct:a", "B");
                }
            }
        });

        try {
            mock.assertIsSatisfied();
            fail("Should have thrown an exception");
        } catch (AssertionError e) {
            assertEquals("mock://result Received message count. Expected: <1> but was: <2>", e.getMessage());
        }

        executor.shutdownNow();
    }

    public void testNoAssertPeriodSecondMessageArrives() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:a", "A");

        final CountDownLatch latch = new CountDownLatch(1);

        // this executor was bound to send a 2nd message
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            public void run() {
                try {
                    latch.await();

                    if (isStarted(template)) {
                        template.sendBody("direct:a", "B");
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        });

        // but the assertion would be complete before hand and thus
        // the assertion was valid at the time given
        assertMockEndpointsSatisfied();

        latch.countDown();

        executor.shutdownNow();
    }

    public void testArrivesBeforeNext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).arrives().noLaterThan(1).seconds().beforeNext();

        template.sendBody("direct:a", "A");
        Thread.sleep(50);
        template.sendBody("direct:a", "B");

        assertMockEndpointsSatisfied();
    }

    public void testArrivesAfterPrevious() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(1).arrives().noLaterThan(1).seconds().afterPrevious();

        template.sendBody("direct:a", "A");
        Thread.sleep(50);
        template.sendBody("direct:a", "B");

        assertMockEndpointsSatisfied();
    }

    public void testArrivesBeforeAndAfter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.message(1).arrives().noLaterThan(250).millis().afterPrevious();
        mock.message(1).arrives().noLaterThan(250).millis().beforeNext();

        template.sendBody("direct:a", "A");
        Thread.sleep(50);
        template.sendBody("direct:a", "B");
        Thread.sleep(50);
        template.sendBody("direct:a", "C");

        assertMockEndpointsSatisfied();
    }

    public void testArrivesWithinAfterPrevious() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(1).arrives().between(10, 500).millis().afterPrevious();

        template.sendBody("direct:a", "A");
        Thread.sleep(50);
        template.sendBody("direct:a", "B");

        assertMockEndpointsSatisfied();
    }

    public void testArrivesWithinBeforeNext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).arrives().between(10, 500).millis().beforeNext();

        template.sendBody("direct:a", "A");
        Thread.sleep(50);
        template.sendBody("direct:a", "B");

        assertMockEndpointsSatisfied();
    }

    public void testArrivesAllMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(5);
        mock.allMessages().arrives().noLaterThan(1).seconds().beforeNext();

        template.sendBody("direct:a", "A");
        template.sendBody("direct:a", "B");
        Thread.sleep(50);
        template.sendBody("direct:a", "C");
        Thread.sleep(50);
        template.sendBody("direct:a", "D");
        Thread.sleep(50);
        template.sendBody("direct:a", "E");

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to("mock:result");
            }
        };
    }

    private boolean isStarted(Service service) {
        if (service instanceof StatefulService) {
            return ((StatefulService) service).isStarted();
        }
        return true;
    }

}
