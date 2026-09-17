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

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.utils.EncryptionConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the Java constant names exposed by the model's {@code @Metadata(enums=...)} annotations (e.g.
 * {@code "AES_256_GCM"}, {@code "RSA_OAEP"}, {@code "SHA256"}, {@code "MGF1_SHA256"}) are accepted by
 * {@link XMLSecurityDataFormat} at runtime as aliases for the corresponding W3C URIs.
 *
 * <p>
 * Before the fix, passing a constant name to {@link XMLSecurityDataFormat#setXmlCipherAlgorithm(String)} would store it
 * verbatim and then cause {@code XMLCipher.getInstance("AES_256_GCM")} to throw
 * {@code XMLEncryptionException: Null or empty transformation}, because XMLCipher only understands URIs.
 */
class XmlSecurityConstantNameTest extends CamelTestSupport {

    TestHelper xmlsecTestHelper = new TestHelper();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    public void doPostSetup() {
        context.getGlobalOptions().put(XmlConverter.OUTPUT_PROPERTIES_PREFIX + javax.xml.transform.OutputKeys.ENCODING,
                "UTF-8");
    }

    // -- resolveAlgorithm unit tests -------------------------------------------------------

    @Test
    void resolveAlgorithmReturnsMappedUri() {
        assertEquals(XMLCipher.AES_256_GCM, XMLSecurityDataFormat.resolveAlgorithm("AES_256_GCM"));
        assertEquals(XMLCipher.AES_128_GCM, XMLSecurityDataFormat.resolveAlgorithm("AES_128_GCM"));
        assertEquals(XMLCipher.AES_128, XMLSecurityDataFormat.resolveAlgorithm("AES_128"));
        assertEquals(XMLCipher.AES_192, XMLSecurityDataFormat.resolveAlgorithm("AES_192"));
        assertEquals(XMLCipher.TRIPLEDES, XMLSecurityDataFormat.resolveAlgorithm("TRIPLEDES"));
        assertEquals(XMLCipher.SEED_128, XMLSecurityDataFormat.resolveAlgorithm("SEED_128"));
        assertEquals(XMLCipher.CAMELLIA_128, XMLSecurityDataFormat.resolveAlgorithm("CAMELLIA_128"));
        assertEquals(XMLCipher.RSA_v1dot5, XMLSecurityDataFormat.resolveAlgorithm("RSA_v1dot5"));
        assertEquals(XMLCipher.RSA_OAEP, XMLSecurityDataFormat.resolveAlgorithm("RSA_OAEP"));
        assertEquals(XMLCipher.RSA_OAEP_11, XMLSecurityDataFormat.resolveAlgorithm("RSA_OAEP_11"));
        assertEquals(XMLCipher.SHA1, XMLSecurityDataFormat.resolveAlgorithm("SHA1"));
        assertEquals(XMLCipher.SHA256, XMLSecurityDataFormat.resolveAlgorithm("SHA256"));
        assertEquals(XMLCipher.SHA512, XMLSecurityDataFormat.resolveAlgorithm("SHA512"));
        assertEquals(EncryptionConstants.MGF1_SHA1, XMLSecurityDataFormat.resolveAlgorithm("MGF1_SHA1"));
        assertEquals(EncryptionConstants.MGF1_SHA256, XMLSecurityDataFormat.resolveAlgorithm("MGF1_SHA256"));
        assertEquals(EncryptionConstants.MGF1_SHA512, XMLSecurityDataFormat.resolveAlgorithm("MGF1_SHA512"));
    }

    @Test
    void resolveAlgorithmPassesThroughUri() {
        // raw W3C URIs must pass through unchanged so existing routes keep working
        assertEquals(XMLCipher.AES_256_GCM, XMLSecurityDataFormat.resolveAlgorithm(XMLCipher.AES_256_GCM));
        assertEquals(XMLCipher.RSA_OAEP, XMLSecurityDataFormat.resolveAlgorithm(XMLCipher.RSA_OAEP));
        assertEquals(XMLCipher.SHA256, XMLSecurityDataFormat.resolveAlgorithm(XMLCipher.SHA256));
    }

    @Test
    void resolveAlgorithmHandlesNull() {
        assertEquals(null, XMLSecurityDataFormat.resolveAlgorithm(null));
    }

    // -- end-to-end encrypt/decrypt tests using constant names ----------------------------

    /**
     * Symmetric AES-256-GCM using the constant name "AES_256_GCM" (the YAML DSL schema value).
     */
    @Test
    void testAES256GCMByConstantName() throws Exception {
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(256);
        SecretKey key = keygen.generateKey();

        XMLSecurityDataFormat df = new XMLSecurityDataFormat();
        df.setPassPhrase(key.getEncoded());
        df.setSecureTagContents(true);
        df.setSecureTag("//cheesesites/italy/cheese");
        // Use the constant name, not the URI
        df.setXmlCipherAlgorithm("AES_256_GCM");

        // verify the setter resolved it to the URI
        assertEquals(XMLCipher.AES_256_GCM, df.getXmlCipherAlgorithm());

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(df).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(df).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    /**
     * Symmetric AES-128 using the constant name "AES_128".
     */
    @Test
    void testAES128ByConstantName() throws Exception {
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        keygen.init(128);
        SecretKey key = keygen.generateKey();

        XMLSecurityDataFormat df = new XMLSecurityDataFormat();
        df.setPassPhrase(key.getEncoded());
        df.setSecureTagContents(true);
        df.setSecureTag("//cheesesites/italy/cheese");
        df.setXmlCipherAlgorithm("AES_128");

        assertEquals(XMLCipher.AES_128, df.getXmlCipherAlgorithm());

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .marshal(df).to("mock:encrypted")
                        .log("Body: + ${body}")
                        .unmarshal(df).to("mock:decrypted");
            }
        });
        xmlsecTestHelper.testDecryption(context);
    }

    /**
     * Asymmetric RSA-OAEP using the constant names "RSA_OAEP" and "AES_128".
     */
    @Test
    void testRSAOAEPByConstantName() throws Exception {
        XMLSecurityDataFormat sendingDataFormat = new XMLSecurityDataFormat();
        sendingDataFormat.setSecureTagContents(true);
        sendingDataFormat.setSecureTag("//cheesesites/italy/cheese");
        sendingDataFormat.setXmlCipherAlgorithm("AES_128");      // constant name
        sendingDataFormat.setKeyCipherAlgorithm("RSA_OAEP");     // constant name
        sendingDataFormat.setRecipientKeyAlias("recipient");

        assertEquals(XMLCipher.AES_128, sendingDataFormat.getXmlCipherAlgorithm());
        assertEquals(XMLCipher.RSA_OAEP, sendingDataFormat.getKeyCipherAlgorithm());

        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");
        sendingDataFormat.setKeyOrTrustStoreParameters(tsParameters);

        XMLSecurityDataFormat receivingDataFormat = new XMLSecurityDataFormat();
        receivingDataFormat.setKeyCipherAlgorithm("RSA_OAEP");   // constant name
        receivingDataFormat.setRecipientKeyAlias("recipient");
        receivingDataFormat.setSecureTag("//cheesesites/italy/cheese");

        assertEquals(XMLCipher.RSA_OAEP, receivingDataFormat.getKeyCipherAlgorithm());

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

    /**
     * Asymmetric RSA-OAEP-11 with SHA-256 digest and MGF1-SHA-256, all specified as constant names.
     */
    @Test
    void testRSAOAEP11WithDigestAndMGFByConstantName() throws Exception {
        XMLSecurityDataFormat sendingDataFormat = new XMLSecurityDataFormat();
        sendingDataFormat.setSecureTagContents(true);
        sendingDataFormat.setSecureTag("//cheesesites/italy/cheese");
        sendingDataFormat.setXmlCipherAlgorithm("AES_128");      // constant name
        sendingDataFormat.setKeyCipherAlgorithm("RSA_OAEP_11");  // constant name
        sendingDataFormat.setDigestAlgorithm("SHA256");          // constant name
        sendingDataFormat.setMgfAlgorithm("MGF1_SHA256");        // constant name
        sendingDataFormat.setRecipientKeyAlias("recipient");

        assertEquals(XMLCipher.AES_128, sendingDataFormat.getXmlCipherAlgorithm());
        assertEquals(XMLCipher.RSA_OAEP_11, sendingDataFormat.getKeyCipherAlgorithm());
        assertEquals(XMLCipher.SHA256, sendingDataFormat.getDigestAlgorithm());
        assertEquals(EncryptionConstants.MGF1_SHA256, sendingDataFormat.getMgfAlgorithm());

        KeyStoreParameters tsParameters = new KeyStoreParameters();
        tsParameters.setPassword("password");
        tsParameters.setResource("sender.truststore");
        sendingDataFormat.setKeyOrTrustStoreParameters(tsParameters);

        XMLSecurityDataFormat receivingDataFormat = new XMLSecurityDataFormat();
        receivingDataFormat.setKeyCipherAlgorithm("RSA_OAEP_11");
        receivingDataFormat.setDigestAlgorithm("SHA256");
        receivingDataFormat.setMgfAlgorithm("MGF1_SHA256");
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
