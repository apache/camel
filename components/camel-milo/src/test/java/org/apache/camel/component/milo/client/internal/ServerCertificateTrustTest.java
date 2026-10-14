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

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.component.milo.call.TestCertificateFactory;
import org.apache.camel.component.milo.client.MiloClientConfiguration;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCertificateTrustTest {

    private static X509Certificate serverCertificate;

    @TempDir
    Path trustListDirectory;

    @BeforeAll
    static void createServerCertificate() {
        final TestCertificateFactory certificateFactory = new TestCertificateFactory();
        final KeyPair keyPair = certificateFactory.createRsaSha256KeyPair();
        serverCertificate = certificateFactory.createRsaSha256CertificateChain(keyPair)[0];
    }

    @Test
    void securedEndpointWithoutTrustConfigurationIsRefused() {
        final ServerCertificateTrust trust = new ServerCertificateTrust(new MiloClientConfiguration());

        final IllegalStateException e
                = assertThrows(IllegalStateException.class, () -> trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256)));

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

        assertDoesNotThrow(() -> validator.validateCertificateChain(List.of(serverCertificate), null, null));
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
        final Path trustedCerts = Files.createDirectories(this.trustListDirectory.resolve("trusted").resolve("certs"));
        Files.write(trustedCerts.resolve("server.der"), serverCertificate.getEncoded());

        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        configuration.setTrustListDirectory(this.trustListDirectory.toString());

        try (ServerCertificateTrust trust = new ServerCertificateTrust(configuration)) {
            final CertificateValidator validator = trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

            assertInstanceOf(DefaultClientCertificateValidator.class, validator);
            assertDoesNotThrow(() -> validator.validateCertificateChain(List.of(serverCertificate), null, null));
            // a reconnect uses the same trust list
            assertSame(validator, trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256)));
        }
    }

    @Test
    void trustListRejectsAndKeepsUntrustedServerCertificate() throws Exception {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        configuration.setTrustListDirectory(this.trustListDirectory.toString());

        try (ServerCertificateTrust trust = new ServerCertificateTrust(configuration)) {
            final CertificateValidator validator = trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

            assertThrows(UaException.class,
                    () -> validator.validateCertificateChain(List.of(serverCertificate), null, null));
        }

        assertTrue(Files.isDirectory(this.trustListDirectory.resolve("trusted").resolve("certs")));
        assertTrue(Files.isDirectory(this.trustListDirectory.resolve("issuer").resolve("certs")));
        try (Stream<Path> rejected = Files.list(this.trustListDirectory.resolve("rejected").resolve("certs"))) {
            assertEquals(1, rejected.count());
        }
    }

    @Test
    void closedTrustListIsNotReused() throws Exception {
        final MiloClientConfiguration configuration = new MiloClientConfiguration();
        configuration.setTrustListDirectory(this.trustListDirectory.toString());
        final ServerCertificateTrust trust = new ServerCertificateTrust(configuration);
        trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256));

        trust.close();

        assertThrows(IllegalStateException.class, () -> trust.validatorFor(endpoint(SecurityPolicy.Basic256Sha256)));
    }

    private static EndpointDescription endpoint(final SecurityPolicy securityPolicy) {
        final MessageSecurityMode securityMode
                = securityPolicy == SecurityPolicy.None ? MessageSecurityMode.None : MessageSecurityMode.SignAndEncrypt;
        return new EndpointDescription(
                "opc.tcp://localhost:4840", null, ByteString.NULL_VALUE, securityMode, securityPolicy.getUri(), null, null,
                UByte.valueOf(0));
    }
}
