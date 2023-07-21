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
package org.apache.camel.component.as2;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.as2.api.AS2EncryptionAlgorithm;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for testing connection to a public 3rd party AS2 server. This class gives more info for camel-as2
 * connectivity to a remote server compared to HTTPS connection to localhost server. Eventually test class will be
 * committed with @Disabled annotation due to the test can bring dependency on 3rd party resource.
 */
@Disabled("Run this test manually")
public class MendelsonSslEndpointManualTest extends AbstractAS2ITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MendelsonSslEndpointManualTest.class);
    private static HostnameVerifier hostnameVerifier;

    private MendelsonCertLoader mendelsonCertLoader;
    private final Properties props = new Properties();

    @BeforeAll
    public void setupTest() {
        InputStream is = MendelsonSslEndpointManualTest.class
                .getClassLoader().getResourceAsStream("test-server.properties");
        try {
            props.load(is);
        } catch (IOException e) {
            LOG.error("Failed to load properties from file test_server.properties");
        }

        // NoopHostnameVerifier needed since we connect to non-localhost remote AS2 server
        hostnameVerifier = new NoopHostnameVerifier();
        mendelsonCertLoader = new MendelsonCertLoader();
        mendelsonCertLoader.setupCertificateChain(props.getProperty("mendelson.certificate.path"),
                props.getProperty("mendelson.keystore.path"),
                props.getProperty("mendelson.keystore.password"));
        mendelsonCertLoader.setupSslContext(props.getProperty("mendelson.keystore.path"),
                props.getProperty("mendelson.keystore.password"));
    }

    @Test
    public void testCreateEndpointAndSendViaHTTPS() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.start();

        String methodName = "send";
        AS2ApiName as2ApiNameClient = AS2ApiName.CLIENT;

        AS2Configuration endpointConfiguration = new AS2Configuration();
        endpointConfiguration.setApiName(as2ApiNameClient);
        endpointConfiguration.setMethodName(methodName);

        endpointConfiguration.setAs2MessageStructure(AS2MessageStructure.SIGNED_ENCRYPTED);
        endpointConfiguration.setSigningAlgorithm(AS2SignatureAlgorithm.SHA3_256WITHRSA);
        endpointConfiguration.setEncryptingAlgorithm(AS2EncryptionAlgorithm.DES_EDE3_CBC);
        endpointConfiguration.setSigningCertificateChain(mendelsonCertLoader.getChain());
        endpointConfiguration.setSigningPrivateKey(mendelsonCertLoader.getPrivateKey());
        endpointConfiguration.setEncryptingCertificateChain(mendelsonCertLoader.getChain());

        endpointConfiguration.setAs2Version(props.getProperty("as2.version"));
        endpointConfiguration.setAs2To(props.getProperty("as2.as2to"));
        endpointConfiguration.setAs2From(props.getProperty("as2.as2from"));
        endpointConfiguration.setFrom(props.getProperty("as2.from"));
        endpointConfiguration.setSubject(props.getProperty("as2.subject"));
        endpointConfiguration.setSigningAlgorithm(AS2SignatureAlgorithm.MD2WITHRSA);
        endpointConfiguration.setEdiMessageTransferEncoding(props.getProperty("as2.transfer.encoding"));
        endpointConfiguration.setAttachedFileName(props.getProperty("as2.attached.filename"));

        endpointConfiguration.setSslContext(mendelsonCertLoader.getSslContext());
        endpointConfiguration.setHostnameVerifier(hostnameVerifier);

        AS2Component as2Component = new AS2Component();
        as2Component.setCamelContext(camelContext);
        as2Component.setConfiguration(endpointConfiguration);
        as2Component.start();

        AS2Endpoint endpoint = (AS2Endpoint) as2Component
                .createEndpoint("as2://client/send?targetHostName=" + props.getProperty("as2.remote.host") +
                                "&targetPortNumber=" + props.getProperty("as2.remote.port") +
                                "&inBody=ediMessage" +
                                "&requestUri=" + props.getProperty("as2.remote.uri") +
                                "&ediMessageContentType=" + props.getProperty("as2.content.type") +
                                "&signingAlgorithm=" + props.getProperty("as2.signing.algorithm"));

        Exchange out
                = camelContext.createProducerTemplate().request(endpoint,
                        exchange -> exchange.getIn().setBody(props.getProperty("as2.edi.message")));
        Throwable cause = out.getException();
        Assertions.assertNull(cause);
        LOG.debug(
                "Sending done. If you used Mendelson settings for connection, " +
                  "you can check your message in http://testas2.mendelson-e-c.com:8080/webas2/ " +
                  "Login guest, password guest");
    }
}
