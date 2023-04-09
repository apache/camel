/*
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
package org.apache.camel.http.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultHttpRegistry implements HttpRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpRegistry.class);

    private static Map<String, HttpRegistry> registries = new HashMap<>();

    private final Object lock = new Object();

    private final Set<HttpConsumer> consumers;
    private final Set<HttpRegistryProvider> providers;

    public DefaultHttpRegistry() {
        consumers = new HashSet<>();
        providers = new HashSet<>();
    }

    /**
     * Lookup or create a new registry if none exists with the given name
     */
    public static synchronized HttpRegistry getHttpRegistry(String name) {
        return registries.computeIfAbsent(name, k -> new DefaultHttpRegistry());
    }

    /**
     * Removes the http registry with the given name
     */
    public static synchronized void removeHttpRegistry(String name) {
        registries.remove(name);
    }

    @Override
    public void register(HttpConsumer consumer) {
        synchronized (lock) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Registering consumer for path {} providers present: {}", consumer.getPath(), providers.size());
            }
            consumers.add(consumer);
            for (HttpRegistryProvider provider : providers) {
                provider.connect(consumer);
            }
        }
    }

    @Override
    public void unregister(HttpConsumer consumer) {
        synchronized (lock) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unregistering consumer for path {}", consumer.getPath());
            }
            consumers.remove(consumer);
            for (HttpRegistryProvider provider : providers) {
                provider.disconnect(consumer);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public void register(CamelServlet provider, Map properties) {
        provider.setServletName((String) properties.get("servlet-name"));
        register(provider);
    }

    @Override
    public void register(HttpRegistryProvider provider) {
        synchronized (lock) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Registering CamelServlet with name {} consumers present: {}", provider.getServletName(),
                        consumers.size());
            }
            providers.add(provider);
            for (HttpConsumer consumer : consumers) {
                provider.connect(consumer);
            }
        }
    }

    @Override
    public void unregister(HttpRegistryProvider provider) {
        synchronized (lock) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unregistering CamelServlet with name {}", provider.getServletName());
            }
            providers.remove(provider);
        }
    }

    @Override
    public HttpRegistryProvider getCamelServlet(String servletName) {
        synchronized (lock) {
            for (HttpRegistryProvider provider : providers) {
                if (provider.getServletName().equals(servletName)) {
                    return provider;
                }
            }
            return null;
        }
    }

    public void setServlets(List<Servlet> servlets) {
        synchronized (lock) {
            providers.clear();
            for (Servlet servlet : servlets) {
                if (servlet instanceof HttpRegistryProvider) {
                    providers.add((HttpRegistryProvider) servlet);
                }
            }
        }
    }

}
