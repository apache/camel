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
import org.apache.camel.model.PollDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PollTest extends YamlTestSupport {

    Stream<Resource> resourceProvider() {
        return Stream.of(
                asResource("uri", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - poll:
                                  uri: "seda:foo"
                              - to: "mock:result"
                        """),
                asResource("timeout", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - poll:
                                  uri: "seda:foo"
                                  timeout: 1234
                              - to: "mock:result"
                        """),
                asResource("properties", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - poll:
                                  uri: "seda"
                                  parameters:
                                    name: "foo"
                              - to: "mock:result"
                        """));
    }

    @ParameterizedTest
    @MethodSource("resourceProvider")
    void pollDefinition(Resource resource) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        var poll = (PollDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(poll.getEndpointUri()).isEqualTo("seda:foo");
    }
}
