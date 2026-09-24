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
package org.apache.camel.spring;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.xml.jaxb.JaxbHelper;
import org.apache.camel.xml.jaxb.JaxbModelToXMLDumper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChoiceSelectorJaxbTest {
    @Test
    void selectorNamespacesAndLiteralBranchesSurviveJaxbRoundTrip() throws Exception {
        try (var context = new DefaultCamelContext()) {
            XPathExpression selector = new XPathExpression("/t:ticket/t:department/text()");
            selector.setNamespaces(Map.of("t", "urn:tickets"));
            RoutesDefinition routes = new RoutesDefinition();
            routes.from("direct:start").routeId("selector").choice(selector)
                    .when("billing").to("mock:billing").otherwise().to("mock:other");
            String xml = new JaxbModelToXMLDumper().dumpModelAsXml(context, routes);
            try (var input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                RoutesDefinition restored = JaxbHelper.loadRoutesDefinition(context, input);
                ChoiceDefinition choice = (ChoiceDefinition) restored.getRoutes().get(0).getOutputs().get(0);
                XPathExpression xpath = (XPathExpression) choice.getSelector().getExpressionType();
                assertEquals("/t:ticket/t:department/text()", xpath.getExpression());
                assertEquals("urn:tickets", xpath.getNamespaces().get("t"));
                assertEquals("billing", choice.getWhenClauses().get(0).getValue());
                assertNull(choice.getWhenClauses().get(0).getExpression());
            }
        }
    }
}
