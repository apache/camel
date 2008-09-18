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

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.ExchangePattern;
import org.apache.camel.component.cxf.util.CxfHeaderHelper;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.MethodDispatcher;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.invoker.Invoker;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;

public class CamelInvoker implements Invoker, MessageInvoker {
    private static final Logger LOG = LogUtils.getL7dLogger(CamelInvoker.class);
    private CxfConsumer cxfConsumer;

    public CamelInvoker(CxfConsumer consumer) {
        cxfConsumer = consumer;
    }

    /**
    * This method is called when the incoming message is to
    * be passed into the Camel processor. The return value is the response
    * from the processor
    */
    public void invoke(Exchange exchange) {
        Message inMessage = exchange.getInMessage();

        //TODO set the request context here
        CxfEndpoint endpoint = cxfConsumer.getEndpoint();
        CxfExchange cxfExchange = endpoint.createExchange(inMessage);

        BindingOperationInfo bop = exchange.get(BindingOperationInfo.class);
        cxfExchange.setProperty(BindingOperationInfo.class.toString(), bop);

        if (bop != null && bop.getOperationInfo().isOneWay()) {
            cxfExchange.setPattern(ExchangePattern.InOnly);
        } else {
            cxfExchange.setPattern(ExchangePattern.InOut);
        }
        try {
            cxfConsumer.getProcessor().process(cxfExchange);
        } catch (Exception ex) {
            // catch the exception and send back to cxf client
            throw new Fault(ex);
        }

        // make sure the client has returned back the message
        copybackExchange(cxfExchange, exchange);

        Message outMessage = exchange.getOutMessage();
        // update the outMessageContext
        outMessage.put(Message.INBOUND_MESSAGE, Boolean.FALSE);
        BindingOperationInfo boi = exchange.get(BindingOperationInfo.class);

        if (boi != null) {
            exchange.put(BindingMessageInfo.class, boi.getOutput());
        }
    }


    public void copybackExchange(CxfExchange result, Exchange exchange) {
        final Endpoint endpoint = exchange.get(Endpoint.class);
        Message outMessage = null;
        if (result.isFailed()) {
            // The exception will be send back to the soap client
            CxfMessage fault = result.getFault();
            outMessage = exchange.getInFaultMessage();
            if (outMessage == null) {
                outMessage = endpoint.getBinding().createMessage();
                outMessage.setExchange(exchange);
                exchange.setInFaultMessage(outMessage);
            }
            Throwable ex = (Throwable) fault.getBody();
            if (ex != null) {
                outMessage.setContent(Throwable.class, ex);
            } else {
                outMessage.setContent(Throwable.class, result.getException());
            }
        } else {
            outMessage = result.getOutMessage();
            if (LOG.isLoggable(Level.FINEST)) {
                LOG.finest("Get the response outMessage " + outMessage);
            }
            // Copy the outMessage back if we set the out's body
            org.apache.camel.Message camelMessage = result.getOut();
            CxfBinding.copyMessage(camelMessage, outMessage);
        }
        // set the CXF outMessage back to the exchange
        exchange.setOutMessage(outMessage);
    }

    @SuppressWarnings("unchecked")
    public void updateContext(Map<String, Object> from, Map<String, Object> to) {
        if (to != null && from != null) {
            for (Iterator iter = from.entrySet().iterator(); iter.hasNext();) {
                Map.Entry entry = (Map.Entry) iter.next();
                String key = (String)entry.getKey();

                //Requires deep copy.
                if (!(Message.INBOUND_MESSAGE.equals(key)
                      || Message.REQUESTOR_ROLE.equals(key)
                      || Message.PROTOCOL_HEADERS.equals(key))) {
                    to.put(key, entry.getValue());
                }
            }
        }
    }

    /**
     * This method is called when the incoming pojo or WebServiceProvider invocation is called
     * from the service invocation interceptor. The return value is the response
     * from the processor
     */
    public Object invoke(Exchange exchange, Object o) {
        CxfEndpoint endpoint = cxfConsumer.getEndpoint();

        Object params = null;
        if (o instanceof List) {
            params = CastUtils.cast((List<?>)o);
        } else if (o != null) {
            params = new MessageContentsList(o);
        }

        CxfExchange cxfExchange = endpoint.createExchange(exchange.getInMessage());

        BindingOperationInfo bop = exchange.get(BindingOperationInfo.class);
        MethodDispatcher md = (MethodDispatcher)
            exchange.get(Service.class).get(MethodDispatcher.class.getName());
        Method m = md.getMethod(bop);
        cxfExchange.setProperty(BindingOperationInfo.class.toString(), bop);

        // The SEI could be the provider class which will not have the bop information.
        if (bop != null && bop.getOperationInfo().isOneWay()) {
            cxfExchange.setPattern(ExchangePattern.InOnly);
        } else {
            cxfExchange.setPattern(ExchangePattern.InOut);
        }
        if (bop != null && bop.getName() != null) {
            cxfExchange.getIn().setHeader(CxfConstants.OPERATION_NAMESPACE, bop.getName().getNamespaceURI());
            cxfExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, bop.getName().getLocalPart());
        } else {
            cxfExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, m.getName());
        }

        CxfHeaderHelper.propagateCxfToCamel(endpoint.getHeaderFilterStrategy(), exchange.getInMessage(),
                cxfExchange.getIn().getHeaders());
        cxfExchange.getIn().setBody(params);
        try {
            cxfConsumer.getProcessor().process(cxfExchange);
        } catch (Exception ex) {
            // catch the exception and send back to cxf client
            throw new Fault(ex);
        }

        Object result = null;
        if (cxfExchange.isFailed()) {
            Throwable ex = (Throwable)cxfExchange.getFault().getBody();
            if (ex instanceof Fault) {
                throw (Fault)ex;
            } else {
                if (ex == null) {
                    ex = cxfExchange.getException();
                }
                throw new Fault(ex);
            }
        } else {
            result = cxfExchange.getOut().getBody();
            if (result != null) {
                if (result instanceof MessageContentsList || result instanceof List || result.getClass().isArray()) {
                    return result;
                } else { // if the result is the single object
                    MessageContentsList resList = new MessageContentsList();
                    resList.add(result);
                    return resList;
                }
            }
        }
        return result;
    }

}
