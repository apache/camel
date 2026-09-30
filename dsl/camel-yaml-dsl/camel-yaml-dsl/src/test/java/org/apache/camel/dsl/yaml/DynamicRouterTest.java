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
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicRouterTest extends YamlTestSupport {

    static Stream<Resource> resourceProvider() {
        return Stream.of(
                asResource("expression", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - dynamicRouter:
                                  simple: "${body}"
                              - to: "mock:result"
                        """),
                asResource("expression-block", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - dynamicRouter:
                                  expression:
                                    simple: "${body}"
                              - to: "mock:result"
                        """));
    }

    @ParameterizedTest
    @MethodSource("resourceProvider")
    void dynamicRouterDefinition(Resource resource) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);
        var dr = (DynamicRouterDefinition<?>) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(dr.getExpression().getLanguage()).isEqualTo("simple");
        assertThat(dr.getExpression().getExpression()).isEqualTo("${body}");
    }
}
