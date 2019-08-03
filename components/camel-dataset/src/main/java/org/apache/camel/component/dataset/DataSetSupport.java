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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/**
 * Base class for DataSet
 */
public abstract class DataSetSupport implements DataSet {
    private Map<String, Object> defaultHeaders;
    private Processor outputTransformer;
    private long size = 10;
    private long reportCount = -1;

    public DataSetSupport() {
    }

    public DataSetSupport(int size) {
        setSize(size);
    }

    @Override
    public void populateMessage(Exchange exchange, long messageIndex) throws Exception {
        Message in = exchange.getIn();
        in.setBody(createMessageBody(messageIndex));
        in.setHeaders(getDefaultHeaders());
        applyHeaders(exchange, messageIndex);

        if (outputTransformer != null) {
            outputTransformer.process(exchange);
        }
    }

    @Override
    public void assertMessageExpected(DataSetEndpoint dataSetEndpoint, Exchange expected, Exchange actual, long index) throws Exception {
        Object expectedBody = expected.getIn().getBody();
        Object actualBody = actual.getIn().getBody();
        if (expectedBody != null) {
            // let's coerce to the correct type
            actualBody = actual.getIn().getMandatoryBody(expectedBody.getClass());
        }
        DataSetEndpoint.assertEquals("message body", expectedBody, actualBody, actual);
    }

    // Properties
    //-------------------------------------------------------------------------

    @Override
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public long getReportCount() {
        if (reportCount <= 0) {
            reportCount = getSize() / 5;
        }
        // report cannot be 0 then default to the size
        if (reportCount == 0) {
            reportCount = getSize();
        }
        return reportCount;
    }

    /**
     * Sets the number of messages in a group on which we will report that messages have been received.
     */
    public void setReportCount(long reportCount) {
        this.reportCount = reportCount;
    }

    public Map<String, Object> getDefaultHeaders() {
        if (defaultHeaders == null) {
            defaultHeaders = new HashMap<>();
            populateDefaultHeaders(defaultHeaders);
        }
        return defaultHeaders;
    }

    public void setDefaultHeaders(Map<String, Object> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
    }

    public Processor getOutputTransformer() {
        return outputTransformer;
    }

    public void setOutputTransformer(Processor outputTransformer) {
        this.outputTransformer = outputTransformer;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected abstract Object createMessageBody(long messageIndex);

    /**
     * Allows derived classes to add some custom headers for a given message
     */
    protected void applyHeaders(Exchange exchange, long messageIndex) {
    }

    /**
     * Allows derived classes to customize a default set of properties
     */
    protected void populateDefaultHeaders(Map<String, Object> map) {
    }
}
