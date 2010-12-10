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
package org.apache.camel.core.osgi;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteContext;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * The OsgiServiceRegistry support to get the service object from the bundle context
 */
public class OsgiServiceRegistry implements Registry, LifecycleStrategy {
    private final BundleContext bundleContext;
    private final Map<String, Object> serviceCacheMap = new ConcurrentHashMap<String, Object>();
    private final ConcurrentLinkedQueue<ServiceReference> serviceReferenceQueue = new ConcurrentLinkedQueue<ServiceReference>();
    
    public OsgiServiceRegistry(BundleContext bc) {
        bundleContext = bc;
    }

    public <T> T lookup(String name, Class<T> type) {
        Object service = lookup(name);
        return type.cast(service);
    }

    public Object lookup(String name) {
        Object service = serviceCacheMap.get(name);
        if (service == null) {
            ServiceReference sr = bundleContext.getServiceReference(name);            
            if (sr != null) {
                // Need to keep the track of Service
                // and call ungetService when the camel context is closed 
                serviceReferenceQueue.add(sr);
                service = bundleContext.getService(sr);
                if (service != null) {
                    serviceCacheMap.put(name, service);
                }
            } 
        }
        return service;
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> lookupByType(Class<T> type) {
        // not implemented so we return an empty map
        return Collections.EMPTY_MAP;
    }

    public void onComponentAdd(String name, Component component) {
        // Do nothing here
    }

    public void onComponentRemove(String name, Component component) {
        // Do nothing here
    }

    public void onContextStart(CamelContext context) {
        // Do nothing here
    }

    public void onContextStop(CamelContext context) {
        // Unget the OSGi service
        ServiceReference sr = serviceReferenceQueue.poll();
        while (sr != null) {
            bundleContext.ungetService(sr);
            sr = serviceReferenceQueue.poll();
        }
        // Clean up the OSGi Service Cache
        serviceCacheMap.clear();
    }

    public void onEndpointAdd(Endpoint endpoint) {
        // Do nothing here
    }

    public void onEndpointRemove(Endpoint endpoint) {
        // Do nothing here
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        // Do nothing here
    }

    public void onRoutesAdd(Collection<Route> routes) {
        // Do nothing here
    }

    public void onRoutesRemove(Collection<Route> routes) {
        // Do nothing here
    }

    public void onServiceAdd(CamelContext context, Service service, Route route) {
        // Do nothing here
    }

    public void onServiceRemove(CamelContext context, Service service, Route route) {
        // Do nothing here
    }

    public void onErrorHandlerAdd(RouteContext routeContext, Processor processor, ErrorHandlerBuilder errorHandlerBuilder) {
        // Do nothing here
    }

    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPool) {
        // Do nothing here
    }

}
