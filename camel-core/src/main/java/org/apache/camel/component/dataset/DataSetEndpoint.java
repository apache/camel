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
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.ThroughputLogger;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoint for DataSet.
 *
 * @version 
 */
public class DataSetEndpoint extends MockEndpoint implements Service {
    private final transient Logger log;
    private DataSet dataSet;
    private AtomicInteger receivedCounter = new AtomicInteger();
    private int minRate;
    private long produceDelay = 3;
    private long consumeDelay;
    private long preloadSize;
    private long initialDelay = 1000;
    private Processor reporter;

    public DataSetEndpoint() {
        this.log = LoggerFactory.getLogger(DataSetEndpoint.class);
    }

    public DataSetEndpoint(String endpointUri, Component component, DataSet dataSet) {
        super(endpointUri, component);
        this.dataSet = dataSet;
        this.log = LoggerFactory.getLogger(endpointUri);
    }

    public DataSetEndpoint(String endpointUri, DataSet dataSet) {
        super(endpointUri);
        this.dataSet = dataSet;
        this.log = LoggerFactory.getLogger(endpointUri);
    }

    public static void assertEquals(String description, Object expected, Object actual, Exchange exchange) {
        if (!ObjectHelper.equal(expected, actual)) {
            throw new AssertionError(description + " does not match. Expected: " + expected + " but was: " + actual + " on " + exchange + " with headers: " + exchange.getIn().getHeaders());
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
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
        in.setHeader(Exchange.DATASET_INDEX, messageIndex);

        return exchange;
    }

    public int getMinRate() {
        return minRate;
    }

    public void setMinRate(int minRate) {
        this.minRate = minRate;
    }

    @Override
    protected void waitForCompleteLatch(long timeout) throws InterruptedException {
        super.waitForCompleteLatch(timeout);

        if (minRate > 0) {
            int count = getReceivedCounter();
            do {
                // wait as long as we get a decent message rate
                super.waitForCompleteLatch(1000L);
                count = getReceivedCounter() - count;
            } while (count >= minRate);
        }
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
     * Sets how many messages should be preloaded (sent) before the route completes its initialization
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
     * Allows a delay to be specified which causes producers to pause - to simulate slow producers
     */
    public void setProduceDelay(long produceDelay) {
        this.produceDelay = produceDelay;
    }

    /**
     * Sets a custom progress reporter
     */
    public void setReporter(Processor reporter) {
        this.reporter = reporter;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected void performAssertions(Exchange actual) throws Exception {
        int receivedCount = receivedCounter.incrementAndGet();
        long index = receivedCount - 1;
        Exchange expected = createExchange(index);

        // now lets assert that they are the same
        if (log.isDebugEnabled()) {
            log.debug("Received message: {} (DataSet index={}) = {}",
                    new Object[]{index, actual.getIn().getHeader(Exchange.DATASET_INDEX, Integer.class), actual});
        }

        assertMessageExpected(index, expected, actual);

        if (reporter != null) {
            reporter.process(actual);
        }

        if (consumeDelay > 0) {
            Thread.sleep(consumeDelay);
        }
    }

    protected void assertMessageExpected(long index, Exchange expected, Exchange actual) throws Exception {
        long actualCounter = ExchangeHelper.getMandatoryHeader(actual, Exchange.DATASET_INDEX, Long.class);
        assertEquals("Header: " + Exchange.DATASET_INDEX, index, actualCounter, actual);

        getDataSet().assertMessageExpected(this, expected, actual, index);
    }

    protected ThroughputLogger createReporter() {
        ThroughputLogger answer = new ThroughputLogger(this.getEndpointUri(), (int) this.getDataSet().getReportCount());
        answer.setAction("Received");
        return answer;
    }

    public void start() throws Exception {
        long size = getDataSet().getSize();
        expectedMessageCount((int) size);
        if (reporter == null) {
            reporter = createReporter();
        }
        log.info("Start: " + this + " expecting " + size + " messages");
    }

    public void stop() throws Exception {
        log.info("Stop: " + this);
    }

}
