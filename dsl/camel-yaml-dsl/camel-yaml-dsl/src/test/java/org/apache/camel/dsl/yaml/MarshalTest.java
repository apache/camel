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

import java.util.stream.Stream;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class MarshalTest extends YamlTestSupport {

    static Stream<Arguments> marshalDefinition() {
        return Stream.of(
                Arguments.of(asResource("data-format", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - marshal:
                                 json:
                                   library: Gson
                              - to: "mock:result"
                        """), "gson"),
                Arguments.of(asResource("data-format-block", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - marshal:
                                 dataFormatType:
                                   json:
                                     library: Gson
                              - to: "mock:result"
                        """), "gson"),
                Arguments.of(asResource("data-format", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - marshal:
                                 json: {}
                              - to: "mock:result"
                        """), "jackson"),
                Arguments.of(asResource("data-format-block", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - marshal:
                                 dataFormatType:
                                   json: {}
                              - to: "mock:result"
                        """), "jackson"),
                Arguments.of(asResource("data-format-library-case", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - marshal:
                                 json:
                                   library: gson
                              - to: "mock:result"
                        """), "gson"));
    }

    @ParameterizedTest
    @MethodSource("marshalDefinition")
    void marshalDefinition(Resource resource, String expected) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        assertThat(context.getRouteDefinitions().get(0).getOutputs().get(0)).isInstanceOf(MarshalDefinition.class);
        MarshalDefinition marshal = (MarshalDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(marshal.getDataFormatType().getDataFormatName()).isEqualTo(expected);
    }

    @Test
    void errorDuplicateDataformat() throws Exception {
        var route = """
                    - from:
                        uri: "direct:start"
                        steps:
                          - marshal:
                             json:
                               library: Gson
                             jacksonXml: {}
                          - to: "mock:result"
                """;
        try {
            loadRoutes(route);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage().contains("2 are valid")).as(e.getMessage()).isTrue();
        }
    }

    @Test
    void errorNoDataformat() throws Exception {
        var route = """
                    - from:
                        uri: "direct:start"
                        steps:
                          - marshal: {}
                          - to: "mock:result"
                """;
        try {
            loadRoutes(route);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage().contains("0 are valid")).as(e.getMessage()).isTrue();
        }
    }
}
