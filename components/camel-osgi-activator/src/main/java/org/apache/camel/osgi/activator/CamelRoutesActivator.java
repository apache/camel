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
package org.apache.camel.osgi.activator;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelRoutesActivator implements BundleActivator, ServiceTrackerCustomizer<RouteBuilder, RouteBuilder> {

    private static final Logger LOG = LoggerFactory.getLogger(CamelRoutesActivator.class);
    private ServiceRegistration<CamelContext> camelContextRef;
    private ModelCamelContext camelContext;
    private BundleContext bundleContext;
    private ServiceTracker<RouteBuilder, RouteBuilder> routeServiceTracker;

    @Override
    @SuppressWarnings("unchecked")
    public void start(BundleContext context) throws Exception {
        this.bundleContext = context;
        this.camelContext = new OsgiDefaultCamelContext(context);

        this.routeServiceTracker = new ServiceTracker<RouteBuilder, RouteBuilder>(context, RouteBuilder.class, this);

        camelContext.start();

        camelContextRef = context.registerService(CamelContext.class, camelContext, null);

        ServiceReference<RouteBuilder>[] existingRouteBuildersReferences = (ServiceReference<RouteBuilder>[]) context
                .getAllServiceReferences(RouteBuilder.class.getName(), null);

        if (existingRouteBuildersReferences != null) {
            for (ServiceReference<RouteBuilder> currentRouteBuilderReference : existingRouteBuildersReferences) {
                addingService(currentRouteBuilderReference);
            }
        }

        this.routeServiceTracker.open();
        LOG.info("Camel OSGi Activator RouteBuilder ServiceTracker Tracker Open");
    }

    @Override
    public RouteBuilder addingService(ServiceReference<RouteBuilder> reference) {
        RouteBuilder builder = this.bundleContext.getService(reference);
        try {
            // need to synchronize here since adding routes is not synchronized
            synchronized (camelContext) {
                this.camelContext.addRoutes(builder);
                LOG.debug("Camel Routes from RouteBuilder Class {} Added to Camel OSGi Activator Context", builder.getClass().getName());
            }
        } catch (Exception e) {
            LOG.error("Error Adding Camel RouteBuilder", e);
        }

        return builder;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        this.routeServiceTracker.close();
        camelContext.stop();
        camelContext.removeRouteDefinitions(new ArrayList<RouteDefinition>(this.camelContext.getRouteDefinitions()));

        context.ungetService(camelContextRef.getReference());
    }

    @Override
    public void modifiedService(ServiceReference<RouteBuilder> reference, RouteBuilder service) {
        removedService(reference, service);
        addingService(reference);
    }

    @Override
    public void removedService(ServiceReference<RouteBuilder> reference, RouteBuilder service) {
        List<RouteDefinition> routesToBeRemoved = service.getRouteCollection().getRoutes();
        try {
            synchronized (camelContext) {
                camelContext.removeRouteDefinitions(routesToBeRemoved);
                LOG.debug("Camel Routes from RouteBuilder Class {} Removed from Camel OSGi Activator Context",
                        service.getClass().getName());
            }
        } catch (Exception e) {
            LOG.error("Error Removing Camel Route Builder", e);
        }

    }

}
