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

import java.util.HashMap;
import java.util.Map;

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
public class DumpModelAsXmlFromRouteTemplateTest extends ContextTestSupport {

    @Test
    public void testDumpModelAsXml() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("bar", "start");
        map.put("greeting", "Hello");
        map.put("whereto", "Moes");
        context.addRouteFromTemplate("foo", "myTemplate", map);

        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("foo"));
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

        nodes = doc.getElementsByTagName("route");
        assertEquals(1, nodes.getLength());
        node = (Element) nodes.item(0);
        assertEquals("foo", node.getAttribute("id"));
    }

    @Test
    public void testDumpModelAsXmlResolvePlaceholder() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("bar", "start");
        map.put("greeting", "Hello");
        map.put("whereto", "Moes");
        context.addRouteFromTemplate("bar", "myTemplate", map);
        map.clear();
        map.put("bar", "start2");
        map.put("greeting", "Bye");
        map.put("whereto", "Jacks");
        context.addRouteFromTemplate("bar2", "myTemplate", map);

        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("bar"), true,
                true);
        assertNotNull(xml);
        log.info(xml);

        Document doc = new XmlConverter().toDOMDocument(xml, null);
        NodeList nodes = doc.getElementsByTagName("simple");
        assertEquals(1, nodes.getLength());
        Element node = (Element) nodes.item(0);
        assertNotNull(node, "Node <simple> expected to be instanceof Element");
        assertEquals("Hello", node.getTextContent());

        nodes = doc.getElementsByTagName("to");
        assertEquals(1, nodes.getLength());
        node = (Element) nodes.item(0);
        assertNotNull(node, "Node <to> expected to be instanceof Element");
        assertEquals("mock:Moes", node.getAttribute("uri"));

        nodes = doc.getElementsByTagName("route");
        assertEquals(1, nodes.getLength());
        node = (Element) nodes.item(0);
        assertEquals("bar", node.getAttribute("id"));

        xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("bar2"), true, true);
        assertNotNull(xml);
        log.info(xml);

        doc = new XmlConverter().toDOMDocument(xml, null);
        nodes = doc.getElementsByTagName("simple");
        assertEquals(1, nodes.getLength());
        node = (Element) nodes.item(0);
        assertNotNull(node, "Node <simple> expected to be instanceof Element");
        assertEquals("Bye", node.getTextContent());

        nodes = doc.getElementsByTagName("to");
        assertEquals(1, nodes.getLength());
        node = (Element) nodes.item(0);
        assertNotNull(node, "Node <to> expected to be instanceof Element");
        assertEquals("mock:Jacks", node.getAttribute("uri"));

        nodes = doc.getElementsByTagName("route");
        assertEquals(1, nodes.getLength());
        node = (Element) nodes.item(0);
        assertEquals("bar2", node.getAttribute("id"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("bar").templateParameter("greeting").templateParameter("whereto")
                        .from("direct:{{bar}}").transform(simple("{{greeting}}")).to("mock:{{whereto}}");
            }
        };
    }
}
