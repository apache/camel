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
import org.apache.camel.model.language.XPathExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XPathNamespaceTest extends YamlTestSupport {

    @Test
    void xpathNamespace() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - choice:
                          when:
                            - expression:
                                xpath:
                                  expression: "/c:number = 55"
                                  namespace:
                                    - key: 'c'
                                      value: 'http://acme.com/cheese'
                                    - key: 'w'
                                      value: 'http://acme.com/wine'
                              steps:
                                - to: "mock:55"
                          otherwise:
                            steps:
                              - to: "mock:other"
                """);
        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        var choice = (ChoiceDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        var when = (WhenDefinition) choice.getWhenClauses().get(0);
        var xpath = (XPathExpression) when.getExpression();
        assertThat(xpath.getExpression()).isEqualTo("/c:number = 55");
        assertThat(xpath.getNamespace().size()).isEqualTo(2);
        assertThat(xpath.getNamespace().get(0).getKey()).isEqualTo("c");
        assertThat(xpath.getNamespace().get(0).getValue()).isEqualTo("http://acme.com/cheese");
        assertThat(xpath.getNamespace().get(1).getKey()).isEqualTo("w");
        assertThat(xpath.getNamespace().get(1).getValue()).isEqualTo("http://acme.com/wine");
        assertThat(((ToDefinition) when.getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:55");
        assertThat(((ToDefinition) choice.getOtherwise().getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:other");
    }
}
