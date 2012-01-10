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
import java.net.URL;
import java.security.KeyStore;
import java.util.Map;

import javax.xml.transform.OutputKeys;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.xml.security.encryption.XMLCipher;
import org.junit.Test;

/**
 * Unit test of the encryptXML data format.
 */
public class XMLSecurityDataFormatTest extends CamelTestSupport {
    private static final String XML_FRAGMENT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
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
    
    // one could use testCypherAlgorithm = XMLCipher.AES_128 if she had the AES Optional Pack installed
    private String testCypherAlgorithm = XMLCipher.AES_128;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
    
    @Override 
    public void setUp() throws Exception {
        super.setUp();
        context.getProperties().put(XmlConverter.OUTPUT_PROPERTIES_PREFIX + OutputKeys.ENCODING, "UTF-8");
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
    
    private void testEncryption() throws Exception {
        MockEndpoint resultEndpoint = context.getEndpoint("mock:encrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(1);
        context.start();
        sendText();
        resultEndpoint.assertIsSatisfied(100);
        Exchange exchange = resultEndpoint.getExchanges().get(0);
        Document inDoc = getDocumentForInMessage(exchange);
        if (log.isDebugEnabled()) {
            logMessage(exchange, inDoc);
        }
        assertTrue("The XML message has no encrypted data.", hasEncryptedData(inDoc));
    }
    
    private void testDecryption() throws Exception {
        MockEndpoint resultEndpoint = context.getEndpoint("mock:decrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(1);
        // verify that the message was encrypted before checking that it is decrypted
        testEncryption();

        resultEndpoint.assertIsSatisfied(100);
        Exchange exchange = resultEndpoint.getExchanges().get(0);
        Document inDoc = getDocumentForInMessage(exchange);
        if (log.isDebugEnabled()) {
            logMessage(exchange, inDoc);
        }
        assertFalse("The XML message has encrypted data.", hasEncryptedData(inDoc));
        
        // verify that the decrypted message matches what was sent
        XmlConverter converter = new XmlConverter();
        String xmlStr = converter.toString(inDoc, exchange);
        assertTrue(xmlStr.equals(XML_FRAGMENT));
    }
    
    private boolean hasEncryptedData(Document doc) throws Exception {
        NodeList nodeList = doc.getElementsByTagNameNS("http://www.w3.org/2001/04/xmlenc#", "EncryptedData");
        return nodeList.getLength() > 0;
    }
    
    private void logMessage(Exchange exchange, Document inDoc) throws Exception {
        XmlConverter converter = new XmlConverter();
        String xmlStr = converter.toString(inDoc, exchange);
        log.debug(xmlStr);   
    }
    
    private Document getDocumentForInMessage(Exchange exchange) {
        byte[] body = exchange.getIn().getBody(byte[].class);
        Document d = createDocumentfromInputStream(new ByteArrayInputStream(body));
        return d;
    }
    
    private Document createDocumentfromInputStream(InputStream is) {
        return context.getTypeConverter().convertTo(Document.class, is);
    }

    /*
     * Encryption Tests
     */
    
    @Test
    public void testFullPayloadXMLEncryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML()
                    .to("mock:encrypted");
            }
        });
        testEncryption();
    }

    @Test
    public void testPartialPayloadXMLContentEncryption() throws Exception {       
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy/cheese", true)
                    .to("mock:encrypted");
            }
        });
        testEncryption();
    }

    @Test
    public void testPartialPayloadMultiNodeXMLContentEncryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                     .marshal().secureXML("//cheesesites/*/cheese", true)
                     .to("mock:encrypted");
            }
        });
        testEncryption();
    }

    @Test
    public void testPartialPayloadXMLElementEncryptionWithKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                     .marshal().secureXML("//cheesesites/france/cheese", false, "Just another 24 Byte key")
                     .to("mock:encrypted");
            }    
        });
        testEncryption();
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
                from("direct:start")
                    .marshal().secureXML("//cheesesites/netherlands", false, passCode, XMLCipher.AES_128)
                    .to("mock:encrypted");
            }
        });
        testEncryption();
    }

    @Test
    public void testFullPayloadAsymmetricKeyEncryption() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        URL trustStoreUrl = getClass().getClassLoader().getResource("sender.ts");
        trustStore.load(trustStoreUrl.openStream(), "password".toCharArray());

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_v1dot5);
        xmlEncDataFormat.setXmlCipherAlgorithm(testCypherAlgorithm);
        xmlEncDataFormat.setTrustStore(trustStore);
        xmlEncDataFormat.setTrustStorePassword("password");
        xmlEncDataFormat.setRecipientKeyAlias("recipient");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal(xmlEncDataFormat).to("mock:encrypted");
            }
        });
        testEncryption();
    }

    @Test
    public void testPartialPayloadAsymmetricKeyEncryptionWithContextTruststoreProperties() throws Exception {
        Map<String, String> contextProps = context.getProperties();
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_URL, 
            getClass().getClassLoader().getResource("sender.ts").toString());
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_PASSWORD, "password");
 
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy/cheese", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5)
                    .to("mock:encrypted");
            }
        });
        testEncryption();
    }
 
    @Test
    public void testPartialPayloadAsymmetricKeyEncryptionWithExchangeRecipientAlias() throws Exception {
        MockEndpoint resultEndpoint = context.getEndpoint("mock:foo", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(1);
 
        Map<String, String> contextProps = context.getProperties();
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_URL,
            getClass().getClassLoader().getResource("sender.ts").toString());
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_PASSWORD, "password");
 
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader(XMLSecurityDataFormat.XML_ENC_RECIPIENT_ALIAS, "recipient");
                        }
                    })
                    .marshal().secureXML("//cheesesites/italy/cheese", true, null, testCypherAlgorithm, XMLCipher.RSA_v1dot5)
                    .to("mock:encrypted");
            }
        });
        testEncryption();
    }
 
    /*
    * Decryption Tests
    */
    @Test
    public void testFullPayloadXMLDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML().to("mock:encrypted")
                    .unmarshal().secureXML().to("mock:decrypted");
            }
        });
        testDecryption();
    }
    
    @Test
    public void testPartialPayloadXMLContentDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy/cheese", true).to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/italy/cheese", true).to("mock:decrypted");
            }
        });
        testDecryption();
    }
    
    @Test
    public void testPartialPayloadMultiNodeXMLContentDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/*/cheese", true).to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/*/cheese", true).to("mock:decrypted");
            }
        });
        testDecryption();
    }

    @Test
    public void testPartialPayloadXMLElementDecryptionWithKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/france/cheese", false, "Just another 24 Byte key").to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/france", false, "Just another 24 Byte key").to("mock:decrypted");
            }
        });
        testDecryption();
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
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy", true, passCode, XMLCipher.AES_128).to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/italy", true, passCode, XMLCipher.AES_128).to("mock:decrypted");
            }
        });
        testDecryption();
    }

    @Test
    public void testFullPayloadAsymmetricKeyDecryption() throws Exception {

        Map<String, String> contextProps = context.getProperties();
        // context properties for encryption
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_URL, getClass().getClassLoader().getResource("sender.ts").toString());
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_PASSWORD, "password");
        contextProps.put(XMLSecurityDataFormat.XML_ENC_RECIPIENT_ALIAS, "recipient");

        // context properties for decryption
        contextProps.put(XMLSecurityDataFormat.XML_ENC_KEY_STORE_URL, getClass().getClassLoader().getResource("recipient.ks").toString());
        contextProps.put(XMLSecurityDataFormat.XML_ENC_KEY_STORE_PASSWORD, "password");
        contextProps.put(XMLSecurityDataFormat.XML_ENC_KEY_STORE_ALIAS, "recipient");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("", true, null, testCypherAlgorithm, XMLCipher.RSA_v1dot5).to("mock:encrypted")
                    .unmarshal().secureXML("", true, null, testCypherAlgorithm, XMLCipher.RSA_v1dot5).to("mock:decrypted");
            }
        });
        testDecryption();
    }

    @Test
    public void testPartialPayloadAsymmetricKeyDecryption() throws Exception {

        Map<String, String> contextProps = context.getProperties();

        // context properties for encryption
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_URL, getClass().getClassLoader().getResource("sender.ts").toString());
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_PASSWORD, "password");
        contextProps.put(XMLSecurityDataFormat.XML_ENC_RECIPIENT_ALIAS, "recipient");

        // context properties for decryption
        contextProps.put(XMLSecurityDataFormat.XML_ENC_KEY_STORE_URL, getClass().getClassLoader().getResource("recipient.ks").toString());
        contextProps.put(XMLSecurityDataFormat.XML_ENC_KEY_STORE_PASSWORD, "password");
        contextProps.put(XMLSecurityDataFormat.XML_ENC_KEY_STORE_ALIAS, "recipient");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy", true, null, testCypherAlgorithm, XMLCipher.RSA_v1dot5).to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/italy", true, null, testCypherAlgorithm, XMLCipher.RSA_v1dot5).to("mock:decrypted");
            }
        });
        testDecryption();
    }
}
