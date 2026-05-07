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
package org.apache.camel.dataformat.xmlsecurity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.StreamCache;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.DomConverter;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.XMLEncryptionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.assertj3.XmlAssert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHelper {
    protected static final String NS_XML_FRAGMENT
            = "<ns1:cheesesites xmlns:ns1=\"http://cheese.xmlsecurity.camel.apache.org/\">"
              + "<netherlands>"
              + "<source>cow</source>"
              + "<cheese>gouda</cheese>"
              + "</netherlands>"
              + "<italy>"
              + "<source>cow</source>"
              + "<cheese>gorgonzola</cheese>"
              + "</italy>"
              + "<france>"
              + "<source>goat</source>"
              + "<cheese>brie</cheese>"
              + "</france>"
              + "</ns1:cheesesites>";

    protected static final String XML_FRAGMENT = "<cheesesites>"
                                                 + "<netherlands>"
                                                 + "<source>cow</source>"
                                                 + "<cheese>gouda</cheese>"
                                                 + "</netherlands>"
                                                 + "<italy>"
                                                 + "<source>cow</source>"
                                                 + "<cheese>gorgonzola</cheese>"
                                                 + "</italy>"
                                                 + "<france>"
                                                 + "<source>goat</source>"
                                                 + "<cheese>brie</cheese>"
                                                 + "</france>"
                                                 + "</cheesesites>";

    static final boolean HAS_3DES;

    static {
        boolean ok = false;
        try {
            org.apache.xml.security.Init.init();
            XMLCipher.getInstance(XMLCipher.TRIPLEDES_KeyWrap);
            ok = true;
        } catch (XMLEncryptionException e) {
            LoggerFactory.getLogger(TestHelper.class).warn("XML encryption exception: {}", e.getMessage(), e);
        }
        HAS_3DES = ok;
    }

    static final boolean UNRESTRICTED_POLICIES_INSTALLED;
    static {
        boolean ok = false;
        try {
            byte[] data = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 };

            SecretKey key192 = new SecretKeySpec(
                    new byte[] {
                            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                            0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                            0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17 },
                    "AES");
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.ENCRYPT_MODE, key192);
            c.doFinal(data);
            ok = true;
        } catch (Exception e) {
            //
        }
        UNRESTRICTED_POLICIES_INSTALLED = ok;
    }

    Logger log = LoggerFactory.getLogger(TestHelper.class);

    protected void sendText(final String fragment, CamelContext context) {
        ProducerTemplate template = context.createProducerTemplate();
        template.start();
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) {
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(fragment);
                log.info("xmlFragment: {}", fragment);
            }
        });
    }

    protected Document testEncryption(String fragment, CamelContext context) throws Exception {
        MockEndpoint resultEndpoint = context.getEndpoint("mock:encrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(1);
        context.start();
        sendText(fragment, context);
        resultEndpoint.assertIsSatisfied(100);
        Exchange exchange = resultEndpoint.getExchanges().get(0);
        Document inDoc = getDocumentForInMessage(exchange);
        if (log.isDebugEnabled()) {
            logMessage(exchange, inDoc);
        }
        assertTrue(hasEncryptedData(inDoc), "The XML message has no encrypted data.");
        return inDoc;
    }

    protected void testEncryption(CamelContext context) throws Exception {
        testEncryption(XML_FRAGMENT, context);
    }

    protected void testDecryption(String fragment, CamelContext context) throws Exception {
        MockEndpoint resultEndpoint = context.getEndpoint("mock:decrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(1);
        // verify that the message was encrypted before checking that it is decrypted
        testEncryption(fragment, context);

        resultEndpoint.assertIsSatisfied(100);
        Exchange exchange = resultEndpoint.getExchanges().get(0);
        Document inDoc = getDocumentForInMessage(exchange);
        if (log.isDebugEnabled()) {
            logMessage(exchange, inDoc);
        }
        assertFalse(hasEncryptedData(inDoc), "The XML message has encrypted data.");

        // verify that the decrypted message matches what was sent
        XmlAssert.assertThat(fragment).and(inDoc).areIdentical();
    }

    protected void testDecryption(CamelContext context) throws Exception {
        testDecryption(XML_FRAGMENT, context);
    }

    protected void testDecryptionNoEncryptedKey(CamelContext context) throws Exception {
        MockEndpoint resultEndpoint = context.getEndpoint("mock:decrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(1);
        context.start();
        resultEndpoint.assertIsSatisfied(100);
        Exchange exchange = resultEndpoint.getExchanges().get(0);
        Document inDoc = getDocumentForInMessage(exchange);
        DomConverter converter = new DomConverter();
        String xmlStr = converter.toString(inDoc, exchange);
        log.info(xmlStr);
        assertFalse(hasEncryptedData(inDoc), "The XML message has encrypted data.");
    }

    private boolean hasEncryptedData(Document doc) {
        NodeList nodeList = doc.getElementsByTagNameNS("http://www.w3.org/2001/04/xmlenc#", "EncryptedData");
        return nodeList.getLength() > 0;
    }

    private void logMessage(Exchange exchange, Document inDoc) throws Exception {
        DomConverter converter = new DomConverter();
        String xmlStr = converter.toString(inDoc, exchange);
        log.debug(xmlStr);
    }

    private Document getDocumentForInMessage(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body instanceof StreamCache) {
            ((StreamCache) body).reset();
        }
        byte[] arr = exchange.getIn().getBody(byte[].class);
        Document d = createDocumentFromInputStream(new ByteArrayInputStream(arr), exchange.getContext());
        return d;
    }

    private Document createDocumentFromInputStream(InputStream is, CamelContext context) {
        return context.getTypeConverter().convertTo(Document.class, is);
    }

}
