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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Traceable;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;

/**
 * Marshals the body of the incoming message using the given
 * <a href="http://camel.apache.org/data-format.html">data format</a>
 *
 * @version
 */
public class MarshalProcessor extends ServiceSupport implements AsyncProcessor, Traceable, CamelContextAware, IdAware {
    private String id;
    private CamelContext camelContext;
    private final DataFormat dataFormat;

    public MarshalProcessor(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        ObjectHelper.notNull(dataFormat, "dataFormat");

        // if stream caching is enabled then use that so we can stream accordingly
        // for example to overflow to disk for big streams
        OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);

        Message in = exchange.getIn();
        Object body = in.getBody();

        // lets setup the out message before we invoke the dataFormat
        // so that it can mutate it if necessary
        Message out = exchange.getOut();
        out.copyFrom(in);

        try {
            dataFormat.marshal(exchange, body, osb);
            out.setBody(osb.build());
        } catch (Throwable e) {
            // remove OUT message, as an exception occurred
            exchange.setOut(null);
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    @Override
    public String toString() {
        return "Marshal[" + dataFormat + "]";
    }

    public String getTraceLabel() {
        return "marshal[" + dataFormat + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        // inject CamelContext on data format
        if (dataFormat instanceof CamelContextAware) {
            ((CamelContextAware) dataFormat).setCamelContext(camelContext);
        }
        // add dataFormat as service which will also start the service
        // (false => we handle the lifecycle of the dataFormat)
        getCamelContext().addService(dataFormat, false, true);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(dataFormat);
        getCamelContext().removeService(dataFormat);
    }
}
