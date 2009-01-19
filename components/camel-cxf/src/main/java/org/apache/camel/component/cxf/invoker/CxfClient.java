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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.transport.MessageObserver;

/**
 * Just deal with the PayLoadMessage and RawMessage
 *
 */
public class CxfClient extends ClientImpl {

    private static final Logger LOG = LogUtils.getL7dLogger(CxfClient.class);

    public CxfClient(Bus b, Endpoint e) {
        super(b, e);

    }

    public Object dispatch(Map<Class, Object> params,
                           Map<String, Object> context,
                           Exchange exchange) throws Exception {

        Object retval = null;
        InvokingContext invokingContext = exchange.get(InvokingContext.class);
        ObjectHelper.notNull(invokingContext, "invokingContext");

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
                                          Map<Class, Object> param,
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
        exchange.put(InvokingContext.class, invokingContext);

        if (bi != null) {
            //Set The InputMessage
            exchange.put(BindingMessageInfo.class, bi.getInput());
            exchange.setOneWay(bi.getOperationInfo().isOneWay());
        }

        Message message = prepareMessage(exchange, requestContext, param, invokingContext);
        // add the invocation context
        message.put(Message.INVOCATION_CONTEXT, context);

        // add the endpoint props to the message
        Endpoint ep = getEndpoint();
        // the ep should not be null , just in case if it set to be null
        if (ep != null) {
            message.putAll(ep);
        }

        setExchangeProperties(exchange, ep, bi);
        PhaseInterceptorChain chain = setupInterceptorChain(getEndpoint());

        message.setInterceptorChain(chain);
        modifyChain(chain, requestContext);
        chain.setFaultObserver(outFaultObserver);
        // setup conduit selector
        prepareConduitSelector(message);

        modifyChain(chain, null);
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

            synchronized (exchange) {
                waitResponse(exchange);
            }
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

    protected void waitResponse(Exchange exchange) {
        int remaining = synchronousTimeout;
        while (!Boolean.TRUE.equals(exchange.get(FINISHED)) && remaining > 0) {
            long start = System.currentTimeMillis();
            try {
                exchange.wait(remaining);
            } catch (InterruptedException ex) {
                // ignore
            }
            long end = System.currentTimeMillis();
            remaining -= (int)(end - start);
        }
        if (!Boolean.TRUE.equals(exchange.get(FINISHED))) {
            LogUtils.log(LOG, Level.WARNING, "RESPONSE_TIMEOUT",
                exchange.get(OperationInfo.class).getName().toString());
        }
    }


    private Message prepareMessage(Exchange exchange, Map<String, Object> requestContext,
            Map<Class, Object> param, InvokingContext invokingContext) {

        Message message = getEndpoint().getBinding().createMessage();
        message.put(Message.REQUESTOR_ROLE, Boolean.TRUE);
        message.put(Message.INBOUND_MESSAGE, Boolean.FALSE);

        // setup the message context
        if (requestContext != null) {
            message.putAll(requestContext);
        }

        if (param != null) {
            invokingContext.setRequestOutMessageContent(message, param);
        }

        if (null != requestContext) {
            exchange.putAll(requestContext);
        }

        exchange.setOutMessage(message);
        return message;
    }

}


