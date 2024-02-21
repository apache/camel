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
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
public class DumpModelAsXmlRouteTemplateTest extends ContextTestSupport {

    @Test
    public void testDumpModelAsXml() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context,
                context.getRouteTemplateDefinition("myTemplate"));
        assertNotNull(xml);
        log.info(xml);

        Document doc = new XmlConverter().toDOMDocument(xml, null);
        NodeList nodes = doc.getElementsByTagName("simple");
        assertEquals(1, nodes.getLength());
        Element node = (Element) nodes.item(0);
        assertNotNull(node, "Node <simple> expected to be instanceof Element");
        assertEquals("{{greeting}}", node.getTextContent());

        nodes = doc.getElementsByTagName("to");
        assertEquals(1, nodes.getLength());
        node = (Element) nodes.item(0);
        assertNotNull(node, "Node <to> expected to be instanceof Element");
        assertEquals("mock:{{whereto}}", node.getAttribute("uri"));

        nodes = doc.getElementsByTagName("routeTemplate");
        assertEquals(1, nodes.getLength());
        node = (Element) nodes.item(0);
        assertEquals("myTemplate", node.getAttribute("id"));

        nodes = doc.getElementsByTagName("templateParameter");
        assertEquals(2, nodes.getLength());
        node = (Element) nodes.item(0);
        assertEquals("greeting", node.getAttribute("name"));
        node = (Element) nodes.item(1);
        assertEquals("whereto", node.getAttribute("name"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("greeting").templateParameter("whereto")
                        .from("direct:start").transform(simple("{{greeting}}")).to("mock:{{whereto}}");
            }
        };
    }
}
