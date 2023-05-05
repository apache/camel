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
package org.apache.camel.component.cxf.converter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.cxf.message.MessageContentsList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ConverterTest {

    @Test
    public void testToArray() throws Exception {
        List<String> testList = new ArrayList<>();
        testList.add("string 1");
        testList.add("string 2");

        Object[] array = CxfConverter.toArray(testList);
        assertNotNull(array, "The array should not be null");
        assertEquals(2, array.length, "The array size should not be wrong");
    }

    @Test
    public void testFallbackConverter() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        MessageContentsList list = new MessageContentsList();
        NodeListWrapper nl = new NodeListWrapper(new ArrayList<Element>());
        list.add(nl);
        exchange.getIn().setBody(list);
        Node node = exchange.getIn().getBody(Node.class);
        assertNull(node);

        File file = new File("src/test/resources/org/apache/camel/component/cxf/converter/test.xml");
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(file);
        document.getDocumentElement().normalize();
        List<Element> elements = new ArrayList<>();
        elements.add(document.getDocumentElement());
        nl = new NodeListWrapper(elements);
        list.clear();
        // there is only 1 element in the list so it can be converted to a single node element
        list.add(nl);
        exchange.getIn().setBody(list);
        node = exchange.getIn().getBody(Node.class);
        assertNotNull(node);
    }

    @Test
    public void testMessageContentsListAsGeneralList() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        MessageContentsList list = new MessageContentsList();
        list.add("hehe");
        list.add("haha");
        exchange.getIn().setBody(list);
        String ret = exchange.getIn().getBody(String.class);
        assertEquals(ret, "[hehe, haha]", "shouldn't miss list content");
    }
}
