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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.xml.transform.OutputKeys;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.xml.security.encryption.XMLCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of the encryptXML data format.
 */
public class XMLSecurityDataFormatTest extends CamelTestSupport {

    protected static String testCypherAlgorithm = XMLCipher.AES_128;

    TestHelper xmlsecTestHelper = new TestHelper();

    private Key defaultKey;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        context.getGlobalOptions().put(XmlConverter.OUTPUT_PROPERTIES_PREFIX + OutputKeys.ENCODING, "UTF-8");
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        defaultKey = keyGenerator.generateKey();
    }

    /*
     * Encryption Tests
     */

    @Test
    public void testFullPayloadXMLEncryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity(defaultKey.getEncoded())
                        .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testPartialPayloadXMLContentEncryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("//cheesesites/italy/cheese", true, defaultKey.getEncoded())
                        .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testPartialPayloadMultiNodeXMLContentEncryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("//cheesesites/*/cheese", true, defaultKey.getEncoded())
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
                (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17 };

        final String passCode = new String(bits128);
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("//cheesesites/netherlands", false, passCode, XMLCipher.AES_128)
                        .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testPartialPayloadXMLElementEncryptionWithByteKeyAndAlgorithm() throws Exception {
        final byte[] bits192 = {
                (byte) 0x24, (byte) 0xf2, (byte) 0xd3, (byte) 0x45,
                (byte) 0xc0, (byte) 0x75, (byte) 0xb1, (byte) 0x00,
                (byte) 0x30, (byte) 0xd4, (byte) 0x3d, (byte) 0xf5,
                (byte) 0x6d, (byte) 0xaa, (byte) 0x7d, (byte) 0xc2,
                (byte) 0x85, (byte) 0x32, (byte) 0x2a, (byte) 0xb6,
                (byte) 0xfe, (byte) 0xed, (byte) 0xbe, (byte) 0xef };

        final Charset passCodeCharset = StandardCharsets.UTF_8;
        final String passCode = new String(bits192, passCodeCharset);
        byte[] bytes = passCode.getBytes(passCodeCharset);
        assertNotEquals(bits192.length, bytes.length);
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("//cheesesites/netherlands", false, bits192, XMLCipher.AES_192)
                        .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testFullPayloadAsymmetricKeyEncryption() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

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

    @Test
    public void testPartialPayloadAsymmetricKeyEncryptionWithContextTruststoreProperties() throws Exception {
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal()
                        .xmlSecurity("//cheesesites/italy/cheese", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5,
                                tsParameters)
                        .to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testAsymmetricEncryptionAddKeyValue() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

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
        NodeList nodeList = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "RSAKeyValue");
        assertTrue(nodeList.getLength() > 0);
    }

    @Test
    public void testAsymmetricEncryptionNoKeyValue() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

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
        NodeList nodeList = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "RSAKeyValue");
        assertEquals(0, nodeList.getLength());
    }

    /*
    * Decryption Tests
    */
    @Test
    public void testFullPayloadXMLDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity(defaultKey.getEncoded()).to("mock:encrypted")
                        .unmarshal().xmlSecurity(defaultKey.getEncoded()).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testPartialPayloadXMLContentDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("//cheesesites/italy/cheese", true, defaultKey.getEncoded()).to("mock:encrypted")
                        .unmarshal().xmlSecurity("//cheesesites/italy/cheese", true, defaultKey.getEncoded())
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testPartialPayloadMultiNodeXMLContentDecryption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("//cheesesites/*/cheese", true, defaultKey.getEncoded()).to("mock:encrypted")
                        .unmarshal().xmlSecurity("//cheesesites/*/cheese", true, defaultKey.getEncoded()).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testPartialPayloadXMLElementDecryptionWithKey() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("//cheesesites/france/cheese", false, defaultKey.getEncoded())
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("//cheesesites/france", false, defaultKey.getEncoded()).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testXMLElementDecryptionWithoutEncryptedKey() throws Exception {
        if (!TestHelper.HAS_3DES) {
            return;
        }
        String passPhrase = "this is a test passphrase";

        byte[] bytes = passPhrase.getBytes();
        final byte[] keyBytes = Arrays.copyOf(bytes, 24);
        for (int j = 0, k = 16; j < 8;) {
            keyBytes[k++] = keyBytes[j++];
        }

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("timer://foo?period=5000&repeatCount=1")
                        .to("language:constant:resource:classpath:org/apache/camel/component/xmlsecurity/EncryptedMessage.xml")
                        .unmarshal()
                        .xmlSecurity("/*[local-name()='Envelope']/*[local-name()='Body']",
                                true, keyBytes, XMLCipher.TRIPLEDES)
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryptionNoEncryptedKey(context);
    }

    @Test
    public void testPartialPayloadXMLContentDecryptionWithKeyAndAlgorithm() throws Exception {
        final byte[] bits128 = {
                (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
                (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
                (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
                (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17 };
        final String passCode = new String(bits128);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("//cheesesites/italy", true, passCode, XMLCipher.AES_128).to("mock:encrypted")
                        .unmarshal().xmlSecurity("//cheesesites/italy", true, passCode, XMLCipher.AES_128).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testFullPayloadAsymmetricKeyDecryption() throws Exception {

        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, ksParameters)
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testFullPayloadAsymmetricKeyDecryptionWithKeyPassword() throws Exception {

        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient-with-key-pass.keystore");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, ksParameters,
                                "keyPassword")
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testPartialPayloadAsymmetricKeyDecryption() throws Exception {
        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("ns1", "http://cheese.xmlsecurity.camel.apache.org/");

        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal()
                        .xmlSecurity("//ns1:cheesesites/italy", namespaces, true, "recipient", testCypherAlgorithm,
                                XMLCipher.RSA_v1dot5, tsParameters)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("//ns1:cheesesites/italy", namespaces, true, "recipient", testCypherAlgorithm,
                                XMLCipher.RSA_v1dot5, ksParameters)
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(TestHelper.NS_XML_FRAGMENT, context);
    }

    @Test
    public void testPartialPayloadAsymmetricKeyDecryptionCustomNS() throws Exception {
        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("cust", "http://cheese.xmlsecurity.camel.apache.org/");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal()
                        .xmlSecurity("//cust:cheesesites/italy", namespaces, true, "recipient", testCypherAlgorithm,
                                XMLCipher.RSA_v1dot5, tsParameters)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("//cust:cheesesites/italy", namespaces, true, "recipient", testCypherAlgorithm,
                                XMLCipher.RSA_v1dot5, ksParameters)
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(TestHelper.NS_XML_FRAGMENT, context);
    }

    @Test
    public void testAsymmetricEncryptionAlgorithmFullPayload() throws Exception {

        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        // RSA v1.5 is not allowed unless explicitly configured
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5, tsParameters)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_OAEP, ksParameters)
                        .to("mock:decrypted");
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
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        // RSA v1.5 is not allowed unless explicitly configured
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal()
                        .xmlSecurity("//cheesesites/italy", true, "recipient", testCypherAlgorithm, XMLCipher.RSA_v1dot5,
                                tsParameters)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("//cheesesites/italy", true, "recipient", testCypherAlgorithm,
                                XMLCipher.RSA_OAEP, ksParameters)
                        .to("mock:decrypted");
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
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        // RSA v1.5 is not allowed unless explicitly configured
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal()
                        .xmlSecurity("//cheesesites/france/cheese", false, "recipient", testCypherAlgorithm,
                                XMLCipher.RSA_v1dot5,
                                tsParameters)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("//cheesesites/france", false, "recipient", testCypherAlgorithm,
                                XMLCipher.RSA_OAEP, ksParameters)
                        .to("mock:decrypted");
            }
        });

        MockEndpoint resultEndpoint = context.getEndpoint("mock:decrypted", MockEndpoint.class);
        resultEndpoint.setExpectedMessageCount(0);
        // verify that the message was encrypted before checking that it is decrypted
        xmlsecTestHelper.testEncryption(TestHelper.XML_FRAGMENT, context);

        resultEndpoint.assertIsSatisfied(100);
    }
}
