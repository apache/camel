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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.builder.Namespaces;
import org.junit.jupiter.api.Test;

/**
 * Tests that a route loaded from plain XML DSL can reference a {@link Namespaces} bean from the registry via the xpath
 * element's namespacesRef attribute, with the bean itself also declared in XML.
 */
class XmlXPathNamespacesRefTest {

    @Test
    void testXPathNamespacesRefFromXml() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            Resource resource = ResourceHelper.fromString("xpathNamespacesRef.xml", """
                    <camel xmlns="http://camel.apache.org/schema/xml-io">
                        <bean name="myNamespaces" type="org.apache.camel.support.builder.Namespaces">
                            <properties>
                                <property key="namespaces[c]" value="http://acme.com/cheese"/>
                                <property key="namespaces[w]" value="http://acme.com/wine"/>
                            </properties>
                        </bean>
                        <route id="xpathNamespacesRef">
                            <from uri="direct:xpathNamespacesRef"/>
                            <choice>
                                <when>
                                    <xpath resultQName="BOOLEAN" namespacesRef="myNamespaces">/c:number = 55</xpath>
                                    <to uri="mock:cheeseMatch"/>
                                </when>
                                <when>
                                    <xpath resultQName="BOOLEAN" namespacesRef="myNamespaces">/w:number = 77</xpath>
                                    <to uri="mock:wineMatch"/>
                                </when>
                                <otherwise>
                                    <to uri="mock:noMatch"/>
                                </otherwise>
                            </choice>
                        </route>
                    </camel>
                    """);
            PluginHelper.getRoutesLoader(context).loadRoutes(resource);

            MockEndpoint cheeseMatch = context.getEndpoint("mock:cheeseMatch", MockEndpoint.class);
            MockEndpoint wineMatch = context.getEndpoint("mock:wineMatch", MockEndpoint.class);
            MockEndpoint noMatch = context.getEndpoint("mock:noMatch", MockEndpoint.class);

            // positive example: cheese namespace
            cheeseMatch.expectedMessageCount(1);
            context.createProducerTemplate().sendBody("direct:xpathNamespacesRef",
                    "<number xmlns=\"http://acme.com/cheese\">55</number>");
            cheeseMatch.assertIsSatisfied();

            // positive example: wine namespace
            wineMatch.expectedMessageCount(1);
            context.createProducerTemplate().sendBody("direct:xpathNamespacesRef",
                    "<number xmlns=\"http://acme.com/wine\">77</number>");
            wineMatch.assertIsSatisfied();

            noMatch.expectedMessageCount(2);

            // negative, data-wise: correct namespace, wrong value
            context.createProducerTemplate().sendBody("direct:xpathNamespacesRef",
                    "<number xmlns=\"http://acme.com/cheese\">99</number>");

            // negative, namespace-wise: correct value, wrong namespace URI
            context.createProducerTemplate().sendBody("direct:xpathNamespacesRef",
                    "<number xmlns=\"http://acme.com/water\">77</number>");

            noMatch.assertIsSatisfied();
        }
    }
}
