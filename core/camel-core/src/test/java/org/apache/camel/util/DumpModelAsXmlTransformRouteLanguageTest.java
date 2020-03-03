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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.junit.Test;

/**
 *
 */
public class DumpModelAsXmlTransformRouteLanguageTest extends ContextTestSupport {

    @Test
    public void testDumpModelAsXml() throws Exception {
        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        String xml = ecc.getModelToXMLDumper().dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertNotNull(xml);
        log.info(xml);

        Document doc = new XmlConverter().toDOMDocument(xml, null);
        NodeList nodes = doc.getElementsByTagName("language");
        assertEquals(1, nodes.getLength());
        Element node = (Element)nodes.item(0);
        assertNotNull("Node <simple> expected to be instanceof Element", node);
        assertEquals("constant", node.getAttribute("language"));
        assertEquals("Hello World", node.getTextContent());

        nodes = doc.getElementsByTagName("to");
        assertEquals(1, nodes.getLength());
        node = (Element)nodes.item(0);
        assertNotNull("Node <to> expected to be instanceof Element", node);
        assertEquals("mock:result", node.getAttribute("uri"));
        assertEquals("myMock", node.getAttribute("id"));
        assertEquals("true", node.getAttribute("customId"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute").transform(language("constant", "Hello World")).to("mock:result").id("myMock");
            }
        };
    }
}
