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
package org.apache.camel.component.dataset;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.EventDrivenPollingConsumer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Endpoint for DataSet.
 *
 * @version $Revision$
 */
public class DataSetEndpoint extends MockEndpoint implements Service {
    private static final transient Log LOG = LogFactory.getLog(DataSetEndpoint.class);
    private DataSet dataSet;
    private AtomicInteger receivedCounter = new AtomicInteger();
    private long produceDelay = -1;
    private long consumeDelay = -1;
    private long startTime;
    private long preloadSize;

    public DataSetEndpoint(String endpointUri, Component component, DataSet dataSet) {
        super(endpointUri, component);
        this.dataSet = dataSet;
    }

    public DataSetEndpoint(String endpointUri, DataSet dataSet) {
        super(endpointUri);
        this.dataSet = dataSet;
    }

    public static void assertEquals(String description, Object expected, Object actual, Exchange exchange) {
        if (!ObjectHelper.equal(expected, actual)) {
            throw new AssertionError(description + " does not match. Expected: " + expected + " but was: " + actual + " on " + exchange + " with headers: " + exchange.getIn().getHeaders());
        }
    }

    @Override
    public PollingConsumer<Exchange> createPollingConsumer() throws Exception {
        return new EventDrivenPollingConsumer<Exchange>(this);
    }

    @Override
    public Consumer<Exchange> createConsumer(Processor processor) throws Exception {
        return new DataSetConsumer(this, processor);
    }

    @Override
    public void reset() {
        super.reset();
        receivedCounter.set(0);
    }

    @Override
    public int getReceivedCounter() {
        return receivedCounter.get();
    }

    /**
     * Creates a message exchange for the given index in the {@link DataSet}
     */
    public Exchange createExchange(long messageIndex) throws Exception {
        Exchange exchange = createExchange();
        getDataSet().populateMessage(exchange, messageIndex);

        Message in = exchange.getIn();
        in.setHeader(DataSet.INDEX_HEADER, messageIndex);

        return exchange;
    }

    @Override
    protected void waitForCompleteLatch() throws InterruptedException {
        // TODO lets do a much better version of this!
        long size = getDataSet().getSize();
        size *= 4000;
        setResultWaitTime(size);
        super.waitForCompleteLatch();
    }

    // Properties
    //-------------------------------------------------------------------------

    public DataSet getDataSet() {
        return dataSet;
    }

    public void setDataSet(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public long getPreloadSize() {
        return preloadSize;
    }

    /**
     * Sets how many messages should be preloaded (sent) before the route completes its initialisation
     */
    public void setPreloadSize(long preloadSize) {
        this.preloadSize = preloadSize;
    }

    public long getConsumeDelay() {
        return consumeDelay;
    }

    /**
     * Allows a delay to be specified which causes consumers to pause - to simulate slow consumers
     */
    public void setConsumeDelay(long consumeDelay) {
        this.consumeDelay = consumeDelay;
    }

    public long getProduceDelay() {
        return produceDelay;
    }

    /**
     * Allows a delay to be specified which causes producers to pause - to simpulate slow producers
     */
    public void setProduceDelay(long produceDelay) {
        this.produceDelay = produceDelay;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected void performAssertions(Exchange actual) throws Exception {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        int receivedCount = receivedCounter.incrementAndGet();
        long index = receivedCount - 1;
        Exchange expected = createExchange(index);

        // now lets assert that they are the same
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received message: " + index + " = " + actual);
        }

        assertMessageExpected(index, expected, actual);

        if (consumeDelay > 0) {
            Thread.sleep(consumeDelay);
        }

        long group = getDataSet().getReportCount();
        if (receivedCount % group == 0) {
            reportProgress(actual, receivedCount);
        }
    }

    protected void reportProgress(Exchange actual, int receivedCount) {
        long time = System.currentTimeMillis();
        long elapsed = time - startTime;
        startTime = time;

        LOG.info("Received: " + receivedCount + " messages so far. Last group took: " + elapsed + " millis");
    }

    protected void assertMessageExpected(long index, Exchange expected, Exchange actual) throws Exception {
        long actualCounter = ExchangeHelper.getMandatoryHeader(actual, DataSet.INDEX_HEADER, Long.class);
        assertEquals("Header: " + DataSet.INDEX_HEADER, index, actualCounter, actual);

        getDataSet().assertMessageExpected(this, expected, actual, index);
    }

    public void start() throws Exception {
        long size = getDataSet().getSize();
        expectedMessageCount((int) size);
    }

    public void stop() throws Exception {
    }
}
