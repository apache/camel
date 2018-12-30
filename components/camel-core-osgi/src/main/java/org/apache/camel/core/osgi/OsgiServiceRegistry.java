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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.LifecycleStrategySupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The OsgiServiceRegistry support to get the service object from the bundle context
 */
public class OsgiServiceRegistry extends LifecycleStrategySupport implements Registry, Service, ServiceListener {
    private static final Logger LOG = LoggerFactory.getLogger(OsgiServiceRegistry.class);
    private final BundleContext bundleContext;
    private final Map<ServiceReference<?>, AtomicLong> serviceReferenceUsageMap = new ConcurrentHashMap<>();
    
    public OsgiServiceRegistry(BundleContext bc) {
        bundleContext = bc;
        bundleContext.addServiceListener(this);
    }

    /**
     * Support to lookup the Object with filter with the (name=NAME) and class type
     */
    public <T> T lookupByNameAndType(String name, Class<T> type) {
        Object service = null;
        ServiceReference<?> sr;
        try {
            ServiceReference<?>[] refs = bundleContext.getServiceReferences(type.getName(), "(name=" + name + ")");            
            if (refs != null && refs.length > 0) {
                // just return the first one
                sr = refs[0];
                incrementServiceUsage(sr);
                service = bundleContext.getService(sr);
            }
        } catch (Exception ex) {
            throw RuntimeCamelException.wrapRuntimeCamelException(ex);
        }
        return type.cast(service);
    }

    /**
     * It's only support to look up the ServiceReference with Class name or service PID
     */
    public Object lookupByName(String name) {
        Object service = null;
        ServiceReference<?> sr = bundleContext.getServiceReference(name);
        if (sr == null) {
            // trying to lookup service by PID if not found by name
            String filterExpression = "(" + Constants.SERVICE_PID + "=" + name + ")";
            try {
                ServiceReference<?>[] refs = bundleContext.getServiceReferences((String)null, filterExpression);
                if (refs != null && refs.length > 0) {
                    // just return the first one
                    sr = refs[0];
                }
            } catch (InvalidSyntaxException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid OSGi service reference filter, skipped lookup by service.pid. Filter expression: {}", filterExpression, ex);
                }
            }
        }
        if (sr != null) {
            incrementServiceUsage(sr);
            service = bundleContext.getService(sr);
        }
        return service;
    }

    public <T> Map<String, T> findByTypeWithName(Class<T> type) {
        Map<String, T> result = new HashMap<>();
        int count = 0;
        try {
            ServiceReference<?>[] refs = bundleContext.getAllServiceReferences(type.getName(), null);
            if (refs != null) {
                for (ServiceReference<?> sr : refs) {
                    if (sr != null) {
                        Object service = bundleContext.getService(sr);
                        incrementServiceUsage(sr);
                        if (service != null) {
                            String name = (String)sr.getProperty("name");
                            if (name != null) {
                                result.put(name, type.cast(service));
                            } else {
                                // generate a unique name for it
                                result.put(type.getSimpleName() + count, type.cast(service));
                                count++;
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw RuntimeCamelException.wrapRuntimeCamelException(ex);
        }
        return result;
    }

    public <T> Set<T> findByType(Class<T> type) {
        Map<String, T> map = findByTypeWithName(type);
        return new HashSet<>(map.values());
    }

    @Override
    public void start() throws Exception {
        // noop
    }

    @Override
    public void stop() throws Exception {
        // Unget the OSGi service as OSGi uses reference counting
        // and we should do this as one of the last actions when stopping Camel
        this.serviceReferenceUsageMap.forEach(this::drainServiceUsage);
        this.serviceReferenceUsageMap.clear();
    }

    private void drainServiceUsage(ServiceReference<?> serviceReference, AtomicLong serviceUsageCount) {
        if (serviceUsageCount != null && serviceReference != null) {
            while (serviceUsageCount.decrementAndGet() >= 0) {
                this.bundleContext.ungetService(serviceReference);
            }
        }
    }

    private void incrementServiceUsage(ServiceReference<?> sr) {
        AtomicLong serviceUsageCount = this.serviceReferenceUsageMap.get(sr);
        if (serviceUsageCount != null) {
            serviceUsageCount.incrementAndGet();
        } else {
            this.serviceReferenceUsageMap.merge(sr, new AtomicLong(1),
                (existingServiceUsageCount, newServiceUsageCount) -> {
                    existingServiceUsageCount.getAndAdd(newServiceUsageCount.get());
                    return existingServiceUsageCount;
                });
        }
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        if (event.getType() == ServiceEvent.UNREGISTERING) {
            ServiceReference<?> serviceReference = event.getServiceReference();
            AtomicLong serviceUsageCount = this.serviceReferenceUsageMap.remove(serviceReference);
            drainServiceUsage(serviceReference, serviceUsageCount);
        }
    }
}
