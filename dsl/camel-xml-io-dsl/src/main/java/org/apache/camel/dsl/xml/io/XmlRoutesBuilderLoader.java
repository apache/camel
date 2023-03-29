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
package org.apache.camel.dsl.xml.io;

import java.util.List;

import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.CachedResource;
import org.apache.camel.xml.in.ModelParser;

@ManagedResource(description = "Managed XML RoutesBuilderLoader")
@RoutesLoader(XmlRoutesBuilderLoader.EXTENSION)
public class XmlRoutesBuilderLoader extends RouteBuilderLoaderSupport {
    public static final String EXTENSION = "xml";
    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";
    private static final List<String> NAMESPACES = List.of("", NAMESPACE);

    public XmlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource input) throws Exception {
        return new RouteConfigurationBuilder() {
            final Resource resource = new CachedResource(input);

            @Override
            public void configure() throws Exception {
                // we use configure to load the routes (with namespace and without namespace)
                for (String ns : NAMESPACES) {
                    new ModelParser(resource, ns)
                            .parseRouteTemplatesDefinition()
                            .ifPresent(this::setRouteTemplateCollection);
                    new ModelParser(resource, ns)
                            .parseTemplatedRoutesDefinition()
                            .ifPresent(this::setTemplatedRouteCollection);
                    new ModelParser(resource, ns)
                            .parseRestsDefinition()
                            .ifPresent(this::setRestCollection);
                    new ModelParser(resource, ns)
                            .parseRoutesDefinition()
                            .ifPresent(this::addRoutes);
                }
            }

            @Override
            public void configuration() throws Exception {
                for (String ns : NAMESPACES) {
                    new ModelParser(resource, ns)
                            .parseRouteConfigurationsDefinition()
                            .ifPresent(this::addConfigurations);
                }
            }

            private void addRoutes(RoutesDefinition routes) {
                CamelContextAware.trySetCamelContext(routes, getCamelContext());

                // xml routes must be prepared in the same way java-dsl (via RoutesDefinition)
                // so create a copy and use the fluent builder to add the route
                for (RouteDefinition route : routes.getRoutes()) {
                    getRouteCollection().route(route);
                }
            }

            private void addConfigurations(RouteConfigurationsDefinition configurations) {
                CamelContextAware.trySetCamelContext(configurations, getCamelContext());

                // xml routes must be prepared in the same way java-dsl (via RouteConfigurationDefinition)
                // so create a copy and use the fluent builder to add the route
                for (RouteConfigurationDefinition config : configurations.getRouteConfigurations()) {
                    getRouteConfigurationCollection().routeConfiguration(config);
                }
            }
        };
    }
}
