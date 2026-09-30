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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java routes without an id are matched to the routes Camel runs by the endpoint they consume from, as in the Spring
 * Boot widget-gadget example (CAMEL-25148).
 */
class RouteKeysTest {

    private static final String ORDER = """
            public class OrderRoute extends RouteBuilder {
                public void configure() throws Exception {
                    from("file:src/main/data?noop=true")
                            .to("amqp:queue:order.queue");
                }
            }
            """;

    private static final String WIDGET_GADGET = """
            public class WidgetGadgetRoute extends RouteBuilder {
                public void configure() throws Exception {
                    from("amqp:queue:order.queue")
                            .choice()
                                .when().jsonpath("$.order[?(@.product=='widget')]")
                                    .to("amqp:queue:widget.queue")
                                .otherwise()
                                    .to("amqp:queue:gadget.queue");
                }
            }
            """;

    @Test
    void routesWithoutIdsGetTheIdsCamelGaveThem() {
        ProjectOverview.Overview o = ProjectOverview.analyze(Path.of("widget-gadget"), Map.of(
                "src/main/java/sample/camel/OrderRoute.java", ORDER,
                "src/main/java/sample/camel/WidgetGadgetRoute.java", WIDGET_GADGET), new DefaultCamelCatalog());
        List<RouteKeys.Running> running = List.of(
                new RouteKeys.Running("route1", "file://src/main/data?noop=true"),
                new RouteKeys.Running("route2", "amqp://queue:order.queue"));

        Map<String, String> ids = RouteKeys.match(o, running, new DefaultCamelCatalog());
        assertThat(ids).containsOnly(
                Map.entry("src/main/java/sample/camel/OrderRoute.java:3", "route1"),
                Map.entry("src/main/java/sample/camel/WidgetGadgetRoute.java:3", "route2"));
    }

    @Test
    void aMatchMustBeTheOnlyOne() {
        ProjectOverview.Overview o = ProjectOverview.analyze(Path.of("x"), Map.of("A.java", ORDER),
                new DefaultCamelCatalog());
        List<RouteKeys.Running> twice = List.of(
                new RouteKeys.Running("route1", "file:src/main/data"),
                new RouteKeys.Running("route9", "file:src/main/data?delay=5"));
        assertThat(RouteKeys.match(o, twice, new DefaultCamelCatalog())).isEmpty();
    }

    @Test
    void shortNames() {
        assertThat(RouteKeys.shortKey("src/main/java/sample/camel/OrderRoute.java:32")).isEqualTo("OrderRoute.java:32");
        assertThat(RouteKeys.shortKey("order-intake")).isEqualTo("order-intake");
        assertThat(RouteKeys.shortKey("POST /api/orders")).isEqualTo("POST /api/orders");
    }
}
