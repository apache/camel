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

import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptorChain;

/**
 * A RoutingContext encapulates specific knowledge about how to route messages of
 * a particular data format.
 *
 */
public abstract class AbstractInvokingContext implements InvokingContext {
    
    private static final Logger LOG = Logger.getLogger(AbstractInvokingContext.class.getName());

    /**
     * This method is called when an request from a (routing) client is observed
     * at the router's transport (inbound to the router from a client).  It will 
     * return an "in" interceptor chain that will allow the appropriate routing 
     * interceptor to receive and handle the message.
     * @param exchange
     * @return in interceptor chain
     */
    public PhaseInterceptorChain getRequestInInterceptorChain(Exchange exchange) {
        return getInInterceptorChain(exchange, false);
    }

    protected PhaseInterceptorChain getInInterceptorChain(Exchange exchange, boolean isResponse) {

        Bus bus = exchange.get(Bus.class);
        assert bus != null;

        PhaseInterceptorChain chain = new PhaseInterceptorChain(getInPhases());

        if (!isResponse) {
            List<Interceptor> routingInterceptors = getRoutingInterceptors();
            chain.add(routingInterceptors);    
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Injected " + routingInterceptors);
            }
        }

        // bus
        List<Interceptor> list = bus.getInInterceptors();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Interceptors contributed by bus: " + list);
        }  
        
        chain.add(list);

        // endpoint
        Endpoint ep = exchange.get(Endpoint.class);
        if (ep != null) {
            list = ep.getInInterceptors();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Interceptors contributed by endpoint: " + list);
            }
            chain.add(list);

            // binding
            list = ep.getBinding().getInInterceptors();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Interceptors contributed by binding: " + list);
            }
            chain.add(list);

            // service
            list = ep.getService().getInInterceptors();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Interceptors contributed by service: " + list);
            }
            chain.add(list);
        }

        return chain;
    }

    /**
     * @return routing interceptor(s) specific to the routing context.
     */
    protected abstract List<Interceptor> getRoutingInterceptors();

    /**
     * @return "in" phrases from the phase manager specific to the routing context.
     */
    protected abstract SortedSet<Phase> getInPhases(); 
    
    /**
     * This method is called when a response from a CXF server is observed at the
     * router's transport (inbound to the router from a server).  It will return an
     * "in" interceptor chain that will allow the response to be returned to the 
     * involved routing interceptor (with the appropriate interceptors in between).
     * @param exchange
     * @return in interceptor chain
     */
    public PhaseInterceptorChain getResponseInInterceptorChain(Exchange exchange) {
        return getInInterceptorChain(exchange, true);
    }

    protected <T> T getResponseObject(Message inMessage, Map<String, Object> responseContext,
            Class <T> clazz) {        
        T retval = null;
        if (inMessage != null) {
            if (null != responseContext) {
                responseContext.putAll(inMessage);
                LOG.info("set responseContext to be" + responseContext);
            }
            retval = inMessage.getContent(clazz);
        }
        return retval;
    }

    /**
     * This method is called to set the fault observers on the endpoint that are specified
     * to the phases meaningful to the routing context.
     * @param endpointImpl
     * @param bus
     */
    public void setEndpointFaultObservers(EndpointImpl endpointImpl, Bus bus) {
        // default is no op
    }
}
