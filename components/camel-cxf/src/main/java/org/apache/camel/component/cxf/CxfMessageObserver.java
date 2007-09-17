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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.component.cxf.invoker.InvokingContext;
import org.apache.camel.component.cxf.invoker.InvokingContextFactory;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.transport.ChainInitiationObserver;

public class CxfMessageObserver extends ChainInitiationObserver {
    private static final Logger LOG = Logger.getLogger(ChainInitiationObserver.class.getName());
    private CxfConsumer cxfConsumer;
    private DataFormat dataFormat;
    private CamelInvoker invoker;
    
    
    public CxfMessageObserver(CxfConsumer consumer, Endpoint endpoint, Bus bus,
            DataFormat dataFormat) {
        super(endpoint, bus);
        cxfConsumer = consumer;
        this.dataFormat = dataFormat;
        invoker = new CamelInvoker(consumer);
    }

    protected void setExchangeProperties(Exchange exchange, Message m) {
        super.setExchangeProperties(exchange, m);        
        exchange.put(CxfConsumer.class, cxfConsumer);
        exchange.put(Bus.class, bus);
        exchange.put(Endpoint.class, endpoint);
        exchange.put(InvokingContext.class, InvokingContextFactory.createContext(dataFormat));
        exchange.put(CamelInvoker.class, invoker);
    }
    
    public void onMessage(Message m) {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.fine("Observed Client request at router's endpoint.  Request message: " + m);
        }
        Message message = endpoint.getBinding().createMessage(m);
        message.put(Message.INBOUND_MESSAGE, Boolean.TRUE);
        Exchange exchange = message.getExchange();
        if (exchange == null) {
            exchange = new ExchangeImpl();
            exchange.setInMessage(message);
        }
        setExchangeProperties(exchange, message);
        
        InvokingContext invokingContext = exchange.get(InvokingContext.class);
        assert invokingContext != null;
        invokingContext.setEndpointFaultObservers((EndpointImpl)endpoint, bus);
        
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Build inbound interceptor chain and inject routing interceptor");
        }
        // now we just support the raw message and the payload message
        PhaseInterceptorChain chain = invokingContext.getRequestInInterceptorChain(exchange);

        message.setInterceptorChain(chain);
        chain.setFaultObserver(endpoint.getOutFaultObserver());
        chain.doIntercept(message);        
    }
}
