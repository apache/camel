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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.ChainInitiationObserver;
import org.apache.cxf.wsdl11.WSDLServiceFactory;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.Bus;
import org.apache.cxf.interceptor.AttachmentInInterceptor;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.binding.AbstractBindingFactory;
import org.apache.cxf.binding.soap.interceptor.ReadHeadersInterceptor;
import org.apache.cxf.binding.soap.interceptor.MustUnderstandInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A CXF based soap consumer.
 * The consumer will delegate to another endpoint for the transport layer
 * and will provide SOAP support on top of it.
 */
public class CxfSoapConsumer implements Consumer {

    private static final Log LOG = LogFactory.getLog(CxfSoapConsumer.class);

    private final CxfSoapEndpoint endpoint;
    private final Consumer consumer;
    private EndpointImpl ep;
    private MessageObserver chain;
    private Server server;

    public CxfSoapConsumer(CxfSoapEndpoint endpoint, Processor processor) throws Exception {
        this.endpoint = endpoint;
        Processor soapProcessor = new AsyncProcessorDecorator(processor,
                new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        processSoapConsumerIn(exchange);
                    }
                },
                new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        processSoapConsumerOut(exchange);
                    }
                });
        this.consumer = endpoint.getInnerEndpoint().createConsumer(soapProcessor);
        WSDLServiceFactory factory = new WSDLServiceFactory(getBus(), endpoint.getDefinition(), endpoint.getService());
        Service cxfService = factory.create();
        // need to find with the endpoint and service Qname
        EndpointInfo ei = cxfService.getServiceInfos().iterator().next().getEndpoints().iterator().next();
        ei.setAddress("local://" + ei.getService().getName().toString() + "/" + ei.getName().getLocalPart());
        ei.getBinding().setProperty(AbstractBindingFactory.DATABINDING_DISABLED, Boolean.TRUE);
        cxfService.getInInterceptors().add(new ReadHeadersInterceptor(getBus()));
        cxfService.getInInterceptors().add(new MustUnderstandInterceptor());
        cxfService.getInInterceptors().add(new AttachmentInInterceptor());
        cxfService.getInInterceptors().add(new StaxInInterceptor());
        cxfService.getInInterceptors().add(new ReadHeadersInterceptor(getBus()));
        ep = new EndpointImpl(getBus(), cxfService, ei);
        chain = new ChainInitiationObserver(ep, getBus());
        server = new ServerImpl(getBus(), ep, null, chain);
    }

    public void start() throws Exception {
        server.start();
        consumer.start();
    }

    public void stop() throws Exception {
        server.stop();
        consumer.stop();
    }

    protected Bus getBus() {
        return endpoint.getBus();
    }

    protected void processSoapConsumerIn(Exchange exchange) throws Exception {
        LOG.info("processSoapConsumerIn: " + exchange);
        // TODO: chain.onMessage();
    }

    protected void processSoapConsumerOut(Exchange exchange) throws Exception {
        LOG.info("processSoapConsumerOut: " + exchange);
        // TODO
    }

}
