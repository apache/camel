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

import java.io.OutputStream;

import javax.xml.transform.Source;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cxf.util.CxfEndpointUtils;
import org.apache.camel.component.cxf.util.Dummy;
import org.apache.camel.component.cxf.util.NullConduit;
import org.apache.camel.component.cxf.util.NullConduitSelector;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.interceptor.OutgoingChainInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;

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
    private ClientImpl client;


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

        //create the endpoint and setup the interceptors
        Class sei = CxfEndpointUtils.getSEIClass(endpoint.getServiceClass());
        ClientFactoryBean cfb = CxfEndpointUtils.getClientFactoryBean(sei);
        if (sei == null) {
            cfb.setServiceClass(Dummy.class);
        } else {
            cfb.setServiceClass(sei);
        }
        cfb.setWsdlURL(endpoint.getWsdl().getURL().toString());
        if (endpoint.getServiceName() != null) {
            cfb.setServiceName(endpoint.getServiceName());
        }
        if (endpoint.getEndpointName() != null) {
            cfb.setEndpointName(endpoint.getEndpointName());
        }
        cfb.setConduitSelector(new NullConduitSelector());
        client = (ClientImpl) cfb.create();

    }

    public org.apache.camel.Endpoint getEndpoint() {
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

    protected void processSoapProviderOut(Exchange exchange) throws Exception {
        LOG.info("processSoapProviderOut: " + exchange);

        org.apache.cxf.message.Message inMessage = CxfSoapBinding.getCxfInMessage(exchange, true);
        client.setInInterceptors(client.getEndpoint().getService().getInInterceptors());
        client.onMessage(inMessage);

        exchange.getOut().setBody(inMessage.getContent(Source.class));

        exchange.getOut().setHeaders(inMessage);
    }

    protected Bus getBus() {
        return endpoint.getBus();
    }

    protected void processSoapProviderIn(Exchange exchange) throws Exception {
        LOG.info("processSoapProviderIn: " + exchange);
        org.apache.cxf.endpoint.Endpoint cxfEndpoint = client.getEndpoint();
        org.apache.cxf.message.Exchange cxfExchange = new ExchangeImpl();
        cxfExchange.put(org.apache.cxf.endpoint.Endpoint.class, cxfEndpoint);
        cxfExchange.put(Bus.class, getBus());
        cxfExchange.setConduit(new NullConduit());
        exchange.setProperty(CxfConstants.CXF_EXCHANGE, cxfExchange);
        org.apache.cxf.message.Message outMessage = CxfSoapBinding.getCxfOutMessage(exchange, true);
        outMessage.put(Message.REQUESTOR_ROLE, Boolean.TRUE);
        outMessage.put(Message.INBOUND_MESSAGE, Boolean.FALSE);
        InterceptorChain chain = OutgoingChainInterceptor.getOutInterceptorChain(cxfExchange);
        outMessage.setInterceptorChain(chain);

        chain.doIntercept(outMessage);
        CachedOutputStream outputStream = (CachedOutputStream)outMessage.getContent(OutputStream.class);
        exchange.getOut().setBody(outputStream.getInputStream());
        exchange.getIn().setBody(outputStream.getInputStream());
    }

}
