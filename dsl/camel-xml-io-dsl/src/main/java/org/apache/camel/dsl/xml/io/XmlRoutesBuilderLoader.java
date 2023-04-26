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

import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.xml.in.ModelParser;

@ManagedResource(description = "Managed XML RoutesBuilderLoader")
@RoutesLoader(XmlRoutesBuilderLoader.EXTENSION)
public class XmlRoutesBuilderLoader extends RouteBuilderLoaderSupport {
    public static final String EXTENSION = "xml";
    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";

    public XmlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    XmlRoutesBuilderLoader(String extension) {
        super(extension);
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        return new RouteConfigurationBuilder() {
            @Override
            public void configure() throws Exception {
                // we use configure to load the routes (with namespace and without namespace)
                new ModelParser(resource, NAMESPACE)
                        .parseRouteTemplatesDefinition()
                        .ifPresent(this::setRouteTemplateCollection);
                new ModelParser(resource, NAMESPACE)
                        .parseTemplatedRoutesDefinition()
                        .ifPresent(this::setTemplatedRouteCollection);
                new ModelParser(resource, NAMESPACE)
                        .parseRestsDefinition()
                        .ifPresent(this::setRestCollection);
                new ModelParser(resource, NAMESPACE)
                        .parseRoutesDefinition()
                        .ifPresent(this::addRoutes);
                new ModelParser(resource)
                        .parseRouteTemplatesDefinition()
                        .ifPresent(this::setRouteTemplateCollection);
                new ModelParser(resource)
                        .parseTemplatedRoutesDefinition()
                        .ifPresent(this::setTemplatedRouteCollection);
                new ModelParser(resource)
                        .parseRestsDefinition()
                        .ifPresent(this::setRestCollection);
                new ModelParser(resource)
                        .parseRoutesDefinition()
                        .ifPresent(this::addRoutes);
            }

            @Override
            public void configuration() throws Exception {
                new ModelParser(resource, NAMESPACE)
                        .parseRouteConfigurationsDefinition()
                        .ifPresent(this::addConfigurations);
                new ModelParser(resource)
                        .parseRouteConfigurationsDefinition()
                        .ifPresent(this::addConfigurations);
            }

            private void addRoutes(RoutesDefinition routes) {
                CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());

                // xml routes must be prepared in the same way java-dsl (via RoutesDefinition)
                // so create a copy and use the fluent builder to add the route
                for (RouteDefinition route : routes.getRoutes()) {
                    getRouteCollection().route(route);
                }
            }

            private void addConfigurations(RouteConfigurationsDefinition configurations) {
                CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                for (RouteConfigurationDefinition config : configurations.getRouteConfigurations()) {
                    getCamelContext().adapt(ModelCamelContext.class).addRouteConfiguration(config);
                }
            }
        };
    }
}
