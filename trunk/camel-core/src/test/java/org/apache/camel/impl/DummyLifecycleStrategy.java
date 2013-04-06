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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.LifecycleStrategySupport;

/**
 * @version 
 */
public class DummyLifecycleStrategy extends LifecycleStrategySupport {

    private List<String> events = new ArrayList<String>();

    public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
        events.add("onContextStart");
    }

    public void onContextStop(CamelContext context) {
        events.add("onContextStop");
    }

    public void onComponentAdd(String name, Component component) {
        events.add("onComponentAdd");
    }

    public void onComponentRemove(String name, Component component) {
        events.add("onComponentRemove");
    }

    public void onEndpointAdd(Endpoint endpoint) {
        events.add("onEndpointAdd");
    }

    public void onEndpointRemove(Endpoint endpoint) {
        events.add("onEndpointRemove");
    }

    public void onServiceAdd(CamelContext context, Service service, Route route) {
        events.add("onServiceAdd");
    }

    public void onServiceRemove(CamelContext context, Service service, Route route) {
        events.add("onServiceRemove");
    }

    public void onRoutesAdd(Collection<Route> routes) {
        events.add("onRoutesAdd");
    }

    public void onRoutesRemove(Collection<Route> routes) {
        events.add("onRoutesRemove");
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        events.add("onRouteContextCreate");
    }

    public void onErrorHandlerAdd(RouteContext routeContext, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        events.add("onErrorHandlerAdd");
    }

    public void onErrorHandlerRemove(RouteContext routeContext, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        events.add("onErrorHandlerRemove");
    }

    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPool, String id,
                                String sourceId, String routeId, String threadPoolProfileId) {
        events.add("onThreadPoolAdd");
    }

    public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPool) {
        events.add("onThreadPoolRemove");
    }

    public List<String> getEvents() {
        return events;
    }
}
