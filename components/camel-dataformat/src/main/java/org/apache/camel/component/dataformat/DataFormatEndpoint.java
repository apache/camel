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
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
 * Marshal or unmarshal the message body with a Camel data format, such as JAXB, CSV or JSON.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "dataformat", title = "Data Format", syntax = "dataformat:name:operation",
             remote = false, producerOnly = true,
             category = { Category.CORE, Category.TRANSFORMATION }, lenientProperties = true)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body the data format named in the path takes, as for the marshal and"
                                                + " unmarshal EIPs"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   description = "The body the data format named in the path gives, as for the marshal and"
                                                 + " unmarshal EIPs"))
public class DataFormatEndpoint extends DefaultEndpoint {

    private AsyncProcessor processor;
    private DataFormat dataFormat;

    @UriPath(description = "The name of the data format, for example jaxb, csv or jackson. The endpoint gets its own instance"
                           + " of the data format, configured first from the global camel.dataformat properties of this data"
                           + " format and then from the other URI parameters of the endpoint. If no data format of this name"
                           + " can be created, the DataFormat bean of this name in the registry is used.")
    @Metadata(required = true)
    private String name;
    @UriPath(enums = "marshal,unmarshal",
             description = "Whether the producer marshals the message body into the data format (marshal) or unmarshals"
                           + " the message body from the data format (unmarshal), as the marshal and unmarshal EIPs do.")
    @Metadata(required = true)
    private String operation;

    public DataFormatEndpoint() {
    }

    public DataFormatEndpoint(String endpointUri, Component component, DataFormat dataFormat) {
        super(endpointUri, component);
        this.dataFormat = dataFormat;
    }

    @Override
    public boolean isRemote() {
        return false;
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
    protected void doInit() throws Exception {
        super.doInit();
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
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(dataFormat, processor);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processor, dataFormat);
        super.doStop();
    }
}
