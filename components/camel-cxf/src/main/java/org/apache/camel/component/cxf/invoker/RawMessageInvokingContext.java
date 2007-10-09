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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.camel.component.cxf.interceptors.RawMessageContentRedirectInterceptor;
import org.apache.camel.component.cxf.interceptors.RawMessageInInterceptor;
import org.apache.camel.component.cxf.phase.RawMessagePhaseManagerImpl;


import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.phase.PhaseManager;

public class RawMessageInvokingContext extends AbstractInvokingContext {
    private static final Logger LOG = Logger.getLogger(RawMessageInvokingContext.class.getName());
    
    private PhaseManager phaseManager; 

    public RawMessageInvokingContext() {
        phaseManager = new RawMessagePhaseManagerImpl();
    }
    
    public PhaseInterceptorChain getRequestOutInterceptorChain(Exchange exchange) {
        return getOutInterceptorChain(exchange);
    }

    public PhaseInterceptorChain getResponseOutInterceptorChain(Exchange exchange) {
        return getOutInterceptorChain(exchange);
    }

    private PhaseInterceptorChain getOutInterceptorChain(Exchange exchange) {
        
        PhaseInterceptorChain chain = new PhaseInterceptorChain(
                new RawMessagePhaseManagerImpl().getOutPhases());
        
        Bus bus = exchange.get(Bus.class);
        assert bus != null;
        
        // bus
        List<Interceptor> list = bus.getOutInterceptors();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Interceptors contributed by bus: " + list);
        }
        chain.add(list);
        
        // endpoint
        Endpoint endpoint = exchange.get(Endpoint.class);
        if (endpoint != null) {
            list = endpoint.getOutInterceptors();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Interceptors contributed by endpoint: " + list);
            }
            chain.add(list);
        }
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("inject " + RawMessageContentRedirectInterceptor.class);
        }
        chain.add(new RawMessageContentRedirectInterceptor());
        
        return chain;
  
    }

    public void setRequestOutMessageContent(Message message, Object content) {
        message.setContent(InputStream.class, content);        
    }

    @Override
    protected SortedSet<Phase> getInPhases() {
        return phaseManager.getInPhases();
    }

    @Override
    protected List<Interceptor> getRoutingInterceptors() {
        List<Interceptor> list = new ArrayList<Interceptor>();
        list.add(new RawMessageInInterceptor());
        return list;
    }

    public Object getResponseObject(Exchange exchange, Map<String, Object> responseContext) {
        
        return getResponseObject(exchange.getInMessage(), responseContext, InputStream.class);
    }
    
    //@Override
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
        
        LOG.fine("Interceptors contributed by bus: " + list);
        chain.add(list);

        // endpoint
        Endpoint ep = exchange.get(Endpoint.class);
        if (ep != null) {
            list = ep.getInInterceptors();
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Interceptors contributed by endpoint: " + list);
            }
            chain.add(list);
        }

        return chain;
    }
    
    public void setResponseContent(Message outMessage, Object resultPayload) {
        LOG.info("Set content: " + resultPayload);
        outMessage.setContent(InputStream.class, resultPayload);
        //loggerTheMessage(outMessage, "Out Message");
    }

    public Object getRequestContent(Message inMessage) {        
        //loggerTheMessage(inMessage, "In Message");
        return inMessage.getContent(InputStream.class);
    }
    
    private void loggerTheMessage(Message message, String messageTile) {
        StringBuffer buffer = new StringBuffer( messageTile + "\n" 
                                               + "--------------------------------------");
        InputStream is = message.getContent(InputStream.class);        
        if (is != null) {
            CachedOutputStream bos = new CachedOutputStream();
            try {
                IOUtils.copy(is, bos);

                is.close();
                bos.close();

                buffer.append("\nMessage:\n");
                buffer.append(bos.getOut().toString());
                
                message.setContent(InputStream.class, bos.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        buffer.append("\n--------------------------------------");
        LOG.info(buffer.toString());
    }
}
