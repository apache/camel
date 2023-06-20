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
import java.util.HashMap;

import javax.xml.transform.OutputKeys;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.utils.EncryptionConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Some unit tests for XML Encryption 1.1 functionality
 */
public class XMLEncryption11Test extends CamelTestSupport {

    TestHelper xmlsecTestHelper = new TestHelper();

    public XMLEncryption11Test() throws Exception {
        //
        // BouncyCastle is required for GCM support
        //
        if (Security.getProvider("BC") == null) {
            Constructor<?> cons = null;
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

    /*
     * Encryption Tests
     */
    @Test
    public void testFullPayloadAsymmetricKeyEncryptionGCM() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_OAEP);
        xmlEncDataFormat.setKeyOrTrustStoreParameters(tsParameters);
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128_GCM);
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
    public void testFullPayloadAsymmetricKeyEncryptionSHA256() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_OAEP);
        xmlEncDataFormat.setKeyOrTrustStoreParameters(tsParameters);
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128);
        xmlEncDataFormat.setRecipientKeyAlias("recipient");
        xmlEncDataFormat.setDigestAlgorithm(XMLCipher.SHA256);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    @Test
    public void testFullPayloadAsymmetricKeyEncryptionMGF256() throws Exception {
        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_OAEP_11);
        xmlEncDataFormat.setKeyOrTrustStoreParameters(tsParameters);
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128);
        xmlEncDataFormat.setMgfAlgorithm(EncryptionConstants.MGF1_SHA256);
        xmlEncDataFormat.setRecipientKeyAlias("recipient");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted");
            }
        });
        xmlsecTestHelper.testEncryption(context);
    }

    /*
     * Decryption Tests
     */
    @Test
    public void testFullPayloadAsymmetricKeyDecryptionGCM() throws Exception {

        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("", true, "recipient", XMLCipher.AES_128_GCM, XMLCipher.RSA_OAEP, tsParameters)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("", true, "recipient", XMLCipher.AES_128_GCM, XMLCipher.RSA_OAEP, ksParameters)
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testFullPayloadAsymmetricKeyDecryptionSHA256() throws Exception {

        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal().xmlSecurity("", new HashMap<String, String>(), true, "recipient", XMLCipher.AES_128,
                                XMLCipher.RSA_OAEP, tsParameters, null, XMLCipher.SHA256)
                        .to("mock:encrypted")
                        .unmarshal().xmlSecurity("", new HashMap<String, String>(), true, "recipient", XMLCipher.AES_128,
                                XMLCipher.RSA_OAEP, ksParameters, null, XMLCipher.SHA256)
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    @Test
    public void testFullPayloadAsymmetricKeyDecryptionMGF256() throws Exception {

        final KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");

        final XMLSecurityDataFormat xmlEncDataFormat = new XMLSecurityDataFormat();
        xmlEncDataFormat.setKeyCipherAlgorithm(XMLCipher.RSA_OAEP_11);
        xmlEncDataFormat.setKeyOrTrustStoreParameters(tsParameters);
        xmlEncDataFormat.setXmlCipherAlgorithm(XMLCipher.AES_128);
        xmlEncDataFormat.setMgfAlgorithm(EncryptionConstants.MGF1_SHA256);
        xmlEncDataFormat.setRecipientKeyAlias("recipient");

        final KeyStoreParameters ksParameters = new KeyStoreParameters();
        ksParameters.setPassword("password");
        ksParameters.setResource("recipient.keystore");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(xmlEncDataFormat).to("mock:encrypted")
                        // .log("Body: + ${body}")
                        .unmarshal().xmlSecurity("", new HashMap<String, String>(), true, "recipient", XMLCipher.AES_128,
                                XMLCipher.RSA_OAEP, ksParameters)
                        .to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

}
