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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Default implementation of the lifecycle strategy.
 */
public class DefaultLifecycleStrategy implements LifecycleStrategy {

    public void onContextStart(CamelContext context) {
        // do nothing
    }

    public void onEndpointAdd(Endpoint endpoint) {
        // do nothing
    }

    public void onServiceAdd(CamelContext context, Service service) {
        // do nothing
    }

    public void onRoutesAdd(Collection<Route> routes) {
        // do nothing
    }

    public void onRouteContextCreate(RouteContext routeContext) {
        RouteDefinition routeType = routeContext.getRoute();

        if (routeType.getInputs() != null && !routeType.getInputs().isEmpty()) {
            // configure the outputs
            List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>(routeType.getOutputs());

            // clearing the outputs
            routeType.clearOutput();

            // a list of processors in the route
            List<ProcessorDefinition> counterList = new ArrayList<ProcessorDefinition>();

            // add the output configure the outputs with the routeType
            for (ProcessorDefinition processorType : outputs) {
                routeType.addOutput(processorType);
                counterList.add(processorType);
            }

            // set the error handler strategy containing the list of outputs added
            // TODO: align this code with InstrumentationLifecycleStrategy
            routeContext.setErrorHandlerWrappingStrategy(new DefaultErrorHandlerWrappingStrategy(routeContext, counterList));
        }
    }

}
