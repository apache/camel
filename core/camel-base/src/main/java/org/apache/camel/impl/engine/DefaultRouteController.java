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
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spi.RouteController;
import org.apache.camel.support.service.ServiceSupport;

public class DefaultRouteController extends ServiceSupport implements RouteController  {
    private CamelContext camelContext;

    public DefaultRouteController() {
        this(null);
    }

    public DefaultRouteController(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // ***************************************************
    // Properties
    // ***************************************************

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    // ***************************************************
    // Route management
    // ***************************************************

    protected RouteController getInternalRouteController() {
        return camelContext.adapt(ExtendedCamelContext.class).getInternalRouteController();
    }

    @Override
    public void startAllRoutes() throws Exception {
        getInternalRouteController().startAllRoutes();
    }

    @Override
    public boolean isStartingRoutes() {
        return getInternalRouteController().isStartingRoutes();
    }

    @Override
    public ServiceStatus getRouteStatus(String routeId) {
        return getInternalRouteController().getRouteStatus(routeId);
    }

    @Override
    public void startRoute(String routeId) throws Exception {
        getInternalRouteController().startRoute(routeId);
    }

    @Override
    public void stopRoute(String routeId) throws Exception {
        getInternalRouteController().stopRoute(routeId);
    }

    @Override
    public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        getInternalRouteController().stopRoute(routeId, timeout, timeUnit);
    }

    @Override
    public boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        return getInternalRouteController().stopRoute(routeId, timeout, timeUnit, abortAfterTimeout);
    }

    @Override
    public void suspendRoute(String routeId) throws Exception {
        getInternalRouteController().suspendRoute(routeId);
    }

    @Override
    public void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        getInternalRouteController().suspendRoute(routeId, timeout, timeUnit);
    }

    @Override
    public void resumeRoute(String routeId) throws Exception {
        getInternalRouteController().resumeRoute(routeId);
    }

    // ***************************************************
    //
    // ***************************************************

    @Override
    public Collection<Route> getControlledRoutes() {
        return Collections.emptyList();
    }
}
