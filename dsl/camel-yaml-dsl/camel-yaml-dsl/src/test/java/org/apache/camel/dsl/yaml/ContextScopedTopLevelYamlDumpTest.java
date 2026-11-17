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

import java.util.List;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that context-scoped EIPs (errorHandler, intercept, interceptFrom, interceptSendToEndpoint, onCompletion) are
 * written as top-level entries before the routes in the YAML dump, and not duplicated in the steps of each route.
 */
class ContextScopedTopLevelYamlDumpTest extends YamlTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void topLevelInterceptIsDumpedAsTopLevel(boolean started) throws Exception {
        loadRoutes("""
                - intercept:
                    steps:
                      - log:
                          message: "intercepted"
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
                """);
        if (started) {
            context.start();
        }
        String yaml = dumpRoutes();
        if (started) {
            context.stop();
        }

        // written once as top-level, not in the steps of each route
        assertThat(yaml).startsWith("- intercept:");
        assertThat(yaml.split("intercept:", -1)).hasSize(2);
        assertThat(yaml).contains("id: first").contains("id: second");

        // the dump is valid for the YAML DSL schema and applies to all routes
        context.removeRouteDefinitions(List.copyOf(context.getRouteDefinitions()));
        loadRoutes(yaml);
        assertThat(context.getRouteDefinitions()).hasSize(2);
        for (String id : List.of("first", "second")) {
            assertThat(context.getRouteDefinition(id).getOutputs().get(0))
                    .isInstanceOf(InterceptDefinition.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void topLevelInterceptFromIsDumpedAsTopLevel(boolean started) throws Exception {
        loadRoutes("""
                - interceptFrom:
                    steps:
                      - log:
                          message: "intercepted from"
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
                """);
        if (started) {
            context.start();
        }
        String yaml = dumpRoutes();
        if (started) {
            context.stop();
        }

        // written once as top-level, not in the steps of each route
        assertThat(yaml).startsWith("- interceptFrom:");
        assertThat(yaml.split("interceptFrom:", -1)).hasSize(2);
        assertThat(yaml).contains("id: first").contains("id: second");

        // the dump is valid for the YAML DSL schema and applies to all routes
        context.removeRouteDefinitions(List.copyOf(context.getRouteDefinitions()));
        loadRoutes(yaml);
        assertThat(context.getRouteDefinitions()).hasSize(2);
        for (String id : List.of("first", "second")) {
            assertThat(context.getRouteDefinition(id).getOutputs().get(0))
                    .isInstanceOf(InterceptFromDefinition.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void topLevelInterceptSendToEndpointIsDumpedAsTopLevel(boolean started) throws Exception {
        loadRoutes("""
                - interceptSendToEndpoint:
                    uri: mock:*
                    steps:
                      - log:
                          message: "intercepted send"
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
                """);
        if (started) {
            context.start();
        }
        String yaml = dumpRoutes();
        if (started) {
            context.stop();
        }

        // written once as top-level, not in the steps of each route
        assertThat(yaml).startsWith("- interceptSendToEndpoint:");
        assertThat(yaml.split("interceptSendToEndpoint:", -1)).hasSize(2);
        assertThat(yaml).contains("id: first").contains("id: second");

        // the dump is valid for the YAML DSL schema and applies to all routes
        context.removeRouteDefinitions(List.copyOf(context.getRouteDefinitions()));
        loadRoutes(yaml);
        assertThat(context.getRouteDefinitions()).hasSize(2);
        for (String id : List.of("first", "second")) {
            assertThat(context.getRouteDefinition(id).getOutputs().get(0))
                    .isInstanceOf(InterceptSendToEndpointDefinition.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void topLevelOnCompletionIsDumpedAsTopLevel(boolean started) throws Exception {
        loadRoutes("""
                - onCompletion:
                    steps:
                      - log:
                          message: "completed"
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
                """);
        if (started) {
            context.start();
        }
        String yaml = dumpRoutes();
        if (started) {
            context.stop();
        }

        // written once as top-level, not in the steps of each route
        assertThat(yaml).startsWith("- onCompletion:");
        assertThat(yaml.split("onCompletion:", -1)).hasSize(2);
        assertThat(yaml).contains("id: first").contains("id: second");

        // the dump is valid for the YAML DSL schema and applies to all routes
        context.removeRouteDefinitions(List.copyOf(context.getRouteDefinitions()));
        loadRoutes(yaml);
        assertThat(context.getRouteDefinitions()).hasSize(2);
        for (String id : List.of("first", "second")) {
            assertThat(context.getRouteDefinition(id).getOutputs().get(0))
                    .isInstanceOf(OnCompletionDefinition.class);
        }
    }

    @Test
    void topLevelErrorHandlerIsDumpedAsTopLevel() throws Exception {
        loadRoutes("""
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: log:dead
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
                """);
        String yaml = dumpRoutes();

        // written once as top-level, not inside any route
        assertThat(yaml).startsWith("- errorHandler:");
        assertThat(yaml.split("errorHandler:", -1)).hasSize(2);
        assertThat(yaml).contains("id: first").contains("id: second");

        // the dump is valid for the YAML DSL schema and the deadLetter channel is active
        context.removeRouteDefinitions(List.copyOf(context.getRouteDefinitions()));
        loadRoutes(yaml);
        assertThat(context.getRouteDefinitions()).hasSize(2);
        assertThat(context.getCamelContextExtension().getErrorHandlerFactory())
                .isInstanceOf(DeadLetterChannelDefinition.class);
    }

    private String dumpRoutes() throws Exception {
        // as the routes are dumped by camel.main.dumpRoutes, which camel validate normalize uses
        RoutesDefinition routes = new RoutesDefinition();
        for (RouteDefinition route : context.getRouteDefinitions()) {
            routes.getRoutes().add(route);
        }
        return new LwModelToYAMLDumper().dumpModelAsYaml(context, routes, false, true, false, false);
    }
}
