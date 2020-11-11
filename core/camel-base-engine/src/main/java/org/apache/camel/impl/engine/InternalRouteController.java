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

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.SupervisingRouteController;

/**
 * Internal {@link RouteController} used internally by {@link AbstractCamelContext}.
 */
class InternalRouteController implements RouteController {

    private final AbstractCamelContext abstractCamelContext;

    public InternalRouteController(AbstractCamelContext abstractCamelContext) {
        this.abstractCamelContext = abstractCamelContext;
    }

    @Override
    public LoggingLevel getRouteStartupLoggingLevel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRouteStartupLoggingLevel(LoggingLevel loggingLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SupervisingRouteController supervising() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends RouteController> T adapt(Class<T> type) {
        return type.cast(this);
    }

    @Override
    public Collection<Route> getControlledRoutes() {
        return abstractCamelContext.getRoutes();
    }

    @Override
    public void startAllRoutes() throws Exception {
        abstractCamelContext.startAllRoutes();
    }

    @Override
    public boolean isStartingRoutes() {
        return abstractCamelContext.isStartingRoutes();
    }

    @Override
    public ServiceStatus getRouteStatus(String routeId) {
        return abstractCamelContext.getRouteStatus(routeId);
    }

    @Override
    public void startRoute(String routeId) throws Exception {
        abstractCamelContext.startRoute(routeId);
    }

    @Override
    public void stopRoute(String routeId) throws Exception {
        abstractCamelContext.stopRoute(routeId);
    }

    @Override
    public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        abstractCamelContext.stopRoute(routeId, timeout, timeUnit);
    }

    @Override
    public boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        return abstractCamelContext.stopRoute(routeId, timeout, timeUnit, abortAfterTimeout);
    }

    @Override
    public void suspendRoute(String routeId) throws Exception {
        abstractCamelContext.suspendRoute(routeId);
    }

    @Override
    public void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        abstractCamelContext.suspendRoute(routeId, timeout, timeUnit);
    }

    @Override
    public void resumeRoute(String routeId) throws Exception {
        abstractCamelContext.resumeRoute(routeId);
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CamelContext getCamelContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException();
    }
}
