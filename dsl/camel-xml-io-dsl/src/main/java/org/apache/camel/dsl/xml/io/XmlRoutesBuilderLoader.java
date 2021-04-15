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

import java.io.InputStream;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
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

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we use configure to load the routes
                try (InputStream is = resource.getInputStream()) {
                    new ModelParser(is, NAMESPACE)
                            .parseRouteTemplatesDefinition()
                            .ifPresent(this::setRouteTemplateCollection);
                }
                try (InputStream is = resource.getInputStream()) {
                    new ModelParser(is, NAMESPACE)
                            .parseRestsDefinition()
                            .ifPresent(this::setRestCollection);
                }
                try (InputStream is = resource.getInputStream()) {
                    new ModelParser(is, NAMESPACE)
                            .parseRoutesDefinition()
                            .ifPresent(this::addRoutes);
                }
            }

            private void addRoutes(RoutesDefinition routes) {
                // xml routes must be marked as un-prepared as camel-core
                // must do special handling for XML DSL
                for (RouteDefinition route : routes.getRoutes()) {
                    RouteDefinitionHelper.prepareRoute(getCamelContext(), route);
                    route.markPrepared();
                }
                setRouteCollection(routes);
            }
        };
    }
}
