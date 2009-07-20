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

import groovy.lang.GroovyClassLoader;

import java.util.List;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.web.util.GroovyRenderer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An abstract class that provides basic support for GroovyRenderer test
 */
public abstract class GroovyRendererTestSupport extends TestCase {

    public final Log LOG = LogFactory.getLog(GroovyRendererTestSupport.class);

    private final String header = GroovyRenderer.header;

    private final String footer = GroovyRenderer.footer;

    private CamelContext context;

    public RouteDefinition createRoute(String dsl) throws Exception {
        if (context != null) {
            context.stop();
        }
        context = new DefaultCamelContext();

        String routeStr = header + dsl + footer;
        GroovyClassLoader classLoader = new GroovyClassLoader();
        Class clazz = classLoader.parseClass(routeStr);
        RouteBuilder builder = (RouteBuilder)clazz.newInstance();

        context.addRoutes(builder);
        List<RouteDefinition> list = context.getRouteDefinitions();
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    public String render(String dsl) throws Exception {
        RouteDefinition route = createRoute(dsl);
        assertNotNull(route);

        StringBuilder sb = new StringBuilder();
        new GroovyRenderer().renderRoute(sb, route);
        return sb.toString();
    }

}
