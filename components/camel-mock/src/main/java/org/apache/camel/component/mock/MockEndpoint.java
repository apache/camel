/*
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Handler;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.NotifyBuilderMatcher;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionComparator;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mock component is used for testing routes and mediation rules using mocks.
 * <p/>
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
 * <p/>
 * <b>Beware:</b> If you want to expect a mock does not receive any messages, by calling
 * {@link #setExpectedMessageCount(int)} with <tt>0</tt>, then take extra care,
 * as <tt>0</tt> matches when the tests starts, so you need to set a assert period time
 * to let the test run for a while to make sure there are still no messages arrived; for
 * that use {@link #setAssertPeriod(long)}.
 * An alternative is to use <a href="http://camel.apache.org/notifybuilder.html">NotifyBuilder</a>, and use the notifier
 * to know when Camel is done routing some messages, before you call the {@link #assertIsSatisfied()} method on the mocks.
 * This allows you to not use a fixed assert period, to speedup testing times.
 * <p/>
 * <b>Important:</b> If using {@link #expectedMessageCount(int)} and also {@link #expectedBodiesReceived(java.util.List)} or
 * {@link #expectedHeaderReceived(String, Object)} then the latter overrides the number of expected message based on the
 * number of values provided in the bodies/headers.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "mock", title = "Mock", syntax = "mock:name", producerOnly = true, label = "core,testing", lenientProperties = true)
public class MockEndpoint extends DefaultEndpoint implements BrowsableEndpoint, NotifyBuilderMatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MockEndpoint.class);

    // must be volatile so changes is visible between the thread which performs the assertions
    // and the threads which process the exchanges when routing messages in Camel
    protected volatile Processor reporter;
    
    private volatile Processor defaultProcessor;
    private volatile Map<Integer, Processor> processors;
    private volatile List<Exchange> receivedExchanges;
    private volatile List<Throwable> failures;
    private volatile List<Runnable> tests;
    private volatile CountDownLatch latch;
    private volatile AssertionError failFastAssertionError;
    private volatile int expectedMinimumCount;
    private volatile List<?> expectedBodyValues;
    private volatile List<Object> actualBodyValues;
    private volatile Map<String, Object> expectedHeaderValues;
    private volatile Map<String, Object> actualHeaderValues;
    private volatile Map<String, Object> expectedPropertyValues;
    private volatile Map<String, Object> actualPropertyValues;

    private volatile int counter;

    @UriPath(description = "Name of mock endpoint") @Metadata(required = true)
    private String name;
    @UriParam(label = "producer", defaultValue = "-1")
    private int expectedCount;
    @UriParam(label = "producer", defaultValue = "0")
    private long sleepForEmptyTest;
    @UriParam(label = "producer", defaultValue = "0")
    private long resultWaitTime;
    @UriParam(label = "producer", defaultValue = "0")
    private long resultMinimumWaitTime;
    @UriParam(label = "producer", defaultValue = "0")
    private long assertPeriod;
    @UriParam(label = "producer", defaultValue = "-1")
    private int retainFirst;
    @UriParam(label = "producer", defaultValue = "-1")
    private int retainLast;
    @UriParam(label = "producer")
    private int reportGroup;
    @UriParam(label = "producer")
    private boolean failFast = true;
    @UriParam(label = "producer,advanced", defaultValue = "true")
    private boolean copyOnExchange = true;

    public MockEndpoint() {
        init();
    }

    public MockEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        init();
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
                endpoint = ((InterceptSendToEndpoint) endpoint).getOriginalEndpoint();
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
                endpoint = ((InterceptSendToEndpoint) endpoint).getOriginalEndpoint();
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
                endpoint = ((InterceptSendToEndpoint) endpoint).getOriginalEndpoint();
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
                endpoint = ((InterceptSendToEndpoint) endpoint).getOriginalEndpoint();
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

    @Override
    public List<Exchange> getExchanges() {
        return getReceivedExchanges();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    @Override
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
        doInit();
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
        this.defaultProcessor = exchange -> {
            Object exp = expression.evaluate(exchange, Object.class);
            exchange.getMessage().setBody(exp);
        };
    }
    
    /**
     * Set the expression which value will be set to the message header
     * @param headerName that will be set value
     * @param expression which is use to set the message header 
     */
    public void returnReplyHeader(String headerName, Expression expression) {
        this.defaultProcessor = exchange -> {
            Object exp = expression.evaluate(exchange, Object.class);
            exchange.getMessage().setHeader(headerName, exp);
        };
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
        LOG.info("Asserting: {} is satisfied", this);
        doAssertIsSatisfied(timeoutForEmptyEndpoints);
        if (assertPeriod > 0) {
            // if an assert period was set then re-assert again to ensure the assertion is still valid
            Thread.sleep(assertPeriod);
            LOG.info("Re-asserting: {} is satisfied after {} millis", this, assertPeriod);
            // do not use timeout when we re-assert
            doAssertIsSatisfied(0);
        }
    }

    protected void doAssertIsSatisfied(long timeoutForEmptyEndpoints) throws InterruptedException {
        if (expectedCount == 0) {
            if (timeoutForEmptyEndpoints > 0) {
                LOG.debug("Sleeping for: {} millis to check there really are no messages received", timeoutForEmptyEndpoints);
                Thread.sleep(timeoutForEmptyEndpoints);
            }
            assertEquals("Received message count", expectedCount, getReceivedCounter());
        } else if (expectedCount > 0) {
            if (expectedCount != getReceivedCounter()) {
                waitForCompleteLatch();
            }
            if (failFastAssertionError == null) {
                assertEquals("Received message count", expectedCount, getReceivedCounter());
            }
        } else if (expectedMinimumCount > 0 && getReceivedCounter() < expectedMinimumCount) {
            waitForCompleteLatch();
        }

        if (failFastAssertionError != null) {
            throw failFastAssertionError;
        }

        if (expectedMinimumCount >= 0) {
            int receivedCounter = getReceivedCounter();
            assertTrue("Received message count " + receivedCounter + ", expected at least " + expectedMinimumCount, expectedMinimumCount <= receivedCounter);
        }

        for (Runnable test : tests) {
            // skip tasks which we have already been running in fail fast mode
            boolean skip = failFast && test instanceof AssertionTask;
            if (!skip) {
                test.run();
            }
        }

        for (Throwable failure : failures) {
            if (failure != null) {
                LOG.error("Caught exception on " + getEndpointUri() + " due to: " + failure.getMessage(), failure);
                fail(failure);
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
            if (LOG.isDebugEnabled()) {
                // log incl stacktrace
                LOG.debug("Caught expected failure: " + e.getMessage(), e);
            } else {
                LOG.info("Caught expected failure: " + e.getMessage());
            }
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
            if (LOG.isDebugEnabled()) {
                // log incl stacktrace
                LOG.debug("Caught expected failure: " + e.getMessage(), e);
            } else {
                LOG.info("Caught expected failure: " + e.getMessage());
            }
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
     * If you want to assert that <b>exactly</b> n messages arrives to this mock
     * endpoint, then see also the {@link #setAssertPeriod(long)} method for further details.
     *
     * @param expectedCount the number of message exchanges that should be
     *                expected by this endpoint
     * @see #setAssertPeriod(long)
     */
    public void expectedMessageCount(int expectedCount) {
        setExpectedMessageCount(expectedCount);
    }

    public long getAssertPeriod() {
        return assertPeriod;
    }

    /**
     * Sets a grace period after which the mock endpoint will re-assert
     * to ensure the preliminary assertion is still valid.
     * <p/>
     * This is used for example to assert that <b>exactly</b> a number of messages 
     * arrives. For example if {@link #expectedMessageCount(int)} was set to 5, then
     * the assertion is satisfied when 5 or more message arrives. To ensure that
     * exactly 5 messages arrives, then you would need to wait a little period
     * to ensure no further message arrives. This is what you can use this
     * method for.
     * <p/>
     * By default this period is disabled.
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
     * <p/>
     * You can set multiple expectations for different header names.
     * If you set a value of <tt>null</tt> that means we accept either the header is absent, or its value is <tt>null</tt>
     */
    public void expectedHeaderReceived(final String name, final Object value) {
        if (expectedMinimumCount == -1 && expectedCount <= 0) {
            expectedMinimumMessageCount(1);
        }
        if (expectedHeaderValues == null) {
            HeadersMapFactory factory = getCamelContext().adapt(ExtendedCamelContext.class).getHeadersMapFactory();
            if (factory != null) {
                expectedHeaderValues = factory.newMap();
            } else {
                // should not really happen but some tests dont start camel context
                expectedHeaderValues = new HashMap<>();
            }
            // we just wants to expects to be called once
            expects(new AssertionTask() {
                @Override
                public void assertOnIndex(int i) {
                    Exchange exchange = getReceivedExchange(i);
                    for (Map.Entry<String, Object> entry : expectedHeaderValues.entrySet()) {
                        String key = entry.getKey();
                        Object expectedValue = entry.getValue();

                        // we accept that an expectedValue of null also means that the header may be absent
                        if (expectedValue != null) {
                            assertTrue("Exchange " + i + " has no headers", exchange.getIn().hasHeaders());
                            boolean hasKey = exchange.getIn().getHeaders().containsKey(key);
                            assertTrue("No header with name " + key + " found for message: " + i, hasKey);
                        }

                        Object actualValue = exchange.getIn().getHeader(key);
                        actualValue = extractActualValue(exchange, actualValue, expectedValue);

                        assertEquals("Header with name " + key + " for message: " + i, expectedValue, actualValue);
                    }
                }

                public void run() {
                    for (int i = 0; i < getReceivedExchanges().size(); i++) {
                        assertOnIndex(i);
                    }
                }
            });
        }
        expectedHeaderValues.put(name, value);
    }

    /**
     * Adds an expectation that the given header values are received by this
     * endpoint in any order.
     * <p/>
     * <b>Important:</b> The number of values must match the expected number of messages, so if you expect 3 messages, then
     * there must be 3 values.
     * <p/>
     * <b>Important:</b> This overrides any previous set value using {@link #expectedMessageCount(int)}
     */
    public void expectedHeaderValuesReceivedInAnyOrder(final String name, final List<?> values) {
        expectedMessageCount(values.size());

        expects(new Runnable() {
            public void run() {
                // these are the expected values to find
                final Set<Object> actualHeaderValues = new CopyOnWriteArraySet<>(values);

                for (int i = 0; i < getReceivedExchanges().size(); i++) {
                    Exchange exchange = getReceivedExchange(i);

                    Object actualValue = exchange.getIn().getHeader(name);
                    for (Object expectedValue : actualHeaderValues) {
                        actualValue = extractActualValue(exchange, actualValue, expectedValue);
                        // remove any found values
                        actualHeaderValues.remove(actualValue);
                    }
                }

                // should be empty, as we should find all the values
                assertTrue("Expected " + values.size() + " headers with key[" + name + "], received " + (values.size() - actualHeaderValues.size())
                        + " headers. Expected header values: " + actualHeaderValues, actualHeaderValues.isEmpty());
            }
        });
    }

    /**
     * Adds an expectation that the given header values are received by this
     * endpoint in any order
     * <p/>
     * <b>Important:</b> The number of values must match the expected number of messages, so if you expect 3 messages, then
     * there must be 3 values.
     * <p/>
     * <b>Important:</b> This overrides any previous set value using {@link #expectedMessageCount(int)}
     */
    public void expectedHeaderValuesReceivedInAnyOrder(String name, Object... values) {
        List<Object> valueList = new ArrayList<>();
        valueList.addAll(Arrays.asList(values));
        expectedHeaderValuesReceivedInAnyOrder(name, valueList);
    }

    /**
     * Sets an expectation that the given property name & value are received by this endpoint
     * <p/>
     * You can set multiple expectations for different property names.
     * If you set a value of <tt>null</tt> that means we accept either the property is absent, or its value is <tt>null</tt>
     */
    public void expectedPropertyReceived(final String name, final Object value) {
        if (expectedPropertyValues == null) {
            expectedPropertyValues = new HashMap<>();
        }
        expectedPropertyValues.put(name, value);

        expects(new AssertionTask() {
            @Override
            public void assertOnIndex(int i) {
                Exchange exchange = getReceivedExchange(i);
                for (Map.Entry<String, Object> entry : expectedPropertyValues.entrySet()) {
                    String key = entry.getKey();
                    Object expectedValue = entry.getValue();

                    // we accept that an expectedValue of null also means that the property may be absent
                    if (expectedValue != null) {
                        assertTrue("Exchange " + i + " has no properties", !exchange.getProperties().isEmpty());
                        boolean hasKey = exchange.getProperties().containsKey(key);
                        assertTrue("No property with name " + key + " found for message: " + i, hasKey);
                    }

                    Object actualValue = exchange.getProperty(key);
                    actualValue = extractActualValue(exchange, actualValue, expectedValue);

                    assertEquals("Property with name " + key + " for message: " + i, expectedValue, actualValue);
                }
            }

            public void run() {
                for (int i = 0; i < getReceivedExchanges().size(); i++) {
                    assertOnIndex(i);
                }
            }
        });
    }

    /**
     * Adds an expectation that the given property values are received by this
     * endpoint in any order.
     * <p/>
     * <b>Important:</b> The number of values must match the expected number of messages, so if you expect 3 messages, then
     * there must be 3 values.
     * <p/>
     * <b>Important:</b> This overrides any previous set value using {@link #expectedMessageCount(int)}
     */
    public void expectedPropertyValuesReceivedInAnyOrder(final String name, final List<?> values) {
        expectedMessageCount(values.size());

        expects(new Runnable() {
            public void run() {
                // these are the expected values to find
                final Set<Object> actualPropertyValues = new CopyOnWriteArraySet<>(values);

                for (int i = 0; i < getReceivedExchanges().size(); i++) {
                    Exchange exchange = getReceivedExchange(i);

                    Object actualValue = exchange.getProperty(name);
                    for (Object expectedValue : actualPropertyValues) {
                        actualValue = extractActualValue(exchange, actualValue, expectedValue);
                        // remove any found values
                        actualPropertyValues.remove(actualValue);
                    }
                }

                // should be empty, as we should find all the values
                assertTrue("Expected " + values.size() + " properties with key[" + name + "], received " + (values.size() - actualPropertyValues.size())
                        + " properties. Expected property values: " + actualPropertyValues, actualPropertyValues.isEmpty());
            }
        });
    }

    /**
     * Adds an expectation that the given property values are received by this
     * endpoint in any order
     * <p/>
     * <b>Important:</b> The number of values must match the expected number of messages, so if you expect 3 messages, then
     * there must be 3 values.
     * <p/>
     * <b>Important:</b> This overrides any previous set value using {@link #expectedMessageCount(int)}
     */
    public void expectedPropertyValuesReceivedInAnyOrder(String name, Object... values) {
        List<Object> valueList = new ArrayList<>();
        valueList.addAll(Arrays.asList(values));
        expectedPropertyValuesReceivedInAnyOrder(name, valueList);
    }    

    /**
     * Adds an expectation that the given body values are received by this
     * endpoint in the specified order
     * <p/>
     * <b>Important:</b> The number of values must match the expected number of messages, so if you expect 3 messages, then
     * there must be 3 values.
     * <p/>
     * <b>Important:</b> This overrides any previous set value using {@link #expectedMessageCount(int)}
     */
    public void expectedBodiesReceived(final List<?> bodies) {
        expectedMessageCount(bodies.size());
        this.expectedBodyValues = bodies;
        this.actualBodyValues = new ArrayList<>();

        expects(new AssertionTask() {
            @Override
            public void assertOnIndex(int i) {
                Exchange exchange = getReceivedExchange(i);
                assertTrue("No exchange received for counter: " + i, exchange != null);

                Object expectedBody = expectedBodyValues.get(i);
                Object actualBody = null;
                if (i < actualBodyValues.size()) {
                    actualBody = actualBodyValues.get(i);
                }
                actualBody = extractActualValue(exchange, actualBody, expectedBody);

                assertEquals("Body of message: " + i, expectedBody, actualBody);
            }

            public void run() {
                for (int i = 0; i < expectedBodyValues.size(); i++) {
                    assertOnIndex(i);
                }
            }
        });
    }

    private Object extractActualValue(Exchange exchange, Object actualValue, Object expectedValue) {
        if (actualValue == null) {
            return null;
        }

        if (actualValue instanceof Expression) {
            Class<?> clazz = Object.class;
            if (expectedValue != null) {
                clazz = expectedValue.getClass();
            }
            actualValue = ((Expression)actualValue).evaluate(exchange, clazz);
        } else if (actualValue instanceof Predicate) {
            actualValue = ((Predicate)actualValue).matches(exchange);
        } else if (expectedValue != null) {
            String from = actualValue.getClass().getName();
            String to = expectedValue.getClass().getName();
            actualValue = getCamelContext().getTypeConverter().convertTo(expectedValue.getClass(), exchange, actualValue);
            assertTrue("There is no type conversion possible from " + from + " to " + to, actualValue != null);
        }
        return actualValue;
    }

    /**
     * Sets an expectation that the given predicates matches the received messages by this endpoint
     */
    public void expectedMessagesMatches(Predicate... predicates) {
        for (int i = 0; i < predicates.length; i++) {
            final int messageIndex = i;
            final Predicate predicate = predicates[i];
            final AssertionClause clause = new AssertionClauseTask(this) {
                @Override
                public void assertOnIndex(int index) {
                    if (messageIndex == index) {
                        addPredicate(predicate);
                        applyAssertionOn(MockEndpoint.this, index, assertExchangeReceived(index));
                    }
                }

                public void run() {
                    for (int i = 0; i < getReceivedExchanges().size(); i++) {
                        assertOnIndex(i);
                    }
                }
            };
            expects(clause);
        }
    }

    /**
     * Sets an expectation that the given body values are received by this endpoint
     * <p/>
     * <b>Important:</b> The number of bodies must match the expected number of messages, so if you expect 3 messages, then
     * there must be 3 bodies.
     * <p/>
     * <b>Important:</b> This overrides any previous set value using {@link #expectedMessageCount(int)}
     */
    public void expectedBodiesReceived(Object... bodies) {
        List<Object> bodyList = new ArrayList<>();
        bodyList.addAll(Arrays.asList(bodies));
        expectedBodiesReceived(bodyList);
    }

    /**
     * Adds an expectation that the given body value are received by this endpoint
     */
    public AssertionClause expectedBodyReceived() {
        expectedMessageCount(1);
        final AssertionClause clause = new AssertionClauseTask(this) {
            @Override
            public void assertOnIndex(int index) {
                if (index == 0) {
                    Exchange exchange = getReceivedExchange(index);
                    assertTrue("No exchange received for counter: " + index, exchange != null);

                    Object actualBody = exchange.getIn().getBody();
                    Expression exp = createExpression(getCamelContext());
                    Object expectedBody = exp.evaluate(exchange, Object.class);

                    assertEquals("Body of message: " + index, expectedBody, actualBody);
                }
            }

            public void run() {
                assertOnIndex(0);
            }
        };
        expects(clause);
        return clause;
    }

    /**
     * Adds an expectation that the given body values are received by this
     * endpoint in any order
     * <p/>
     * <b>Important:</b> The number of bodies must match the expected number of messages, so if you expect 3 messages, then
     * there must be 3 bodies.
     * <p/>
     * <b>Important:</b> This overrides any previous set value using {@link #expectedMessageCount(int)}
     */
    public void expectedBodiesReceivedInAnyOrder(final List<?> bodies) {
        expectedMessageCount(bodies.size());
        this.expectedBodyValues = bodies;
        this.actualBodyValues = new ArrayList<>();

        expects(new Runnable() {
            public void run() {
                List<Object> actualBodyValuesSet = new ArrayList<>(actualBodyValues);
                for (int i = 0; i < expectedBodyValues.size(); i++) {
                    Exchange exchange = getReceivedExchange(i);
                    assertTrue("No exchange received for counter: " + i, exchange != null);

                    Object expectedBody = expectedBodyValues.get(i);
                    assertTrue("Message with body " + expectedBody + " was expected but not found in " + actualBodyValuesSet, actualBodyValuesSet.remove(expectedBody));
                }
            }
        });
    }

    /**
     * Adds an expectation that the given body values are received by this
     * endpoint in any order
     * <p/>
     * <b>Important:</b> The number of bodies must match the expected number of messages, so if you expect 3 messages, then
     * there must be 3 bodies.
     * <p/>
     * <b>Important:</b> This overrides any previous set value using {@link #expectedMessageCount(int)}
     */
    public void expectedBodiesReceivedInAnyOrder(Object... bodies) {
        List<Object> bodyList = new ArrayList<>();
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
        final File file = new File(FileUtil.normalizePath(name));

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
        expects(new AssertionTask() {
            @Override
            public void assertOnIndex(int index) {
                assertMessagesSorted(expression, true, index);
            }

            public void run() {
                assertMessagesAscending(expression);
            }
        });
    }

    /**
     * Adds an expectation that messages received should have ascending values
     * of the given expression such as a user generated counter value
     */
    public AssertionClause expectsAscending() {
        final AssertionClause clause = new AssertionClauseTask(this) {
            @Override
            public void assertOnIndex(int index) {
                assertMessagesSorted(createExpression(getCamelContext()), true, index);
            }

            public void run() {
                assertMessagesAscending(createExpression(getCamelContext()));
            }
        };
        expects(clause);
        return clause;
    }

    /**
     * Adds an expectation that messages received should have descending values
     * of the given expression such as a user generated counter value
     */
    public void expectsDescending(final Expression expression) {
        expects(new AssertionTask() {
            @Override
            public void assertOnIndex(int index) {
                assertMessagesSorted(expression, false, index);
            }

            public void run() {
                assertMessagesDescending(expression);
            }
        });
    }

    /**
     * Adds an expectation that messages received should have descending values
     * of the given expression such as a user generated counter value
     */
    public AssertionClause expectsDescending() {
        final AssertionClause clause = new AssertionClauseTask(this) {
            @Override
            public void assertOnIndex(int index) {
                assertMessagesSorted(createExpression(getCamelContext()), false, index);
            }

            public void run() {
                assertMessagesDescending(createExpression(getCamelContext()));
            }
        };
        expects(clause);
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
        expects(new AssertionTask() {
            private Map<Object, Exchange> map = new HashMap<>();

            @Override
            public void assertOnIndex(int index) {
                List<Exchange> list = getReceivedExchanges();
                Exchange e2 = list.get(index);
                Object key = expression.evaluate(e2, Object.class);
                Exchange e1 = map.get(key);
                if (e1 != null) {
                    fail("Duplicate message found on message " + index + " has value: " + key + " for expression: " + expression + ". Exchanges: " + e1 + " and " + e2);
                } else {
                    map.put(key, e2);
                }
            }

            public void run() {
                for (int i = 0; i < getReceivedExchanges().size(); i++) {
                    assertOnIndex(i);
                }
            }
        });
    }

    /**
     * Adds an expectation that no duplicate messages should be received using
     * the expression to determine the message ID
     */
    public AssertionClause expectsNoDuplicates() {
        final AssertionClause clause = new AssertionClauseTask(this) {
            private Map<Object, Exchange> map = new HashMap<>();
            private Expression exp;

            @Override
            public void assertOnIndex(int index) {
                if (exp == null) {
                    exp = createExpression(getCamelContext());
                }
                List<Exchange> list = getReceivedExchanges();
                Exchange e2 = list.get(index);
                Object key = exp.evaluate(e2, Object.class);
                Exchange e1 = map.get(key);
                if (e1 != null) {
                    fail("Duplicate message found on message " + index + " has value: " + key + " for expression: " + exp + ". Exchanges: " + e1 + " and " + e2);
                } else {
                    map.put(key, e2);
                }
            }

            public void run() {
                for (int i = 0; i < getReceivedExchanges().size(); i++) {
                    assertOnIndex(i);
                }
            }
        };
        expects(clause);
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
        List<Exchange> list = getReceivedExchanges();
        for (int i = 0; i < list.size(); i++) {
            assertMessagesSorted(expression, ascending, i);
        }
    }

    protected void assertMessagesSorted(Expression expression, boolean ascending, int index) {
        String type = ascending ? "ascending" : "descending";
        ExpressionComparator comparator = new ExpressionComparator(expression);

        int prev = index - 1;
        if (prev > 0) {
            List<Exchange> list = getReceivedExchanges();
            Exchange e1 = list.get(prev);
            Exchange e2 = list.get(index);
            int result = comparator.compare(e1, e2);
            if (result == 0) {
                fail("Messages not " + type + ". Messages" + prev + " and " + index + " are equal with value: "
                        + expression.evaluate(e1, Object.class) + " for expression: " + expression + ". Exchanges: " + e1 + " and " + e2);
            } else {
                if (!ascending) {
                    result = result * -1;
                }
                if (result > 0) {
                    fail("Messages not " + type + ". Message " + prev + " has value: " + expression.evaluate(e1, Object.class)
                            + " and message " + index + " has value: " + expression.evaluate(e2, Object.class) + " for expression: "
                            + expression + ". Exchanges: " + e1 + " and " + e2);
                }
            }
        }
    }


    /**
     * Asserts among all the current received exchanges that there are no duplicate message
     *
     * @param expression  the expression to use for duplication check
     */
    public void assertNoDuplicates(Expression expression) {
        Map<Object, Exchange> map = new HashMap<>();
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
        final AssertionClause clause = new AssertionClauseTask(this) {
            @Override
            public void assertOnIndex(int index) {
                if (index == messageIndex) {
                    applyAssertionOn(MockEndpoint.this, index, assertExchangeReceived(index));
                }
            }

            public void run() {
                assertOnIndex(messageIndex);
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
        final AssertionClause clause = new AssertionClauseTask(this) {
            @Override
            public void assertOnIndex(int index) {
                if (index < getReceivedExchanges().size()) {
                    applyAssertionOn(MockEndpoint.this, index, assertExchangeReceived(index));
                }
            }

            public void run() {
                for (int i = 0; i < getReceivedExchanges().size(); i++) {
                    assertOnIndex(i);
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
        return getReceivedExchange(index);
    }

    @Override
    public void notifyBuilderOnExchange(Exchange exchange) {
        onExchange(exchange);
    }

    @Override
    public void notifyBuilderReset() {
        reset();
    }

    @Override
    public boolean notifyBuilderMatches() {
        if (latch != null) {
            try {
                return latch.await(0, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        } else {
            return true;
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Throwable> getFailures() {
        return failures;
    }

    public int getReceivedCounter() {
        return counter;
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

    public long getResultMinimumWaitTime() {
        return resultMinimumWaitTime;
    }

    /**
     * Sets the minimum expected amount of time (in millis) the {@link #assertIsSatisfied()} will
     * wait on a latch until it is satisfied
     */
    public void setResultMinimumWaitTime(long resultMinimumWaitTime) {
        this.resultMinimumWaitTime = resultMinimumWaitTime;
    }

    /**
     * Specifies the expected number of message exchanges that should be
     * received by this endpoint.
     * <p/>
     * <b>Beware:</b> If you want to expect that <tt>0</tt> messages, then take extra care,
     * as <tt>0</tt> matches when the tests starts, so you need to set a assert period time
     * to let the test run for a while to make sure there are still no messages arrived; for
     * that use {@link #setAssertPeriod(long)}.
     * An alternative is to use <a href="http://camel.apache.org/notifybuilder.html">NotifyBuilder</a>, and use the notifier
     * to know when Camel is done routing some messages, before you call the {@link #assertIsSatisfied()} method on the mocks.
     * This allows you to not use a fixed assert period, to speedup testing times.
     * <p/>
     * If you want to assert that <b>exactly</b> n'th message arrives to this mock
     * endpoint, then see also the {@link #setAssertPeriod(long)} method for further details.
     *
     * @param expectedCount the number of message exchanges that should be
     *                expected by this endpoint
     * @see #setAssertPeriod(long)                      
     */
    public void setExpectedCount(int expectedCount) {
        setExpectedMessageCount(expectedCount);
    }

    /**
     * @see #setExpectedCount(int)
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

    public int getRetainFirst() {
        return retainFirst;
    }

    /**
     * Specifies to only retain the first n'th number of received {@link Exchange}s.
     * <p/>
     * This is used when testing with big data, to reduce memory consumption by not storing
     * copies of every {@link Exchange} this mock endpoint receives.
     * <p/>
     * <b>Important:</b> When using this limitation, then the {@link #getReceivedCounter()}
     * will still return the actual number of received {@link Exchange}s. For example
     * if we have received 5000 {@link Exchange}s, and have configured to only retain the first
     * 10 {@link Exchange}s, then the {@link #getReceivedCounter()} will still return <tt>5000</tt>
     * but there is only the first 10 {@link Exchange}s in the {@link #getExchanges()} and
     * {@link #getReceivedExchanges()} methods.
     * <p/>
     * When using this method, then some of the other expectation methods is not supported,
     * for example the {@link #expectedBodiesReceived(Object...)} sets a expectation on the first
     * number of bodies received.
     * <p/>
     * You can configure both {@link #setRetainFirst(int)} and {@link #setRetainLast(int)} methods,
     * to limit both the first and last received.
     * 
     * @param retainFirst  to limit and only keep the first n'th received {@link Exchange}s, use
     *                     <tt>0</tt> to not retain any messages, or <tt>-1</tt> to retain all.
     * @see #setRetainLast(int)
     */
    public void setRetainFirst(int retainFirst) {
        this.retainFirst = retainFirst;
    }

    public int getRetainLast() {
        return retainLast;
    }

    /**
     * Specifies to only retain the last n'th number of received {@link Exchange}s.
     * <p/>
     * This is used when testing with big data, to reduce memory consumption by not storing
     * copies of every {@link Exchange} this mock endpoint receives.
     * <p/>
     * <b>Important:</b> When using this limitation, then the {@link #getReceivedCounter()}
     * will still return the actual number of received {@link Exchange}s. For example
     * if we have received 5000 {@link Exchange}s, and have configured to only retain the last
     * 20 {@link Exchange}s, then the {@link #getReceivedCounter()} will still return <tt>5000</tt>
     * but there is only the last 20 {@link Exchange}s in the {@link #getExchanges()} and
     * {@link #getReceivedExchanges()} methods.
     * <p/>
     * When using this method, then some of the other expectation methods is not supported,
     * for example the {@link #expectedBodiesReceived(Object...)} sets a expectation on the first
     * number of bodies received.
     * <p/>
     * You can configure both {@link #setRetainFirst(int)} and {@link #setRetainLast(int)} methods,
     * to limit both the first and last received.
     *
     * @param retainLast  to limit and only keep the last n'th received {@link Exchange}s, use
     *                     <tt>0</tt> to not retain any messages, or <tt>-1</tt> to retain all.
     * @see #setRetainFirst(int)
     */
    public void setRetainLast(int retainLast) {
        this.retainLast = retainLast;
    }

    public int getReportGroup() {
        return reportGroup;
    }

    /**
     * A number that is used to turn on throughput logging based on groups of the size.
     */
    public void setReportGroup(int reportGroup) {
        this.reportGroup = reportGroup;
    }

    public boolean isCopyOnExchange() {
        return copyOnExchange;
    }

    /**
     * Sets whether to make a deep copy of the incoming {@link Exchange} when received at this mock endpoint.
     * <p/>
     * Is by default <tt>true</tt>.
     */
    public void setCopyOnExchange(boolean copyOnExchange) {
        this.copyOnExchange = copyOnExchange;
    }

    public boolean isFailFast() {
        return failFast;
    }

    /**
     * Sets whether {@link #assertIsSatisfied()} should fail fast
     * at the first detected failed expectation while it may otherwise wait for all expected
     * messages to arrive before performing expectations verifications.
     *
     * Is by default <tt>true</tt>. Set to <tt>false</tt> to use behavior as in Camel 2.x.
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    @Override
    protected void doInit() {
        expectedCount = -1;
        counter = 0;
        defaultProcessor = null;
        processors = new HashMap<>();
        receivedExchanges = new CopyOnWriteArrayList<>();
        failures = new CopyOnWriteArrayList<>();
        tests = new CopyOnWriteArrayList<>();
        latch = null;
        failFastAssertionError = null;
        sleepForEmptyTest = 0;
        resultWaitTime = 0;
        resultMinimumWaitTime = 0L;
        assertPeriod = 0L;
        expectedMinimumCount = -1;
        expectedBodyValues = null;
        actualBodyValues = new ArrayList<>();
        expectedHeaderValues = null;
        actualHeaderValues = null;
        expectedPropertyValues = null;
        actualPropertyValues = null;
        retainFirst = -1;
        retainLast = -1;
    }

    protected synchronized void onExchange(Exchange exchange) {
        try {
            if (reporter != null) {
                reporter.process(exchange);
            }
            Exchange copy = exchange;
            if (copyOnExchange) {
                // copy the exchange so the mock stores the copy and not the actual exchange
                copy = ExchangeHelper.createCopy(exchange, true);
            }
            performAssertions(exchange, copy);

            if (failFast) {
                // fail fast mode so check n'th expectations as soon as possible
                int index = getReceivedCounter() - 1;
                for (Runnable test : tests) {
                    // only assertion tasks can support fail fast mode
                    if (test instanceof AssertionTask) {
                        AssertionTask task = (AssertionTask) test;
                        try {
                            LOG.debug("Running assertOnIndex({}) on task: {}", index, task);
                            task.assertOnIndex(index);
                        } catch (AssertionError e) {
                            failFastAssertionError = e;
                            // signal latch we are done as we are failing fast
                            LOG.debug("Assertion failed fast on " + index + " received exchange due to " + e.getMessage());
                            while (latch != null && latch.getCount() > 0) {
                                latch.countDown();
                            }
                            // we are failing fast
                            break;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            // must catch java.lang.Throwable as AssertionError extends java.lang.Error
            failures.add(e);
        } finally {
            // make sure latch is counted down to avoid test hanging forever
            if (latch != null) {
                latch.countDown();
            }
        }
    }

    /**
     * Performs the assertions on the incoming exchange.
     *
     * @param exchange   the actual exchange
     * @param copy       a copy of the exchange (only store this)
     * @throws Exception can be thrown if something went wrong
     */
    protected void performAssertions(Exchange exchange, Exchange copy) throws Exception {
        Message in = copy.getIn();
        Object actualBody = in.getBody();

        if (expectedHeaderValues != null) {
            if (actualHeaderValues == null) {
                HeadersMapFactory factory = getCamelContext().adapt(ExtendedCamelContext.class).getHeadersMapFactory();
                if (factory != null) {
                    actualHeaderValues = factory.newMap();
                } else {
                    // should not really happen but some tests dont start camel context
                    actualHeaderValues = new HashMap<>();
                }
            }
            if (in.hasHeaders()) {
                actualHeaderValues.putAll(in.getHeaders());
            }
        }

        if (expectedPropertyValues != null) {
            if (actualPropertyValues == null) {
                HeadersMapFactory factory = getCamelContext().adapt(ExtendedCamelContext.class).getHeadersMapFactory();
                if (factory != null) {
                    actualPropertyValues = factory.newMap();
                } else {
                    // should not really happen but some tests dont start camel context
                    actualPropertyValues = new HashMap<>();
                }
            }
            actualPropertyValues.putAll(copy.getProperties());
        }

        if (expectedBodyValues != null) {
            int index = actualBodyValues.size();
            if (expectedBodyValues.size() > index) {
                Object expectedBody = expectedBodyValues.get(index);
                if (expectedBody != null) {
                    // prefer to convert body early, for example when using files
                    // we need to read the content at this time
                    Object body = in.getBody(expectedBody.getClass());
                    if (body != null) {
                        actualBody = body;
                    }
                }
                actualBodyValues.add(actualBody);
            }
        }

        // let counter be 0 index-based in the logs
        if (LOG.isDebugEnabled()) {
            String msg = getEndpointUri() + " >>>> " + counter + " : " + copy + " with body: " + actualBody;
            if (copy.getIn().hasHeaders()) {
                msg += " and headers:" + copy.getIn().getHeaders();
            }
            LOG.debug(msg);
        }

        // record timestamp when exchange was received
        copy.setProperty(Exchange.RECEIVED_TIMESTAMP, new Date());

        // add a copy of the received exchange
        addReceivedExchange(copy);
        // and then increment counter after adding received exchange
        ++counter;

        Processor processor = processors.get(getReceivedCounter()) != null
                ? processors.get(getReceivedCounter()) : defaultProcessor;

        if (processor != null) {
            try {
                // must process the incoming exchange and NOT the copy as the idea
                // is the end user can manipulate the exchange
                processor.process(exchange);
            } catch (Exception e) {
                // set exceptions on exchange so we can throw exceptions to simulate errors
                exchange.setException(e);
            }
        }
    }

    /**
     * Adds the received exchange.
     * 
     * @param copy  a copy of the received exchange
     */
    protected void addReceivedExchange(Exchange copy) {
        if (retainFirst == 0 && retainLast == 0) {
            // do not retain any messages at all
        } else if (retainFirst < 0 && retainLast < 0) {
            // no limitation so keep them all
            receivedExchanges.add(copy);
        } else {
            // okay there is some sort of limitations, so figure out what to retain
            if (retainFirst > 0 && counter < retainFirst) {
                // store a copy as its within the retain first limitation
                receivedExchanges.add(copy);
            } else if (retainLast > 0) {
                // remove the oldest from the last retained boundary,
                int index = receivedExchanges.size() - retainLast;
                if (index >= 0) {
                    // but must be outside the first range as well
                    // otherwise we should not remove the oldest
                    if (retainFirst <= 0 || retainFirst <= index) {
                        receivedExchanges.remove(index);
                    }
                }
                // store a copy of the last n'th received
                receivedExchanges.add(copy);
            }
        }
    }

    protected void waitForCompleteLatch() throws InterruptedException {
        if (latch == null) {
            fail("Should have a latch!");
        }

        StopWatch watch = new StopWatch();
        waitForCompleteLatch(resultWaitTime);
        long delta = watch.taken();
        LOG.debug("Took {} millis to complete latch", delta);

        if (resultMinimumWaitTime > 0 && delta < resultMinimumWaitTime) {
            fail("Expected minimum " + resultMinimumWaitTime
                + " millis waiting on the result, but was faster with " + delta + " millis.");
        }
    }

    protected void waitForCompleteLatch(long timeout) throws InterruptedException {
        // Wait for a default 10 seconds if resultWaitTime is not set
        long waitTime = timeout == 0 ? 10000L : timeout;

        // now let's wait for the results
        LOG.debug("Waiting on the latch for: {} millis", timeout);
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
                LOG.debug("{} failed and received[{}]: {}", getEndpointUri(), ++index, exchange);
            }
        }
        if (message instanceof Throwable) {
            Throwable cause = (Throwable) message;
            String msg = "Caught exception on " + getEndpointUri() + " due to: " + cause.getMessage();
            throw new AssertionError(msg, cause);
        } else {
            throw new AssertionError(getEndpointUri() + " " + message);
        }
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

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    private Exchange getReceivedExchange(int index) {
        if (index <= receivedExchanges.size() - 1) {
            return receivedExchanges.get(index);
        } else {
            return null;
        }
    }

}
