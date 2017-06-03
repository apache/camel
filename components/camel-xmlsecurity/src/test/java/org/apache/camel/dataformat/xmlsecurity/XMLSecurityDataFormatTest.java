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

import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.OutputKeys;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.xml.security.encryption.XMLCipher;
import org.junit.Assert;
import org.junit.Test;


/**
 * Unit test of the encryptXML data format.
 */
public class XMLSecurityDataFormatTest extends CamelTestSupport {
    
    // one could use testCypherAlgorithm = XMLCipher.AES_128 if she had the AES Optional Pack installed
    protected static String testCypherAlgorithm = XMLCipher.AES_128;
    
    TestHelper xmlsecTestHelper = new TestHelper();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
    
    @Override 
    public void setUp() throws Exception {
        super.setUp();
        context.getProperties().put(XmlConverter.OUTPUT_PROPERTIES_PREFIX + OutputKeys.ENCODING, "UTF-8");
    }
    
    
    /*
     * Encryption Tests
     */
    
    @Test
    public void testFullPayloadXMLEncryption() throws Exception {
        if (!TestHelper.HAS_3DES) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML()
                    .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testPartialPayloadXMLContentEncryption() throws Exception {       
        if (!TestHelper.HAS_3DES) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy/cheese", true)
                    .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testPartialPayloadMultiNodeXMLContentEncryption() throws Exception {
        if (!TestHelper.HAS_3DES) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                     .marshal().secureXML("//cheesesites/*/cheese", true)
                     .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testPartialPayloadXMLElementEncryptionWithKey() throws Exception {
        if (!TestHelper.HAS_3DES) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                     .marshal().secureXML("//cheesesites/france/cheese", false, "Just another 24 Byte key")
                     .to("mock:encrypted");
            }    
        });
        xmlsecTestHelper.testEncryption(context);
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
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testFullPayloadAsymmetricKeyEncryption() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_v1dot5);
        xmlEncDataFormat.setKeyOrTrustStoreParameters(tsParameters);
        xmlEncDataFormat.setXmlCipherAlgorithm(testCypherAlgorithm);
        xmlEncDataFormat.setRecipientKeyAlias("recipient");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal(xmlEncDataFormat).to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testPartialPayloadAsymmetricKeyEncryptionWithContextTruststoreProperties() throws Exception {
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
        
        Map<String, String> contextProps = context.getProperties();
        contextProps.put(XMLSecurityDataFormat.XML_ENC_TRUST_STORE_PASSWORD, "password");
 
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy/cheese", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters)
                    .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }
 
    @Test
    @SuppressWarnings("deprecation")
    public void testPartialPayloadAsymmetricKeyEncryptionWithExchangeRecipientAlias() throws Exception {
        MockEndpoint resultEndpoint = context.getEndpoint("mock:foo", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(1);
 
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
 
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader(XMLSecurityDataFormat.XML_ENC_RECIPIENT_ALIAS, "recipient");
                        }
                    })
                    .marshal().secureXML("//cheesesites/italy/cheese", true, null, testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters)
                    .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }
    
    @Test
    public void testAsymmetricEncryptionAddKeyValue() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setKeyOrTrustStoreParameters(tsParameters);
        xmlEncDataFormat.setXmlCipherAlgorithm(testCypherAlgorithm);
        xmlEncDataFormat.setRecipientKeyAlias("recipient");
        xmlEncDataFormat.setAddKeyValueForEncryptedKey(true);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal(xmlEncDataFormat).to("mock:encrypted");
            }
        });
        Document doc = xmlsecTestHelper.testEncryption(TestHelper.XML_FRAGMENT, context);
        NodeList nodeList = 
            doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "RSAKeyValue");
        Assert.assertTrue(nodeList.getLength() > 0);
    }
    
    @Test
    public void testAsymmetricEncryptionNoKeyValue() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setKeyOrTrustStoreParameters(tsParameters);
        xmlEncDataFormat.setXmlCipherAlgorithm(testCypherAlgorithm);
        xmlEncDataFormat.setRecipientKeyAlias("recipient");
        xmlEncDataFormat.setAddKeyValueForEncryptedKey(false);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal(xmlEncDataFormat).to("mock:encrypted");
            }
        });
        Document doc = xmlsecTestHelper.testEncryption(TestHelper.XML_FRAGMENT, context);
        NodeList nodeList = 
            doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "RSAKeyValue");
        Assert.assertTrue(nodeList.getLength() == 0);
    }
 
    /*
    * Decryption Tests
    */
    @Test
    public void testFullPayloadXMLDecryption() throws Exception {
        if (!TestHelper.HAS_3DES) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML().to("mock:encrypted")
                    .unmarshal().secureXML().to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }
    
    @Test
    public void testPartialPayloadXMLContentDecryption() throws Exception {
        if (!TestHelper.HAS_3DES) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy/cheese", true).to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/italy/cheese", true).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }
    
    @Test
    public void testPartialPayloadMultiNodeXMLContentDecryption() throws Exception {
        if (!TestHelper.HAS_3DES) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/*/cheese", true).to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/*/cheese", true).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testPartialPayloadXMLElementDecryptionWithKey() throws Exception {
        if (!TestHelper.HAS_3DES) {
            return;
        }
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/france/cheese", false, "Just another 24 Byte key").to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/france", false, "Just another 24 Byte key").to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
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
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testFullPayloadAsymmetricKeyDecryption() throws Exception {
                      
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
        
        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.ks");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters).to("mock:encrypted")
                    .unmarshal().secureXML("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, ksParameters).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }
    
    @Test
    public void testFullPayloadAsymmetricKeyDecryptionWithKeyPassword() throws Exception {
                      
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
        
        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient-with-key-pass.ks");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters).to("mock:encrypted")
                    .unmarshal().secureXML("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, ksParameters, "keyPassword").to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }    

    @Test
    public void testPartialPayloadAsymmetricKeyDecryption() throws Exception {
        final Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("ns1", "http://cheese.xmlsecurity.camel.apache.org/");
        
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
        
        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.ks");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//ns1:cheesesites/italy", namespaces, true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters).to("mock:encrypted")
                    .unmarshal().secureXML("//ns1:cheesesites/italy", namespaces, true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, ksParameters).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(TestHelper.NS_XML_FRAGMENT, context);
    }
    
    @Test
    public void testPartialPayloadAsymmetricKeyDecryptionCustomNS() throws Exception {
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
        
        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.ks");
        
        
        final Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("cust", "http://cheese.xmlsecurity.camel.apache.org/");


        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cust:cheesesites/italy", namespaces, true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters).to("mock:encrypted")
                    .unmarshal().secureXML("//cust:cheesesites/italy", namespaces, true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, ksParameters).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(TestHelper.NS_XML_FRAGMENT, context);
    }
    
    @Test
    public void testAsymmetricEncryptionAlgorithmFullPayload() throws Exception {
                      
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
        
        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.ks");

        // RSA v1.5 is not allowed unless explicitly configured
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters).to("mock:encrypted")
                    .unmarshal().secureXML("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_OAEP, ksParameters).to("mock:decrypted");
            }
        });
        
        MockEndpoint resultEndpoint = context.getEndpoint("mock:decrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(0);
        // verify that the message was encrypted before checking that it is decrypted
        xmlsecTestHelper.testEncryption(TestHelper.XML_FRAGMENT, context);
        
        resultEndpoint.assertIsSatisfied(100);
    }
    
    @Test
    public void testAsymmetricEncryptionAlgorithmPartialPayload() throws Exception {
                      
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
        
        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.ks");

        // RSA v1.5 is not allowed unless explicitly configured
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/italy", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters).to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/italy", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_OAEP, ksParameters).to("mock:decrypted");
            }
        });
        
        MockEndpoint resultEndpoint = context.getEndpoint("mock:decrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(0);
        // verify that the message was encrypted before checking that it is decrypted
        xmlsecTestHelper.testEncryption(TestHelper.XML_FRAGMENT, context);
        
        resultEndpoint.assertIsSatisfied(100);
    }
    
    @Test
    public void testAsymmetricEncryptionAlgorithmPartialPayloadElement() throws Exception {
                      
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.ts");
        
        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.ks");

        // RSA v1.5 is not allowed unless explicitly configured
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .marshal().secureXML("//cheesesites/france/cheese", false, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters).to("mock:encrypted")
                    .unmarshal().secureXML("//cheesesites/france", false, "recipient", testCypherAlgorithm, XMLCipher.RSA_OAEP, ksParameters).to("mock:decrypted");
            }
        });
        
        MockEndpoint resultEndpoint = context.getEndpoint("mock:decrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(0);
        // verify that the message was encrypted before checking that it is decrypted
        xmlsecTestHelper.testEncryption(TestHelper.XML_FRAGMENT, context);
        
        resultEndpoint.assertIsSatisfied(100);
    }
}
