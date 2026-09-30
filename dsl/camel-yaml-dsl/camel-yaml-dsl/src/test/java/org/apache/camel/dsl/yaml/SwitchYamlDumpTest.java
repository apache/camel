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

import java.util.Map;

import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SwitchYamlDumpTest extends YamlTestSupport {
    @ParameterizedTest
    @ValueSource(strings = { "001", "1.00", "1e2", "true" })
    void scalarLiteralsRemainStringsAfterDump(String value) throws Exception {
        RouteDefinition route = new RouteDefinition("direct:start").routeId("literal");
        route.doSwitch(new HeaderExpression("decision")).doCase(value, "mock:matched");
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
        loadRoutes(yaml);
        SwitchDefinition restored = (SwitchDefinition) context.getRouteDefinition("literal").getOutputs().get(0);
        assertThat(restored.getCases().get(0).getValue()).isEqualTo(value);
    }

    @Test
    void fluentSelectorSurvivesYamlDump() throws Exception {
        RouteDefinition route = new RouteDefinition().from("direct:start").routeId("fluent");
        SwitchDefinition sw = route.doSwitch().header("department").doCase("billing", "mock:billing");
        assertThat(sw.getSelector().getExpressionType().getExpressionValue()).isInstanceOf(ExpressionClause.class);
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
        loadRoutes(yaml);
        SwitchDefinition restored = (SwitchDefinition) context.getRouteDefinition("fluent").getOutputs().get(0);
        assertThat(restored.getSelector().getExpressionType().getExpression()).isEqualTo("department");
        assertThat(restored.getSelector().getExpressionType().getLanguage()).isEqualTo("header");
    }

    @Test
    void selectorNamespacesAndLiteralBranchesSurviveYamlDump() throws Exception {
        XPathExpression selector = new XPathExpression("/t:ticket/t:department/text()");
        selector.setNamespaces(Map.of("t", "urn:tickets"));
        RouteDefinition route = new RouteDefinition().from("direct:start").routeId("selector");
        route.doSwitch(selector).doCase("billing", "mock:billing").otherwise("mock:other");
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
        loadRoutes(yaml);
        SwitchDefinition restored = (SwitchDefinition) context.getRouteDefinition("selector").getOutputs().get(0);
        XPathExpression xpath = (XPathExpression) restored.getSelector().getExpressionType();
        assertThat(xpath.getExpression()).isEqualTo("/t:ticket/t:department/text()");
        assertThat(xpath.getNamespaces()).containsEntry("t", "urn:tickets");
        assertThat(restored.getCases().get(0).getValue()).isEqualTo("billing");
        assertThat(restored.getCases().get(0).getUri()).isEqualTo("mock:billing");
        assertThat(restored.getOtherwise().getUri()).isEqualTo("mock:other");
        SwitchDefinition copy = restored.copyDefinition();
        copy.getOtherwise().setUri("mock:copy");
        assertThat(restored.getOtherwise().getUri()).isEqualTo("mock:other");
        assertThat(copy.getOtherwise().getUri()).isEqualTo("mock:copy");
    }
}
