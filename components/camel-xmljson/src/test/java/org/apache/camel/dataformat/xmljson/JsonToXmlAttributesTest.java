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
package org.apache.camel.dataformat.xmljson;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.camel.builder.RouteBuilder;

import org.junit.Test;

public class JsonToXmlAttributesTest extends AbstractJsonTestSupport {

    @Test
    public void shouldCreateAttribute() {
        // Given
        InputStream inStream = getClass().getResourceAsStream("jsonToXmlAttributesMessage.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        // When
        String xml = template.requestBody("direct:unmarshal", in, String.class);

        // Then
        assertTrue(xml.contains(" b=\"2\""));
    }

    @Test
    public void shouldCreateOnlyOneAttribute() {
        // Given
        InputStream inStream = getClass().getResourceAsStream("jsonToXmlAttributesMessage.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        // When
        String xml = template.requestBody("direct:unmarshal", in, String.class);

        // Then
        assertFalse(xml.contains("a="));
    }

    @Test
    public void shouldCreateElementWithAttribute() throws ParserConfigurationException, IOException, SAXException {
        // Given
        InputStream inStream = getClass().getResourceAsStream("jsonToXmlElementWithAttributeMessage.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        // When
        String xml = template.requestBody("direct:unmarshal", in, String.class);

        // Then
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().
                parse(new ByteArrayInputStream(xml.getBytes()));
        NodeList nodeList = document.getDocumentElement().getElementsByTagName("element");
        assertEquals(1, nodeList.getLength());
        Element element = (Element) nodeList.item(0);
        assertEquals("elementContent", element.getTextContent());
        assertEquals("attributeValue", element.getAttribute("attribute"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:unmarshal").unmarshal().xmljson().to("mock:xml");
            }
        };
    }

}
