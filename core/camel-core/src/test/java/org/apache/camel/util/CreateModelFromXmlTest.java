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
package org.apache.camel.util;

import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.NamespaceAware;
import org.junit.Test;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

public class CreateModelFromXmlTest extends ContextTestSupport {

    public static final String NS_CAMEL = "http://camel.apache.org/schema/spring";
    public static final String NS_FOO = "http://foo";
    public static final String NS_BAR = "http://bar";

    @Test
    public void testCreateModelFromXmlForInputStreamWithDefaultNamespace() throws Exception {
        RoutesDefinition routesDefinition = createModelFromXml("simpleRoute.xml", false);
        assertNotNull(routesDefinition);

        Map<String, String> expectedNamespaces = new LinkedHashMap<>();
        expectedNamespaces.put("xmlns", NS_CAMEL);

        assertNamespacesPresent(routesDefinition, expectedNamespaces);
    }

    @Test
    public void testCreateModelFromXmlForInputStreamWithAdditionalNamespaces() throws Exception {
        RoutesDefinition routesDefinition = createModelFromXml("simpleRouteWithNamespaces.xml", false);
        assertNotNull(routesDefinition);

        Map<String, String> expectedNamespaces = new LinkedHashMap<>();
        expectedNamespaces.put("xmlns", NS_CAMEL);
        expectedNamespaces.put("foo", NS_FOO);
        expectedNamespaces.put("bar", NS_BAR);

        assertNamespacesPresent(routesDefinition, expectedNamespaces);
    }

    @Test
    public void testCreateModelFromXmlForStringWithDefaultNamespace() throws Exception {
        RoutesDefinition routesDefinition = createModelFromXml("simpleRoute.xml", true);
        assertNotNull(routesDefinition);

        Map<String, String> expectedNamespaces = new LinkedHashMap<>();
        expectedNamespaces.put("xmlns", NS_CAMEL);

        assertNamespacesPresent(routesDefinition, expectedNamespaces);
    }

    @Test
    public void testCreateModelFromXmlForStringWithAdditionalNamespaces() throws Exception {
        RoutesDefinition routesDefinition = createModelFromXml("simpleRouteWithNamespaces.xml", true);
        assertNotNull(routesDefinition);

        Map<String, String> expectedNamespaces = new LinkedHashMap<>();
        expectedNamespaces.put("xmlns", NS_CAMEL);
        expectedNamespaces.put("foo", NS_FOO);
        expectedNamespaces.put("bar", NS_BAR);

        assertNamespacesPresent(routesDefinition, expectedNamespaces);
    }

    private RoutesDefinition createModelFromXml(String camelContextResource, boolean fromString) throws Exception {
        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);

        InputStream inputStream = getClass().getResourceAsStream(camelContextResource);

        if (fromString) {
            String xml = context.getTypeConverter().convertTo(String.class, inputStream);
            InputStream isxml = context.getTypeConverter().convertTo(InputStream.class, xml);
            return (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(context, isxml);
        }

        return (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(context, inputStream);
    }

    private void assertNamespacesPresent(RoutesDefinition routesDefinition, Map<String, String> expectedNamespaces) {
        for (RouteDefinition route : routesDefinition.getRoutes()) {
            Iterator<ExpressionNode> it = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
            if (it.hasNext()) {
                ExpressionNode en = it.next();
                ExpressionDefinition ed = en.getExpression();

                NamespaceAware na = null;
                Expression exp = ed.getExpressionValue();
                if (exp instanceof NamespaceAware) {
                    na = (NamespaceAware)exp;
                } else if (ed instanceof NamespaceAware) {
                    na = (NamespaceAware)ed;
                }

                assertNotNull(na);
                assertEquals(expectedNamespaces, na.getNamespaces());
            } else {
                fail("Expected to find at least one ExpressionNode in route");
            }
        }
    }
}
