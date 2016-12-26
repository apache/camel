/**
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

import java.io.FileInputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import junit.framework.TestCase;

public class XmlLineNumberParserTest extends TestCase {

    public void testParse() throws Exception {
        FileInputStream fis = new FileInputStream("src/test/resources/org/apache/camel/util/camel-context.xml");
        Document dom = XmlLineNumberParser.parseXml(fis);
        assertNotNull(dom);

        NodeList list = dom.getElementsByTagName("beans");
        assertEquals(1, list.getLength());
        Node node = list.item(0);

        String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
        String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

        assertEquals("23", lineNumber);
        assertEquals("48", lineNumberEnd);
    }

    public void testParseCamelContext() throws Exception {
        FileInputStream fis = new FileInputStream("src/test/resources/org/apache/camel/util/camel-context.xml");
        Document dom = XmlLineNumberParser.parseXml(fis, null, "camelContext", null);
        assertNotNull(dom);

        NodeList list = dom.getElementsByTagName("camelContext");
        assertEquals(1, list.getLength());
        Node node = list.item(0);

        String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
        String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

        assertEquals("28", lineNumber);
        assertEquals("46", lineNumberEnd);
    }

    public void testParseCamelContextForceNamespace() throws Exception {
        FileInputStream fis = new FileInputStream("src/test/resources/org/apache/camel/util/camel-context.xml");
        Document dom = XmlLineNumberParser.parseXml(fis, null, "camelContext", "http://camel.apache.org/schema/spring");
        assertNotNull(dom);

        NodeList list = dom.getElementsByTagName("camelContext");
        assertEquals(1, list.getLength());
        Node node = list.item(0);

        String lineNumber = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER);
        String lineNumberEnd = (String) node.getUserData(XmlLineNumberParser.LINE_NUMBER_END);

        String ns = node.getNamespaceURI();
        assertEquals("http://camel.apache.org/schema/spring", ns);

        assertEquals("28", lineNumber);
        assertEquals("46", lineNumberEnd);

        // and there are two routes
        list = dom.getElementsByTagName("route");
        assertEquals(2, list.getLength());
        Node node1 = list.item(0);
        Node node2 = list.item(1);

        String lineNumber1 = (String) node1.getUserData(XmlLineNumberParser.LINE_NUMBER);
        String lineNumberEnd1 = (String) node1.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
        assertEquals("30", lineNumber1);
        assertEquals("36", lineNumberEnd1);

        String lineNumber2 = (String) node2.getUserData(XmlLineNumberParser.LINE_NUMBER);
        String lineNumberEnd2 = (String) node2.getUserData(XmlLineNumberParser.LINE_NUMBER_END);
        assertEquals("38", lineNumber2);
        assertEquals("44", lineNumberEnd2);
    }

}
