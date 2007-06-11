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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A Mock endpoint which provides a literate, fluent API for testing routes using
 * a <a href="http://jmock.org/">JMock style</a> API.
 *
 * @version $Revision: 1.1 $
 */
public class MockEndpoint extends DefaultEndpoint<Exchange> {
    private static final transient Log log = LogFactory.getLog(MockEndpoint.class);
    private int expectedCount = -1;
    private Map<Integer, Processor> processors = new HashMap<Integer, Processor>();
    private List<Exchange> receivedExchanges = new ArrayList<Exchange>();
    private List<Throwable> failures = new ArrayList<Throwable>();
    private List<Runnable> tests = new ArrayList<Runnable>();
    private CountDownLatch latch;
    private long sleepForEmptyTest = 0L;
	private int expectedMinimumCount=-1;

    public static void assertWait(long timeout, TimeUnit unit, MockEndpoint... endpoints) throws InterruptedException {
    	long start = System.currentTimeMillis();
    	long left = unit.toMillis(timeout);
    	long end = start + left;
        for (MockEndpoint endpoint : endpoints) {
			if( !endpoint.await(left, TimeUnit.MILLISECONDS) )
	    		throw new AssertionError("Timeout waiting for endpoints to receive enough messages. "+endpoint.getEndpointUri()+" timed out.");
			left = end - System.currentTimeMillis();
			if( left <= 0 )
				left = 0;
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
            assertEquals("Received message count" , expectedCount, receivedCounter);
        }
        
        if( expectedMinimumCount >= 0 ) {
            int receivedCounter = getReceivedCounter();
            assertTrue("Received message count "+receivedCounter+", expected at least "+expectedCount, expectedCount <= receivedCounter);
        	
        }

        for (Runnable test : tests) {
            test.run();
        }

        for (Throwable failure : failures) {
           if (failure != null) {
               log.error("Caught on " + getEndpointUri() + " Exception: " + failure, failure);
               fail("Failed due to caught exception: " + failure);
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
     * Specifies the minimum number of expected message exchanges that should be received by this endpoint
     *
     * @param expectedCount the number of message exchanges that should be expected by this endpoint
     */
    public void expectedMinimumMessageCount(int expectedCount) {
        this.expectedMinimumCount = expectedCount;
        if (expectedCount <= 0) {
            latch = null;
        }
        else {
            latch = new CountDownLatch(expectedMinimumCount);
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
                    Exchange exchange = getReceivedExchanges().get(counter++);
                    assertTrue("No exchange received for counter: " + counter, exchange != null);

                    Message in = exchange.getIn();

                    Object actualBody = (expectedBody != null)
                            ? in.getBody(expectedBody.getClass()) : in.getBody();

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
    //-------------------------------------------------------------------------
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

            receivedExchanges.add(exchange);

            Processor processor = processors.get(getReceivedCounter());
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
            fail(message + ". Expected: <" + expectedValue + "> but was: <" + actualValue + ">");
        }
    }

    protected void assertTrue(String message, boolean predicate) {
        if (!predicate) {
            fail(message);
        }
    }

    protected void fail(Object message) {
        throw new AssertionError(getEndpointUri() + " " + message);
    }

	public int getExpectedMinimumCount() {
		return expectedMinimumCount;
	}

	public void await() throws InterruptedException {
		if( latch!=null ) {
			latch.await();
		}
	}

	public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
		if( latch!=null ) {
			return latch.await(timeout, unit);
		}
		return true;
	}
	
	public boolean isSingleton() {
		return true;
	}
	
}
