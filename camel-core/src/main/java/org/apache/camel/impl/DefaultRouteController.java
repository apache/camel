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
package org.apache.camel.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Experimental;
import org.apache.camel.Route;
import org.apache.camel.spi.RouteController;

@Experimental
public class DefaultRouteController extends org.apache.camel.support.ServiceSupport implements RouteController  {
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
    // Life cycle
    // ***************************************************

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    // ***************************************************
    // Route management
    // ***************************************************

    @Override
    public void startRoute(String routeId) throws Exception {
        camelContext.startRoute(routeId);
    }

    @Override
    public void stopRoute(String routeId) throws Exception {
        camelContext.stopRoute(routeId);
    }

    @Override
    public void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        camelContext.stopRoute(routeId, timeout, timeUnit);
    }

    @Override
    public boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception {
        return camelContext.stopRoute(routeId, timeout, timeUnit, abortAfterTimeout);
    }

    @Override
    public void suspendRoute(String routeId) throws Exception {
        camelContext.suspendRoute(routeId);
    }

    @Override
    public void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception {
        camelContext.suspendRoute(routeId, timeout, timeUnit);
    }

    @Override
    public void resumeRoute(String routeId) throws Exception {
        camelContext.resumeRoute(routeId);
    }

    // ***************************************************
    //
    // ***************************************************

    @Override
    public Collection<Route> getControlledRoutes() {
        return Collections.emptyList();
    }
}
