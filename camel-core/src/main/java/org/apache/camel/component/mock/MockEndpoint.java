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
    private int receivedCounter;
    private int expectedCount = -1;
    private Map<Integer, Processor<Exchange>> processors = new HashMap<Integer, Processor<Exchange>>();
    private List<Exchange> exchangesReceived = new ArrayList<Exchange>();
    private List<Throwable> failures = new ArrayList<Throwable>();
    private CountDownLatch latch = new CountDownLatch(1);

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
    public void assertIsSatisfied() throws InterruptedException {
        if (latch != null) {
            // now lets wait for the results
            latch.await(10, TimeUnit.SECONDS);
        }
        else if (expectedCount == 0) {
            // lets wait a little bit just in case
            Thread.sleep(1000);
        }

        if (expectedCount >= 0) {
            assertEquals("Expected message count", expectedCount, receivedCounter);
        }
    }

    protected void assertEquals(String message, Object expectedValue, Object actualValue) {
        if (!ObjectHelper.equals(expectedValue, actualValue)) {
            throw new AssertionError(message + ". Expected: <" + expectedValue + "> but was: <" + actualValue + ">");
        }
    }

    public void reset() {
        receivedCounter = 0;
    }

    public void expectedMessageCount(int expectedCount) {
        this.expectedCount = expectedCount;
        if (expectedCount <= 0) {
            latch = null;
        }
        else {
            latch = new CountDownLatch(expectedCount);
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public List<Throwable> getFailures() {
        return failures;
    }

    public int getReceivedCounter() {
        return receivedCounter;
    }

    public List<Exchange> getExchangesReceived() {
        return exchangesReceived;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected synchronized void onExchange(Exchange exchange) {
        try {
            exchangesReceived.add(exchange);

            if (latch != null) {
                latch.countDown();
            }

            Processor<Exchange> processor = processors.get(++receivedCounter);
            if (processor != null) {
                processor.process(exchange);
            }
        }
        catch (Exception e) {
            failures.add(e);
        }
    }
}
