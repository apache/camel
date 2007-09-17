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
package org.apache.camel.component.cxf.interceptors;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.camel.component.cxf.CamelInvoker;
import org.apache.camel.component.cxf.invoker.InvokingContext;
import org.apache.cxf.endpoint.ConduitSelector;
import org.apache.cxf.endpoint.PreexistingConduitSelector;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

/**
 * This is the base class for routing interceptors that can intercept and forward
 * message to router as different phases.
 *
 */
public abstract class AbstractInvokerInterceptor extends AbstractPhaseInterceptor<Message> {
    public static final String ROUTING_INERCEPTOR_PHASE = "Routing-Phase";
    public static final String BUNDLE = "wsdl-cxf"; 
    public AbstractInvokerInterceptor(String phase) {
        super(phase);
    }
    
    private boolean isRequestor(Message message) {
        return Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE));
    }
    
    /**
     * Send the intercepted message to router
     */
    @SuppressWarnings("unchecked")
    public void handleMessage(Message inMessage) throws Fault {
        if (isRequestor(inMessage)) {
            return;
        }

        Exchange exchange = inMessage.getExchange();
        Message outMessage = null;
        try {
            CamelInvoker invoker = exchange.get(CamelInvoker.class);
            outMessage = invoker.invoke(inMessage);
        } catch (Exception e) {
            throw new Fault(e);
        }

        // set back channel conduit in the exchange if it is not present
        setBackChannelConduit(exchange, outMessage);
        
        Exception ex = outMessage.getContent(Exception.class);
        if (ex != null) {
            if (!(ex instanceof Fault)) {
                ex = new Fault(ex); 
            }
            throw (Fault)ex;
        }

        outMessage.put(Message.INBOUND_MESSAGE, Boolean.FALSE);
        BindingOperationInfo boi = exchange.get(BindingOperationInfo.class);
        if (boi != null) {
            exchange.put(BindingMessageInfo.class, boi.getOutput());
        }

        InvokingContext invokingContext = exchange.get(InvokingContext.class);
        assert invokingContext != null;
        
        InterceptorChain chain = invokingContext.getResponseOutInterceptorChain(exchange);
        if (chain != null) {
            outMessage.setInterceptorChain(chain);
            
            //Initiate the OutBound Chain.
            chain.doIntercept(outMessage);
        }                
    }
    
    /**
     * Creates a conduit if not present on the exchange. outMessage or faultMessage
     * It will create a back channel in PAYLOAD and MESSAGE case.
     * POJO case will have a coduit due to OutgoingChainInterceptor in PRE_INVOKE Phase
     */

    protected void setBackChannelConduit(Exchange ex, Message message) throws Fault {
        Conduit conduit = ex.getConduit(message);
        if (conduit == null) {
            try {
                EndpointReferenceType target =
                    ex.get(EndpointReferenceType.class);
                conduit = ex.getDestination().getBackChannel(ex.getInMessage(), null, target);
                ex.put(ConduitSelector.class,
                       new PreexistingConduitSelector(conduit));
            } catch (IOException e) {
                throw new Fault(e);
            }
        }
        
        assert conduit != null;
    }
    
    protected abstract Logger getLogger();
}
