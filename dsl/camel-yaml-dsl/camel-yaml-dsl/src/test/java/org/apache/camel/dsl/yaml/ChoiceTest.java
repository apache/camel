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
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.WhenDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChoiceTest extends YamlTestSupport {

    @Test
    void choiceDefinition() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - choice:
                          when:
                            - simple: "${body.size()} == 1"
                              steps:
                                - to: "log:when-a"
                            - expression:
                                simple: "${body.size()} == 2"
                              steps:
                                - to: "log:when-b"
                          otherwise:
                            steps:
                              - to: "log:otherwise"
                """);
        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);
        var choice = (ChoiceDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        var when0 = (WhenDefinition) choice.getWhenClauses().get(0);
        assertThat(when0.getExpression().getLanguage()).isEqualTo("simple");
        assertThat(when0.getExpression().getExpression()).isEqualTo("${body.size()} == 1");
        assertThat(((ToDefinition) when0.getOutputs().get(0)).getEndpointUri()).isEqualTo("log:when-a");
        var when1 = (WhenDefinition) choice.getWhenClauses().get(1);
        assertThat(when1.getExpression().getLanguage()).isEqualTo("simple");
        assertThat(when1.getExpression().getExpression()).isEqualTo("${body.size()} == 2");
        assertThat(((ToDefinition) when1.getOutputs().get(0)).getEndpointUri()).isEqualTo("log:when-b");
        assertThat(((ToDefinition) choice.getOtherwise().getOutputs().get(0)).getEndpointUri()).isEqualTo("log:otherwise");
    }

    @Test
    void choiceInPreconditionMode() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - choice:
                          precondition: true
                          when:
                            - simple: "{{?red}}"
                              steps:
                                - to: "mock:red"
                            - simple: "{{?blue}}"
                              steps:
                                - to: "mock:blue"
                          otherwise:
                            steps:
                              - to: "mock:other"
                """);
        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);
        var choice = (ChoiceDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(((WhenDefinition) choice.getWhenClauses().get(0)).getExpression().getExpression()).isEqualTo("{{?red}}");
        assertThat(((ToDefinition) ((WhenDefinition) choice.getWhenClauses().get(0)).getOutputs().get(0)).getEndpointUri())
                .isEqualTo("mock:red");
        assertThat(((ToDefinition) choice.getOtherwise().getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:other");
    }
}
