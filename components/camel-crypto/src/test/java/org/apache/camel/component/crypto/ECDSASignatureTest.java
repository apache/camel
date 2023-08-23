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
package org.apache.camel.component.crypto;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.isJavaVendor;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class ECDSASignatureTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ECDSASignatureTest.class);

    private String payload = "Dear Alice, Rest assured it's me, signed Bob";
    private boolean ibmJDK;
    private PrivateKey privateKey;
    private X509Certificate x509;
    private boolean canRun = true;

    public ECDSASignatureTest() {

        // This test fails with the IBM JDK
        if (isJavaVendor("IBM")) {
            ibmJDK = true;
        }

        // see if we can load the keystore et all
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream in = ECDSASignatureTest.class.getResourceAsStream("/org/apache/camel/component/crypto/ecdsa.jks");
            keyStore.load(in, "security".toCharArray());
            privateKey = (PrivateKey) keyStore.getKey("ECDSA", "security".toCharArray());
            x509 = (X509Certificate) keyStore.getCertificate("ECDSA");
        } catch (Exception e) {
            LOG.warn("Cannot setup keystore for running this test due {}. This test is skipped.", e.getMessage(), e);
            canRun = false;
        }
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        if (ibmJDK || !canRun) {
            return new RouteBuilder[] {};
        }

        return new RouteBuilder[] { new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ecdsa-sha1

                // we can set the keys explicitly on the endpoint instances.
                context.getEndpoint("crypto:sign:ecdsa-sha1?algorithm=SHA1withECDSA", DigitalSignatureEndpoint.class)
                        .setPrivateKey(privateKey);
                context.getEndpoint("crypto:verify:ecdsa-sha1?algorithm=SHA1withECDSA", DigitalSignatureEndpoint.class)
                        .setPublicKey(x509.getPublicKey());

                from("direct:ecdsa-sha1")
                        .to("crypto:sign:ecdsa-sha1?algorithm=SHA1withECDSA")
                        .to("crypto:verify:ecdsa-sha1?algorithm=SHA1withECDSA")
                        .to("mock:result");
                // END SNIPPET: ecdsa-sha1
            }
        } };
    }

    @Test
    void testECDSASHA1() throws Exception {
        assumeFalse(ibmJDK || !canRun, "Test preconditions failed: ibmJDK=" + ibmJDK + ", canRun=" + canRun);

        setupMock();
        sendBody("direct:ecdsa-sha1", payload);
        MockEndpoint.assertIsSatisfied(context);
    }

    private MockEndpoint setupMock() {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(payload);
        return mock;
    }

    public Exchange doTestSignatureRoute(RouteBuilder builder) throws Exception {
        return doSignatureRouteTest(builder, null, Collections.<String, Object> emptyMap());
    }

    public Exchange doSignatureRouteTest(RouteBuilder builder, Exchange e, Map<String, Object> headers) throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(builder);
            context.start();

            MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
            mock.setExpectedMessageCount(1);

            ProducerTemplate template = context.createProducerTemplate();
            if (e != null) {
                template.send("direct:in", e);
            } else {
                template.sendBodyAndHeaders("direct:in", payload, headers);
            }
            MockEndpoint.assertIsSatisfied(ECDSASignatureTest.this.context);
            return mock.getReceivedExchanges().get(0);
        } finally {
            context.stop();
        }
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        disableJMX();
        super.setUp();
    }

}
