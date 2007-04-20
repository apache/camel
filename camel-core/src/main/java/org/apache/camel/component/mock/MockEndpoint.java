/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mock;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A Mock endpoint which provides a literate, fluent API for testing routes using
 * a <a href="http://jmock.org/">JMock style</a> API.
 *
 * @version $Revision: 1.1 $
 */
public class MockEndpoint extends DefaultEndpoint<Exchange> {
    private static final transient Log log = LogFactory.getLog(MockEndpoint.class);
    private int expectedCount = -1;
    private Map<Integer, Processor<Exchange>> processors = new HashMap<Integer, Processor<Exchange>>();
    private List<Exchange> exchangesReceived = new ArrayList<Exchange>();
    private List<Throwable> failures = new ArrayList<Throwable>();
    private List<Runnable> tests = new ArrayList<Runnable>();
    private CountDownLatch latch;
    private long sleepForEmptyTest = 0L;

    public static void assertIsSatisfied(MockEndpoint... endpoints) throws InterruptedException {
        // lets only wait on the first empty endpoint
        int count = 0;
        for (MockEndpoint endpoint : endpoints) {
            if (endpoint.getExpectedCount() != 0) {
                endpoint.assertIsSatisfied();
                count++;
            }
        }

        for (MockEndpoint endpoint : endpoints) {
            if (endpoint.getExpectedCount() == 0) {
                if (count == 0) {
                    endpoint.assertIsSatisfied();
                    count++;
                }
                else {
                    endpoint.assertIsSatisfied(0);
                }
            }
        }
    }

    public static void expectsMessageCount(int count, MockEndpoint... endpoints) throws InterruptedException {
        for (MockEndpoint endpoint : endpoints) {
            endpoint.expectsMessageCount(count);
        }
    }

    public MockEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public Exchange createExchange() {
        return new DefaultExchange(getContext());
    }

    public Consumer<Exchange> createConsumer(Processor<Exchange> processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    public Producer<Exchange> createProducer() throws Exception {
        return new DefaultProducer<Exchange>(this) {
            public void process(Exchange exchange) {
                onExchange(exchange);
            }
        };
    }

    // Testing API
    //-------------------------------------------------------------------------

    /**
     * Validates that all the available expectations on this endpoint are satisfied; or throw an exception
     */
    public void assertIsSatisfied() throws InterruptedException {
        assertIsSatisfied(sleepForEmptyTest);
    }

    /**
     * Validates that all the available expectations on this endpoint are satisfied; or throw an exception
     */
    public void assertIsSatisfied(long timeoutForEmptyEndpoints) throws InterruptedException {
        if (latch != null) {
            // now lets wait for the results
            latch.await(10, TimeUnit.SECONDS);
        }
        else if (expectedCount == 0) {
            // lets wait a little bit just in case
            if (timeoutForEmptyEndpoints > 0) {
                Thread.sleep(timeoutForEmptyEndpoints);
            }
        }

        if (expectedCount >= 0) {
            int receivedCounter = getReceivedCounter();
            assertEquals("Expected message count", expectedCount, receivedCounter);
        }

        for (Runnable test : tests) {
            test.run();
        }

        for (Throwable failure : failures) {
           if (failure != null) {
               log.error("Caught: " + failure, failure);
               throw new AssertionError("Failed due to caught exception: " + failure);
           }
        }
    }

    /**
     * Specifies the expected number of message exchanges that should be received by this endpoint
     *
     * @param expectedCount the number of message exchanges that should be expected by this endpoint
     */
    public void expectedMessageCount(int expectedCount) {
        this.expectedCount = expectedCount;
        if (expectedCount <= 0) {
            latch = null;
        }
        else {
            latch = new CountDownLatch(expectedCount);
        }
    }

    /**
     * Adds an expectation that the given body values are received by this endpoint
     */
    public void expectedBodiesReceived(final List bodies) {
        expectedMessageCount(bodies.size());

        expects(new Runnable() {
            public void run() {
                int counter = 0;
                for (Object expectedBody : bodies) {
                    Exchange exchange = getExchangesReceived().get(counter++);
                    assertTrue("No exchange received for counter: " + counter, exchange != null);

                    Object actualBody = exchange.getIn().getBody();

                    assertEquals("Body of message: " + counter, expectedBody, actualBody);

                    log.debug(getEndpointUri() + " >>>> message: " + counter + " with body: " + actualBody);
                }
            }
        });
    }

    /**
     * Adds an expectation that the given body values are received by this endpoint
     */
    public void expectedBodiesReceived(Object... bodies) {
        List bodyList = new ArrayList();
        for (Object body : bodies) {
            bodyList.add(body);
        }
        expectedBodiesReceived(bodyList);
    }


    /**
     * Adds the expection which will be invoked when enough messages are received
     */
    public void expects(Runnable runnable) {
        tests.add(runnable);
    }
    

    // Properties
    //-------------------------------------------------------------------------
    public List<Throwable> getFailures() {
        return failures;
    }

    public int getReceivedCounter() {
        return getExchangesReceived().size();
    }

    public List<Exchange> getExchangesReceived() {
        return exchangesReceived;
    }

    public int getExpectedCount() {
        return expectedCount;
    }

    public long getSleepForEmptyTest() {
        return sleepForEmptyTest;
    }

    /**
     * Allows a sleep to be specified to wait to check that this endpoint really is empty when
     * {@link #expectedMessageCount(int)} is called with zero
     *
     * @param sleepForEmptyTest the milliseconds to sleep for to determine that this endpoint really is empty
     */
    public void setSleepForEmptyTest(long sleepForEmptyTest) {
        this.sleepForEmptyTest = sleepForEmptyTest;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected synchronized void onExchange(Exchange exchange) {
        try {
            log.debug(getEndpointUri() + " >>>> " + exchange);

            exchangesReceived.add(exchange);

            Processor<Exchange> processor = processors.get(getReceivedCounter());
            if (processor != null) {
                processor.process(exchange);
            }

            if (latch != null) {
                latch.countDown();
            }
        }
        catch (Exception e) {
            failures.add(e);
        }
    }

    protected void assertEquals(String message, Object expectedValue, Object actualValue) {
        if (!ObjectHelper.equals(expectedValue, actualValue)) {
            throw new AssertionError(message + ". Expected: <" + expectedValue + "> but was: <" + actualValue + ">");
        }
    }

    protected void assertTrue(String message, boolean predicate) {
        if (!predicate) {
            throw new AssertionError(message);
        }
    }
}
