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
package org.apache.camel.component.dataformat;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.processor.MarshalProcessor;
import org.apache.camel.support.processor.UnmarshalProcessor;
import org.apache.camel.support.service.ServiceHelper;

/**
 * The dataformat component is used for working with Data Formats as if it was a regular Component supporting Endpoints and URIs.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "dataformat", title = "Data Format", syntax = "dataformat:name:operation", producerOnly = true,
        label = "core,transformation", lenientProperties = true)
public class DataFormatEndpoint extends DefaultEndpoint {

    private AsyncProcessor processor;
    private DataFormat dataFormat;

    @UriPath(description = "Name of data format") @Metadata(required = true)
    private String name;
    @UriPath(enums = "marshal,unmarshal") @Metadata(required = true)
    private String operation;

    public DataFormatEndpoint() {
    }

    public DataFormatEndpoint(String endpointUri, Component component, DataFormat dataFormat) {
        super(endpointUri, component);
        this.dataFormat = dataFormat;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DataFormat getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Operation to use either marshal or unmarshal
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DefaultAsyncProducer(this) {
            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                return processor.process(exchange, callback);
            }

            @Override
            public String toString() {
                return "DataFormatProducer[" + dataFormat + "]";
            }
        };
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from data format");
    }

    @Override
    public boolean isLenientProperties() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        if (dataFormat == null && name != null) {
            dataFormat = getCamelContext().resolveDataFormat(name);
        }
        if (operation.equals("marshal")) {
            MarshalProcessor marshal = new MarshalProcessor(dataFormat);
            marshal.setCamelContext(getCamelContext());

            processor = marshal;
        } else {
            UnmarshalProcessor unmarshal = new UnmarshalProcessor(dataFormat);
            unmarshal.setCamelContext(getCamelContext());

            processor = unmarshal;
        }

        ServiceHelper.startService(dataFormat, processor);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processor, dataFormat);
        super.doStop();
    }
}
