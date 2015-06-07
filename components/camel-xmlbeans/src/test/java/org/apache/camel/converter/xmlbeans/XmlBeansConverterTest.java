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
package org.apache.camel.converter.xmlbeans;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.ByteBuffer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.apache.camel.BytesSource;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.piccolo.xml.XMLStreamReader;
import org.junit.Test;

import samples.services.xsd.BuyStocksDocument;
import samples.services.xsd.BuyStocksDocument.BuyStocks;

public class XmlBeansConverterTest extends CamelTestSupport {

    private static final String PAYLOAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xsd:buyStocks xmlns:xsd=\"http://services.samples/xsd\"><order><symbol>IBM</symbol><buyerID>cmueller"
        + "</buyerID><price>140.34</price><volume>2000</volume></order></xsd:buyStocks>";

    @Test
    public void testConvertToXmlObject() throws Exception {
        Exchange exchange = createExchangeWithBody("<hello>world!</hello>");
        Message in = exchange.getIn();
        XmlObject object = in.getBody(XmlObject.class);
        assertNotNull("Should have created an XmlObject!", object);

        log.info("Found: " + object);
        assertEquals("body as String", in.getBody(String.class), object.toString());
    }

    @Test
    public void toXmlObjectFromFile() throws Exception {
        XmlObject result = XmlBeansConverter.toXmlObject(new File("src/test/data/buyStocks.xml"),
                                                         new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    @Test
    public void toXmlObjectFromReader() throws Exception {
        XmlObject result = XmlBeansConverter.toXmlObject(new FileReader("src/test/data/buyStocks.xml"), 
                                                         new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    @Test
    public void toXmlObjectFromNode() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(PAYLOAD)));
        
        XmlObject result = XmlBeansConverter.toXmlObject(document, 
                                                         new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    @Test
    public void toXmlObjectFromInputStream() throws Exception {
        XmlObject result = XmlBeansConverter.toXmlObject(new FileInputStream("src/test/data/buyStocks.xml"),
                                                         new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    @Test
    public void toXmlObjectFromString() throws Exception {
        XmlObject result = XmlBeansConverter.toXmlObject(PAYLOAD, new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    @Test
    public void toXmlObjectFromByteArray() throws Exception {
        XmlObject result = XmlBeansConverter.toXmlObject(PAYLOAD.getBytes(), new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    @Test
    public void toXmlObjectFromByteBuffer() throws Exception {
        XmlObject result = XmlBeansConverter.toXmlObject(ByteBuffer.wrap(PAYLOAD.getBytes()), new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    @Test
    public void toXmlObjectFromXMLStreamReader() throws Exception {
        XmlObject result = XmlBeansConverter.toXmlObject(new XMLStreamReader(new ByteArrayInputStream(PAYLOAD.getBytes()), false), 
                                                         new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    @Test
    public void toXmlObjectFromSource() throws Exception {
        XmlObject result = XmlBeansConverter.toXmlObject(new BytesSource(PAYLOAD.getBytes()), new DefaultExchange(new DefaultCamelContext()));
        assertBuyStocks(result);
    }

    private void assertBuyStocks(Object result) {
        BuyStocks buyStocks = ((BuyStocksDocument) result).getBuyStocks();
        assertEquals(1, buyStocks.getOrderArray().length);
        assertEquals("IBM", buyStocks.getOrderArray(0).getSymbol());
        assertEquals("cmueller", buyStocks.getOrderArray(0).getBuyerID());
        assertEquals(140.34, buyStocks.getOrderArray(0).getPrice(), 0);
        assertEquals(2000, buyStocks.getOrderArray(0).getVolume());
    }
}
