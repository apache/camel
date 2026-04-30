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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for OpenSSH user certificate authentication via JSch.
 * <p>
 * Scenario 1: Verify that a Camel SFTP consumer can authenticate using an OpenSSH user certificate (signed by a CA)
 * paired with its private key. The embedded server is configured to only accept certificates signed by a trusted CA,
 * rejecting raw public keys.
 * <p>
 * Test resources generated with ssh-keygen:
 * <ul>
 * <li>cert_user_ca / cert_user_ca.pub — User CA key pair (ed25519)</li>
 * <li>cert_user_key / cert_user_key.pub — User key pair (RSA 2048)</li>
 * <li>cert_user_key-cert.pub — User certificate signed by cert_user_ca, principal "admin"</li>
 * </ul>
 */
@DisabledOnOs(architectures = "s390x")
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpCertUserAuthIT extends BaseServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SftpCertUserAuthIT.class);

    /**
     * Custom SFTP service that only accepts user certificates signed by the trusted CA. Raw public key authentication
     * is rejected.
     */
    @RegisterExtension
    protected SftpEmbeddedService service = new SftpEmbeddedService() {
        @Override
        protected PublickeyAuthenticator getPublickeyAuthenticator() {
            return createCaAwareAuthenticator();
        }
    };

    /**
     * Creates a PublickeyAuthenticator that only accepts OpenSSH certificates signed by our User CA.
     */
    private static PublickeyAuthenticator createCaAwareAuthenticator() {
        final PublicKey trustedCaKey;
        try {
            String caLine = Files.readString(Paths.get("src/test/resources/cert_user_ca.pub")).trim();
            trustedCaKey = PublicKeyEntry.parsePublicKeyEntry(caLine).resolvePublicKey(null, null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load User CA public key", e);
        }

        return (username, key, session) -> {
            if (!(key instanceof OpenSshCertificate cert)) {
                LOG.debug("Rejecting raw public key authentication for user '{}'", username);
                return false;
            }

            // Verify the certificate was signed by our trusted CA
            if (!KeyUtils.compareKeys(cert.getCaPubKey(), trustedCaKey)) {
                LOG.debug("Rejecting certificate signed by untrusted CA for user '{}'", username);
                return false;
            }

            // Verify the username is listed as a principal
            if (!cert.getPrincipals().contains(username)) {
                LOG.debug("Rejecting certificate: principal '{}' not found in {}", username, cert.getPrincipals());
                return false;
            }

            LOG.info("Accepted certificate authentication for user '{}' (cert id: {})", username, cert.getId());
            return true;
        };
    }

    protected Path ftpFile(String file) {
        return service.getFtpRootDir().resolve(file);
    }

    // ========================================================================
    // Positive test: certFile parameter
    // ========================================================================

    @Test
    public void testCertificateAuthWithCertFile() throws Exception {
        String expected = "Hello World";

        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:certFile");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.getRouteController().startRoute("certFileRoute");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Positive test: certUri parameter (classpath)
    // ========================================================================

    @Test
    public void testCertificateAuthWithCertUri() throws Exception {
        String expected = "Hello World";

        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:certUri");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.getRouteController().startRoute("certUriRoute");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Positive test: certBytes parameter (bean reference)
    // ========================================================================

    @BindToRegistry("userCertBytes")
    public byte[] certBytes() throws IOException {
        return Files.readAllBytes(Paths.get("src/test/resources/cert_user_key-cert.pub"));
    }

    @Test
    public void testCertificateAuthWithCertBytes() throws Exception {
        String expected = "Hello World";

        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:certBytes");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.getRouteController().startRoute("certBytesRoute");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Negative test: raw public key rejected (no certificate)
    // ========================================================================

    @Test
    public void testAuthFailsWithoutCertificate() throws Exception {
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), "test", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:noCert");
        mock.expectedMessageCount(0);
        mock.setResultWaitTime(5000);

        // The route should fail to connect because the server rejects raw public keys
        context.getRouteController().startRoute("noCertRoute");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route 1: certFile — both private key and cert as file paths
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyFile=./src/test/resources/cert_user_key"
                     + "&certFile=./src/test/resources/cert_user_key-cert.pub"
                     + "&knownHostsFile=" + service.getKnownHostsFile()
                     + "&useUserKnownHostsFile=false"
                     + "&delay=10000&disconnect=true")
                        .routeId("certFileRoute").noAutoStartup()
                        .to("mock:certFile");

                // Route 2: certUri — certificate loaded from classpath
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyFile=./src/test/resources/cert_user_key"
                     + "&certUri=classpath:cert_user_key-cert.pub"
                     + "&knownHostsFile=" + service.getKnownHostsFile()
                     + "&useUserKnownHostsFile=false"
                     + "&delay=10000&disconnect=true")
                        .routeId("certUriRoute").noAutoStartup()
                        .to("mock:certUri");

                // Route 3: certBytes — certificate loaded as byte array via bean reference
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyFile=./src/test/resources/cert_user_key"
                     + "&certBytes=#userCertBytes"
                     + "&knownHostsFile=" + service.getKnownHostsFile()
                     + "&useUserKnownHostsFile=false"
                     + "&delay=10000&disconnect=true")
                        .routeId("certBytesRoute").noAutoStartup()
                        .to("mock:certBytes");

                // Route 4: no cert — should fail because server rejects raw public keys
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin"
                     + "&privateKeyFile=./src/test/resources/cert_user_key"
                     + "&knownHostsFile=" + service.getKnownHostsFile()
                     + "&useUserKnownHostsFile=false"
                     + "&delay=10000&disconnect=true")
                        .routeId("noCertRoute").noAutoStartup()
                        .to("mock:noCert");
            }
        };
    }
}
