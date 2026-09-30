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
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlSwitchTest {
    @Test
    void fluentSelectorSurvivesDumpBeforeStartup() throws Exception {
        try (var context = new DefaultCamelContext()) {
            RouteDefinition route = new RouteDefinition("direct:start").routeId("fluent");
            route.doSwitch().header("department").otherwise("mock:other");
            String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, route);
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("fluent.xml", xml));
            SwitchDefinition sw = (SwitchDefinition) context.getRouteDefinition("fluent").getOutputs().get(0);
            assertEquals("header", sw.getSelector().getExpressionType().getLanguage());
            assertEquals("department", sw.getSelector().getExpressionType().getExpression());
        }
    }

    @Test
    void selectorNamespacesSurviveModelDump() throws Exception {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/spring" xmlns:t="urn:tickets">
                  <route id="selector">
                    <from uri="direct:start"/>
                    <switch>
                      <selector><xpath resultType="java.lang.String">string(/t:ticket/t:department)</xpath></selector>
                      <case value="billing" uri="direct:matched"/>
                    </switch>
                  </route>
                  <route><from uri="direct:matched"/><setBody><constant>matched</constant></setBody></route>
                </routes>
                """;
        try (var context = new DefaultCamelContext(); var restored = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("selector.xml", xml));
            String roundTrip = PluginHelper.getModelToXMLDumper(context)
                    .dumpModelAsXml(context, context.getRouteDefinition("selector"));
            PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString("restored.xml", roundTrip));
            SwitchDefinition sw = (SwitchDefinition) restored.getRouteDefinition("selector").getOutputs().get(0);
            assertEquals("urn:tickets", ((XPathExpression) sw.getSelector().getExpressionType()).getNamespaces().get("t"));
            context.start();
            try (var template = context.createProducerTemplate()) {
                assertEquals("matched", template.requestBody("direct:start",
                        "<t:ticket xmlns:t='urn:tickets'><t:department>billing</t:department></t:ticket>"));
            }
        }
    }

    @Test
    void scalarRoundTripPreservesFallbackAndExecutes() throws Exception {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/spring">
                  <route id="selector">
                    <from uri="direct:start"/>
                    <switch>
                      <selector><header>decision</header></selector>
                      <case value="billing" uri="direct:matched"/>
                      <otherwise uri="direct:other"/>
                    </switch>
                  </route>
                  <route><from uri="direct:matched"/><setBody><constant>matched</constant></setBody></route>
                  <route><from uri="direct:other"/><setBody><constant>other</constant></setBody></route>
                </routes>
                """;
        try (var context = new DefaultCamelContext(); var restored = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("selector.xml", xml));
            for (var route : context.getRouteDefinitions()) {
                String roundTrip = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, route);
                PluginHelper.getRoutesLoader(restored).loadRoutes(ResourceHelper.fromString(route.getId() + ".xml", roundTrip));
            }
            restored.start();
            try (var template = restored.createProducerTemplate()) {
                assertEquals("matched", template.requestBodyAndHeader("direct:start", "original", "decision",
                        "BILLING"));
                assertEquals("other", template.requestBodyAndHeader("direct:start", "original", "decision",
                        "other"));
            }
        }
    }
}
