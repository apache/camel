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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Handler;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.InterceptSendToEndpoint;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ExpressionComparator;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Mock endpoint which provides a literate, fluent API for testing routes
 * using a <a href="http://jmock.org/">JMock style</a> API.
 * <p/>
 * The mock endpoint have two set of methods
 * <ul>
 *   <li>expectedXXX or expectsXXX - To set pre conditions, before the test is executed</li>
 *   <li>assertXXX - To assert assertions, after the test has been executed</li>
 * </ul>
 * Its <b>important</b> to know the difference between the two set. The former is used to
 * set expectations before the test is being started (eg before the mock receives messages).
 * The latter is used after the test has been executed, to verify the expectations; or
 * other assertions which you can perform after the test has been completed.
 *
 * @version 
 */
public class MockEndpoint extends DefaultEndpoint implements BrowsableEndpoint {
    private static final transient Logger LOG = LoggerFactory.getLogger(MockEndpoint.class);
    // must be volatile so changes is visible between the thread which performs the assertions
    // and the threads which process the exchanges when routing messages in Camel
    private volatile int expectedCount;
    private volatile int counter;
    private volatile Processor defaultProcessor;
    private volatile Map<Integer, Processor> processors;
    private volatile List<Exchange> receivedExchanges;
    private volatile List<Throwable> failures;
    private volatile List<Runnable> tests;
    private volatile CountDownLatch latch;
    private volatile long sleepForEmptyTest;
    private volatile long resultWaitTime;
    private volatile long resultMinimumWaitTime;
    private volatile long assertPeriod;
    private volatile int expectedMinimumCount;
    private volatile List<Object> expectedBodyValues;
    private volatile List<Object> actualBodyValues;
    private volatile String headerName;
    private volatile Object headerValue;
    private volatile Object actualHeader;
    private volatile String propertyName;
    private volatile Object propertyValue;
    private volatile Object actualProperty;
    private volatile Processor reporter;

    public MockEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        init();
    }

    public MockEndpoint(String endpointUri) {
        super(endpointUri);
        init();
    }

    public MockEndpoint() {
        this(null);
    }

    /**
     * A helper method to resolve the mock endpoint of the given URI on the given context
     *
     * @param context the camel context to try resolve the mock endpoint from
     * @param uri the uri of the endpoint to resolve
     * @return the endpoint
     */
    public static MockEndpoint resolve(CamelContext context, String uri) {
        return CamelContextHelper.getMandatoryEndpoint(context, uri, MockEndpoint.class);
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
        ObjectHelper.notNull(context, "camelContext");
        Collection<Endpoint> endpoints = context.getEndpoints();
        for (Endpoint endpoint : endpoints) {
            // if the endpoint was intercepted we should get the delegate
            if (endpoint instanceof InterceptSendToEndpoint) {
                endpoint = ((InterceptSendToEndpoint) endpoint).getDelegate();
            }
            if (endpoint instanceof MockEndpoint) {
                MockEndpoint mockEndpoint = (MockEndpoint) endpoint;
                mockEndpoint.assertIsSatisfied();
            }
        }
    }

    /**
     * Asserts that all the expectations on any {@link MockEndpoint} instances registered
     * in the given context are valid
     *
     * @param context the camel context used to find all the available endpoints to be asserted
     * @param timeout timeout
     * @param unit    time unit
     */
    public static void assertIsSatisfied(CamelContext context, long timeout, TimeUnit unit) throws InterruptedException {
        ObjectHelper.notNull(context, "camelContext");
        ObjectHelper.notNull(unit, "unit");
        Collection<Endpoint> endpoints = context.getEndpoints();
        long millis = unit.toMillis(timeout);
        for (Endpoint endpoint : endpoints) {
            // if the endpoint was intercepted we should get the delegate
            if (endpoint instanceof InterceptSendToEndpoint) {
                endpoint = ((InterceptSendToEndpoint) endpoint).getDelegate();
            }
            if (endpoint instanceof MockEndpoint) {
                MockEndpoint mockEndpoint = (MockEndpoint) endpoint;
                mockEndpoint.setResultWaitTime(millis);
                mockEndpoint.assertIsSatisfied();
            }
        }
    }

    /**
     * Sets the assert period on all the expectations on any {@link MockEndpoint} instances registered
     * in the given context.
     *
     * @param context the camel context used to find all the available endpoints
     * @param period the period in millis
     */
    public static void setAssertPeriod(CamelContext context, long period) {
        ObjectHelper.notNull(context, "camelContext");
        Collection<Endpoint> endpoints = context.getEndpoints();
        for (Endpoint endpoint : endpoints) {
            // if the endpoint was intercepted we should get the delegate
            if (endpoint instanceof InterceptSendToEndpoint) {
                endpoint = ((InterceptSendToEndpoint) endpoint).getDelegate();
            }
            if (endpoint instanceof MockEndpoint) {
                MockEndpoint mockEndpoint = (MockEndpoint) endpoint;
                mockEndpoint.setAssertPeriod(period);
            }
        }
    }

    /**
     * Reset all mock endpoints
     *
     * @param context the camel context used to find all the available endpoints to reset
     */
    public static void resetMocks(CamelContext context) {
        ObjectHelper.notNull(context, "camelContext");
        Collection<Endpoint> endpoints = context.getEndpoints();
        for (Endpoint endpoint : endpoints) {
            // if the endpoint was intercepted we should get the delegate
            if (endpoint instanceof InterceptSendToEndpoint) {
                endpoint = ((InterceptSendToEndpoint) endpoint).getDelegate();
            }
            if (endpoint instanceof MockEndpoint) {
                MockEndpoint mockEndpoint = (MockEndpoint) endpoint;
                mockEndpoint.reset();
            }
        }
    }

    public static void expectsMessageCount(int count, MockEndpoint... endpoints) throws InterruptedException {
        for (MockEndpoint endpoint : endpoints) {
            endpoint.setExpectedMessageCount(count);
        }
    }

    public List<Exchange> getExchanges() {
        return getReceivedExchanges();
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    public Producer createProducer() throws Exception {
        return new DefaultAsyncProducer(this) {
            public boolean process(Exchange exchange, AsyncCallback callback) {
                onExchange(exchange);
                callback.done(true);
                return true;
            }
        };
    }

    public void reset() {
        init();
    }


    // Testing API
    // -------------------------------------------------------------------------

    /**
     * Handles the incoming exchange.
     * <p/>
     * This method turns this mock endpoint into a bean which you can use
     * in the Camel routes, which allows you to inject MockEndpoint as beans
     * in your routes and use the features of the mock to control the bean.
     *
     * @param exchange  the exchange
     * @throws Exception can be thrown
     */
    @Handler
    public void handle(Exchange exchange) throws Exception {
        onExchange(exchange);
    }

    /**
     * Set the processor that will be invoked when the index
     * message is received.
     */
    public void whenExchangeReceived(int index, Processor processor) {
        this.processors.put(index, processor);
    }

    /**
     * Set the processor that will be invoked when the some message
     * is received.
     *
     * This processor could be overwritten by
     * {@link #whenExchangeReceived(int, Processor)} method.
     */
    public void whenAnyExchangeReceived(Processor processor) {
        this.defaultProcessor = processor;
    }
    
    /**
     * Set the expression which value will be set to the message body
     * @param expression which is use to set the message body 
     */
    public void returnReplyBody(Expression expression) {
        this.defaultProcessor = ProcessorBuilder.setBody(expression);
    }
    
    /**
     * Set the expression which value will be set to the message header
     * @param headerName that will be set value
     * @param expression which is use to set the message header 
     */
    public void returnReplyHeader(String headerName, Expression expression) {
        this.defaultProcessor = ProcessorBuilder.setHeader(headerName, expression);
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
        doAssertIsSatisfied(timeoutForEmptyEndpoints);
        if (assertPeriod > 0) {
            // if an assert period was set then re-assert again to ensure the assertion is still valid
            Thread.sleep(assertPeriod);
            LOG.info("Re-asserting: " + this + " is satisfied after " + assertPeriod + " millis");
            // do not use timeout when we re-assert
            doAssertIsSatisfied(0);
        }
    }

    protected void doAssertIsSatisfied(long timeoutForEmptyEndpoints) throws InterruptedException {
        if (expectedCount == 0) {
            if (timeoutForEmptyEndpoints > 0) {
                LOG.debug("Sleeping for: " + timeoutForEmptyEndpoints + " millis to check there really are no messages received");
                Thread.sleep(timeoutForEmptyEndpoints);
            }
            assertEquals("Received message count", expectedCount, getReceivedCounter());
        } else if (expectedCount > 0) {
            if (expectedCount != getReceivedCounter()) {
                waitForCompleteLatch();
            }
            assertEquals("Received message count", expectedCount, getReceivedCounter());
        } else if (expectedMinimumCount > 0 && getReceivedCounter() < expectedMinimumCount) {
            waitForCompleteLatch();
        }

        if (expectedMinimumCount >= 0) {
            int receivedCounter = getReceivedCounter();
            assertTrue("Received message count " + receivedCounter + ", expected at least " + expectedMinimumCount, expectedMinimumCount <= receivedCounter);
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
        boolean failed = false;
        try {
            assertIsSatisfied();
            // did not throw expected error... fail!
            failed = true;
        } catch (AssertionError e) {
            LOG.info("Caught expected failure: " + e);
        }
        if (failed) {
            // fail() throws the AssertionError to indicate the test failed. 
            fail("Expected assertion failure but test succeeded!");
        }
    }

    /**
     * Validates that the assertions fail on this endpoint

     * @param timeoutForEmptyEndpoints the timeout in milliseconds that we
     *        should wait for the test to be true
     */
    public void assertIsNotSatisfied(long timeoutForEmptyEndpoints) throws InterruptedException {
        boolean failed = false;
        try {
            assertIsSatisfied(timeoutForEmptyEndpoints);
            // did not throw expected error... fail!
            failed = true;
        } catch (AssertionError e) {
            LOG.info("Caught expected failure: " + e);
        }
        if (failed) { 
            // fail() throws the AssertionError to indicate the test failed. 
            fail("Expected assertion failure but test succeeded!");
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
        setExpectedMessageCount(expectedCount);
    }

    /**
     * Sets a grace period after which the mock endpoint will re-assert
     * to ensure the preliminary assertion is still valid.
     * <p/>
     * By default this period is disabled
     *
     * @param period grace period in millis
     */
    public void setAssertPeriod(long period) {
        this.assertPeriod = period;
    }

    /**
     * Specifies the minimum number of expected message exchanges that should be
     * received by this endpoint
     *
     * @param expectedCount the number of message exchanges that should be
     *                expected by this endpoint
     */
    public void expectedMinimumMessageCount(int expectedCount) {
        setMinimumExpectedMessageCount(expectedCount);
    }

    /**
     * Sets an expectation that the given header name & value are received by this endpoint
     */
    public void expectedHeaderReceived(final String name, final Object value) {
        this.headerName = name;
        this.headerValue = value;

        expects(new Runnable() {
            public void run() {
                assertTrue("No header with name " + headerName + " found.", actualHeader != null);

                Object actualValue;
                if (actualHeader instanceof Expression) {
                    actualValue = ((Expression)actualHeader).evaluate(mostRecentExchange(), headerValue.getClass());
                } else if (actualHeader instanceof Predicate) {
                    actualValue = ((Predicate)actualHeader).matches(mostRecentExchange());
                } else {                    
                    actualValue = getCamelContext().getTypeConverter().convertTo(headerValue.getClass(), actualHeader);
                    assertTrue("There is no type conversion possible from " + actualHeader.getClass().getName() 
                            + " to " + headerValue.getClass().getName(), actualValue != null);
                }
                assertEquals("Header with name " + headerName, headerValue, actualValue);
            }
        });
    }

    private Exchange mostRecentExchange() {
        return receivedExchanges.get(receivedExchanges.size() - 1);
    }
    
    /**
     * Sets an expectation that the given property name & value are received by this endpoint
     */
    public void expectedPropertyReceived(final String name, final Object value) {
        this.propertyName = name;
        this.propertyValue = value;

        expects(new Runnable() {
            public void run() {
                assertTrue("No property with name " + propertyName + " found.", actualProperty != null);

                Object actualValue = getCamelContext().getTypeConverter().convertTo(actualProperty.getClass(), propertyValue);
                assertEquals("Property with name " + propertyName, actualValue, actualProperty);
            }
        });
    }

    /**
     * Adds an expectation that the given body values are received by this
     * endpoint in the specified order
     */
    @SuppressWarnings("unchecked")
    public void expectedBodiesReceived(final List bodies) {
        expectedMessageCount(bodies.size());
        this.expectedBodyValues = bodies;
        this.actualBodyValues = new ArrayList<Object>();

        expects(new Runnable() {
            public void run() {
                for (int i = 0; i < expectedBodyValues.size(); i++) {
                    Exchange exchange = getReceivedExchanges().get(i);
                    assertTrue("No exchange received for counter: " + i, exchange != null);

                    Object expectedBody = expectedBodyValues.get(i);
                    Object actualBody = null;
                    if (i < actualBodyValues.size()) {
                        actualBody = actualBodyValues.get(i);
                    }

                    // TODO: coerce types before assertEquals
                    assertEquals("Body of message: " + i, expectedBody, actualBody);
                }
            }
        });
    }

    /**
     * Sets an expectation that the given predicates matches the received messages by this endpoint
     */
    public void expectedMessagesMatches(Predicate... predicates) {
        for (int i = 0; i < predicates.length; i++) {
            final int messageIndex = i;
            final Predicate predicate = predicates[i];
            final AssertionClause clause = new AssertionClause(this) {
                public void run() {
                    addPredicate(predicate);
                    applyAssertionOn(MockEndpoint.this, messageIndex, assertExchangeReceived(messageIndex));
                }
            };
            expects(clause);
        }
    }

    /**
     * Sets an expectation that the given body values are received by this endpoint
     */
    public void expectedBodiesReceived(Object... bodies) {
        List<Object> bodyList = new ArrayList<Object>();
        bodyList.addAll(Arrays.asList(bodies));
        expectedBodiesReceived(bodyList);
    }

    /**
     * Adds an expectation that the given body value are received by this endpoint
     */
    public ExpressionClause<?> expectedBodyReceived() {
        final ExpressionClause<?> clause = new ExpressionClause<MockEndpoint>(this);

        expectedMessageCount(1);

        expects(new Runnable() {
            public void run() {
                Exchange exchange = getReceivedExchanges().get(0);
                assertTrue("No exchange received for counter: " + 0, exchange != null);

                Object actualBody = exchange.getIn().getBody();
                Object expectedBody = clause.evaluate(exchange, Object.class);

                assertEquals("Body of message: " + 0, expectedBody, actualBody);
            }
        });

        return clause;
    }

    /**
     * Adds an expectation that the given body values are received by this
     * endpoint in any order
     */
    @SuppressWarnings("unchecked")
    public void expectedBodiesReceivedInAnyOrder(final List bodies) {
        expectedMessageCount(bodies.size());
        this.expectedBodyValues = bodies;
        this.actualBodyValues = new ArrayList<Object>();

        expects(new Runnable() {
            public void run() {
                Set<Object> actualBodyValuesSet = new HashSet<Object>(actualBodyValues);
                for (int i = 0; i < expectedBodyValues.size(); i++) {
                    Exchange exchange = getReceivedExchanges().get(i);
                    assertTrue("No exchange received for counter: " + i, exchange != null);

                    Object expectedBody = expectedBodyValues.get(i);
                    assertTrue("Message with body " + expectedBody
                            + " was expected but not found in " + actualBodyValuesSet,
                            actualBodyValuesSet.remove(expectedBody));
                }
            }
        });
    }

    /**
     * Adds an expectation that the given body values are received by this
     * endpoint in any order
     */
    public void expectedBodiesReceivedInAnyOrder(Object... bodies) {
        List<Object> bodyList = new ArrayList<Object>();
        bodyList.addAll(Arrays.asList(bodies));
        expectedBodiesReceivedInAnyOrder(bodyList);
    }

    /**
     * Adds an expectation that a file exists with the given name
     *
     * @param name name of file, will cater for / and \ on different OS platforms
     */
    public void expectedFileExists(final String name) {
        expectedFileExists(name, null);
    }

    /**
     * Adds an expectation that a file exists with the given name
     * <p/>
     * Will wait at most 5 seconds while checking for the existence of the file.
     *
     * @param name name of file, will cater for / and \ on different OS platforms
     * @param content content of file to compare, can be <tt>null</tt> to not compare content
     */
    public void expectedFileExists(final String name, final String content) {
        final File file = new File(FileUtil.normalizePath(name)).getAbsoluteFile();

        expects(new Runnable() {
            public void run() {
                // wait at most 5 seconds for the file to exists
                final long timeout = System.currentTimeMillis() + 5000;

                boolean stop = false;
                while (!stop && !file.exists()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    stop = System.currentTimeMillis() > timeout;
                }

                assertTrue("The file should exists: " + name, file.exists());

                if (content != null) {
                    String body = getCamelContext().getTypeConverter().convertTo(String.class, file);
                    assertEquals("Content of file: " + name, content, body);
                }
            }
        });
    }

    /**
     * Adds an expectation that messages received should have the given exchange pattern
     */
    public void expectedExchangePattern(final ExchangePattern exchangePattern) {
        expectedMessagesMatches(new Predicate() {
            public boolean matches(Exchange exchange) {
                return exchange.getPattern().equals(exchangePattern);
            }
        });
    }

    /**
     * Adds an expectation that messages received should have ascending values
     * of the given expression such as a user generated counter value
     */
    public void expectsAscending(final Expression expression) {
        expects(new Runnable() {
            public void run() {
                assertMessagesAscending(expression);
            }
        });
    }

    /**
     * Adds an expectation that messages received should have ascending values
     * of the given expression such as a user generated counter value
     */
    public ExpressionClause<?> expectsAscending() {
        final ExpressionClause<?> clause = new ExpressionClause<MockEndpoint>(this);
        expects(new Runnable() {
            public void run() {
                assertMessagesAscending(clause.getExpressionValue());
            }
        });
        return clause;
    }

    /**
     * Adds an expectation that messages received should have descending values
     * of the given expression such as a user generated counter value
     */
    public void expectsDescending(final Expression expression) {
        expects(new Runnable() {
            public void run() {
                assertMessagesDescending(expression);
            }
        });
    }

    /**
     * Adds an expectation that messages received should have descending values
     * of the given expression such as a user generated counter value
     */
    public ExpressionClause<?> expectsDescending() {
        final ExpressionClause<?> clause = new ExpressionClause<MockEndpoint>(this);
        expects(new Runnable() {
            public void run() {
                assertMessagesDescending(clause.getExpressionValue());
            }
        });
        return clause;
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
    public void expectsNoDuplicates(final Expression expression) {
        expects(new Runnable() {
            public void run() {
                assertNoDuplicates(expression);
            }
        });
    }

    /**
     * Adds an expectation that no duplicate messages should be received using
     * the expression to determine the message ID
     */
    public ExpressionClause<?> expectsNoDuplicates() {
        final ExpressionClause<?> clause = new ExpressionClause<MockEndpoint>(this);
        expects(new Runnable() {
            public void run() {
                assertNoDuplicates(clause.getExpressionValue());
            }
        });
        return clause;
    }

    /**
     * Asserts that the messages have ascending values of the given expression
     */
    public void assertMessagesAscending(Expression expression) {
        assertMessagesSorted(expression, true);
    }

    /**
     * Asserts that the messages have descending values of the given expression
     */
    public void assertMessagesDescending(Expression expression) {
        assertMessagesSorted(expression, false);
    }

    protected void assertMessagesSorted(Expression expression, boolean ascending) {
        String type = ascending ? "ascending" : "descending";
        ExpressionComparator comparator = new ExpressionComparator(expression);
        List<Exchange> list = getReceivedExchanges();
        for (int i = 1; i < list.size(); i++) {
            int j = i - 1;
            Exchange e1 = list.get(j);
            Exchange e2 = list.get(i);
            int result = comparator.compare(e1, e2);
            if (result == 0) {
                fail("Messages not " + type + ". Messages" + j + " and " + i + " are equal with value: "
                    + expression.evaluate(e1, Object.class) + " for expression: " + expression + ". Exchanges: " + e1 + " and " + e2);
            } else {
                if (!ascending) {
                    result = result * -1;
                }
                if (result > 0) {
                    fail("Messages not " + type + ". Message " + j + " has value: " + expression.evaluate(e1, Object.class)
                        + " and message " + i + " has value: " + expression.evaluate(e2, Object.class) + " for expression: "
                        + expression + ". Exchanges: " + e1 + " and " + e2);
                }
            }
        }
    }

    public void assertNoDuplicates(Expression expression) {
        Map<Object, Exchange> map = new HashMap<Object, Exchange>();
        List<Exchange> list = getReceivedExchanges();
        for (int i = 0; i < list.size(); i++) {
            Exchange e2 = list.get(i);
            Object key = expression.evaluate(e2, Object.class);
            Exchange e1 = map.get(key);
            if (e1 != null) {
                fail("Duplicate message found on message " + i + " has value: " + key + " for expression: " + expression + ". Exchanges: " + e1 + " and " + e2);
            } else {
                map.put(key, e2);
            }
        }
    }

    /**
     * Adds the expectation which will be invoked when enough messages are received
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
        final AssertionClause clause = new AssertionClause(this) {
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
        final AssertionClause clause = new AssertionClause(this) {
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
        return receivedExchanges.size();
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

    public long getResultWaitTime() {
        return resultWaitTime;
    }

    /**
     * Sets the maximum amount of time (in millis) the {@link #assertIsSatisfied()} will
     * wait on a latch until it is satisfied
     */
    public void setResultWaitTime(long resultWaitTime) {
        this.resultWaitTime = resultWaitTime;
    }

    /**
     * Sets the minimum expected amount of time (in millis) the {@link #assertIsSatisfied()} will
     * wait on a latch until it is satisfied
     */
    public void setMinimumResultWaitTime(long resultMinimumWaitTime) {
        this.resultMinimumWaitTime = resultMinimumWaitTime;
    }

    /**
     * Specifies the expected number of message exchanges that should be
     * received by this endpoint
     *
     * @param expectedCount the number of message exchanges that should be
     *                expected by this endpoint
     */
    public void setExpectedMessageCount(int expectedCount) {
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
    public void setMinimumExpectedMessageCount(int expectedCount) {
        this.expectedMinimumCount = expectedCount;
        if (expectedCount <= 0) {
            latch = null;
        } else {
            latch = new CountDownLatch(expectedMinimumCount);
        }
    }

    public Processor getReporter() {
        return reporter;
    }

    /**
     * Allows a processor to added to the endpoint to report on progress of the test
     */
    public void setReporter(Processor reporter) {
        this.reporter = reporter;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    private void init() {
        expectedCount = -1;
        counter = 0;
        processors = new HashMap<Integer, Processor>();
        receivedExchanges = new CopyOnWriteArrayList<Exchange>();
        failures = new CopyOnWriteArrayList<Throwable>();
        tests = new CopyOnWriteArrayList<Runnable>();
        latch = null;
        sleepForEmptyTest = 0;
        resultWaitTime = 0;
        resultMinimumWaitTime = 0L;
        assertPeriod = 0L;
        expectedMinimumCount = -1;
        expectedBodyValues = null;
        actualBodyValues = new ArrayList<Object>();
    }

    protected synchronized void onExchange(Exchange exchange) {
        try {
            if (reporter != null) {
                reporter.process(exchange);
            }
            performAssertions(exchange);
        } catch (Throwable e) {
            // must catch java.lang.Throwable as AssertionException extends java.lang.Error
            failures.add(e);
        } finally {
            // make sure latch is counted down to avoid test hanging forever
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    protected void performAssertions(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        Object actualBody = in.getBody();

        if (headerName != null) {
            actualHeader = in.getHeader(headerName);
        }

        if (propertyName != null) {
            actualProperty = exchange.getProperty(propertyName);
        }

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

        // let counter be 0 index-based in the logs
        if (LOG.isDebugEnabled()) {
            String msg = getEndpointUri() + " >>>> " + counter + " : " + exchange + " with body: " + actualBody;
            if (exchange.getIn().hasHeaders()) {
                msg += " and headers:" + exchange.getIn().getHeaders();
            }
            LOG.debug(msg);
        }
        ++counter;

        // record timestamp when exchange was received
        exchange.setProperty(Exchange.RECEIVED_TIMESTAMP, new Date());
        receivedExchanges.add(exchange);

        Processor processor = processors.get(getReceivedCounter()) != null
                ? processors.get(getReceivedCounter()) : defaultProcessor;

        if (processor != null) {
            try {
                processor.process(exchange);
            } catch (Exception e) {
                // set exceptions on exchange so we can throw exceptions to simulate errors
                exchange.setException(e);
            }
        }
    }

    protected void waitForCompleteLatch() throws InterruptedException {
        if (latch == null) {
            fail("Should have a latch!");
        }

        StopWatch watch = new StopWatch();
        waitForCompleteLatch(resultWaitTime);
        long delta = watch.stop();
        LOG.debug("Took {} millis to complete latch", delta);

        if (resultMinimumWaitTime > 0 && delta < resultMinimumWaitTime) {
            fail("Expected minimum " + resultMinimumWaitTime
                + " millis waiting on the result, but was faster with " + delta + " millis.");
        }
    }

    protected void waitForCompleteLatch(long timeout) throws InterruptedException {
        // Wait for a default 10 seconds if resultWaitTime is not set
        long waitTime = timeout == 0 ? 10000L : timeout;

        // now lets wait for the results
        LOG.debug("Waiting on the latch for: " + timeout + " millis");
        latch.await(waitTime, TimeUnit.MILLISECONDS);
    }

    protected void assertEquals(String message, Object expectedValue, Object actualValue) {
        if (!ObjectHelper.equal(expectedValue, actualValue)) {
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
                LOG.debug("{} failed and received[{}]: {}", new Object[]{getEndpointUri(), ++index, exchange});
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

    public boolean isLenientProperties() {
        return true;
    }
}
