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
package org.apache.camel.scr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.Service;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.osgi.framework.BundleContext;

class ConcreteCamelRunner extends AbstractCamelRunner implements LifecycleStrategy {

    int camelContextStarted;
    int camelContextStopped;
    int routeAdded;

    Map<String, String> getDefaultProperties() {
        // Set default properties
        Map<String, String> defaultProps = new HashMap<>();
        defaultProps.put("camelContextId", "camel-runner");
        defaultProps.put("unit.camelContextId", "camel-runner-unitTest");
        defaultProps.put("camelRouteId", "test/direct-mock");
        defaultProps.put("active", "true");
        defaultProps.put("from", "direct:start");
        defaultProps.put("to", "mock:end");
        defaultProps.put("messageOk", "Success");
        defaultProps.put("messageError", "Failure");
        defaultProps.put("maximumRedeliveries", "0");
        defaultProps.put("redeliveryDelay", "1000");
        defaultProps.put("backOffMultiplier", "2");
        defaultProps.put("maximumRedeliveryDelay", "60000");
        return defaultProps;
    }

    @Override
    protected void createCamelContext(BundleContext bundleContext, Map<String, String> props) {
        super.createCamelContext(bundleContext, props);
        getContext().disableJMX();
        getContext().addLifecycleStrategy(this);
    }

    @Override
    public List<RoutesBuilder> getRouteBuilders() throws Exception {
        List<RoutesBuilder> routesBuilders = new ArrayList<>();
        routesBuilders.add(new TestRouteBuilder());
        routesBuilders.add(new TestRouteBuilder2());
        return routesBuilders;
    }

    @Override
    public void onContextStart(CamelContext camelContext) throws VetoCamelContextStartException {
        camelContextStarted++;
    }

    @Override
    public void onContextStop(CamelContext camelContext) {
        camelContextStopped++;
    }

    @Override
    public void onComponentAdd(String s, Component component) {
    }

    @Override
    public void onComponentRemove(String s, Component component) {
    }

    @Override
    public void onEndpointAdd(Endpoint endpoint) {
    }

    @Override
    public void onEndpointRemove(Endpoint endpoint) {
    }

    @Override
    public void onServiceAdd(CamelContext camelContext, Service service, Route route) {
    }

    @Override
    public void onServiceRemove(CamelContext camelContext, Service service, Route route) {
    }

    @Override
    public void onRoutesAdd(Collection<Route> routes) {
        routeAdded++;
    }

    @Override
    public void onRoutesRemove(Collection<Route> routes) {
    }

    @Override
    public void onRouteContextCreate(RouteContext routeContext) {
    }

    @Override
    public void onErrorHandlerAdd(RouteContext routeContext, Processor processor, ErrorHandlerFactory errorHandlerFactory) {
    }

    @Override
    public void onErrorHandlerRemove(RouteContext routeContext, Processor processor, ErrorHandlerFactory errorHandlerFactory) {
    }

    @Override
    public void onThreadPoolAdd(CamelContext camelContext, ThreadPoolExecutor threadPoolExecutor, String s, String s2, String s3, String s4) {
    }

    @Override
    public void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPoolExecutor) {
    }
}
