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
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TryTest extends YamlTestSupport {

    @Test
    void doTry() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - doTry:
                         steps:
                           - to: "log:when-a"
                           - to: "log:when-b"
                         doCatch:
                           - exception:
                               - "java.io.FileNotFoundException"
                               - "java.io.IOException"
                             steps:
                               - to: "log:io-error"
                """);
        var route = (RouteDefinition) context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:start");
        var tryDef = (TryDefinition) route.getOutputs().get(0);
        assertThat(tryDef.getCatchClauses().size()).isEqualTo(1);
        assertThat(tryDef.getCatchClauses().get(0).getOutputs().size()).isEqualTo(1);
        assertThat(tryDef.getCatchClauses().get(0).getExceptions().contains("java.io.FileNotFoundException")).isTrue();
        assertThat(tryDef.getCatchClauses().get(0).getExceptions().contains("java.io.IOException")).isTrue();
        assertThat(tryDef.getFinallyClause()).isNull();
    }

    @Test
    void doTryWithOnWhen() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - doTry:
                         steps:
                           - to: "log:when-a"
                         doCatch:
                           - exception:
                               - "java.io.FileNotFoundException"
                             onWhen:
                               simple: "${body.size()} == 1"
                             steps:
                               - to: "log:io-error"
                """);
        var tryDef = (TryDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        var onWhenExpr = (SimpleExpression) tryDef.getCatchClauses().get(0).getOnWhen().getExpression();
        assertThat(onWhenExpr.getExpression()).isEqualTo("${body.size()} == 1");
        assertThat(tryDef.getFinallyClause()).isNull();
    }

    @Test
    void doTryWithDoWhenAndDoFinally() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - doTry:
                         steps:
                           - to: "log:when-a"
                         doCatch:
                           - exception:
                               - "java.io.FileNotFoundException"
                             onWhen:
                               simple: "${body.size()} == 1"
                             steps:
                               - to: "log:io-error"
                         doFinally:
                           steps:
                             - to: "log:finally"
                """);
        var tryDef = (TryDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(tryDef.getCatchClauses().size()).isEqualTo(1);
        assertThat(tryDef.getFinallyClause()).isNotNull();
        assertThat(tryDef.getFinallyClause().getOutputs().size()).isEqualTo(1);
    }

    @Test
    void doTryWithDoFinally() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - doTry:
                         steps:
                           - to: "log:when-a"
                         doFinally:
                           steps:
                             - to: "log:finally"
                """);
        var tryDef = (TryDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(tryDef.getFinallyClause().getOutputs().size()).isEqualTo(1);
    }
}
