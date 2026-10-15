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
package org.apache.camel.component.milo.client.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.apache.camel.component.milo.call.TestCertificateFactory;
import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCertificateTrustTest {

    private static X509Certificate selfSignedServerCertificate;
    private static TestPki pki;

    @TempDir
    Path trustListDirectory;

    @BeforeAll
    static void createCertificates() throws Exception {
        final TestCertificateFactory certificateFactory = new TestCertificateFactory();
        final KeyPair keyPair = certificateFactory.createRsaSha256KeyPair();
        selfSignedServerCertificate = certificateFactory.createRsaSha256CertificateChain(keyPair)[0];
        pki = new TestPki("Camel Milo Test CA");
    }

    @Test
    void securedEndpointWithoutTrustConfigurationIsRefused() {
        final ServerCertificateTrust trust = new ServerCertificateTrust(new MiloClientConfiguration());

        final ServerCertificateTrust.NotConfiguredException e = assertThrows(
                ServerCertificateTrust.NotConfiguredException.class,
                () -> trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256)));

        assertTrue(e.getMessage().contains("trustListDirectory"), e.getMessage());
        assertTrue(e.getMessage().contains("certificateValidator"), e.getMessage());
        assertTrue(e.getMessage().contains("trustAllCertificates"), e.getMessage());
    }

    @Test
    void endpointWithoutSecurityDoesNotNeedTrustConfiguration() throws Exception {
        final ServerCertificateTrust trust = new ServerCertificateTrust(new MiloClientConfiguration());

        assertInstanceOf(CertificateValidator.InsecureCertificateValidator.class,
                trust.validatorFor(endpoint(SecurityPolicy.None)));
    }

    @Test
    void trustAllCertificatesAcceptsAnyServerCertificate() throws Exception {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        configuration.setTrustAllCertificates(true);
        final ServerCertificateTrust trust = new ServerCertificateTrust(configuration);

        final CertificateValidator validator = trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

        assertDoesNotThrow(() -> validator.validateCertificateChain(List.of(selfSignedServerCertificate), null, null));
    }

    @Test
    void certificateValidatorIsUsedBeforeOtherOptions() throws Exception {
        final CertificateValidator custom = (certificateChain, applicationUri, validHostNames) -> {
        };
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        configuration.setCertificateValidator(custom);
        configuration.setTrustListDirectory(this.trustListDirectory.toString());
        configuration.setTrustAllCertificates(true);

        try (ServerCertificateTrust trust = new ServerCertificateTrust(configuration)) {
            assertSame(custom, trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256)));
        }
    }

    @Test
    void trustListAcceptsTrustedServerCertificate() throws Exception {
        trust(selfSignedServerCertificate);

        try (ServerCertificateTrust trust = trustListTrust()) {
            final CertificateValidator validator = trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

            assertInstanceOf(DefaultClientCertificateValidator.class, validator);
            assertDoesNotThrow(
                    () -> validator.validateCertificateChain(List.of(selfSignedServerCertificate), null, null));
            // a reconnect uses the same trust list
            assertSame(validator, trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256)));
        }
    }

    @Test
    void trustListRejectsAndKeepsUntrustedServerCertificate() throws Exception {
        // the trust list holds a CA that did not issue the server certificate
        trust(new TestPki("Another Test CA").certificate());

        try (ServerCertificateTrust trust = trustListTrust()) {
            final CertificateValidator validator = trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

            // Milo reports a certificate without a chain up to a trusted certificate as a failed security check
            final UaException e = assertThrows(UaException.class,
                    () -> validator.validateCertificateChain(List.of(selfSignedServerCertificate), null, null));
            assertEquals(StatusCodes.Bad_SecurityChecksFailed, e.getStatusCode().getValue(), e.toString());
        }

        assertTrue(Files.isDirectory(this.trustListDirectory.resolve("trusted").resolve("certs")));
        assertTrue(Files.isDirectory(this.trustListDirectory.resolve("issuer").resolve("certs")));
        assertEquals(1, countRejected());
    }

    @Test
    void trustListAcceptsServerCertificateOfCaWithRevocationList() throws Exception {
        final X509Certificate serverCertificate = validServerCertificate();
        trust(pki.certificate());
        trustRevocationList(pki.revocationList());

        try (ServerCertificateTrust trust = trustListTrust()) {
            final CertificateValidator validator = trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

            assertDoesNotThrow(() -> validator.validateCertificateChain(
                    List.of(serverCertificate, pki.certificate()), TestPki.SERVER_APPLICATION_URI, null));
        }
    }

    @Test
    void trustListRefusesServerCertificateOfCaWithoutRevocationList() throws Exception {
        final X509Certificate serverCertificate = validServerCertificate();
        trust(pki.certificate());

        assertRefused(List.of(serverCertificate, pki.certificate()), TestPki.SERVER_APPLICATION_URI,
                StatusCodes.Bad_CertificateRevocationUnknown);
    }

    @Test
    void trustListRefusesRevokedServerCertificate() throws Exception {
        final X509Certificate serverCertificate = validServerCertificate();
        trust(pki.certificate());
        trustRevocationList(pki.revocationList(serverCertificate));

        assertRefused(List.of(serverCertificate, pki.certificate()), TestPki.SERVER_APPLICATION_URI,
                StatusCodes.Bad_CertificateRevoked);
    }

    @Test
    void trustListRefusesExpiredServerCertificate() throws Exception {
        final Instant now = Instant.now();
        final X509Certificate serverCertificate = TestPki.selfSignedServerCertificate(
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        trust(serverCertificate);

        assertRefused(List.of(serverCertificate), TestPki.SERVER_APPLICATION_URI, StatusCodes.Bad_CertificateTimeInvalid);
    }

    @Test
    void trustListRefusesServerCertificateOfAnotherApplication() throws Exception {
        final Instant now = Instant.now();
        final X509Certificate serverCertificate = TestPki.selfSignedServerCertificate(
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
        trust(serverCertificate);

        assertRefused(List.of(serverCertificate), "urn:camel:milo:test:other", StatusCodes.Bad_CertificateUriInvalid);
    }

    @Test
    void closedTrustListIsNotReused() throws Exception {
        final ServerCertificateTrust trust = trustListTrust();
        trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

        trust.close();

        assertThrows(IllegalStateException.class, () -> trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256)));
    }

    @Test
    void certificateFailuresAreFoundInTheCauseChain() {
        final ServerCertificateTrust.NotConfiguredException notConfigured
                = new ServerCertificateTrust.NotConfiguredException("not configured");
        final UaException untrusted = new UaException(StatusCodes.Bad_CertificateUntrusted, "untrusted");

        assertSame(notConfigured,
                ServerCertificateTrust.findCertificateFailure(new IllegalStateException("wrapped", notConfigured)));
        assertSame(untrusted, ServerCertificateTrust.findCertificateFailure(new ExecutionException(untrusted)));
        assertNull(ServerCertificateTrust.findCertificateFailure(new UaException(StatusCodes.Bad_Timeout, "timeout")));
        assertNull(ServerCertificateTrust.findCertificateFailure(new IOException("connection refused")));
    }

    private void assertRefused(
            final List<X509Certificate> certificateChain, final String applicationUri, final long statusCode)
            throws Exception {
        try (ServerCertificateTrust trust = trustListTrust()) {
            final CertificateValidator validator = trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

            final UaException e = assertThrows(UaException.class,
                    () -> validator.validateCertificateChain(certificateChain, applicationUri, null));
            assertEquals(statusCode, e.getStatusCode().getValue(), e.toString());
        }
        // only a certificate that does not chain up to a trusted certificate is kept for review
        assertEquals(0, countRejected());
    }

    private static X509Certificate validServerCertificate() throws Exception {
        final Instant now = Instant.now();
        return pki.issueServerCertificate(now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.HOURS));
    }

    private ServerCertificateTrust trustListTrust() {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        configuration.setTrustListDirectory(this.trustListDirectory.toString());
        return new ServerCertificateTrust(configuration);
    }

    private void trust(final X509Certificate certificate) throws Exception {
        final Path trustedCerts = Files.createDirectories(this.trustListDirectory.resolve("trusted").resolve("certs"));
        Files.write(trustedCerts.resolve(certificate.getSerialNumber() + "-"
                                         + Integer.toHexString(certificate.hashCode()) + ".der"),
                certificate.getEncoded());
    }

    private void trustRevocationList(final X509CRL revocationList) throws Exception {
        final Path trustedCrl = Files.createDirectories(this.trustListDirectory.resolve("trusted").resolve("crl"));
        Files.write(trustedCrl.resolve("ca.crl"), revocationList.getEncoded());
    }

    private long countRejected() throws IOException {
        final Path rejectedCerts = this.trustListDirectory.resolve("rejected").resolve("certs");
        if (!Files.isDirectory(rejectedCerts)) {
            return 0;
        }
        try (Stream<Path> rejected = Files.list(rejectedCerts)) {
            return rejected.count();
        }
    }

    private static EndpointDescription endpoint(final SecurityPolicy securityPolicy) {
        final MessageSecurityMode securityMode
                = securityPolicy == SecurityPolicy.None ? MessageSecurityMode.None : MessageSecurityMode.SignAndEncrypt;
        return new EndpointDescription(
                "opc.tcp://localhost:4840", null, ByteString.NULL_VALUE, securityMode, securityPolicy.getUri(), null, null,
                UByte.valueOf(0));
    }
}
