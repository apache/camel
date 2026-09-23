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
package org.apache.camel.dsl.xml.io;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlChoiceSelectorTest {
    @Test
    void selectorNamespacesSurviveModelDump() throws Exception {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/spring" xmlns:t="urn:tickets">
                  <route id="selector">
                    <from uri="direct:start"/>
                    <choice>
                      <selector><xpath resultType="java.lang.String">string(/t:ticket/t:department)</xpath></selector>
                      <when value="billing"><to uri="mock:billing"/></when>
                    </choice>
                  </route>
                </routes>
                """;
        try (var context = new DefaultCamelContext(); var restored = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("selector.xml", xml));
            String roundTrip = PluginHelper.getModelToXMLDumper(context)
                    .dumpModelAsXml(context, context.getRouteDefinition("selector"));
            PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString("restored.xml", roundTrip));
            ChoiceDefinition choice = (ChoiceDefinition) restored.getRouteDefinition("selector").getOutputs().get(0);
            assertEquals("urn:tickets", ((XPathExpression) choice.getSelector().getExpressionType()).getNamespaces().get("t"));
        }
    }

    @Test
    void parsesSerializesAndExecutesSelectorChoices() throws Exception {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/spring">
                  <route id="selector">
                    <from uri="direct:start"/>
                    <choice>
                      <selector><header>department</header></selector>
                      <when value="billing"><setBody><constant>matched</constant></setBody></when>
                      <otherwise><setBody><constant>unmatched</constant></setBody></otherwise>
                    </choice>
                  </route>
                </routes>
                """;
        try (var context = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("selector.xml", xml));
            var route = context.getRouteDefinition("selector");
            ChoiceDefinition choice = (ChoiceDefinition) route.getOutputs().get(0);
            assertEquals("department", choice.getSelector().getExpressionType().getExpression());
            assertEquals("billing", choice.getWhenClauses().get(0).getValue());
            String roundTrip = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, route);
            try (var restored = new DefaultCamelContext()) {
                PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString("restored.xml", roundTrip));
                restored.start();
                try (var template = restored.createProducerTemplate()) {
                    assertEquals("matched", template.requestBodyAndHeader("direct:start", "original", "department", "billing"));
                    assertEquals("unmatched", template.requestBody("direct:start", "original"));
                }
            }
        }
    }
}
