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

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChoiceSelectorYamlDumpTest extends YamlTestSupport {
    @Test
    void selectorNamespacesAndLiteralBranchesSurviveYamlDump() throws Exception {
        XPathExpression selector = new XPathExpression("/t:ticket/t:department/text()");
        selector.setNamespaces(Map.of("t", "urn:tickets"));
        RouteDefinition route = new RouteDefinition().from("direct:start").routeId("selector");
        route.choice(selector).when("billing").to("mock:billing").otherwise().to("mock:other");
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
        loadRoutes(yaml);
        ChoiceDefinition restored = (ChoiceDefinition) context.getRouteDefinition("selector").getOutputs().get(0);
        XPathExpression xpath = (XPathExpression) restored.getSelector().getExpressionType();
        assertThat(xpath.getExpression()).isEqualTo("/t:ticket/t:department/text()");
        assertThat(xpath.getNamespaces()).containsEntry("t", "urn:tickets");
        assertThat(restored.getWhenClauses().get(0).getValue()).isEqualTo("billing");
        assertThat(restored.getWhenClauses().get(0).getExpression()).isNull();
    }
}
