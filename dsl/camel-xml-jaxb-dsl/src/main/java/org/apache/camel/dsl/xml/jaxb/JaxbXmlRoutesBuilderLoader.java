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
package org.apache.camel.dsl.xml.jaxb;

import java.io.InputStream;

import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.TemplatedRoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;

import static org.apache.camel.xml.jaxb.JaxbHelper.loadRestsDefinition;
import static org.apache.camel.xml.jaxb.JaxbHelper.loadRouteConfigurationsDefinition;
import static org.apache.camel.xml.jaxb.JaxbHelper.loadRouteTemplatesDefinition;
import static org.apache.camel.xml.jaxb.JaxbHelper.loadRoutesDefinition;
import static org.apache.camel.xml.jaxb.JaxbHelper.loadTemplatedRoutesDefinition;

@ManagedResource(description = "Managed JAXB XML RoutesBuilderLoader")
@RoutesLoader(JaxbXmlRoutesBuilderLoader.EXTENSION)
public class JaxbXmlRoutesBuilderLoader extends RouteBuilderLoaderSupport {
    public static final String EXTENSION = "xml";

    public JaxbXmlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    JaxbXmlRoutesBuilderLoader(String extension) {
        super(extension);
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        return new RouteConfigurationBuilder() {
            @Override
            public void configure() throws Exception {
                // we use configure to load the routes
                try (InputStream is = resourceInputStream(resource)) {
                    RouteTemplatesDefinition templates = loadRouteTemplatesDefinition(getCamelContext(), is);
                    if (templates != null) {
                        setRouteTemplateCollection(templates);
                    }
                }

                try (InputStream is = resourceInputStream(resource)) {
                    TemplatedRoutesDefinition templates = loadTemplatedRoutesDefinition(getCamelContext(), is);
                    if (templates != null) {
                        setTemplatedRouteCollection(templates);
                    }
                }

                try (InputStream is = resourceInputStream(resource)) {
                    RestsDefinition rests = loadRestsDefinition(getCamelContext(), is);
                    if (rests != null) {
                        setRestCollection(rests);
                    }
                }

                try (InputStream is = resourceInputStream(resource)) {
                    RoutesDefinition routes = loadRoutesDefinition(getCamelContext(), is);
                    if (routes != null) {
                        // xml routes must be prepared in the same way java-dsl (via RoutesDefinition)
                        // so create a copy and use the fluent builder to add the route
                        for (RouteDefinition route : routes.getRoutes()) {
                            CamelContextAware.trySetCamelContext(route, getCamelContext());
                            getRouteCollection().route(route);
                        }
                    }
                }
            }

            @Override
            public void configuration() throws Exception {
                try (InputStream is = resourceInputStream(resource)) {
                    RouteConfigurationsDefinition configurations = loadRouteConfigurationsDefinition(getCamelContext(), is);
                    if (configurations != null) {
                        for (RouteConfigurationDefinition config : configurations.getRouteConfigurations()) {
                            CamelContextAware.trySetCamelContext(config, getCamelContext());
                            getRouteConfigurationCollection().routeConfiguration(config);
                        }
                    }
                }
            }

        };
    }
}
