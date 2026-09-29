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

import java.math.BigDecimal;
import java.util.Map;

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
    void compositeValuesAndEndpointParametersRoundTrip() throws Exception {
        loadRoutes("""
                - route:
                    id: decision
                    from:
                      uri: direct:start
                      steps:
                        - switch:
                            selector:
                              header:
                                expression: decision
                            keys: [department, urgent]
                            case:
                              - values: [department: billing, urgent: true]
                                uri: direct
                                parameters:
                                  name: urgent
                              - values: [urgent: false, department: billing]
                                uri: direct:billing
                            otherwise:
                              uri: direct:review
                - route:
                    from:
                      uri: direct:urgent
                      steps:
                        - setBody:
                            constant: urgent
                - route:
                    from:
                      uri: direct:billing
                      steps:
                        - setBody:
                            constant: billing
                - route:
                    from:
                      uri: direct:review
                      steps:
                        - setBody:
                            constant: review
                """);
        SwitchDefinition sw = (SwitchDefinition) context.getRouteDefinition("decision").getOutputs().get(0);
        assertThat(sw.getCases().get(0).getUri()).isEqualTo("direct:urgent");
        assertThat(sw.getCases().get(0).getValues().get(1).asLiteral()).isEqualTo(true);
        assertThat(sw.getOtherwise().getUri()).isEqualTo("direct:review");
        try (var restored = new DefaultCamelContext()) {
            for (var route : context.getRouteDefinitions()) {
                String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
                PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString(route.getId() + ".yaml", yaml));
            }
            restored.start();
            try (var template = restored.createProducerTemplate()) {
                assertThat(template.requestBodyAndHeader("direct:start", "original", "decision",
                        Map.of("department", "BILLING", "urgent", true))).isEqualTo("urgent");
                assertThat(template.requestBodyAndHeader("direct:start", "original", "decision",
                        Map.of("department", "billing", "urgent", false))).isEqualTo("billing");
                assertThat(template.requestBodyAndHeader("direct:start", "original", "decision",
                        Map.of("department", "billing", "urgent", "true"))).isEqualTo("review");
            }
        }
    }

    @Test
    void quotedScalarsRetainTheirTypesAfterDump() throws Exception {
        loadRoutes("""
                - route:
                    id: quoted
                    from:
                      uri: direct:start
                      steps:
                        - switch:
                            selector:
                              header: decision
                            keys: [urgent, score]
                            case:
                              - values: [urgent: 'true', score: '2']
                                uri: mock:string
                              - values: [urgent: true, score: 2.0]
                                uri: mock:boolean
                """);
        var route = context.getRouteDefinition("quoted");
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
        try (var restored = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString("restored.yaml", yaml));
            SwitchDefinition sw = (SwitchDefinition) restored.getRouteDefinition("quoted").getOutputs().get(0);
            assertThat(sw.getCases().get(0).getValues().get(0).asLiteral()).isEqualTo("true");
            assertThat(sw.getCases().get(1).getValues().get(0).asLiteral()).isEqualTo(true);
            assertThat(sw.getCases().get(0).getValues().get(1).asLiteral()).isEqualTo("2");
            assertThat(sw.getCases().get(1).getValues().get(1).asLiteral()).isInstanceOf(BigDecimal.class);
            assertThat((BigDecimal) sw.getCases().get(1).getValues().get(1).asLiteral()).isEqualByComparingTo("2.0");
        }
    }

    @Test
    void rejectsNonScalarOrMultipleBindingsPerEntry() {
        for (String values : new String[] { "[{urgent: true, department: billing}]", "[urgent: [true]]", "[urgent: null]" }) {
            String yaml = """
                    - route:
                        from:
                          uri: direct:start
                          steps:
                            - switch:
                                selector:
                                  header: decision
                                keys: [urgent]
                                case:
                                  - values: %s
                                    uri: mock:a
                    """.formatted(values);
            assertThatThrownBy(() -> loadRoutes(yaml)).isInstanceOf(Exception.class);
        }
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
