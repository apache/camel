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
package org.apache.camel.support;

import java.util.Collection;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * A useful base class for {@link LifecycleStrategy} implementations.
 */
public abstract class LifecycleStrategySupport implements LifecycleStrategy {

    @Override
    public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
        // noop
    }

    @Override
    public void onContextStop(CamelContext context) {
        // noop
    }

    @Override
    public void onComponentAdd(String name, Component component) {
        // noop
    }

    @Override
    public void onComponentRemove(String name, Component component) {
        // noop
    }

    @Override
    public void onEndpointAdd(Endpoint endpoint) {
        // noop
    }

    @Override
    public void onEndpointRemove(Endpoint endpoint) {
        // noop
    }

    @Override
    public void onServiceAdd(CamelContext context, Service service, Route route) {
        // noop
    }

    @Override
    public void onServiceRemove(CamelContext context, Service service, Route route) {
        // noop
    }

    @Override
    public void onRoutesAdd(Collection<Route> routes) {
        // noop
    }

    @Override
    public void onRoutesRemove(Collection<Route> routes) {
        // noop
    }

    @Override
    public void onRouteContextCreate(RouteContext routeContext) {
        // noop
    }

    @Override
    public void onErrorHandlerAdd(RouteContext routeContext, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        // noop
    }

    @Override
    public void onErrorHandlerRemove(RouteContext routeContext, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        // noop
    }

    @Override
    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPool, String id,
                                String sourceId, String routeId, String threadPoolProfileId) {
        // noop
    }

    @Override
    public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPool) {
        // noop
    }
}
