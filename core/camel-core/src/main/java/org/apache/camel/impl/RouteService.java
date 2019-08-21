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
package org.apache.camel.impl;

import java.util.List;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.impl.engine.BaseRouteService;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.support.CamelContextHelper;

/**
 * Represents the runtime objects for a given {@link RouteDefinition} so that it
 * can be stopped independently of other routes
 */
public class RouteService extends BaseRouteService {

    private final RouteDefinition routeDefinition;

    public RouteService(Route route) {
        super(route);
        this.routeDefinition = (RouteDefinition)route.getRouteContext().getRoute();
    }

    public RouteDefinition getRouteDefinition() {
        return routeDefinition;
    }

    @Override
    public Integer getStartupOrder() {
        return routeDefinition.getStartupOrder();
    }

    @Override
    protected String getRouteDescription() {
        return RouteDefinitionHelper.getRouteMessage(routeDefinition.toString());
    }

    @Override
    public boolean isAutoStartup() throws Exception {
        if (!getCamelContext().isAutoStartup()) {
            return false;
        }
        if (!getRouteContext().isAutoStartup()) {
            return false;
        }
        if (routeDefinition.getAutoStartup() == null) {
            // should auto startup by default
            return true;
        }
        Boolean isAutoStartup = CamelContextHelper.parseBoolean(getCamelContext(), routeDefinition.getAutoStartup());
        return isAutoStartup != null && isAutoStartup;
    }

    @Override
    public boolean isContextScopedErrorHandler() {
        if (!routeDefinition.isContextScopedErrorHandler()) {
            return false;
        }
        // if error handler ref is configured it may refer to a context scoped,
        // so we need to check this first
        // the XML DSL will configure error handlers using refs, so we need this
        // additional test
        if (routeDefinition.getErrorHandlerRef() != null) {
            ErrorHandlerFactory routeScoped = getRouteContext().getErrorHandlerFactory();
            ErrorHandlerFactory contextScoped = getCamelContext().adapt(ExtendedCamelContext.class).getErrorHandlerFactory();
            return routeScoped != null && contextScoped != null && routeScoped == contextScoped;
        }

        return true;
    }

    /**
     * Gather all other kind of route scoped services from the given route,
     * except error handler
     */
    @Override
    protected void doGetRouteScopedServices(List<Service> services) {

        for (ProcessorDefinition<?> output : routeDefinition.getOutputs()) {
            if (output instanceof OnExceptionDefinition) {
                OnExceptionDefinition onExceptionDefinition = (OnExceptionDefinition)output;
                if (onExceptionDefinition.isRouteScoped()) {
                    Processor errorHandler = getRouteContext().getOnException(onExceptionDefinition.getId());
                    if (errorHandler instanceof Service) {
                        services.add((Service)errorHandler);
                    }
                }
            } else if (output instanceof OnCompletionDefinition) {
                OnCompletionDefinition onCompletionDefinition = (OnCompletionDefinition)output;
                if (onCompletionDefinition.isRouteScoped()) {
                    Processor onCompletionProcessor = getRouteContext().getOnCompletion(onCompletionDefinition.getId());
                    if (onCompletionProcessor instanceof Service) {
                        services.add((Service)onCompletionProcessor);
                    }
                }
            }
        }
    }

}
