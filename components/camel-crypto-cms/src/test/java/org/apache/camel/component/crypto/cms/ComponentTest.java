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
package org.apache.camel.component.crypto.cms;

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.crypto.cms.crypt.DefaultKeyTransRecipientInfo;
import org.apache.camel.component.crypto.cms.exception.CryptoCmsVerifierCertificateNotValidException;
import org.apache.camel.component.crypto.cms.sig.DefaultSignerInfo;
import org.apache.camel.component.crypto.cms.util.KeystoreUtil;
import org.apache.camel.component.crypto.cms.util.TestAttributesGeneratorProvider;
import org.apache.camel.component.crypto.cms.util.TestOriginatorInformationProvider;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ComponentTest extends CamelTestSupport {

    private SimpleRegistry simpleReg;

    @Test
    public void execute() throws Exception {

        String message = "Testmessage";
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(message);
        sendBody("direct:start", message.getBytes("UTF-8"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void signedWithOutdatedCert() throws Exception {

        String message = "Testmessage";
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        MockEndpoint mockException = getMockEndpoint("mock:exception");
        mockException.expectedMessageCount(1);

        sendBody("direct:outdated", message.getBytes("UTF-8"));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void decryptAndVerify() throws Exception {

        InputStream input = this.getClass().getClassLoader().getResourceAsStream("signed_enveloped_other_CMS_vendor.binary");
        assertNotNull(input);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Testmessage");
        sendBody("direct:decryptAndVerify", input);

        assertMockEndpointsSatisfied();

        input.close();

    }

    @Test
    public void orignatorUnprotectedAttributes() throws Exception {

        String message = "Testmessage";
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(message);
        sendBody("direct:encryptDecryptOriginatorAttributes", message.getBytes("UTF-8"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        simpleReg = new SimpleRegistry();
        CamelContext context = new DefaultCamelContext(simpleReg);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setTracing(true);
                context.setStreamCaching(true);

                KeyStoreParameters keystore = KeystoreUtil.getKeyStoreParameters("system.jks");

                DefaultKeyTransRecipientInfo recipient = new DefaultKeyTransRecipientInfo();
                recipient.setCertificateAlias("rsa");
                recipient.setKeyStoreParameters(keystore);

                DefaultSignerInfo signerInfo = new DefaultSignerInfo();
                signerInfo.setIncludeCertificates(true); 
                signerInfo.setSignatureAlgorithm("SHA256withRSA"); 
                signerInfo.setPrivateKeyAlias("rsa");
                signerInfo.setKeyStoreParameters(keystore);

                DefaultSignerInfo signerInfo2 = new DefaultSignerInfo();
                signerInfo2.setSignatureAlgorithm("SHA256withDSA"); // mandatory
                signerInfo2.setPrivateKeyAlias("dsa");
                signerInfo2.setKeyStoreParameters(keystore);

                simpleReg.bind("keyStoreParameters", keystore);
                simpleReg.bind("signer1", signerInfo);
                simpleReg.bind("signer2", signerInfo2);
                simpleReg.bind("recipient1", recipient);

                onException(CryptoCmsVerifierCertificateNotValidException.class).handled(false).to("mock:exception");

                from("direct:start").to("crypto-cms:sign://testsign?signer=#signer1,#signer2&includeContent=true")
                    .to("crypto-cms:encrypt://testencrpyt?toBase64=true&recipient=#recipient1&contentEncryptionAlgorithm=DESede/CBC/PKCS5Padding&secretKeyLength=128")
                    // .to("file:target/test_signed_encrypted.base64")
                    .to("crypto-cms:decrypt://testdecrypt?fromBase64=true&keyStoreParameters=#keyStoreParameters")
                    .to("crypto-cms:verify://testverify?keyStoreParameters=#keyStoreParameters").convertBodyTo(String.class).to("log:after").to("mock:result");

                DefaultSignerInfo signerOutdated = new DefaultSignerInfo();
                signerOutdated.setIncludeCertificates(false);
                signerOutdated.setSignatureAlgorithm("SHA1withRSA");
                signerOutdated.setPrivateKeyAlias("outdated");
                signerOutdated.setKeyStoreParameters(keystore);
                simpleReg.bind("signerOutdated", signerOutdated);

                from("direct:outdated").to("crypto-cms:sign://outdated?signer=#signerOutdated&includeContent=true")
                    .to("crypto-cms:verify://outdated?keyStoreParameters=#keyStoreParameters").to("mock:result");

                from("direct:decryptAndVerify").to("crypto-cms:decrypt://testdecrypt?fromBase64=true&keyStoreParameters=#keyStoreParameters")
                    .to("crypto-cms:verify://testverify?keyStoreParameters=#keyStoreParameters").to("mock:result");

                TestOriginatorInformationProvider originatorInformationProvider = new TestOriginatorInformationProvider();
                TestAttributesGeneratorProvider attributesGeneratorProvider = new TestAttributesGeneratorProvider();
                simpleReg.bind("originatorInformationProvider1", originatorInformationProvider);
                simpleReg.bind("attributesGeneratorProvider1", attributesGeneratorProvider);

                from("direct:encryptDecryptOriginatorAttributes")
                    .to("crypto-cms:encrypt://testencrpyt?toBase64=true&recipient=#recipient1&contentEncryptionAlgorithm=DESede/CBC/PKCS5Padding&secretKeyLength=128&"
                        + "originatorInformationProvider=#originatorInformationProvider1&unprotectedAttributesGeneratorProvider=#attributesGeneratorProvider1")
                    .to("crypto-cms:decrypt://testdecrypt?fromBase64=true&keyStoreParameters=#keyStoreParameters").to("mock:result");
            }
        };
    }

    @Test(expected = IllegalStateException.class)
    public void wrongOperation() throws Exception {
        CryptoCmsComponent c = new CryptoCmsComponent(new DefaultCamelContext());
        c.createEndpoint("uri", "wrongoperation", null);
    }
}
