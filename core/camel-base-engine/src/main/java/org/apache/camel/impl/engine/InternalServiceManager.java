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

package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.IsSingleton;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.StartupListener;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.service.BaseService;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InternalServiceManager {
    private static final Logger LOG = LoggerFactory.getLogger(InternalServiceManager.class);

    private final InternalRouteStartupManager internalRouteStartupManager;

    private final DeferServiceStartupListener deferStartupListener = new DeferServiceStartupListener();
    private final List<Service> services = new CopyOnWriteArrayList<>();

    InternalServiceManager(InternalRouteStartupManager internalRouteStartupManager, List<StartupListener> startupListeners) {
        /*
         Note: this is an internal API and not meant to be public, so it uses assertion for lightweight nullability
         checking for extremely unlikely scenarios that should be found during development time.
         */
        assert internalRouteStartupManager != null : "the internalRouteStartupManager cannot be null";
        assert startupListeners != null : "the startupListeners cannot be null";

        this.internalRouteStartupManager = internalRouteStartupManager;

        startupListeners.add(deferStartupListener);
    }

    public <T> T addService(CamelContext camelContext, T object) {
        return addService(camelContext, object, true);
    }

    public <T> T addService(CamelContext camelContext, T object, boolean stopOnShutdown) {
        return addService(camelContext, object, stopOnShutdown, true, true);
    }

    public <T> T addService(
            CamelContext camelContext, T object, boolean stopOnShutdown, boolean forceStart, boolean useLifecycleStrategies) {
        try {
            doAddService(camelContext, object, stopOnShutdown, forceStart, useLifecycleStrategies);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return object;
    }

    public void doAddService(
            CamelContext camelContext, Object object, boolean stopOnShutdown, boolean forceStart,
            boolean useLifecycleStrategies)
            throws Exception {

        if (object == null) {
            return;
        }

        // inject CamelContext
        CamelContextAware.trySetCamelContext(object, camelContext);

        if (object instanceof Service) {
            Service service = (Service) object;

            if (useLifecycleStrategies) {
                for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                    if (service instanceof Endpoint) {
                        // use specialized endpoint add
                        strategy.onEndpointAdd((Endpoint) service);
                    } else {
                        Route route;
                        if (service instanceof RouteAware) {
                            route = ((RouteAware) service).getRoute();
                        } else {
                            // if the service is added while creating a new route then grab the route from the startup manager
                            route = internalRouteStartupManager.getSetupRoute();
                        }
                        strategy.onServiceAdd(camelContext, service, route);
                    }
                }
            }

            if (!forceStart) {
                ServiceHelper.initService(service);
                // now start the service (and defer starting if CamelContext is
                // starting up itself)
                camelContext.deferStartService(object, stopOnShutdown);
            } else {
                // only add to services to close if its a singleton
                // otherwise we could for example end up with a lot of prototype
                // scope endpoints
                boolean singleton = true; // assume singleton by default
                if (object instanceof IsSingleton) {
                    singleton = ((IsSingleton) service).isSingleton();
                }
                // do not add endpoints as they have their own list
                if (singleton && !(service instanceof Endpoint)) {
                    // only add to list of services to stop if its not already there
                    if (stopOnShutdown && !camelContext.hasService(service)) {
                        // special for type converter / type converter registry which is stopped manual later
                        boolean tc = service instanceof TypeConverter || service instanceof TypeConverterRegistry;
                        if (!tc) {
                            services.add(service);
                        }
                    }
                }

                if (camelContext instanceof BaseService baseService) {
                    if (baseService.isStartingOrStarted()) {
                        ServiceHelper.startService(service);
                    } else {
                        ServiceHelper.initService(service);
                        deferStartService(camelContext, object, stopOnShutdown, true);
                    }
                }
            }
        }
    }

    public void deferStartService(CamelContext camelContext, Object object, boolean stopOnShutdown, boolean startEarly) {
        if (object instanceof Service) {
            Service service = (Service) object;

            // only add to services to close if its a singleton
            // otherwise we could for example end up with a lot of prototype
            // scope endpoints
            boolean singleton = true; // assume singleton by default
            if (object instanceof IsSingleton) {
                singleton = ((IsSingleton) service).isSingleton();
            }
            // do not add endpoints as they have their own list
            if (singleton && !(service instanceof Endpoint)) {
                // only add to list of services to stop if its not already there
                if (stopOnShutdown && !camelContext.hasService(service)) {
                    services.add(service);
                }
            }
            // are we already started?
            if (camelContext.isStarted()) {
                ServiceHelper.startService(service);
            } else {
                deferStartupListener.addService(service, startEarly);
            }
        }
    }

    public boolean removeService(Service service) {
        return services.remove(service);
    }

    @SuppressWarnings("unchecked")
    public <T> Set<T> hasServices(Class<T> type) {
        if (services.isEmpty()) {
            return Collections.emptySet();
        }

        Set<T> set = new HashSet<>();
        for (Service service : services) {
            if (type.isInstance(service)) {
                set.add((T) service);
            }
        }
        return set;
    }

    public boolean hasService(Object object) {
        if (services.isEmpty()) {
            return false;
        }
        if (object instanceof Service) {
            Service service = (Service) object;
            return services.contains(service);
        }
        return false;
    }

    public <T> T hasService(Class<T> type) {
        if (services.isEmpty()) {
            return null;
        }
        for (Service service : services) {
            if (type.isInstance(service)) {
                return type.cast(service);
            }
        }
        return null;
    }

    public void stopConsumers(CamelContext camelContext) {
        for (Service service : services) {
            if (service instanceof Consumer) {
                InternalServiceManager.shutdownServices(camelContext, service);
            }
        }
    }

    public void shutdownServices(CamelContext camelContext) {
        InternalServiceManager.shutdownServices(camelContext, services);
        services.clear();
    }

    public static void shutdownServices(CamelContext camelContext, Collection<?> services) {
        // reverse stopping by default
        shutdownServices(camelContext, services, true);
    }

    public List<Service> getServices() {
        return Collections.unmodifiableList(services);
    }

    public static void shutdownServices(CamelContext camelContext, Collection<?> services, boolean reverse) {
        Collection<?> list = services;
        if (reverse) {
            List<Object> reverseList = new ArrayList<>(services);
            Collections.reverse(reverseList);
            list = reverseList;
        }

        for (Object service : list) {
            shutdownServices(camelContext, service);
        }
    }

    public static void shutdownServices(CamelContext camelContext, Object service) {
        // do not rethrow exception as we want to keep shutting down in case of
        // problems

        // allow us to do custom work before delegating to service helper
        try {
            if (service instanceof Service) {
                ServiceHelper.stopAndShutdownService(service);
            } else if (service instanceof Collection) {
                ServiceHelper.stopAndShutdownServices((Collection<?>) service);
            }
        } catch (Exception e) {
            LOG.warn("Error occurred while shutting down service: {}. This exception will be ignored.", service, e);
            // fire event
            EventHelper.notifyServiceStopFailure(camelContext, service, e);
        }
    }
}
