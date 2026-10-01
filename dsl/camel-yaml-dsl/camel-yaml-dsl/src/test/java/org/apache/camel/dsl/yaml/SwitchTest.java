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

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwitchTest extends YamlTestSupport {
    @Test
    void resourceWithScalarValuesAndEndpointParametersRoundTrips() throws Exception {
        loadRoutes(ResourceHelper.resolveResource(context, "classpath:switch.camel.yaml"));
        SwitchDefinition sw = (SwitchDefinition) context.getRouteDefinition("decision").getOutputs().get(0);
        assertThat(sw.getCases().get(0).getUri()).isEqualTo("direct:urgent");
        assertThat(sw.getCases().get(0).getId()).isEqualTo("urgentCase");
        assertThat(sw.getCases().get(0).getValue()).isEqualTo("urgent");
        assertThat(sw.getOtherwise().getUri()).isEqualTo("direct:review");
        try (var restored = new DefaultCamelContext()) {
            for (var route : context.getRouteDefinitions()) {
                String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
                PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString(route.getId() + ".yaml", yaml));
            }
            SwitchDefinition restoredSwitch = (SwitchDefinition) restored.getRouteDefinition("decision").getOutputs().get(0);
            assertThat(restoredSwitch.getCases().get(0).getId()).isEqualTo("urgentCase");
            restored.start();
            try (var template = restored.createProducerTemplate()) {
                assertThat(template.requestBodyAndHeader("direct:start", "original", "decision",
                        "URGENT")).isEqualTo("urgent");
                assertThat(template.requestBodyAndHeader("direct:start", "original", "decision",
                        "BILLING")).isEqualTo("billing");
                assertThat(template.requestBodyAndHeader("direct:start", "original", "decision",
                        "other")).isEqualTo("review");
                assertThat(template.requestBody("direct:start", "original")).isEqualTo("review");
            }
        }
    }

    @Test
    void rejectsCompositeConfiguration() throws Exception {
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - switch:
                            selector:
                              header: decision
                            case:
                              - value: billing
                                uri: mock:billing
                """;
        loadRoutes(yaml);
        assertThatThrownBy(() -> loadRoutesNoValidate(yaml.replace("case:", "keys: [department, urgent]\n            case:")))
                .hasStackTraceContaining("unsupported field: keys");
        assertThatThrownBy(() -> loadRoutesNoValidate(yaml.replace("value: billing", "values: [department: billing]")))
                .hasStackTraceContaining("unsupported field: values");
    }

    @Test
    void fallbackRequiresAnEndpointObject() {
        for (String fallback : new String[] { "direct:review", "{steps: [{to: {uri: direct:review}}]}" }) {
            String yaml = """
                    - route:
                        from:
                          uri: direct:start
                          steps:
                            - switch:
                                selector:
                                  header:
                                    expression: department
                                case:
                                  - value: billing
                                    uri: direct:billing
                                otherwise: %s
                    """.formatted(fallback);
            assertThatThrownBy(() -> loadRoutes(yaml)).isInstanceOf(Exception.class);
        }
    }

    @Test
    void toDStillAcceptsParametersAndRoundTrips() throws Exception {
        loadRoutes("""
                - route:
                    id: dynamic
                    from:
                      uri: direct:start
                      steps:
                        - toD:
                            uri: direct
                            parameters:
                              name: "${header.destination}"
                """);
        var route = context.getRouteDefinition("dynamic");
        assertThat(((ToDynamicDefinition) route.getOutputs().get(0)).getUri()).isEqualTo("direct:${header.destination}");
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
        try (var restored = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString("restored.yaml", yaml));
            assertThat(((ToDynamicDefinition) restored.getRouteDefinition("dynamic").getOutputs().get(0)).getUri())
                    .isEqualTo("direct:${header.destination}");
        }
    }
}
