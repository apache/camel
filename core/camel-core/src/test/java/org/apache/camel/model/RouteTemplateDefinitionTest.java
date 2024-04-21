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
package org.apache.camel.model;

import java.util.List;
import java.util.Map;

import org.apache.camel.support.RoutePolicySupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class RouteTemplateDefinitionTest {

    @Test
    void testDeepCopyMutableProperties() {
        RouteDefinition route = new RouteDefinition();
        route.setTemplateParameters(Map.of("parameter", "parameterValue"));
        route.setRouteProperties(List.of(new PropertyDefinition("property", "propertyValue")));
        route.setRoutePolicies(List.of(new RoutePolicySupport() {}));
        route.setInput(new FromDefinition("direct://fromEndpoint"));
        route.setOutputs(List.of(
            new ToDefinition("direct://toEndpoint"),
            new SetHeaderDefinition("header", "headerValue"),
            new EnrichDefinition(),
            new PollEnrichDefinition()));
        RouteTemplateDefinition routeTemplate = new RouteTemplateDefinition();
        routeTemplate.setRoute(route);

        RouteDefinition routeCopy = routeTemplate.asRouteDefinition();

        assertNotSame(route.getTemplateParameters(), routeCopy.getTemplateParameters());
        assertEquals(route.getTemplateParameters(), routeCopy.getTemplateParameters());
        assertNotSame(route.getRouteProperties(), routeCopy.getRouteProperties());
        assertEquals(route.getRouteProperties(), routeCopy.getRouteProperties());
        assertNotSame(route.getRoutePolicies(), routeCopy.getRoutePolicies());
        assertEquals(route.getRoutePolicies(), routeCopy.getRoutePolicies());
        assertNotSame(route.getInput(), routeCopy.getInput());
        assertEquals(route.getInput().getUri(), routeCopy.getInput().getUri());
        assertNotSame(route.getOutputs(), routeCopy.getOutputs());
        assertEquals(2, routeCopy.getOutputs().size());
        assertNotSame(route.getOutputs().get(0), routeCopy.getOutputs().get(0));
        assertInstanceOf(ToDefinition.class, route.getOutputs().get(0));
        assertInstanceOf(ToDefinition.class, routeCopy.getOutputs().get(0));
        assertEquals(((ToDefinition) route.getOutputs().get(0)).getUri(),
                ((ToDefinition) routeCopy.getOutputs().get(0)).getUri());
        assertSame(route.getOutputs().get(1), routeCopy.getOutputs().get(1));
    }
}
