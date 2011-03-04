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
package org.apache.camel.component.servlet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.Servlet;

import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHttpRegistry implements HttpRegistry {
    private static final transient Logger LOG = LoggerFactory.getLogger(DefaultHttpRegistry.class);

    private static HttpRegistry singleton;
    
    private final Set<HttpConsumer> consumers;
    private final Set<CamelServlet> providers;
    
    public DefaultHttpRegistry() {
        consumers = new HashSet<HttpConsumer>();
        providers = new HashSet<CamelServlet>();
    }
    
    /**
     * Lookup or create a HttpRegistry
     * @param registry
     * @return
     */
    public static synchronized HttpRegistry getSingletonHttpRegistry() {
        if (singleton == null) {
            singleton = new DefaultHttpRegistry();
        }
        return singleton;
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.component.servlet.HttpRegistry#register(org.apache.camel.component.http.HttpConsumer)
     */
    @Override
    public void register(HttpConsumer consumer) {
        LOG.debug("Registering consumer for path {} providers present: {}",
                consumer.getPath(), providers.size());
        consumers.add(consumer);
        for (CamelServlet provider : providers) {
            provider.connect(consumer);
        }
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.component.servlet.HttpRegistry#unregister(org.apache.camel.component.http.HttpConsumer)
     */
    @Override
    public void unregister(HttpConsumer consumer) {
        LOG.debug("Unregistering consumer for path {} ", consumer.getPath());
        consumers.remove(consumer);
        for (CamelServlet provider : providers) {
            provider.disconnect(consumer);
        }
    }
    
    @SuppressWarnings("rawtypes")
    public void register(CamelServlet provider, Map properties) {
        LOG.debug("Registering provider through OSGi service listener {}", properties);
        if (provider instanceof CamelServlet) {
            CamelServlet camelServlet = (CamelServlet)provider;
            camelServlet.setServletName((String) properties.get("servlet-name"));
            register(camelServlet);
        }
    }
    
    public void unregister(Servlet provider, Map<String, Object> properties) {
        if (provider instanceof CamelServlet) {
            unregister((CamelServlet)provider);
        }
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.component.servlet.HttpRegistry#register(org.apache.camel.component.http.CamelServlet)
     */
    @Override
    public void register(CamelServlet provider) {
        LOG.debug("Registering CamelServlet with name {} consumers present: {}", 
                provider.getServletName(), consumers.size());
        providers.add(provider);
        for (HttpConsumer consumer : consumers) {
            provider.connect(consumer);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.servlet.HttpRegistry#unregister(org.apache.camel.component.http.CamelServlet)
     */
    @Override
    public void unregister(CamelServlet provider) {
        providers.remove(provider);
    }
    
    public void setServlets(List<Servlet> servlets) {
        providers.clear();
        for (Servlet servlet : servlets) {
            if (servlet instanceof CamelServlet) {
                providers.add((CamelServlet) servlet);
            }
        }
    }

}
