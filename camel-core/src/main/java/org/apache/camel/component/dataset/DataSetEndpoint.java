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
package org.apache.camel.component.dataset;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.Service;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Message;
import org.apache.camel.impl.EventDrivenPollingConsumer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision: 1.1 $
 */
public class DataSetEndpoint extends MockEndpoint implements Service {
    private static final transient Log LOG = LogFactory.getLog(DataSetEndpoint.class);
    private DataSet dataSet;
    private AtomicInteger receivedCounter = new AtomicInteger();

    public static void assertEquals(String description, Object expected, Object actual, Exchange exchange) {
        if (!ObjectHelper.equal(expected, actual)) {
            throw new AssertionError(description + " does not match. Expected: " + expected + " but was: " + actual + " on  " + exchange);
        }
    }

    public DataSetEndpoint(String endpointUri, Component component, DataSet dataSet) {
        super(endpointUri, component);
        this.dataSet = dataSet;
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


    public DataSet getDataSet() {
        return dataSet;
    }

    public void setDataSet(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    protected void performAssertions(Exchange actual) throws Exception {
        int receivedCount = receivedCounter.incrementAndGet();
        long index = receivedCount - 1;
        Exchange expected = createExchange(index);

        // now lets assert that they are the same
        LOG.debug("Received message: " + index + " = " + actual);

        assertMessageExpected(index, expected, actual);
    }

    protected void assertMessageExpected(long index, Exchange expected, Exchange actual) throws Exception {
        long actualCounter = ExchangeHelper.getMandatoryHeader(actual, DataSet.INDEX_HEADER, Long.class);
        assertEquals(DataSet.INDEX_HEADER, index, actualCounter, actual);

        getDataSet().assertMessageExpected(this, expected, actual, index);
    }

    public void start() throws Exception {
        long size = getDataSet().getSize();
        expectedMessageCount((int) size);
    }

    public void stop() throws Exception {
    }
}
