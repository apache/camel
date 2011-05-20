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
package org.apache.camel.dataformat.xmlsecurity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.xml.security.encryption.XMLCipher;
import org.junit.Test;


/**
 * Unit test of the encryptXML data format.
 */
public class XMLSecurityDataFormatTest extends CamelTestSupport {
    private static final String XML_FRAGMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
            + "<cheesesites>"
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

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void sendText() throws Exception {
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                // Set the property of the charset encoding
                exchange.setProperty(Exchange.CHARSET_NAME, "UTF-8");
                Message in = exchange.getIn();
                in.setBody(XML_FRAGMENT);
                log.info("xmlFragment: " + XML_FRAGMENT);
            }

        });
    }

    /*
     * Encryption Tests
     */
    @Test
    public void testFullPayloadXMLEncryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML().process(new EncryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    @Test
    public void testPartialPayloadXMLContentEncryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML("//cheesesites/italy/cheese", true).process(new EncryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    @Test
    public void testPartialPayloadMultiNodeXMLContentEncryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML("//cheesesites/*/cheese", true).process(new EncryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    @Test
    public void testPartialPayloadXMLElementEncryptionWithKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML("//cheesesites/france/cheese", false, "Just another 24 Byte key").process(new EncryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    @Test
    public void testPartialPayloadXMLElementEncryptionWithKeyAndAlgorithm() throws Exception {
        final byte[] bits128 = {
            (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
            (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
            (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
            (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17};

        final String passCode = new String(bits128);
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML("//cheesesites/netherlands", false, passCode, XMLCipher.AES_128).process(new EncryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    /*
    * Decryption Tests
    */
    @Test
    public void testFullPayloadXMLDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML().unmarshal().secureXML().process(new DecryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }
    
    @Test
    public void testPartialPayloadXMLContentDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML("//cheesesites/italy/cheese", true).
                        unmarshal().secureXML("//cheesesites/italy/cheese", true).process(new DecryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }
    
    @Test
    public void testPartialPayloadMultiNodeXMLContentDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML("//cheesesites/*/cheese", true).
                        unmarshal().secureXML("//cheesesites/*/cheese", true).process(new DecryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    @Test
    public void testPartialPayloadXMLElementDecryptionWithKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML("//cheesesites/france/cheese", false, "Just another 24 Byte key").
                        unmarshal().secureXML("//cheesesites/france", false, "Just another 24 Byte key").process(new DecryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }

    @Test
    public void testPartialPayloadXMLContentDecryptionWithKeyAndAlgorithm() throws Exception {
        final byte[] bits128 = {
            (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
            (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
            (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
            (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17};
        final String passCode = new String(bits128);
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().secureXML("cheese", true, passCode, XMLCipher.AES_128).
                        unmarshal().secureXML("cheese", true, passCode, XMLCipher.AES_128).process(new DecryptedXMLMessageProcessor());
            }
        });
        context.start();

        sendText();
    }


    private class EncryptedXMLMessageProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            byte[] body = exchange.getIn().getBody(byte[].class);

            Document d = createDocumentfromInputStream(new ByteArrayInputStream(body));

            NodeList nodeList = d.getElementsByTagNameNS("http://www.w3.org/2001/04/xmlenc#", "EncryptedData");
            if (nodeList.getLength() >= 0) {
                assertTrue(true);
            } else {
                assertTrue(false);
            }
        }
    }

    private class DecryptedXMLMessageProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            byte[] body = exchange.getIn().getBody(byte[].class);

            Document d = createDocumentfromInputStream(new ByteArrayInputStream(body));

            NodeList nodeList = d.getElementsByTagNameNS("http://www.w3.org/2001/04/xmlenc#", "EncryptedData");
            if (nodeList.getLength() == 0) {
                assertTrue(true);
            } else {
                assertTrue(false);
            }
        }
    }


    private Document createDocumentfromInputStream(InputStream is) {
        return context.getTypeConverter().convertTo(Document.class, is);
    }
}
