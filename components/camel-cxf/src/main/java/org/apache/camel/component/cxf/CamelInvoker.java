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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cxf.invoker.InvokingContext;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.MethodDispatcher;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.invoker.AbstractInvoker;
import org.apache.cxf.service.invoker.Invoker;
import org.apache.cxf.service.model.BindingOperationInfo;

public class CamelInvoker implements Invoker  {
    private static final Logger LOG = Logger.getLogger(CamelInvoker.class.getName());
    private CxfConsumer cxfConsumer;
    public CamelInvoker(CxfConsumer consumer) {
        cxfConsumer = consumer;
    }
    /**
    * This method is called when the incoming message is to
    * be passed into the camel processor. The return value is the response
    * from the processor
    * @param inMessage
    * @return outMessage
    */
    public Message invoke(Message inMessage) {
        System.out.println("invoke the message " + inMessage);
        Exchange exchange = inMessage.getExchange();
        //InvokingContext invokingContext = exchange.get(InvokingContext.class);
                               
        //Set Request Context into CXF Message
        Map<String, Object> ctxContainer = new HashMap<String, Object>();
        Map<String, Object> requestCtx = new HashMap<String, Object>();
        ctxContainer.put(Client.REQUEST_CONTEXT, requestCtx);
        updateContext(inMessage, requestCtx);
        CxfEndpoint endpoint = (CxfEndpoint) cxfConsumer.getEndpoint();
        CxfExchange cxfExchange = endpoint.createExchange(inMessage);
        try {
            cxfConsumer.getProcessor().process(cxfExchange);
        } catch (Exception e) {
            // catch the exception and send back to cxf client
            e.printStackTrace();
        }
       
        // make sure the client has retrun back the message
        Message outMessage = getCxfMessage(cxfExchange, exchange);
               
        //Set Response Context into CXF Message
        /*ctxContainer = (Map<String, Object>)outMessage.getProperty(CxfMessageAdapter.REQ_RESP_CONTEXT);
        Map<String, Object> respCtx = (Map<String, Object>)ctxContainer.get(Client.RESPONSE_CONTEXT);
        updateContext(respCtx, outMessage);*/
       
        return outMessage;
    }
    
    public Message getCxfMessage(CxfExchange result, Exchange exchange) {        
        Message outMessage = null;
        if (result.isFailed()) {
            CxfMessage fault = result.getFault();
            outMessage = exchange.getInFaultMessage();
            //REVISIT ?
            if (outMessage == null) {
                outMessage = new MessageImpl();
                //outMessage.setExchange(exchange);
                exchange.setInFaultMessage(outMessage);
            }
            Exception ex = (Exception) fault.getBody();
            outMessage.setContent(Exception.class, ex);
        } else {
            if (LOG.isLoggable(Level.FINEST)) {
                LOG.finest("Payload is a response.");
            }
            // get the payload message
            outMessage = result.getOutMessage();
            if (outMessage == null) {
                Endpoint ep = exchange.get(Endpoint.class);                    
                outMessage = ep.getBinding().createMessage();
                exchange.setOutMessage(outMessage);
            }
        }
        
        return outMessage;
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
     * This method is called when the incoming pojo invocation is called
     * from the service invocation interceptor. The return value is the response
     * from the processor
     * @param inMessage
     * @return outMessage
     */
    public Object invoke(Exchange exchange, Object o) {
        BindingOperationInfo bop = exchange.get(BindingOperationInfo.class);
        MethodDispatcher md = (MethodDispatcher) 
            exchange.get(Service.class).get(MethodDispatcher.class.getName());
        Method m = md.getMethod(bop);
        
        List<Object> params = null;
        if (o instanceof List) {
            params = CastUtils.cast((List<?>)o);
        } else if (o != null) {
            params = new MessageContentsList(o);
        }
        
        CxfEndpoint endpoint = (CxfEndpoint) cxfConsumer.getEndpoint();
        CxfExchange cxfExchange = endpoint.createExchange(exchange.getInMessage());
        cxfExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, m.getName());
        cxfExchange.getIn().setBody(params);
        
        
        try {
            cxfConsumer.getProcessor().process(cxfExchange);
        } catch (Exception e) {
            // catch the exception and send back to cxf client
            e.printStackTrace();
        }
        //System.out.println(cxfExchange.getOut().getBody());
        //TODO deal with the fault message
        Object[] result;
        if (cxfExchange.isFailed()) {
            Exception ex= (Exception)cxfExchange.getFault().getBody();
            throw new Fault(ex);
        } else {
            result = (Object[])cxfExchange.getOut().getBody();
        }
        
        return result;
        
    }

}
