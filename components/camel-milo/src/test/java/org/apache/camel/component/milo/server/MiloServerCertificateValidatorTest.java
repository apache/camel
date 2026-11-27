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
package org.apache.camel.component.milo.server;

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.component.milo.call.TestCertificateFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateGroup;
import org.eclipse.milo.opcua.stack.core.security.CertificateManager;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultApplicationGroup;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateStore;
import org.eclipse.milo.opcua.stack.core.security.MemoryTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The certificateValidator option of the milo-server component: the server starts with it, and the server validates the
 * certificates of the clients with it.
 */
class MiloServerCertificateValidatorTest {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @TempDir
    Path trustList;

    private final AtomicInteger validations = new AtomicInteger();

    private final CertificateValidator denyAll = new CertificateValidator() {
        @Override
        public void validateCertificateChain(
                List<X509Certificate> certificateChain, String applicationUri, String[] validHostNames)
                throws UaException {
            validations.incrementAndGet();
            throw new UaException(StatusCodes.Bad_SecurityChecksFailed);
        }
    };

    private CamelContext context;

    @AfterEach
    void stopContext() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void serverStartsWithCustomValidator() throws Exception {
        final DefaultApplicationGroup certificateGroup = createCertificateGroup();
        final X509Certificate serverCertificate = certificateGroup.getCertificateEntries().get(0).certificateChain[0];

        final MiloServerComponent server = createServer();
        server.setCertificateManager(new DefaultCertificateManager(new MemoryCertificateQuarantine(), certificateGroup));
        server.setCertificate(serverCertificate);
        server.setCertificateValidator(denyAll);

        startServer(server);

        assertNotNull(server.getServer(), "The OPC UA server should have been started");

        // the server looks the certificate group up by the thumbprint of its own certificate, and validates the
        // client certificate with the validator of that group
        final CertificateManager certificateManager = server.getServer().getConfig().getCertificateManager();
        final CertificateGroup group = certificateManager
                .getCertificateGroup(CertificateUtil.thumbprint(serverCertificate))
                .orElseThrow();
        final UaException e = assertThrows(UaException.class,
                () -> group.getCertificateValidator().validateCertificateChain(
                        List.of(serverCertificate), null, null));
        assertEquals(StatusCodes.Bad_SecurityChecksFailed, e.getStatusCode().getValue());
        assertEquals(1, validations.get(), "The custom certificate validator should have been called");

        // the key pair and certificate of the server still come from the certificate manager
        assertTrue(certificateManager.getKeyPair(CertificateUtil.thumbprint(serverCertificate)).isPresent());
        assertFalse(certificateManager.getCertificateGroups().isEmpty());
        for (CertificateGroup each : certificateManager.getCertificateGroups()) {
            assertEquals(denyAll, each.getCertificateValidator());
        }
    }

    @Test
    void customValidatorRequiresCertificateManager() {
        final MiloServerComponent server = createServer();
        // the security policy None needs no certificate of the server
        server.setSecurityPolicies(EnumSet.of(SecurityPolicy.None));
        server.setCertificateValidator(denyAll);

        assertStartFails(server, "requires the option certificateManager");
    }

    @Test
    void customAndDefaultValidatorCannotBeUsedTogether() throws Exception {
        final MiloServerComponent server = createServerWithCertificate();
        server.setCertificateValidator(denyAll);
        server.setDefaultCertificateValidator(trustList.toString());

        assertStartFails(server, "cannot be used together");
    }

    @Test
    void defaultAndCustomValidatorCannotBeUsedTogether() throws Exception {
        final MiloServerComponent server = createServerWithCertificate();
        server.setDefaultCertificateValidator(trustList.toString());
        server.setCertificateValidator(denyAll);

        assertStartFails(server, "cannot be used together");
    }

    private MiloServerComponent createServer() {
        final MiloServerComponent server = new MiloServerComponent();
        server.setBindAddresses("localhost");
        server.setPort(port.getPort());
        server.setSecurityPolicies(EnumSet.of(SecurityPolicy.Basic256Sha256));
        return server;
    }

    private MiloServerComponent createServerWithCertificate() throws Exception {
        final DefaultApplicationGroup certificateGroup = createCertificateGroup();
        final MiloServerComponent server = createServer();
        server.setCertificateManager(new DefaultCertificateManager(new MemoryCertificateQuarantine(), certificateGroup));
        server.setCertificate(certificateGroup.getCertificateEntries().get(0).certificateChain[0]);
        return server;
    }

    private void startServer(final MiloServerComponent server) {
        context = new DefaultCamelContext();
        context.addComponent("milo-server", server);
        context.start();
        server.start();
    }

    private void assertStartFails(final MiloServerComponent server, final String expectedMessage) {
        final Exception e = assertThrows(Exception.class, () -> startServer(server));

        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException && cause.getMessage().contains(expectedMessage)) {
                return;
            }
        }
        fail("Expected an IllegalArgumentException with: " + expectedMessage, e);
    }

    private static DefaultApplicationGroup createCertificateGroup() throws Exception {
        return DefaultApplicationGroup.createAndInitialize(
                new MemoryTrustListManager(), new MemoryCertificateStore(), new TestCertificateFactory(),
                new CertificateValidator.InsecureCertificateValidator());
    }
}
