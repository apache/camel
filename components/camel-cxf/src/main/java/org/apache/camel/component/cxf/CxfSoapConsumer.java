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

import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;

import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.util.NullConduit;
import org.apache.camel.component.cxf.util.CxfEndpointUtils;
import org.apache.camel.component.cxf.util.NullDestinationFactory;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.ChainInitiationObserver;
import org.apache.cxf.wsdl11.WSDLServiceFactory;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.Bus;
import org.apache.cxf.interceptor.AttachmentInInterceptor;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.interceptor.OutgoingChainInterceptor;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageImpl;
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
    private MessageObserver inMessageObserver;     
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
        Class sei = CxfEndpointUtils.getSEIClass(endpoint.getServiceClass());
        ServerFactoryBean sfb = CxfEndpointUtils.getServerFactoryBean(sei);
        sfb.setWsdlURL(endpoint.getWsdl().getURL().toString());        
        if (endpoint.getServiceName() != null) {
            sfb.setServiceName(endpoint.getServiceName());
        }
        if (endpoint.getEndpointName() != null) {
            sfb.setEndpointName(endpoint.getEndpointName());
        }
        // we do not need use the destination here
        sfb.setDestinationFactory(new NullDestinationFactory());
        sfb.setStart(false);
        
        server = sfb.create();
    }
    
    
    public void start() throws Exception {
        server.start();
        inMessageObserver = server.getDestination().getMessageObserver();
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
        CxfSoapBinding binding = endpoint.getCxfSoapBinding();
        org.apache.cxf.message.Message inMessage = binding.getCxfInMessage(exchange, false);
        org.apache.cxf.message.Exchange cxfExchange = inMessage.getExchange();
        cxfExchange.put(org.apache.cxf.endpoint.Endpoint.class, server.getEndpoint());
        cxfExchange.put(Bus.class, getBus());
        cxfExchange.setConduit(new NullConduit());        
        // get the message input stream, deal with the exchange in message
        inMessageObserver.onMessage(inMessage);       
        exchange.getIn().setBody(inMessage.getContent(Source.class));
        //TODO copy the right header information
        exchange.getIn().setHeaders(inMessage);
        
    }

    protected void processSoapConsumerOut(Exchange exchange) throws Exception {
        LOG.info("processSoapConsumerOut: " + exchange);
        CxfSoapBinding binding = endpoint.getCxfSoapBinding();
        // TODO check if the message is oneway message
        // Get the method name form the soap endpoint
        org.apache.cxf.message.Message outMessage = binding.getCxfOutMessage(exchange, false);
        org.apache.cxf.message.Exchange cxfExchange = outMessage.getExchange();
        InterceptorChain chain = OutgoingChainInterceptor.getOutInterceptorChain(cxfExchange);
        outMessage.setInterceptorChain(chain);        
        chain.doIntercept(outMessage);
        CachedOutputStream outputStream = (CachedOutputStream)outMessage.getContent(OutputStream.class);               
        exchange.getOut().setBody(outputStream.getInputStream());        
    }

}
