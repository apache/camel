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

import org.apache.camel.FailedToCreateRouteFromTemplateException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The route of a Kamelet endpoint with a route id must not replace an existing route that was not created from the same
 * Kamelet, while the route of the same Kamelet is created again when its parent route is updated.
 */
class KameletExistingRouteIdTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void routeIdOfRegularRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .templateParameter("prefix")
                        .from("kamelet:source")
                        .setBody().simple("{{prefix}}-${body}");

                from("direct:existing").routeId("existing")
                        .setBody().constant("existing");
            }
        });
        context.start();

        assertThatThrownBy(() -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:b").routeId("b").to("kamelet:echo/existing?prefix=b");
            }
        })).hasRootCauseInstanceOf(FailedToCreateRouteFromTemplateException.class)
                .satisfies(e -> assertThat(ObjectHelper.getException(FailedToCreateKameletException.class, e)).isNotNull());

        // the regular route is kept
        assertThat(context.getRoute("existing").getEndpoint().getEndpointUri()).isEqualTo("direct://existing");
        assertThat(template.requestBody("direct:existing", "x")).isEqualTo("existing");
    }

    @Test
    void routeIdOfRouteFromOtherKamelet() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .templateParameter("prefix")
                        .from("kamelet:source")
                        .setBody().simple("{{prefix}}-${body}");

                routeTemplate("shout")
                        .templateParameter("prefix")
                        .from("kamelet:source")
                        .setBody().simple("{{prefix}}-${body.toUpperCase()}");
            }
        });
        context.start();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("kamelet:echo/myId?prefix=a");
            }
        });
        assertThat(template.requestBody("direct:a", "x")).isEqualTo("a-x");

        assertThatThrownBy(() -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:b").routeId("b").to("kamelet:shout/myId?prefix=b");
            }
        })).hasRootCauseInstanceOf(FailedToCreateRouteFromTemplateException.class)
                .satisfies(e -> assertThat(ObjectHelper.getException(FailedToCreateKameletException.class, e)).isNotNull());

        // the route of the first Kamelet endpoint is kept
        assertThat(template.requestBody("direct:a", "x")).isEqualTo("a-x");
    }

    @Test
    void updateParent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .templateParameter("prefix")
                        .from("kamelet:source")
                        .setBody().simple("{{prefix}}-${body}");

                from("direct:a").routeId("a").to("kamelet:echo/myId?prefix=a");
            }
        });
        context.start();
        assertThat(template.requestBody("direct:a", "x")).isEqualTo("a-x");

        // as a route reload with removeAllRoutes=false does
        new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("kamelet:echo/myId?prefix=b");
            }
        }.updateRoutesToCamelContext(context);
        assertThat(template.requestBody("direct:a", "x")).isEqualTo("b-x");
    }

    @Test
    void removeAndReAddParent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .templateParameter("prefix")
                        .from("kamelet:source")
                        .setBody().simple("{{prefix}}-${body}");
            }
        });
        context.start();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("kamelet:echo/myId?prefix=a");
            }
        });
        assertThat(template.requestBody("direct:a", "x")).isEqualTo("a-x");

        context.getRouteController().stopRoute("a");
        context.removeRoute("a");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a").to("kamelet:echo/myId?prefix=b");
            }
        });
        assertThat(template.requestBody("direct:a", "x")).isEqualTo("b-x");
    }
}
