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

import java.lang.reflect.Constructor;
import java.security.Provider;
import java.security.Security;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.xml.transform.OutputKeys;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.xml.security.encryption.XMLCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test all available encryption algorithms
 */
public class EncryptionAlgorithmTest extends CamelTestSupport {

    TestHelper xmlsecTestHelper = new TestHelper();

    public EncryptionAlgorithmTest() throws Exception {
        // BouncyCastle is required for some algorithms
        if (Security.getProvider("BC") == null) {
            Constructor<?> cons;
            Class<?> c = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
            cons = c.getConstructor(new Class[] {});

            Provider provider = (java.security.Provider) cons.newInstance();
            Security.insertProviderAt(provider, 2);
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        context.getGlobalOptions().put(XmlConverter.OUTPUT_PROPERTIES_PREFIX + OutputKeys.ENCODING, "UTF-8");
    }

    @Test
    public void testAES128() throws Exception {
        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(128);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testAES128GCM() throws Exception {
        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(128);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128_GCM);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testAES192() throws Exception {
        assumeTrue(TestHelper.UNRESTRICTED_POLICIES_INSTALLED,
                "Test preconditions failed: UNRESTRICTED_POLICIES_INSTALLED=" + TestHelper.UNRESTRICTED_POLICIES_INSTALLED);

        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(192);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_192);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testAES192GCM() throws Exception {
        assumeTrue(TestHelper.UNRESTRICTED_POLICIES_INSTALLED,
                "Test preconditions failed: UNRESTRICTED_POLICIES_INSTALLED=" + TestHelper.UNRESTRICTED_POLICIES_INSTALLED);

        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(192);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_192_GCM);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testAES256() throws Exception {
        assumeTrue(TestHelper.UNRESTRICTED_POLICIES_INSTALLED,
                "Test preconditions failed: UNRESTRICTED_POLICIES_INSTALLED=" + TestHelper.UNRESTRICTED_POLICIES_INSTALLED);

        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(256);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_256);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testAES256GCM() throws Exception {
        assumeTrue(TestHelper.UNRESTRICTED_POLICIES_INSTALLED,
                "Test preconditions failed: UNRESTRICTED_POLICIES_INSTALLED=" + TestHelper.UNRESTRICTED_POLICIES_INSTALLED);

        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(256);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_256_GCM);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testTRIPLEDES() throws Exception {
        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("DESede");
        keygen.init(192);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.TRIPLEDES);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testSEED128() throws Exception {
        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("SEED");
        keygen.init(128);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.SEED_128);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testCAMELLIA128() throws Exception {
        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("CAMELLIA");
        keygen.init(128);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.CAMELLIA_128);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testCAMELLIA192() throws Exception {
        assumeTrue(TestHelper.UNRESTRICTED_POLICIES_INSTALLED,
                "Test preconditions failed: UNRESTRICTED_POLICIES_INSTALLED=" + TestHelper.UNRESTRICTED_POLICIES_INSTALLED);

        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("CAMELLIA");
        keygen.init(192);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.CAMELLIA_192);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testCAMELLIA256() throws Exception {
        assumeTrue(TestHelper.UNRESTRICTED_POLICIES_INSTALLED,
                "Test preconditions failed: UNRESTRICTED_POLICIES_INSTALLED=" + TestHelper.UNRESTRICTED_POLICIES_INSTALLED);

        // Set up the Key
        KeyGenerator keygen = KeyGenerator.getInstance("CAMELLIA");
        keygen.init(256);
        SecretKey key = keygen.generateKey();

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setPassPhrase(key.getEncoded());
        xmlEncDataFormat.setSecureTagContents(true);
        xmlEncDataFormat.setSecureTag("//cheesesites/italy/cheese");
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.CAMELLIA_256);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(xmlEncDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testRSAOAEPKW() throws Exception {
        final XMLSecurityDataFormat sendingDataFormat = new XMLSecurityDataFormat();
        sendingDataFormat.setSecureTagContents(true);
        sendingDataFormat.setSecureTag("//cheesesites/italy/cheese");
        sendingDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128);
        sendingDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_OAEP);
        sendingDataFormat.setRecipientKeyAlias("recipient");

        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");
        sendingDataFormat.setKeyOrTrustStoreParameters(tsParameters);

        final XMLSecurityDataFormat receivingDataFormat = new XMLSecurityDataFormat();
        receivingDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_OAEP);
        receivingDataFormat.setRecipientKeyAlias("recipient");
        receivingDataFormat.setSecureTag("//cheesesites/italy/cheese");

        KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");
        receivingDataFormat.setKeyOrTrustStoreParameters(ksParameters);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(sendingDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(receivingDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testRSAv15KW() throws Exception {
        final XMLSecurityDataFormat sendingDataFormat = new XMLSecurityDataFormat();
        sendingDataFormat.setSecureTagContents(true);
        sendingDataFormat.setSecureTag("//cheesesites/italy/cheese");
        sendingDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128);
        sendingDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_v1dot5);
        sendingDataFormat.setRecipientKeyAlias("recipient");

        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");
        sendingDataFormat.setKeyOrTrustStoreParameters(tsParameters);

        final XMLSecurityDataFormat receivingDataFormat = new XMLSecurityDataFormat();
        receivingDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_v1dot5);
        receivingDataFormat.setRecipientKeyAlias("recipient");
        receivingDataFormat.setSecureTag("//cheesesites/italy/cheese");

        KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");
        receivingDataFormat.setKeyOrTrustStoreParameters(ksParameters);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(sendingDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(receivingDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testRSAOAEP11KW() throws Exception {
        final XMLSecurityDataFormat sendingDataFormat = new XMLSecurityDataFormat();
        sendingDataFormat.setSecureTagContents(true);
        sendingDataFormat.setSecureTag("//cheesesites/italy/cheese");
        sendingDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128);
        sendingDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_OAEP_11);
        sendingDataFormat.setRecipientKeyAlias("recipient");

        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");
        sendingDataFormat.setKeyOrTrustStoreParameters(tsParameters);

        final XMLSecurityDataFormat receivingDataFormat = new XMLSecurityDataFormat();
        receivingDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_OAEP_11);
        receivingDataFormat.setRecipientKeyAlias("recipient");
        receivingDataFormat.setSecureTag("//cheesesites/italy/cheese");

        KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");
        receivingDataFormat.setKeyOrTrustStoreParameters(ksParameters);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(sendingDataFormat).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(receivingDataFormat).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }
}
