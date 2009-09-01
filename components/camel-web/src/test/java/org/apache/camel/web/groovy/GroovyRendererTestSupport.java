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

package org.apache.camel.web.groovy;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import groovy.lang.GroovyClassLoader;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.web.util.GroovyRenderer;
import org.junit.Assert;

/**
 * An abstract class that provides basic support for GroovyRenderer test
 */
public abstract class GroovyRendererTestSupport extends Assert {
    private String header = GroovyRenderer.HEADER;
    private final String footer = GroovyRenderer.FOOTER;

    private CamelContext context;

    /**
     * get the first route in camelContext
     */
    public RouteDefinition getRoute(String dsl) throws Exception {
        createAndAddRoute(dsl);
        List<RouteDefinition> list = context.getRouteDefinitions();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /**
     * get all routes in camelContext
     */
    public List<RouteDefinition> getRoutes(String dsl) throws Exception {
        createAndAddRoute(dsl);
        return context.getRouteDefinitions();
    }

    public String render(String dsl) throws Exception {
        RouteDefinition route = getRoute(dsl);
        assertNotNull(route);

        StringBuilder sb = new StringBuilder();
        GroovyRenderer.renderRoute(sb, route);
        return sb.toString();
    }

    /**
     * render a route with some import packages
     */
    public String render(String dsl, String[] imports) throws Exception {
        // add import
        StringBuilder sb = new StringBuilder();
        for (String importPackage : imports) {
            sb.append(importPackage).append("\n");
        }
        header = sb.toString() + "\n" + header;

        return render(dsl);
    }

    /**
     * render a route with some import packages and new object
     */
    public String render(String dsl, String[] imports, Map<String, String> newObjects) throws Exception {
        // add new objects
        StringBuilder sb = new StringBuilder();
        for (Entry<String, String> entry : newObjects.entrySet()) {
            String objectName = entry.getKey();
            String clazzName = entry.getValue();
            sb.append(clazzName).append(" ").append(objectName).append(" = new ").append(clazzName).append("();\n");
        }
        header += sb.toString();

        return render(dsl, imports);
    }

    public String renderRoutes(String dsl) throws Exception {
        List<RouteDefinition> routes = getRoutes(dsl);

        StringBuilder sb = new StringBuilder();
        GroovyRenderer.renderRoutes(sb, routes);
        return sb.toString();
    }

    /**
     * create routes using the dsl and add them into camelContext
     */
    private void createAndAddRoute(String dsl) throws Exception, InstantiationException, IllegalAccessException {
        if (context != null) {
            context.stop();
        }
        context = new DefaultCamelContext();

        String routeStr = header + dsl + footer;
        GroovyClassLoader classLoader = new GroovyClassLoader();
        Class clazz = classLoader.parseClass(routeStr);
        RouteBuilder builder = (RouteBuilder)clazz.newInstance();

        context.addRoutes(builder);
    }
}
