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
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.language.JqExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SagaTest extends YamlTestSupport {

    Stream<Resource> resourceProvider() {
        return Stream.of(
                asResource("full-parameters-id", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - saga:
                                 propagation: "MANDATORY"
                                 completionMode: "MANUAL"
                                 compensation: direct:compensation
                                 completion: direct:completion
                                 steps:
                                   - to: "direct:something"
                                 option:
                                   - key: o1
                                     jq: ".foo"
                                   - key: o2
                                     expression:
                                       simple:
                                         id: key2
                                         expression: "${body}"
                              - to: "mock:result"
                        """),
                asResource("short", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - saga:
                                 propagation: "MANDATORY"
                                 completionMode: "MANUAL"
                                 compensation: "direct:compensation"
                                 completion: "direct:completion"
                                 steps:
                                   - to: "direct:something"
                                 option:
                                   - key: o1
                                     jq: ".foo"
                                   - key: o2
                                     expression:
                                       simple: "${body}"
                              - to: "mock:result"
                        """));
    }

    @ParameterizedTest
    @MethodSource("resourceProvider")
    void saga(Resource resource) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        var saga = (SagaDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(saga.getPropagation()).isEqualTo("MANDATORY");
        assertThat(saga.getCompletionMode()).isEqualTo("MANUAL");
        assertThat(saga.getCompensation()).isEqualTo("direct:compensation");
        assertThat(saga.getCompletion()).isEqualTo("direct:completion");

        // saga spans the entire route so steps are inserted before any saga specific step
        // https://issues.apache.org/jira/browse/CAMEL-17129
        assertThat(((ToDefinition) saga.getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:result");
        assertThat(((ToDefinition) saga.getOutputs().get(1)).getEndpointUri()).isEqualTo("direct:something");

        assertThat(saga.getOptions().size()).isEqualTo(2);
        assertThat(saga.getOptions().get(0).getKey()).isEqualTo("o1");
        assertThat(saga.getOptions().get(0).getExpression()).isInstanceOf(JqExpression.class);
        assertThat(saga.getOptions().get(0).getExpression().getExpression()).isEqualTo(".foo");
        assertThat(saga.getOptions().get(1).getKey()).isEqualTo("o2");
        assertThat(saga.getOptions().get(1).getExpression()).isInstanceOf(SimpleExpression.class);
        assertThat(saga.getOptions().get(1).getExpression().getExpression()).isEqualTo("${body}");
    }
}
