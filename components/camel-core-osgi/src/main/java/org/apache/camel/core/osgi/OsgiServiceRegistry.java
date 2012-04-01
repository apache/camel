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

import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.LifecycleStrategySupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * The OsgiServiceRegistry support to get the service object from the bundle context
 */
public class OsgiServiceRegistry extends LifecycleStrategySupport implements Registry {
    private final BundleContext bundleContext;
    private final Map<String, Object> serviceCacheMap = new ConcurrentHashMap<String, Object>();
    private final Queue<ServiceReference> serviceReferenceQueue = new ConcurrentLinkedQueue<ServiceReference>();
    
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

    public <T> Map<String, T> lookupByType(Class<T> type) {
        // not implemented so we return an empty map
        return Collections.<String, T>emptyMap();
    }

    @Override
    public void onContextStop(CamelContext context) {
        // Unget the OSGi service
        ServiceReference sr = serviceReferenceQueue.poll();
        while (sr != null) {
            bundleContext.ungetService(sr);
            sr = serviceReferenceQueue.poll();
        }
        // Clean up the OSGi Service Cache
        serviceReferenceQueue.clear();
        serviceCacheMap.clear();
    }

}
