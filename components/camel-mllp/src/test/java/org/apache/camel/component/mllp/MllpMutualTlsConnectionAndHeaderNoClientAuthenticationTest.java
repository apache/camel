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

import org.junit.jupiter.api.Test;

/**
 * Does mTLS connection with MLLP and asserts that the headers are properly set.
 */
class MllpMutualTlsConnectionAndHeaderNoClientAuthenticationTest extends MllpMutualTlsConnectionAndHeaderBase {
    MllpMutualTlsConnectionAndHeaderNoClientAuthenticationTest() {
        super(NO_CLIENT_AUTHENTICATION);
    }

    /**
     * This test does TLS connection without a client sending its certificate, that is, no mTLS. In this case, none of
     * the MLLP_SSL_CLIENT_CERT* headers should exist as the client didn't provide a certificate.
     *
     * @throws Exception if anything goes wrong and then should fail the test.
     */
    @Test
    void testSendingTlsWithNoClientCertificateToMllpConsumerWithNoClientAuthentication() throws Exception {
        result.expectedBodiesReceived(TEST_PAYLOAD);

        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_SUBJECT_NAME, null);
        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_ISSUER_NAME, null);
        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_SERIAL_NO, null);
        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_NOT_BEFORE, null);
        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_NOT_AFTER, null);

        template.sendBody(assembleEndpointUri(WITH_ONLY_TRUSTSTORE), TEST_PAYLOAD);
        result.assertIsSatisfied();
    }

    /**
     * As the server is configured to not do any client authentication, make sure the MLLP_SSL_CLIENT_CERT* headers are
     * not present.
     *
     * @throws Exception if anything goes wrong and then should fail the test.
     */
    @Test
    void testCommunicationWithConfiguredClientCertificateWithMllpConsumerWhichDoesNotDoClientAuthentication()
            throws Exception {
        result.expectedBodiesReceived(TEST_PAYLOAD);

        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_SUBJECT_NAME, null);
        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_ISSUER_NAME, null);
        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_SERIAL_NO, null);
        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_NOT_BEFORE, null);
        result.expectedHeaderReceived(MllpConstants.MLLP_SSL_CLIENT_CERT_NOT_AFTER, null);

        // Any sslContextParameter with a client certificate should be valid here, but the worst case scenario for this test
        // should be the context with the server flag for requiring client authentication. That flag should not matter,
        // and here is the proof it doesn't.
        template.sendBody(assembleEndpointUri(REQUIRES_CLIENT_AUTHENTICATION), TEST_PAYLOAD);
        result.assertIsSatisfied();

    }
}
