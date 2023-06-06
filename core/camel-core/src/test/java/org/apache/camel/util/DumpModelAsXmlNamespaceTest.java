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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.builder.Namespaces;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DumpModelAsXmlNamespaceTest extends ContextTestSupport {

    private static final String URL_FOO = "http://foo.com";
    private static final String URL_BAR = "http://bar.com";

    @Test
    public void testDumpModelAsXml() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertNotNull(xml);

        Document dom = context.getTypeConverter().convertTo(Document.class, xml);
        NodeList nl = dom.getElementsByTagName("xpath");
        assertEquals(2, nl.getLength());

        Element n1 = (Element) nl.item(0);
        String attributeFoo = n1.getAttribute("xmlns:foo");
        assertNotNull(attributeFoo);
        assertEquals(URL_FOO, attributeFoo);

        Element n2 = (Element) nl.item(1);
        String attributeBar = n2.getAttribute("xmlns:bar");
        assertNotNull(attributeBar);
        assertEquals(URL_BAR, attributeBar);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Namespaces foo = new Namespaces("foo", URL_FOO);
                Namespaces bar = new Namespaces("bar", URL_BAR);

                from("direct:start").routeId("myRoute").choice().when(xpath("/foo:customer", foo)).to("mock:foo")
                        .when(xpath("/bar:customer", bar)).to("mock:bar");
            }
        };
    }
}
