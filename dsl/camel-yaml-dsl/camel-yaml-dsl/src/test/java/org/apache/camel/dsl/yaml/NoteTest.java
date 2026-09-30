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
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NoteTest extends YamlTestSupport {

    Stream<Resource> resourceProvider() {
        return Stream.of(
                asResource("route", """
                        - route:
                            id: cheese
                            note: "some route note here"
                            description: "some route description here"
                            from:
                              uri: "direct:start"
                              steps:
                                - to:
                                    uri: "direct:start"
                                    note: "some note here"
                                    description: "some description here"
                        """),
                asResource("uri", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - to:
                                  uri: "direct:start"
                                  note: "some note here"
                                  description: "some description here"
                        """),
                asResource("properties", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - to:
                                  uri: "direct"
                                  note: "some note here"
                                  description: "some description here"
                                  parameters:
                                    name: "start"
                        """),
                asResource("properties-out-of-order", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - to:
                                  parameters:
                                    name: "start"
                                  note: "some note here"
                                  description: "some description here"
                                  uri: "direct"
                        """));
    }

    @ParameterizedTest
    @MethodSource("resourceProvider")
    void noteDefinition(Resource resource) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        var to = (ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(to.getEndpointUri()).isEqualTo("direct:start");
        assertThat(to.getDescription()).isEqualTo("some description here");
        assertThat(to.getNote()).isEqualTo("some note here");

        var route = (RouteDefinition) context.getRouteDefinitions().get(0);
        if ("cheese".equals(route.getId())) {
            assertThat(route.getDescription()).isEqualTo("some route description here");
            assertThat(route.getNote()).isEqualTo("some route note here");
        } else {
            assertThat(route.getDescription()).isNull();
            assertThat(route.getNote()).isNull();
        }
    }
}
