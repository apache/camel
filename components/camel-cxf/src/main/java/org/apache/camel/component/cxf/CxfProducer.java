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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.ws.handler.MessageContext.Scope;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.service.model.BindingOperationInfo;

/**
 * CxfProducer binds a Camel exchange to a CXF exchange, acts an a CXF 
 * client, and sends the request to a CXF to a server.  Any response will 
 * be bound to Camel exchange. 
 *
 * @version $Revision$
 */
public class CxfProducer extends DefaultProducer {
    private static final Log LOG = LogFactory.getLog(CxfProducer.class);
    private Client client;

    /**
     * Constructor to create a CxfProducer.  It will create a CXF client
     * object.
     * 
     * @param endpoint a CxfEndpoint that creates this producer
     * @throws Exception any exception thrown during the creation of a 
     * CXF client
     */
    public CxfProducer(CxfEndpoint endpoint) throws Exception {
        super(endpoint);
        client = endpoint.createClient();
    }

    /**
     * This processor binds Camel exchange to a CXF exchange and
     * invokes the CXF client.
     */
    public void process(Exchange camelExchange) throws Exception {
        
        if (LOG.isTraceEnabled()) {
            LOG.trace("Process exchange: " + camelExchange);
        }
        
        // create CXF exchange
        ExchangeImpl cxfExchange = new ExchangeImpl();
        
        // get CXF binding
        CxfEndpoint endpoint = (CxfEndpoint)getEndpoint();
        CxfBinding binding = endpoint.getCxfBinding();
        
        // create invocation context
        Map<String, Object> requestContext = new WrappedMessageContext(
                new HashMap<String, Object>(), null, Scope.APPLICATION);
        Map<String, Object> responseContext = new HashMap<String, Object>();
        
        
        // set data format mode in exchange
        DataFormat dataFormat = endpoint.getDataFormat();
        camelExchange.setProperty(DataFormat.class.getName(), dataFormat);   
        if (LOG.isTraceEnabled()) {
            LOG.trace("Set Camel Exchange property: " + DataFormat.class.getName() 
                    + "=" + dataFormat);
        }
        
        // set data format mode in the request context
        requestContext.put(DataFormat.class.getName(), dataFormat);

        // don't let CXF ClientImpl close the input stream 
        if (dataFormat == DataFormat.MESSAGE) {
            cxfExchange.put(Client.KEEP_CONDUIT_ALIVE, true);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Set CXF Exchange property: " + Client.KEEP_CONDUIT_ALIVE  
                        + "=" + true);
            }
        }
        
        // bind the request CXF exchange
        binding.populateCxfRequestFromExchange(cxfExchange, camelExchange, 
                requestContext);
 
        // get binding operation info
        BindingOperationInfo boi = getBindingOperationInfo(camelExchange);
        if (LOG.isTraceEnabled()) {
            LOG.trace("BOI = " + boi);
        }
        
        ObjectHelper.notNull(boi, "You should set '" + CxfConstants.OPERATION_NAME 
                + "' in header.");
        
        if (!endpoint.isWrapped() && boi != null) {
            if (boi.isUnwrappedCapable()) {
                boi = boi.getUnwrappedOperation();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Unwrapped BOI " + boi);
                }
            }
        }
        
        Map<String, Object> invocationContext = new HashMap<String, Object>();
        invocationContext.put(Client.RESPONSE_CONTEXT, responseContext);
        invocationContext.put(Client.REQUEST_CONTEXT, 
                ((WrappedMessageContext)requestContext).getWrappedMap());

        // send the CXF request
        client.invoke(boi, getParams(endpoint, camelExchange), 
                invocationContext, cxfExchange);
        
        // bind the CXF response to Camel exchange
        if (!boi.getOperationInfo().isOneWay()) {
            binding.populateExchangeFromCxfResponse(camelExchange, cxfExchange,
                    responseContext);
        }
    }

    /**
     * Get the parameters for the web service operation
     */
    private Object[] getParams(CxfEndpoint endpoint, Exchange exchange) {
        
        Object[] params = null;
        if (endpoint.getDataFormat() == DataFormat.POJO) {
            List<?> list = exchange.getIn().getBody(List.class);
            if (list != null) {
                params = list.toArray();
            } else {
                params = new Object[0];
            }
        } else if (endpoint.getDataFormat() == DataFormat.PAYLOAD) {
            params = new Object[1];
            params[0] = exchange.getIn().getBody();
        } else if (endpoint.getDataFormat() == DataFormat.MESSAGE) {
            params = new Object[1];
            params[0] = exchange.getIn().getBody(InputStream.class);
        }

        if (LOG.isTraceEnabled()) {
            if (params instanceof Object[]) {
                for (int i = 0; i < params.length; i++) {
                    LOG.trace("params[" + i + "] = " + params[i]);
                }
            } else {
                LOG.trace("params = " + params);
            }
        }
        
        return params;
    }

    /**
     * Get operation name from header and use it to lookup and return a 
     * {@link BindingOperationInfo}.
     */
    private BindingOperationInfo getBindingOperationInfo(Exchange ex) {

        BindingOperationInfo answer = null;
        String lp = ex.getIn().getHeader(CxfConstants.OPERATION_NAME, String.class);
        
        if (lp == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Try to find a default operation.  You should set '" 
                        + CxfConstants.OPERATION_NAME + "' in header.");
            }
            Collection<BindingOperationInfo> bois = 
                client.getEndpoint().getEndpointInfo().getBinding().getOperations();
            
            Iterator<BindingOperationInfo> iter = bois.iterator(); 
            if (iter.hasNext()) {
                answer = iter.next();
            }
            
        } else {
            String ns = ex.getIn().getHeader(CxfConstants.OPERATION_NAMESPACE, String.class);
            if (ns == null) {
                ns = client.getEndpoint().getService().getName().getNamespaceURI();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Operation namespace not in header.  Set it to: " + ns);
                }
            }

            QName qname = new QName(ns, lp);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Operation qname = " + qname.toString());
            }
            
            answer = client.getEndpoint().getEndpointInfo().getBinding().getOperation(qname);
        }
        return answer;
    }

}
