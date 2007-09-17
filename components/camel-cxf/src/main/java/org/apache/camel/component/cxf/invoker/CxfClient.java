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
package org.apache.camel.component.cxf.invoker;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.binding.Binding;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.endpoint.ConduitSelector;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.endpoint.PreexistingConduitSelector;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.transport.MessageObserver;

/**
 * Just deal with the PayLoadMessage and RawMessage
 *
 */
public class CxfClient extends ClientImpl {

    private static final Logger LOG = Logger.getLogger(CxfClient.class.getName());

    private Endpoint endpoint;
    
    public CxfClient(Bus b, Endpoint e) {
        super(b, e);
        endpoint = e; 
    }
    
    public Object dispatch(Object params, 
                           Map<String, Object> context,
                           Exchange exchange) throws Exception {
        
        Object retval = null;
        InvokingContext invokingContext = exchange.get(InvokingContext.class);
        assert invokingContext != null;

        // get InBound binding operation info from the exchange object
        BindingOperationInfo inBoundOp = exchange.get(BindingOperationInfo.class);
        
        BindingOperationInfo outBoundOp = null;

        if (inBoundOp != null) {
            //Get the BindingOperationInfo for the outbound binding.
            BindingInfo bi = getEndpoint().getEndpointInfo().getBinding();
            outBoundOp = bi.getOperation(inBoundOp.getOperationInfo().getName());
            if (outBoundOp != null 
                && inBoundOp.isUnwrapped()) {
                outBoundOp = outBoundOp.getUnwrappedOperation();
            }
        }
        
       
        retval = invokeWithMessageStream(outBoundOp, params, context, invokingContext);
        
        return retval;
        
        
    }

 
    @SuppressWarnings("unchecked")
    public Object invokeWithMessageStream(BindingOperationInfo bi, 
                                          Object param, 
                                          Map<String, Object> context,
                                          InvokingContext invokingContext) throws Exception {

        Object retval = null;

        Map<String, Object> requestContext = null;
        Map<String, Object> responseContext = null;

        if (null != context) {
            requestContext = (Map<String, Object>) context.get(REQUEST_CONTEXT);
            responseContext = (Map<String, Object>) context.get(RESPONSE_CONTEXT);
        }

        Exchange exchange = new ExchangeImpl();
        // put the message Observer to call the CxfClient onMessage()
        exchange.put(MessageObserver.class, this);
        exchange.put(InvokingContext.class, invokingContext);
        exchange.put(Bus.class, bus);
        exchange.put(Endpoint.class, getEndpoint());
        exchange.put(BindingInfo.class, getEndpoint().getEndpointInfo().getBinding());
        if (bi != null) {
            //Set The InputMessage
            exchange.put(BindingOperationInfo.class, bi);
            exchange.put(BindingMessageInfo.class, bi.getInput());            
            exchange.setOneWay(bi.getOperationInfo().isOneWay());
        }

        Message message = prepareMessage(exchange, requestContext, param, invokingContext);
        
        PhaseInterceptorChain chain = setupOutChain(requestContext, message, invokingContext);

        // setup conduit selector
        prepareConduitSelector(message);

        // execute chain
        chain.doIntercept(message);
                
        //it will close all the stream in the message, so we do not call it  
        //getConduitSelector().complete(exchange);
                
        // Check to see if there is a Fault from the outgoing chain
        Exception ex = message.getContent(Exception.class);

        if (ex != null) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Exception in outgoing chain: " + ex.toString());
            }
            throw ex;
        }

        if (!exchange.isOneWay()) {
            ex = getException(exchange);
    
            if (ex != null) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Exception in incoming chain: " + ex.toString());
                }
                throw ex;
            }
            retval = invokingContext.getResponseObject(exchange, responseContext);  
            
        }

        return retval;
    }

    public void onMessage(Message message) {
        Exchange exchange = message.getExchange();
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("call the cxf client on message , exchange is " + exchange);
        }    
        if (exchange.get(InvokingContext.class) == null) {
            super.onMessage(message);
        } else {

            message = getEndpoint().getBinding().createMessage(message);
            message.put(Message.REQUESTOR_ROLE, Boolean.TRUE);
            message.put(Message.INBOUND_MESSAGE, Boolean.TRUE);
                        
            exchange.put(Binding.class, getEndpoint().getBinding());
            BindingOperationInfo bi = exchange.get(BindingOperationInfo.class);        
            if (bi != null) {
                //Set The OutputMessage
                exchange.put(BindingMessageInfo.class, bi.getOutput());
            }
            InvokingContext invokingContext = exchange.get(InvokingContext.class);
            assert invokingContext != null;

            // setup interceptor chain
            PhaseInterceptorChain chain = invokingContext.getResponseInInterceptorChain(exchange);
            message.setInterceptorChain(chain);
            
            // execute chain
            chain.doIntercept(message);

            // set inMessage in the exchange
            exchange.setInMessage(message);
        }
    }

    private Message prepareMessage(Exchange exchange, Map<String, Object> requestContext,
            Object param, InvokingContext InvokingContext) {

        Message message = getEndpoint().getBinding().createMessage();
        message.put(Message.REQUESTOR_ROLE, Boolean.TRUE);
        message.put(Message.INBOUND_MESSAGE, Boolean.FALSE);

        // setup the message context
        if (requestContext != null) {
            message.putAll(requestContext);
        }

        if (param != null) {
            InvokingContext.setRequestOutMessageContent(message, param);
        }

        if (null != requestContext) {
            exchange.putAll(requestContext);
        }

        exchange.setOutMessage(message);
        return message;
    }

    private PhaseInterceptorChain setupOutChain(Map<String, Object> requestContext,
            Message message, InvokingContext invokingContext) {

        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest("Build an out interceptor chain to send request to server");
        }
        Exchange exchange = message.getExchange();
        PhaseInterceptorChain chain = invokingContext.getRequestOutInterceptorChain(exchange);
        message.setInterceptorChain(chain);
        modifyChain(chain, requestContext);
        chain.setFaultObserver(outFaultObserver);
        return chain;
    }
    
    public Endpoint getEndpoint() {
        return endpoint;
    }
    
    public Bus getBus() {
        return bus;
    }
}

