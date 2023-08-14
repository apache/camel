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
package org.apache.camel.maven.htmlxlsx.process;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.maven.htmlxlsx.model.CamelContextRouteCoverage;
import org.apache.camel.maven.htmlxlsx.model.Components;
import org.apache.camel.maven.htmlxlsx.model.Route;
import org.apache.camel.maven.htmlxlsx.model.Routes;
import org.apache.camel.maven.htmlxlsx.model.TestResult;

public class TestResultParser {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    public TestResult parse(TestResult testResult) {

        CamelContextRouteCoverage camelContextRouteCoverage = testResult.getCamelContextRouteCoverage();

        Routes routes = camelContextRouteCoverage.getRoutes();

        List<Route> routeList = routes.getRouteList();

        routeList.forEach(route -> {

            route.setComponents(components(route));
        });

        return testResult;
    }

    protected Components components(Route route) {

        Components components;

        try {
            Map<String, Object> componentsMap = route.getComponentsMap();
            String asString = objectMapper().writeValueAsString(componentsMap);
            components = objectMapper().readValue(asString, Components.class);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }

        return components;
    }

    /*
     * Provides a convenience method to facilitate unit testing.
     */
    protected ObjectMapper objectMapper() {

        return objectMapper;
    }
}
