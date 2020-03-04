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
import org.apache.camel.support.LifecycleStrategySupport;

public class DummyLifecycleStrategy extends LifecycleStrategySupport {

    private List<String> events = new ArrayList<>();

    @Override
    public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
        events.add("onContextStart");
    }

    @Override
    public void onContextStop(CamelContext context) {
        events.add("onContextStop");
    }

    @Override
    public void onComponentAdd(String name, Component component) {
        events.add("onComponentAdd");
    }

    @Override
    public void onComponentRemove(String name, Component component) {
        events.add("onComponentRemove");
    }

    @Override
    public void onEndpointAdd(Endpoint endpoint) {
        events.add("onEndpointAdd");
    }

    @Override
    public void onEndpointRemove(Endpoint endpoint) {
        events.add("onEndpointRemove");
    }

    @Override
    public void onServiceAdd(CamelContext context, Service service, org.apache.camel.Route route) {
        events.add("onServiceAdd");
    }

    @Override
    public void onServiceRemove(CamelContext context, Service service, org.apache.camel.Route route) {
        events.add("onServiceRemove");
    }

    @Override
    public void onRoutesAdd(Collection<org.apache.camel.Route> routes) {
        events.add("onRoutesAdd");
    }

    @Override
    public void onRoutesRemove(Collection<org.apache.camel.Route> routes) {
        events.add("onRoutesRemove");
    }

    @Override
    public void onRouteContextCreate(Route route) {
        events.add("onRouteContextCreate");
    }

    @Override
    public void onErrorHandlerAdd(Route route, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        events.add("onErrorHandlerAdd");
    }

    @Override
    public void onErrorHandlerRemove(Route route, Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        events.add("onErrorHandlerRemove");
    }

    @Override
    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPool, String id, String sourceId, String routeId, String threadPoolProfileId) {
        events.add("onThreadPoolAdd");
    }

    @Override
    public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPool) {
        events.add("onThreadPoolRemove");
    }

    public List<String> getEvents() {
        return events;
    }
}
