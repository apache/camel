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
package org.apache.camel.dsl.yaml;

import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.dsl.yaml.deserializers.OutputAwareFromDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.snakeyaml.engine.v2.nodes.Node;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asSequenceNode;

@ManagedResource(description = "Managed YAML RoutesBuilderLoader")
@RoutesLoader(YamlRoutesBuilderLoader.EXTENSION)
public class YamlRoutesBuilderLoader extends YamlRoutesBuilderLoaderSupport {
    public static final String EXTENSION = "yaml";

    public YamlRoutesBuilderLoader() {
        super(EXTENSION);
    }

    protected RouteBuilder builder(Node root) {
        return new RouteConfigurationBuilder() {
            @Override
            public void configure() throws Exception {
                for (Node node : asSequenceNode(root).getValue()) {
                    Object item = getDeserializationContext().mandatoryResolve(node).construct(node);

                    if (item instanceof OutputAwareFromDefinition) {
                        RouteDefinition route = new RouteDefinition();
                        route.setInput(((OutputAwareFromDefinition) item).getDelegate());
                        route.setOutputs(((OutputAwareFromDefinition) item).getOutputs());

                        CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                        getRouteCollection().route(route);
                    } else if (item instanceof RouteDefinition) {
                        CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                        getRouteCollection().route((RouteDefinition) item);
                    } else if (item instanceof CamelContextCustomizer) {
                        ((CamelContextCustomizer) item).configure(getCamelContext());
                    } else if (item instanceof OnExceptionDefinition) {
                        if (!getRouteCollection().getRoutes().isEmpty()) {
                            throw new IllegalArgumentException(
                                    "onException must be defined before any routes in the RouteBuilder");
                        }
                        CamelContextAware.trySetCamelContext(getRouteCollection(), getCamelContext());
                        getRouteCollection().getOnExceptions().add((OnExceptionDefinition) item);
                    } else if (item instanceof ErrorHandlerBuilder) {
                        if (!getRouteCollection().getRoutes().isEmpty()) {
                            throw new IllegalArgumentException(
                                    "errorHandler must be defined before any routes in the RouteBuilder");
                        }
                        errorHandler((ErrorHandlerBuilder) item);
                    } else if (item instanceof RouteTemplateDefinition) {
                        CamelContextAware.trySetCamelContext(getRouteTemplateCollection(), getCamelContext());
                        getRouteTemplateCollection().routeTemplate((RouteTemplateDefinition) item);
                    } else if (item instanceof RestDefinition) {
                        RestDefinition definition = (RestDefinition) item;
                        for (VerbDefinition verb : definition.getVerbs()) {
                            verb.setRest(definition);
                        }
                        CamelContextAware.trySetCamelContext(getRestCollection(), getCamelContext());
                        getRestCollection().rest(definition);
                    } else if (item instanceof RestConfigurationDefinition) {
                        ((RestConfigurationDefinition) item).asRestConfiguration(
                                getCamelContext(),
                                getCamelContext().getRestConfiguration());
                    }
                }
            }

            @Override
            public void configuration() throws Exception {
                for (Node node : asSequenceNode(root).getValue()) {
                    Object item = getDeserializationContext().mandatoryResolve(node).construct(node);
                    if (item instanceof RouteConfigurationDefinition) {
                        CamelContextAware.trySetCamelContext(getRouteConfigurationCollection(), getCamelContext());
                        getRouteConfigurationCollection().routeConfiguration((RouteConfigurationDefinition) item);
                    }
                }
            }
        };
    }

}
