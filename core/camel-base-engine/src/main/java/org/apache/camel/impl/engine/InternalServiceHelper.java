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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.IsSingleton;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.Service;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.service.BaseService;
import org.apache.camel.support.service.ServiceHelper;

final class InternalServiceHelper {

    private InternalServiceHelper() {

    }

    public static void internalAddService(
            Object object, boolean stopOnShutdown,
            boolean forceStart, boolean useLifecycleStrategies, CamelContext camelContext,
            InternalRouteStartupManager internalRouteStartupManager,
            List<Service> servicesToStop, DeferServiceStartupListener deferStartupListener)
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
                            servicesToStop.add(service);
                        }
                    }
                }

                if (camelContext instanceof BaseService baseService) {
                    if (baseService.isStartingOrStarted()) {
                        ServiceHelper.startService(service);
                    } else {
                        ServiceHelper.initService(service);
                        deferStartService(object, stopOnShutdown, true, camelContext, servicesToStop, deferStartupListener);
                    }
                }
            }
        }
    }

    private static void deferStartService(
            Object object, boolean stopOnShutdown, boolean startEarly, CamelContext camelContext,
            List<Service> servicesToStop, DeferServiceStartupListener deferStartupListener)
            throws Exception {
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
                    servicesToStop.add(service);
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
}
