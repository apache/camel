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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.*;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExpressionComparator;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A Mock endpoint which provides a literate, fluent API for testing routes
 * using a <a href="http://jmock.org/">JMock style</a> API.
 * 
 * @version $Revision: 1.1 $
 */
public class MockEndpoint extends DefaultEndpoint<Exchange> {
    private static final transient Log LOG = LogFactory.getLog(MockEndpoint.class);
    private int expectedCount = -1;
    private int counter;
    private Map<Integer, Processor> processors = new HashMap<Integer, Processor>();
    private List<Exchange> receivedExchanges = new CopyOnWriteArrayList<Exchange>();
    private List<Throwable> failures = new CopyOnWriteArrayList<Throwable>();
    private List<Runnable> tests = new CopyOnWriteArrayList<Runnable>();
    private CountDownLatch latch;
    private long sleepForEmptyTest = 1000L;
    private long defaulResultWaitMillis = 20000L;
    private int expectedMinimumCount = -1;
    private List expectedBodyValues;
    private List actualBodyValues = new ArrayList();

    public MockEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public static void assertWait(long timeout, TimeUnit unit, MockEndpoint... endpoints) throws InterruptedException {
        long start = System.currentTimeMillis();
        long left = unit.toMillis(timeout);
        long end = start + left;
        for (MockEndpoint endpoint : endpoints) {
            if (!endpoint.await(left, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timeout waiting for endpoints to receive enough messages. " + endpoint.getEndpointUri() + " timed out.");
            }
            left = end - System.currentTimeMillis();
            if (left <= 0) {
                left = 0;
            }
        }
    }

    public static void assertIsSatisfied(long timeout, TimeUnit unit, MockEndpoint... endpoints) throws InterruptedException {
        assertWait(timeout, unit, endpoints);
        for (MockEndpoint endpoint : endpoints) {
            endpoint.assertIsSatisfied();
        }
    }

    public static void assertIsSatisfied(MockEndpoint... endpoints) throws InterruptedException {
        for (MockEndpoint endpoint : endpoints) {
            endpoint.assertIsSatisfied();
        }
    }


    /**
     * Asserts that all the expectations on any {@link MockEndpoint} instances registered
     * in the given context are valid
     *
     * @param context the camel context used to find all the available endpoints to be asserted
     */
    public static void assertIsSatisfied(CamelContext context) throws InterruptedException {
        Collection<Endpoint> endpoints = context.getSingletonEndpoints();
        for (Endpoint endpoint : endpoints) {
            if (endpoint instanceof MockEndpoint) {
                MockEndpoint mockEndpoint = (MockEndpoint) endpoint;
                mockEndpoint.assertIsSatisfied();
            }
        }
    }


    public static void expectsMessageCount(int count, MockEndpoint... endpoints) throws InterruptedException {
        for (MockEndpoint endpoint : endpoints) {
            endpoint.expectsMessageCount(count);
        }
    }

    public Consumer<Exchange> createConsumer(Processor processor) throws Exception {
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
    // -------------------------------------------------------------------------

    /**
     * Set the processor that will be invoked when the index
     * message is received.
     *
     * @param index
     * @param processor
     */
    public void whenExchangeReceived(int index, Processor processor) {
        this.processors.put(index, processor);
    }

    /**
     * Validates that all the available expectations on this endpoint are
     * satisfied; or throw an exception
     */
    public void assertIsSatisfied() throws InterruptedException {
        assertIsSatisfied(sleepForEmptyTest);
    }

    /**
     * Validates that all the available expectations on this endpoint are
     * satisfied; or throw an exception
     * 
     * @param timeoutForEmptyEndpoints the timeout in milliseconds that we
     *                should wait for the test to be true
     */
    public void assertIsSatisfied(long timeoutForEmptyEndpoints) throws InterruptedException {
        LOG.info("Asserting: " + this + " is satisfied");
        if (expectedCount >= 0) {
            if (expectedCount != getReceivedCounter()) {
                if (expectedCount == 0) {
                    // lets wait a little bit just in case
                    if (timeoutForEmptyEndpoints > 0) {
                        LOG.debug("Sleeping for: " + timeoutForEmptyEndpoints + " millis to check there really are no messages received");
                        Thread.sleep(timeoutForEmptyEndpoints);
                    }
                } else {
                    waitForCompleteLatch();
                }
            }
            assertEquals("Received message count", expectedCount, getReceivedCounter());
        } else if (expectedMinimumCount > 0 && getReceivedCounter() < expectedMinimumCount) {
            waitForCompleteLatch();
        }

        if (expectedMinimumCount >= 0) {
            int receivedCounter = getReceivedCounter();
            assertTrue("Received message count " + receivedCounter + ", expected at least " + expectedCount, expectedCount <= receivedCounter);
        }

        for (Runnable test : tests) {
            test.run();
        }

        for (Throwable failure : failures) {
            if (failure != null) {
                LOG.error("Caught on " + getEndpointUri() + " Exception: " + failure, failure);
                fail("Failed due to caught exception: " + failure);
            }
        }
    }

    /**
     * Validates that the assertions fail on this endpoint
     */
    public void assertIsNotSatisfied() throws InterruptedException {
        try {
            assertIsSatisfied();
            fail("Expected assertion failure!");
        } catch (AssertionError e) {
            LOG.info("Caught expected failure: " + e);
        }
    }

    /**
     * Specifies the expected number of message exchanges that should be
     * received by this endpoint
     * 
     * @param expectedCount the number of message exchanges that should be
     *                expected by this endpoint
     */
    public void expectedMessageCount(int expectedCount) {
        this.expectedCount = expectedCount;
        if (expectedCount <= 0) {
            latch = null;
        } else {
            latch = new CountDownLatch(expectedCount);
        }
    }

    /**
     * Specifies the minimum number of expected message exchanges that should be
     * received by this endpoint
     * 
     * @param expectedCount the number of message exchanges that should be
     *                expected by this endpoint
     */
    public void expectedMinimumMessageCount(int expectedCount) {
        this.expectedMinimumCount = expectedCount;
        if (expectedCount <= 0) {
            latch = null;
        } else {
            latch = new CountDownLatch(expectedMinimumCount);
        }
    }

    /**
     * Adds an expectation that the given body values are received by this
     * endpoint
     */
    public void expectedBodiesReceived(final List bodies) {
        expectedMessageCount(bodies.size());
        this.expectedBodyValues = bodies;
        this.actualBodyValues = new ArrayList();

        expects(new Runnable() {
            public void run() {
                for (int i = 0; i < expectedBodyValues.size(); i++) {
                    Exchange exchange = getReceivedExchanges().get(i);
                    assertTrue("No exchange received for counter: " + i, exchange != null);

                    Object expectedBody = expectedBodyValues.get(i);
                    Object actualBody = actualBodyValues.get(i);

                    assertEquals("Body of message: " + i, expectedBody, actualBody);
                }
            }
        });
    }

    /**
     * Adds an expectation that the given body values are received by this
     * endpoint
     */
    public void expectedBodiesReceived(Object... bodies) {
        List bodyList = new ArrayList();
        for (Object body : bodies) {
            bodyList.add(body);
        }
        expectedBodiesReceived(bodyList);
    }

    /**
     * Adds an expectation that messages received should have ascending values
     * of the given expression such as a user generated counter value
     * 
     * @param expression
     */
    public void expectsAscending(final Expression<Exchange> expression) {
        expects(new Runnable() {
            public void run() {
                assertMessagesAscending(expression);
            }
        });
    }

    /**
     * Adds an expectation that messages received should have descending values
     * of the given expression such as a user generated counter value
     * 
     * @param expression
     */
    public void expectsDescending(final Expression<Exchange> expression) {
        expects(new Runnable() {
            public void run() {
                assertMessagesDescending(expression);
            }
        });
    }

    /**
     * Adds an expectation that no duplicate messages should be received using
     * the expression to determine the message ID
     * 
     * @param expression the expression used to create a unique message ID for
     *                message comparison (which could just be the message
     *                payload if the payload can be tested for uniqueness using
     *                {@link Object#equals(Object)} and
     *                {@link Object#hashCode()}
     */
    public void expectsNoDuplicates(final Expression<Exchange> expression) {
        expects(new Runnable() {
            public void run() {
                assertNoDuplicates(expression);
            }
        });
    }

    /**
     * Asserts that the messages have ascending values of the given expression
     */
    public void assertMessagesAscending(Expression<Exchange> expression) {
        assertMessagesSorted(expression, true);
    }

    /**
     * Asserts that the messages have descending values of the given expression
     */
    public void assertMessagesDescending(Expression<Exchange> expression) {
        assertMessagesSorted(expression, false);
    }

    protected void assertMessagesSorted(Expression<Exchange> expression, boolean ascending) {
        String type = ascending ? "ascending" : "descending";
        ExpressionComparator comparator = new ExpressionComparator(expression);
        List<Exchange> list = getReceivedExchanges();
        for (int i = 1; i < list.size(); i++) {
            int j = i - 1;
            Exchange e1 = list.get(j);
            Exchange e2 = list.get(i);
            int result = comparator.compare(e1, e2);
            if (result == 0) {
                fail("Messages not " + type + ". Messages" + j + " and " + i + " are equal with value: " + expression.evaluate(e1) + " for expression: " + expression + ". Exchanges: " + e1 + " and "
                     + e2);
            } else {
                if (!ascending) {
                    result = result * -1;
                }
                if (result > 0) {
                    fail("Messages not " + type + ". Message " + j + " has value: " + expression.evaluate(e1) + " and message " + i + " has value: " + expression.evaluate(e2) + " for expression: "
                         + expression + ". Exchanges: " + e1 + " and " + e2);
                }
            }
        }
    }

    public void assertNoDuplicates(Expression<Exchange> expression) {
        Map<Object, Exchange> map = new HashMap<Object, Exchange>();
        List<Exchange> list = getReceivedExchanges();
        for (int i = 0; i < list.size(); i++) {
            Exchange e2 = list.get(i);
            Object key = expression.evaluate(e2);
            Exchange e1 = map.get(key);
            if (e1 != null) {
                fail("Duplicate message found on message " + i + " has value: " + key + " for expression: " + expression + ". Exchanges: " + e1 + " and " + e2);
            } else {
                map.put(key, e2);
            }
        }
    }

    /**
     * Adds the expection which will be invoked when enough messages are
     * received
     */
    public void expects(Runnable runnable) {
        tests.add(runnable);
    }

    /**
     * Adds an assertion to the given message index
     * 
     * @param messageIndex the number of the message
     * @return the assertion clause
     */
    public AssertionClause message(final int messageIndex) {
        AssertionClause clause = new AssertionClause() {
            public void run() {
                applyAssertionOn(MockEndpoint.this, messageIndex, assertExchangeReceived(messageIndex));
            }
        };
        expects(clause);
        return clause;
    }

    /**
     * Adds an assertion to all the received messages
     * 
     * @return the assertion clause
     */
    public AssertionClause allMessages() {
        AssertionClause clause = new AssertionClause() {
            public void run() {
                List<Exchange> list = getReceivedExchanges();
                int index = 0;
                for (Exchange exchange : list) {
                    applyAssertionOn(MockEndpoint.this, index++, exchange);
                }
            }
        };
        expects(clause);
        return clause;
    }

    /**
     * Asserts that the given index of message is received (starting at zero)
     */
    public Exchange assertExchangeReceived(int index) {
        int count = getReceivedCounter();
        assertTrue("Not enough messages received. Was: " + count, count > index);
        return getReceivedExchanges().get(index);
    }

    // Properties
    // -------------------------------------------------------------------------
    public List<Throwable> getFailures() {
        return failures;
    }

    public int getReceivedCounter() {
        return getReceivedExchanges().size();
    }

    public List<Exchange> getReceivedExchanges() {
        return receivedExchanges;
    }

    public int getExpectedCount() {
        return expectedCount;
    }

    public long getSleepForEmptyTest() {
        return sleepForEmptyTest;
    }

    /**
     * Allows a sleep to be specified to wait to check that this endpoint really
     * is empty when {@link #expectedMessageCount(int)} is called with zero
     * 
     * @param sleepForEmptyTest the milliseconds to sleep for to determine that
     *                this endpoint really is empty
     */
    public void setSleepForEmptyTest(long sleepForEmptyTest) {
        this.sleepForEmptyTest = sleepForEmptyTest;
    }

    public long getDefaulResultWaitMillis() {
        return defaulResultWaitMillis;
    }

    /**
     * Sets the maximum amount of time the {@link #assertIsSatisfied()} will
     * wait on a latch until it is satisfied
     */
    public void setDefaulResultWaitMillis(long defaulResultWaitMillis) {
        this.defaulResultWaitMillis = defaulResultWaitMillis;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected synchronized void onExchange(Exchange exchange) {
        try {
            Message in = exchange.getIn();
            Object actualBody = in.getBody();

            if (expectedBodyValues != null) {
                int index = actualBodyValues.size();
                if (expectedBodyValues.size() > index) {
                    Object expectedBody = expectedBodyValues.get(index);
                    if (expectedBody != null) {
                        actualBody = in.getBody(expectedBody.getClass());
                    }
                    actualBodyValues.add(actualBody);
                }
            }

            LOG.debug(getEndpointUri() + " >>>> " + (++counter) + " : " + exchange + " with body: " + actualBody);

            receivedExchanges.add(exchange);

            Processor processor = processors.get(getReceivedCounter());
            if (processor != null) {
                processor.process(exchange);
            }

            if (latch != null) {
                latch.countDown();
            }
        } catch (Exception e) {
            failures.add(e);
        }
    }

    protected void waitForCompleteLatch() throws InterruptedException {
        if (latch == null) {
            fail("Should have a latch!");
        }

        // now lets wait for the results
        LOG.debug("Waiting on the latch for: " + defaulResultWaitMillis + " millis");
        latch.await(defaulResultWaitMillis, TimeUnit.MILLISECONDS);
    }

    protected void assertEquals(String message, Object expectedValue, Object actualValue) {
        if (!ObjectHelper.equals(expectedValue, actualValue)) {
            fail(message + ". Expected: <" + expectedValue + "> but was: <" + actualValue + ">");
        }
    }

    protected void assertTrue(String message, boolean predicate) {
        if (!predicate) {
            fail(message);
        }
    }

    protected void fail(Object message) {
        if (LOG.isDebugEnabled()) {
            List<Exchange> list = getReceivedExchanges();
            int index = 0;
            for (Exchange exchange : list) {
                LOG.debug("Received[" + (++index) + "]: " + exchange);
            }
        }
        throw new AssertionError(getEndpointUri() + " " + message);
    }

    public int getExpectedMinimumCount() {
        return expectedMinimumCount;
    }

    public void await() throws InterruptedException {
        if (latch != null) {
            latch.await();
        }
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        if (latch != null) {
            return latch.await(timeout, unit);
        }
        return true;
    }

    public boolean isSingleton() {
        return true;
    }
}
