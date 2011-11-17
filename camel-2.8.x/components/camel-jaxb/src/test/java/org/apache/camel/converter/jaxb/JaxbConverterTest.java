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
package org.apache.camel.converter.jaxb;

import javax.xml.bind.JAXBException;
import javax.xml.bind.util.JAXBSource;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;

import org.w3c.dom.Document;

import org.apache.camel.CamelException;
import org.apache.camel.example.PurchaseOrder;
import org.apache.camel.test.junit4.ExchangeTestSupport;
import org.junit.Before;
import org.junit.Test;

public class JaxbConverterTest  extends ExchangeTestSupport {
    private JaxbConverter jaxbConverter;
    private PurchaseOrder order;
        
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        jaxbConverter = new JaxbConverter();
        order = new PurchaseOrder();
        order.setAmount(12);
        order.setName("Beer");
        order.setPrice(2.4);
    }
    
    @Test
    public void testToSourceUsingExplicitJaxbConverter() throws JAXBException {        
        JAXBSource source = jaxbConverter.toSource(order);
        assertNotNull("The result should be not be null", source);
        source = jaxbConverter.toSource("test");
        assertNull("The result should be null", source);
    }
    
    @Test
    public void testToSourceUsingTypeConverter() {
        // this time the fall back converter will be use
        exchange.getIn().setBody(order);
        Source source = exchange.getIn().getBody(Source.class);
        assertTrue("The result source should be Source instance", source instanceof Source);
        exchange.getIn().setBody(new CamelException("Test"));
        source = exchange.getIn().getBody(Source.class);
        assertNull("The result should be null", source);
    }

    @Test
    public void testToDocumentUsingExplicitJaxbConverter() throws JAXBException, ParserConfigurationException {
        Document document = jaxbConverter.toDocument(order);
        assertNotNull("The result should be not be null", document);
        document = jaxbConverter.toDocument("test");
        assertNull("The result should be null", document);
    }
    
    @Test
    public void testToDocumentUsingTypeConverter() {
        // this time the fall back converter will be use
        exchange.getIn().setBody(order);
        Document document = exchange.getIn().getBody(Document.class);
        assertNotNull("The result source should not be JAXBSource", document != null);
        exchange.getIn().setBody(new CamelException("Test"));
        document = exchange.getIn().getBody(Document.class);
        assertNull("The result should be null", document);
    }

}
