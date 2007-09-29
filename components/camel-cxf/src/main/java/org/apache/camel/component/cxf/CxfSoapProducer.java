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
package org.apache.camel.component.cxf;

import org.apache.camel.*;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A CXF based soap provider.
 * The consumer will delegate to another endpoint for the transport layer
 * and will provide SOAP support on top of it.
 */
public class CxfSoapProducer implements Producer, AsyncProcessor {

    private static final Log LOG = LogFactory.getLog(CxfSoapProducer.class);

    private final CxfSoapEndpoint endpoint;
    private final Producer producer;
    private final AsyncProcessor processor;

    public CxfSoapProducer(CxfSoapEndpoint endpoint) throws Exception {
        this.endpoint = endpoint;
        this.producer = endpoint.getInnerEndpoint().createProducer();
        this.processor = new AsyncProcessorDecorator(producer,
                new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        processSoapProviderIn(exchange);
                    }
                },
                new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        processSoapProviderOut(exchange);
                    }
                });
    }

    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    public Exchange createExchange() {
        return producer.createExchange();
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return producer.createExchange(pattern);
    }

    public Exchange createExchange(Exchange exchange) {
        return producer.createExchange(exchange);
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        return processor.process(exchange, callback);
    }

    public void start() throws Exception {
        producer.start();
    }

    public void stop() throws Exception {
        producer.stop();
    }

    protected void processSoapProviderIn(Exchange exchange) throws Exception {
        LOG.info("processSoapProviderIn: " + exchange);
        // TODO
    }

    protected void processSoapProviderOut(Exchange exchange) throws Exception {
        LOG.info("processSoapProviderOut: " + exchange);
        // TODO
    }

}
