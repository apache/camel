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
package org.apache.camel.component.mllp;

import java.io.File;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.*;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Does mTLS connection with MLLP and asserts that the headers are properly set.
 */
public class MllpMutualTlsConnectionAndHeaderBase extends CamelTestSupport {

    public static final String WANTS_CLIENT_AUTHENTICATION = "sslContextParametersWantsClientAuthentication";
    public static final String REQUIRES_CLIENT_AUTHENTICATION = "sslContextParametersRequiresClientAuthentication";
    public static final String NO_CLIENT_AUTHENTICATION = "sslContextParametersNoClientAuthentication";
    public static final String WITH_ONLY_TRUSTSTORE = "sslContextParametersWithOnlyTruststore";

    public static final String TEST_PAYLOAD = "MSH|^~\\&|CLIENT|TEST|SERVER|ACK|20231118120000||ADT^A01|123456|T|2.6\r" +
                                              "EVN|A01|20231118120000\r" +
                                              "PID|1|12345|67890||DOE^JOHN||19800101|M|||123 Main St^^Springfield^IL^62704||(555)555-5555|||||S\r"
                                              +
                                              "PV1|1|O\r";

    private final String sslContextParamtersUsedInTestRoute;

    protected String expectedCertSubjectName;
    protected String expectedCertIssuerName;
    protected String expectedCertSerialNo;
    protected Date expectedCertNotBefore;
    protected Date expectedCertNotAfter;

    @RegisterExtension
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    protected MockEndpoint result;

    protected MllpMutualTlsConnectionAndHeaderBase(String sslContextParametersUsedInTestRoute) {
        this.sslContextParamtersUsedInTestRoute = sslContextParametersUsedInTestRoute;
    }

    /**
     * Creates test route.
     *
     * @return RouteBuilder.
     */
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                fromF(assembleEndpointUri(sslContextParamtersUsedInTestRoute))
                        .log(LoggingLevel.INFO, "mllp-ssl-sender", "Received Message: ${body}")
                        .to(result);
            }
        };
    }

    /**
     * Creates an SSLContextParameters object with a key and truststore so that mTLS is conducted.
     *
     * @return           SSLContextParamters with both keystore and truststore paramters.
     * @throws Exception if anything goes wrong and then should fail the test.
     */
    private SSLContextParameters createSslContextParameters(ClientAuthentication clientAuthentication) throws Exception {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("password");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("password");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        extractExpectedSSLCertHeaderValuesFromActualCertificate(ksp);

        sslContextParameters.setServerParameters(new SSLContextServerParameters());
        sslContextParameters.getServerParameters().setClientAuthentication(clientAuthentication.name());

        return sslContextParameters;
    }

    /**
     * Extracts values from the certificate the client will use to be used for validation during the tests.
     *
     * @param  ksp       KeyStoreParameters object created from SSLContextParameters creation. Holds the certificate
     *                   information.
     * @throws Exception if anything goes wrong and then should fail the test.
     */
    private void extractExpectedSSLCertHeaderValuesFromActualCertificate(KeyStoreParameters ksp)
            throws Exception {
        File certFile = new File(URI.create(ksp.getResource()));
        char[] password = ksp.getPassword().toCharArray();

        KeyStore ks = KeyStore.getInstance(certFile, password);
        X509Certificate certificate = (X509Certificate) ks.getCertificate("testKey");

        expectedCertIssuerName = certificate.getIssuerX500Principal().toString();
        expectedCertSubjectName = certificate.getSubjectX500Principal().toString();
        expectedCertSerialNo = certificate.getSerialNumber().toString();
        expectedCertNotBefore = certificate.getNotBefore();
        expectedCertNotAfter = certificate.getNotAfter();

        // Be really sure the expected headers aren't null as expectedHeaderReceived can accept null values,
        // which could cause false positive test results when the mocks use these for comparing with the actual values.
        Assertions.assertNotNull(expectedCertIssuerName);
        Assertions.assertNotNull(expectedCertSubjectName);
        Assertions.assertNotNull(expectedCertSerialNo);
        Assertions.assertNotNull(expectedCertNotBefore);
        Assertions.assertNotNull(expectedCertNotAfter);
    }

    /**
     * Creates a SSLContextParameters object with only a truststore. With this, the client will only do TLS connection,
     * it will not send its own certificate for validation.
     *
     * @return           SSLContextParameter object with only a truststore configured.
     * @throws Exception if anything goes wrong and then should fail the test.
     */
    private SSLContextParameters createSslContextParametersWithOnlyTruststore() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("password");

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }

    /**
     * Registers sslContextParametes, both of them, to camel context.
     *
     * @return           camelContext.
     * @throws Exception if anything goes wrong and then should fail the test.
     */
    @Override
    protected CamelContext createCamelContext() throws Exception {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());

        context.getRegistry().bind(WANTS_CLIENT_AUTHENTICATION,
                createSslContextParameters(ClientAuthentication.WANT));
        context.getRegistry().bind(REQUIRES_CLIENT_AUTHENTICATION,
                createSslContextParameters(ClientAuthentication.REQUIRE));
        context.getRegistry().bind(NO_CLIENT_AUTHENTICATION,
                createSslContextParameters(ClientAuthentication.NONE));

        SSLContextParameters sslContextParametersWithOnlyTruststore = createSslContextParametersWithOnlyTruststore();
        context.getRegistry().bind(WITH_ONLY_TRUSTSTORE, sslContextParametersWithOnlyTruststore);
        return context;
    }

    protected String assembleEndpointUri(String sslContextParameters) {
        return String.format("mllp://%s:%d?sslContextParameters=#%s", mllpClient.getMllpHost(), mllpClient.getMllpPort(),
                sslContextParameters);
    }
}
