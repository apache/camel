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
import org.apache.camel.model.SetVariableDefinition;
import org.apache.camel.model.SetVariablesDefinition;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetVariablesTest extends YamlTestSupport {

    @Test
    void setVariablesDefinition() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - setVariables:
                          variables:
                            - name: testbody
                              simple: "${body}"
                            - name: testconstant
                              constant: ABC
                      - to: "mock:result"
                """);

        var setVariables = (SetVariablesDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(setVariables).isNotNull();

        var var0 = (SetVariableDefinition) setVariables.getVariables().get(0);
        assertThat(var0.getName()).isEqualTo("testbody");
        assertThat(var0.getExpression().getLanguage()).isEqualTo("simple");
        assertThat(var0.getExpression().getExpression()).isEqualTo("${body}");

        var var1 = (SetVariableDefinition) setVariables.getVariables().get(1);
        assertThat(var1.getName()).isEqualTo("testconstant");
        assertThat(var1.getExpression().getLanguage()).isEqualTo("constant");
        assertThat(var1.getExpression().getExpression()).isEqualTo("ABC");
    }

    @Test
    void setVariablesResultType() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - setVariables:
                          variables:
                            - name: foo
                              simple: "${body}"
                            - name: bar
                              simple:
                                expression: "${variable.foo} > 10"
                                resultType: "boolean"
                      - to: "mock:result"
                """);

        var setVariables = (SetVariablesDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(setVariables).isNotNull();

        var var1 = (SetVariableDefinition) setVariables.getVariables().get(1);
        assertThat(var1.getName()).isEqualTo("bar");
        var simple = (SimpleExpression) var1.getExpression();
        assertThat(simple.getLanguage()).isEqualTo("simple");
        assertThat(simple.getExpression()).isEqualTo("${variable.foo} > 10");
        assertThat(simple.getResultTypeName()).isEqualTo("boolean");
    }
}
