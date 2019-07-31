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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
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
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class ConverterTest extends Assert {
    
    @Test
    public void testToArray() throws Exception {
        List<String> testList = new ArrayList<>();
        testList.add("string 1");
        testList.add("string 2");
        
        Object[] array = CxfConverter.toArray(testList);
        assertNotNull("The array should not be null", array);
        assertEquals("The array size should not be wrong", 2, array.length);
    }
    
    @Test
    public void testToInputStream() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        
        Response response = mock(Response.class);
        InputStream is = mock(InputStream.class);
        
        when(response.getEntity()).thenReturn(is);
        
        InputStream result = CxfConverter.toInputStream(response, exchange);
        assertEquals("We should get the inputStream here ", is, result);
        
        reset(response);
        when(response.getEntity()).thenReturn("Hello World");
        result = CxfConverter.toInputStream(response, exchange);
        assertTrue("We should get the inputStream here ", result instanceof ByteArrayInputStream);
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
        list.add(nl);
        exchange.getIn().setBody(list);
        node = exchange.getIn().getBody(Node.class);
        assertNotNull(node);
    }

}
