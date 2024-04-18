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
package org.apache.camel.component.dataset;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.processor.ThroughputLogger;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide data for load and soak testing of your Camel application.
 *
 * It works by allowing you to create DataSet instances both as a source of messages and as a way to assert that the
 * data set is received. Camel will use the throughput logger when sending dataset's.
 */
@UriEndpoint(firstVersion = "1.3.0", scheme = "dataset", title = "Dataset", syntax = "dataset:name",
             remote = false, category = { Category.CORE, Category.TESTING }, lenientProperties = true,
             headersClass = DataSetConstants.class)
public class DataSetEndpoint extends MockEndpoint implements Service {
    private final transient Logger log;
    private final AtomicInteger receivedCounter = new AtomicInteger();
    @UriPath(name = "name", description = "Name of DataSet to lookup in the registry")
    @Metadata(required = true)
    private volatile DataSet dataSet;
    @UriParam(label = "consumer", defaultValue = "0")
    private int minRate;
    @UriParam(label = "consumer", defaultValue = "3", javaType = "java.time.Duration")
    private long produceDelay = 3;
    @UriParam(label = "producer", defaultValue = "0", javaType = "java.time.Duration")
    private long consumeDelay;
    @UriParam(label = "consumer", defaultValue = "0")
    private long preloadSize;
    @UriParam(label = "consumer", defaultValue = "1000", javaType = "java.time.Duration")
    private long initialDelay = 1000;
    @UriParam(enums = "strict,lenient,off", defaultValue = "lenient")
    private String dataSetIndex = "lenient";

    public DataSetEndpoint(String endpointUri, Component component, DataSet dataSet) {
        super(endpointUri, component);
        this.dataSet = dataSet;
        this.log = LoggerFactory.getLogger(endpointUri);
        // optimize as we dont need to copy the exchange
        setCopyOnExchange(false);
    }

    public static void assertEquals(String description, Object expected, Object actual, Exchange exchange) {
        if (!ObjectHelper.equal(expected, actual)) {
            throw new AssertionError(
                    description + " does not match. Expected: " + expected + " but was: " + actual + " on " + exchange
                                     + " with headers: " + exchange.getIn().getHeaders());
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new DataSetConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        Producer answer = super.createProducer();

        long size = getDataSet().getSize();
        expectedMessageCount((int) size);

        return answer;
    }

    @Override
    public void reset() {
        super.reset();
        if (receivedCounter != null) {
            receivedCounter.set(0);
        }
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

        if (!getDataSetIndex().equals("off")) {
            Message in = exchange.getIn();
            in.setHeader(DataSetConstants.DATASET_INDEX, messageIndex);
        }

        return exchange;
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

    public int getMinRate() {
        return minRate;
    }

    /**
     * Wait until the DataSet contains at least this number of messages
     */
    public void setMinRate(int minRate) {
        this.minRate = minRate;
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
     * Allows a delay to be specified which causes a delay when a message is consumed by the producer (to simulate slow
     * processing)
     */
    public void setConsumeDelay(long consumeDelay) {
        this.consumeDelay = consumeDelay;
    }

    public long getProduceDelay() {
        return produceDelay;
    }

    /**
     * Allows a delay to be specified which causes a delay when a message is sent by the consumer (to simulate slow
     * processing)
     */
    public void setProduceDelay(long produceDelay) {
        this.produceDelay = produceDelay;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    /**
     * Time period in millis to wait before starting sending messages.
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    /**
     * Controls the behaviour of the CamelDataSetIndex header.
     *
     * off (consumer) the header will not be set. strict (consumer) the header will be set. lenient (consumer) the
     * header will be set. off (producer) the header value will not be verified, and will not be set if it is not
     * present. strict (producer) the header value must be present and will be verified. lenient (producer) the header
     * value will be verified if it is present, and will be set if it is not present.
     */
    public void setDataSetIndex(String dataSetIndex) {
        switch (dataSetIndex) {
            case "off":
            case "lenient":
            case "strict":
                this.dataSetIndex = dataSetIndex;
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid value specified for the dataSetIndex URI parameter:" + dataSetIndex
                                                   + "Supported values are strict, lenient and off ");
        }
    }

    public String getDataSetIndex() {
        return dataSetIndex;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected void performAssertions(Exchange actual, Exchange copy) throws Exception {
        int receivedCount = receivedCounter.incrementAndGet();
        long index = receivedCount - 1;
        Exchange expected = createExchange(index);

        // now let's assert that they are the same
        if (log.isDebugEnabled()) {
            if (copy.getIn().getHeader(DataSetConstants.DATASET_INDEX) != null) {
                log.debug("Received message: {} (DataSet index={}) = {}",
                        index, copy.getIn().getHeader(DataSetConstants.DATASET_INDEX, Integer.class), copy);
            } else {
                log.debug("Received message: {} = {}", index, copy);
            }
        }

        assertMessageExpected(index, expected, copy);

        if (consumeDelay > 0) {
            Thread.sleep(consumeDelay);
        }
    }

    protected void assertMessageExpected(long index, Exchange expected, Exchange actual) throws Exception {
        switch (getDataSetIndex()) {
            case "off":
                break;
            case "strict":
                long actualCounter = ExchangeHelper.getMandatoryHeader(actual, DataSetConstants.DATASET_INDEX, Long.class);
                assertEquals("Header: " + DataSetConstants.DATASET_INDEX, index, actualCounter, actual);
                break;
            case "lenient":
            default:
                // Validate the header value if it is present
                Long dataSetIndexHeaderValue = actual.getIn().getHeader(DataSetConstants.DATASET_INDEX, Long.class);
                if (dataSetIndexHeaderValue != null) {
                    assertEquals("Header: " + DataSetConstants.DATASET_INDEX, index, dataSetIndexHeaderValue, actual);
                } else {
                    // set the header if it isn't there
                    actual.getIn().setHeader(DataSetConstants.DATASET_INDEX, index);
                }
                break;
        }

        getDataSet().assertMessageExpected(this, expected, actual, index);
    }

    protected ThroughputLogger createReporter() {
        // must sanitize uri to avoid logging sensitive information
        String uri = URISupport.sanitizeUri(getEndpointUri());
        CamelLogger logger = new CamelLogger(uri);
        ThroughputLogger answer = new ThroughputLogger(logger, (int) this.getDataSet().getReportCount());
        answer.setAction("Received");
        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (reporter == null) {
            reporter = createReporter();
        }

        log.debug("{} expecting {} messages", this, getExpectedCount());
    }

}
