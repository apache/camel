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

import java.security.Security;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.crypto.cms.crypt.DefaultEnvelopedDataDecryptorConfiguration;
import org.apache.camel.component.crypto.cms.crypt.DefaultKeyTransRecipientInfo;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataDecryptor;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataEncryptor;
import org.apache.camel.component.crypto.cms.crypt.EnvelopedDataEncryptorConfiguration;
import org.apache.camel.component.crypto.cms.sig.DefaultSignedDataVerifierConfiguration;
import org.apache.camel.component.crypto.cms.sig.DefaultSignerInfo;
import org.apache.camel.component.crypto.cms.sig.SignedDataCreator;
import org.apache.camel.component.crypto.cms.sig.SignedDataCreatorConfiguration;
import org.apache.camel.component.crypto.cms.sig.SignedDataVerifier;
import org.apache.camel.component.crypto.cms.util.KeystoreUtil;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

public class ProcessorsTest extends CamelTestSupport {

    private SimpleRegistry simpleReg;

    @BeforeClass
    public static void setUpProvider() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void execute() throws Exception {

        String message = "Testmessage";
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(message);
        sendBody("direct:start", message);

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

                String keystoreName = "system.jks";
                KeyStoreParameters keystore = KeystoreUtil.getKeyStoreParameters(keystoreName);

                DefaultKeyTransRecipientInfo recipient = new DefaultKeyTransRecipientInfo();
                recipient.setCertificateAlias("rsa");
                recipient.setKeyStoreParameters(keystore);

                EnvelopedDataEncryptorConfiguration enConf = new EnvelopedDataEncryptorConfiguration(context);
                enConf.setContentEncryptionAlgorithm("DESede/CBC/PKCS5Padding");
                enConf.setRecipient(recipient);
                enConf.setSecretKeyLength(192); // mandatory
                enConf.init();
                EnvelopedDataEncryptor encryptor = new EnvelopedDataEncryptor(enConf);

                DefaultEnvelopedDataDecryptorConfiguration conf = new DefaultEnvelopedDataDecryptorConfiguration();
                conf.setKeyStoreParameters(keystore);
                EnvelopedDataDecryptor decryptor = new EnvelopedDataDecryptor(conf);

                DefaultSignerInfo signerInfo = new DefaultSignerInfo();

                signerInfo.setIncludeCertificates(true); // optional default
                                                         // value is true
                signerInfo.setSignatureAlgorithm("SHA256withRSA"); // mandatory
                signerInfo.setPrivateKeyAlias("rsa");
                signerInfo.setKeyStoreParameters(keystore);

                SignedDataCreatorConfiguration config = new SignedDataCreatorConfiguration(new DefaultCamelContext());
                config.addSigner(signerInfo);
                config.setIncludeContent(true); // optional default value is
                                                // true
                config.init();
                SignedDataCreator signer = new SignedDataCreator(config);

                DefaultSignedDataVerifierConfiguration verifierConf = new DefaultSignedDataVerifierConfiguration();
                verifierConf.setKeyStoreParameters(keystore);

                SignedDataVerifier verifier = new SignedDataVerifier(verifierConf);

                from("direct:start").to("log:before").process(signer).process(encryptor).to("log:signed_encrypted").process(decryptor).process(verifier).convertBodyTo(String.class)
                    .to("log:after").to("mock:result");

            }
        };
    }
}
