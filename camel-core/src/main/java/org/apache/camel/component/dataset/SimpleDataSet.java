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

import java.util.Map;
import java.util.HashMap;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/**
 * A simple DataSet that allows a static payload to be used to create each message exchange
 * along with using a pluggable transformer to randomize the message.
 *
 * @version $Revision: 1.1 $
 */
public class SimpleDataSet implements DataSet {
    private Object defaultBody = "<hello>world!</hello>";
    private Map<String,Object> defaultHeaders;
    private Processor outputTransformer;
    private Processor inputTransformer;
    private long size = 10;

    public SimpleDataSet() {
    }

    public SimpleDataSet(int size) {
        setSize(size);
    }

    public void populateMessage(Exchange exchange, long messageIndex) throws Exception {
        Message in = exchange.getIn();
        in.setBody(getDefaultBody());
        in.setHeaders(getDefaultHeaders());
        applyHeaders(exchange, messageIndex);

        if (outputTransformer != null) {
            outputTransformer.process(exchange);
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Object getDefaultBody() {
        return defaultBody;
    }

    public void setDefaultBody(Object defaultBody) {
        this.defaultBody = defaultBody;
    }

    public Map<String, Object> getDefaultHeaders() {
        if (defaultHeaders == null) {
            defaultHeaders = new HashMap<String, Object>();
            populateDefaultHeaders(defaultHeaders);
        }
        return defaultHeaders;
    }

    public void setDefaultHeaders(Map<String, Object> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
    }

    public Processor getInputTransformer() {
        return inputTransformer;
    }

    public void setInputTransformer(Processor inputTransformer) {
        this.inputTransformer = inputTransformer;
    }

    public Processor getOutputTransformer() {
        return outputTransformer;
    }

    public void setOutputTransformer(Processor outputTransformer) {
        this.outputTransformer = outputTransformer;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void applyHeaders(Exchange exchange, long messageIndex) {
        Message in = exchange.getIn();
        in.setHeader(DataSet.INDEX_HEADER, messageIndex);
    }

    protected void populateDefaultHeaders(Map<String, Object> map) {
    }
}
