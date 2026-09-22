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
import java.util.stream.Stream;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnmarshalTest extends YamlTestSupport {

    static Stream<Arguments> unmarshalDefinition() {
        return Stream.of(
                Arguments.of(asResource("data-format", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - unmarshal:
                                 json:
                                   library: Gson
                              - to: "mock:result"
                        """), "gson"),
                Arguments.of(asResource("data-format-block", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - unmarshal:
                                 data-format-type:
                                   json:
                                     library: Gson
                              - to: "mock:result"
                        """), "gson"),
                Arguments.of(asResource("data-format", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - unmarshal:
                                 json: {}
                              - to: "mock:result"
                        """), "jackson"),
                Arguments.of(asResource("data-format-block", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - unmarshal:
                                 data-format-type:
                                   json: {}
                              - to: "mock:result"
                        """), "jackson"),
                Arguments.of(asResource("data-format-xml", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - unmarshal:
                                 jacksonXml: {}
                              - to: "mock:result"
                        """), "jacksonXml"));
    }

    @ParameterizedTest
    @MethodSource("unmarshalDefinition")
    void unmarshalDefinition(Resource resource, String expected) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        assertThat(context.getRouteDefinitions().get(0).getOutputs().get(0)).isInstanceOf(UnmarshalDefinition.class);
        UnmarshalDefinition unmarshal = (UnmarshalDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(unmarshal.getDataFormatType().getDataFormatName()).isEqualTo(expected);
    }

    static Stream<Arguments> unmarshalDefinitionWithAllowNullBody() {
        return Stream.of(
                Arguments.of(asResource("allow-null-body-set-to-true", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - unmarshal:
                                 allow-null-body: true
                                 json:
                                   library: Gson
                              - to: "mock:result"
                        """), "true"),
                Arguments.of(asResource("allow-null-body-set-to-false", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - unmarshal:
                                 allow-null-body: false
                                 json:
                                   library: Gson
                              - to: "mock:result"
                        """), "false"),
                Arguments.of(asResource("allow-null-body-not-set", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - unmarshal:
                                 json:
                                   library: Gson
                              - to: "mock:result"
                        """), null));
    }

    @ParameterizedTest
    @MethodSource("unmarshalDefinitionWithAllowNullBody")
    void unmarshalDefinitionWithAllowNullBody(Resource resource, String expected) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        assertThat(context.getRouteDefinitions().get(0).getOutputs().get(0)).isInstanceOf(UnmarshalDefinition.class);
        UnmarshalDefinition unmarshal = (UnmarshalDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(unmarshal.getAllowNullBody()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            unmarshal | jackson      | the data format is json, Jackson is its library: write json: {library: Jackson}
            unmarshal | json-jackson | write json: {library: Jackson}
            unmarshal | gson         | write json: {library: Gson}
            unmarshal | bindy-csv    | the data format is bindy, Csv is its type: write bindy: {type: Csv}
            unmarshal | snake-yaml   | the data format is yaml: write yaml: {...}
            unmarshal | JSON         | did you mean 'json'?
            marshal   | jackson      | the data format is json, Jackson is its library: write json: {library: Jackson}
            marshal   | JSON         | did you mean 'json'?
            """)
    void eipWithKeyFailsWithMessageNamingDataFormatKey(String eip, String key, String hint) {
        String yaml = """
                    - from:
                        uri: timer:tick
                        steps:
                          - %s:
                              %s: {}
                """.formatted(eip, key);

        Exception e = assertThrows(Exception.class,
                () -> loadRoutes(List.of(ResourceHelper.fromString("route-1.yaml", yaml)), false));

        boolean found = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null
                    && t.getMessage().contains("Error constructing YAML node id: " + eip + ": unsupported field: " + key)
                    && t.getMessage().contains(hint)) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }
}
