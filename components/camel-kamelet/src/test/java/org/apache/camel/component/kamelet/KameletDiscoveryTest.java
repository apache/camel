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
package org.apache.camel.component.kamelet;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.RoutesBuilderLoaderSupport;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KameletDiscoveryTest extends CamelTestSupport {

    @Test
    public void kameletCanBeDiscovered() throws Exception {
        context.getRegistry().bind(
                DefaultRoutesLoader.ROUTES_LOADER_KEY_PREFIX + "kamelet.yaml",
                new RoutesBuilderLoaderSupport() {
                    @Override
                    public String getSupportedExtension() {
                        return "kamelet.yaml";
                    }

                    @Override
                    public RoutesBuilder loadRoutesBuilder(Resource resource) {
                        return new RouteBuilder() {
                            @Override
                            public void configure() {
                                routeTemplate("mySetBody")
                                        .from("kamelet:source")
                                        .setBody().constant("discovered");

                            }
                        };
                    }
                });

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:discovery")
                        .toF("kamelet:mySetBody");
            }
        });

        assertThat(fluentTemplate.to("direct:discovery").request(String.class)).isEqualTo("discovered");
    }

    @Test
    public void kameletNotFound() {
        context.getRegistry().bind(
                DefaultRoutesLoader.ROUTES_LOADER_KEY_PREFIX + "kamelet.yaml",
                new RoutesBuilderLoaderSupport() {
                    @Override
                    public String getSupportedExtension() {
                        return "kamelet.yaml";
                    }

                    @Override
                    public RoutesBuilder loadRoutesBuilder(Resource resource) {
                        return new RouteBuilder() {
                            @Override
                            public void configure() {
                            }
                        };
                    }
                });

        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:discovery")
                        .toF("kamelet:mySetBody");
            }
        };

        assertThatThrownBy(() -> context.addRoutes(builder))
                .isInstanceOf(FailedToCreateRouteException.class)
                .hasRootCauseMessage("Cannot find RouteTemplate with id mySetBody");
    }
}
