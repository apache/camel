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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.A2ASubTaskDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2ASubTaskYamlDslTest {

    @Test
    void loadsA2ASubTaskFromGeneratedModelDeserializer() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            loadRoutes(context, "a2a-sub-task.yaml", """
                    - route:
                        id: yaml-a2a-sub-task
                        from:
                          uri: "direct:start"
                          steps:
                            - a2aSubTask:
                                emitBefore: "Searching ${body}"
                                emitAfter: "Done ${body}"
                                emitOnError: "Failed ${exception.message}"
                                failIfNoTaskContext: true
                                steps:
                                  - setBody:
                                      simple: "${body}-found"
                                  - to:
                                      uri: "mock:result"
                    """);

            RouteDefinition route = context.getRouteDefinition("yaml-a2a-sub-task");
            assertThat(route.getOutputs()).hasSize(1);

            A2ASubTaskDefinition subTask = (A2ASubTaskDefinition) route.getOutputs().get(0);
            assertThat(subTask.getEmitBefore()).isEqualTo("Searching ${body}");
            assertThat(subTask.getEmitAfter()).isEqualTo("Done ${body}");
            assertThat(subTask.getEmitOnError()).isEqualTo("Failed ${exception.message}");
            assertThat(subTask.getFailIfNoTaskContext()).isEqualTo("true");
            assertThat(subTask.getOutputs()).hasSize(2);
            assertThat(subTask.getOutputs().get(0).getShortName()).isEqualTo("setBody");
            assertThat(subTask.getOutputs().get(1).getShortName()).isEqualTo("to");
        }
    }

    @Test
    void loadsA2ASubTaskFromRouteTemplate() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            loadRoutes(context, "a2a-sub-task-template.yaml", """
                    - routeTemplate:
                        id: yaml-a2a-template
                        from:
                          uri: "direct:{{routeName}}"
                          steps:
                            - a2aSubTask:
                                emitBefore: "Template ${body}"
                                steps:
                                  - to: "mock:template"
                    """);

            assertThat(context.getRouteTemplateDefinitions()).hasSize(1);

            RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinitions().get(0);
            assertThat(routeTemplate.getId()).isEqualTo("yaml-a2a-template");
            A2ASubTaskDefinition subTask = (A2ASubTaskDefinition) routeTemplate.getRoute().getOutputs().get(0);
            assertThat(subTask.getEmitBefore()).isEqualTo("Template ${body}");
            assertThat(subTask.getOutputs()).hasSize(1);
        }
    }

    @Test
    void loadsA2ASubTaskFromKameletTemplate() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            KameletRoutesBuilderLoader loader = new KameletRoutesBuilderLoader();
            loader.setCamelContext(context);
            loader.start();
            RoutesBuilder routesBuilder = loader.loadRoutesBuilder(ResourceHelper.fromString("a2a-progress.kamelet.yaml", """
                    apiVersion: camel.apache.org/v1
                    kind: Kamelet
                    metadata:
                      name: a2a-progress
                    spec:
                      definition:
                        title: "A2A Progress"
                        type: object
                      template:
                        from:
                          uri: "kamelet:source"
                          steps:
                            - a2aSubTask:
                                emitAfter: "Kamelet ${body}"
                                steps:
                                  - to: "mock:kamelet"
                    """));
            context.addRoutes(routesBuilder);

            assertThat(context.getRouteTemplateDefinitions()).hasSize(1);

            RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinitions().get(0);
            assertThat(routeTemplate.getId()).isEqualTo("a2a-progress");
            A2ASubTaskDefinition subTask = (A2ASubTaskDefinition) routeTemplate.getRoute().getOutputs().get(0);
            assertThat(subTask.getEmitAfter()).isEqualTo("Kamelet ${body}");
        }
    }

    @Test
    void loadsA2ASubTaskInsideRouteConfigurationOnException() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            loadRoutes(context, "a2a-sub-task-route-configuration.yaml", """
                    - routeConfiguration:
                        onException:
                          - onException:
                              exception:
                                - java.lang.IllegalArgumentException
                              steps:
                                - a2aSubTask:
                                    emitOnError: "Configured ${exception.message}"
                                    steps:
                                      - to: "mock:configured"
                    """);

            assertThat(context.getRouteConfigurationDefinitions()).hasSize(1);

            RouteConfigurationDefinition routeConfiguration = context.getRouteConfigurationDefinitions().get(0);
            A2ASubTaskDefinition subTask = (A2ASubTaskDefinition) routeConfiguration.getOnExceptions().get(0)
                    .getOutputs().get(0);
            assertThat(subTask.getEmitOnError()).isEqualTo("Configured ${exception.message}");
            assertThat(subTask.getOutputs()).hasSize(1);
        }
    }

    @Test
    void loadsA2ASubTaskNestedInsideSplit() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            loadRoutes(context, "a2a-sub-task-split.yaml", """
                    - route:
                        id: yaml-a2a-split
                        from:
                          uri: "direct:start"
                          steps:
                            - split:
                                tokenize: ","
                                steps:
                                  - a2aSubTask:
                                      emitBefore: "Split ${body}"
                                      steps:
                                        - to: "mock:split"
                    """);

            RouteDefinition route = context.getRouteDefinition("yaml-a2a-split");
            SplitDefinition split = (SplitDefinition) route.getOutputs().get(0);
            A2ASubTaskDefinition subTask = (A2ASubTaskDefinition) split.getOutputs().get(0);
            assertThat(subTask.getEmitBefore()).isEqualTo("Split ${body}");
            assertThat(subTask.getOutputs()).hasSize(1);
        }
    }

    @Test
    void loadsA2ASubTaskFromCompactYamlNotation() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            loadRoutes(context, "a2a-sub-task-compact.yaml",
                    """
                            - route:
                                id: yaml-a2a-compact
                                from:
                                  uri: "direct:start"
                                  steps:
                                    - a2aSubTask: { emitBefore: "Compact ${body}", steps: [ { setBody: { simple: "${body}-compact" } } ] }
                            """);

            RouteDefinition route = context.getRouteDefinition("yaml-a2a-compact");
            A2ASubTaskDefinition subTask = (A2ASubTaskDefinition) route.getOutputs().get(0);
            assertThat(subTask.getEmitBefore()).isEqualTo("Compact ${body}");
            assertThat(subTask.getOutputs()).hasSize(1);
            assertThat(subTask.getOutputs().get(0).getShortName()).isEqualTo("setBody");
        }
    }

    @Test
    void rejectsA2ASubTaskWithUnknownField() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            assertThatThrownBy(() -> loadRoutes(context, "a2a-sub-task-unknown-field.yaml", """
                    - route:
                        id: yaml-a2a-unknown-field
                        from:
                          uri: "direct:start"
                          steps:
                            - a2aSubTask:
                                emitBefore: "Before ${body}"
                                unknownField: true
                                steps:
                                  - to: "mock:result"
                    """))
                    .hasStackTraceContaining("unknownField");
        }
    }

    @Test
    void rejectsA2ASubTaskWithInvalidStepsType() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            assertThatThrownBy(() -> loadRoutes(context, "a2a-sub-task-invalid-steps.yaml", """
                    - route:
                        id: yaml-a2a-invalid-steps
                        from:
                          uri: "direct:start"
                          steps:
                            - a2aSubTask:
                                emitBefore: "Before ${body}"
                                steps: "not-a-list"
                    """))
                    .hasMessageContaining("steps");
        }
    }

    @Test
    void failsAtStartupWhenA2ARuntimeIsAbsent() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            loadRoutes(context, "a2a-sub-task-runtime-absent.yaml", """
                    - route:
                        id: yaml-a2a-runtime-absent
                        from:
                          uri: "direct:start"
                          steps:
                            - a2aSubTask:
                                emitBefore: "Before ${body}"
                                steps:
                                  - to: "mock:result"
                    """);

            assertThatThrownBy(context::start)
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot find camel-a2a on the classpath");
        }
    }

    private static void loadRoutes(DefaultCamelContext context, String name, String yaml) throws Exception {
        YamlRoutesBuilderLoader loader = new YamlRoutesBuilderLoader();
        loader.setCamelContext(context);
        RoutesBuilder routesBuilder = loader.loadRoutesBuilder(ResourceHelper.fromString(name, yaml));
        context.addRoutes(routesBuilder);
    }
}
