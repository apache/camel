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
package org.apache.camel.component.scp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for OpenSSH user certificate authentication via the SCP component.
 * <p>
 * Verifies that a Camel SCP producer can authenticate using an OpenSSH user certificate (signed by a CA) paired with
 * its private key. The embedded server is configured to only accept certificates signed by a trusted CA, rejecting raw
 * public keys.
 * <p>
 * Test resources generated with ssh-keygen:
 * <ul>
 * <li>cert_user_ca / cert_user_ca.pub — User CA key pair (ed25519)</li>
 * <li>cert_user_key / cert_user_key.pub — User key pair (RSA 2048)</li>
 * <li>cert_user_key-cert.pub — User certificate signed by cert_user_ca, principal "admin"</li>
 * </ul>
 */
public class ScpCertUserAuthIT extends ScpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ScpCertUserAuthIT.class);

    public ScpCertUserAuthIT() {
        super(true);
        this.serverConfigurer = sshd -> {
            // Only accept certificates signed by the trusted User CA
            sshd.setPublickeyAuthenticator(createCaAwareAuthenticator());

            // Add certificate signature factories so the server can process cert key types
            List<NamedFactory<Signature>> signatureFactories = sshd.getSignatureFactories();
            signatureFactories.add(BuiltinSignatures.rsa_cert);
            signatureFactories.add(BuiltinSignatures.rsaSHA256_cert);
            signatureFactories.add(BuiltinSignatures.rsaSHA512_cert);
            signatureFactories.add(BuiltinSignatures.ed25519_cert);
            sshd.setSignatureFactories(signatureFactories);
        };
    }

    /**
     * Creates a PublickeyAuthenticator that only accepts OpenSSH certificates signed by the User CA.
     */
    private static org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator createCaAwareAuthenticator() {
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
            if (!KeyUtils.compareKeys(cert.getCaPubKey(), trustedCaKey)) {
                LOG.debug("Rejecting certificate signed by untrusted CA for user '{}'", username);
                return false;
            }
            if (!cert.getPrincipals().contains(username)) {
                LOG.debug("Rejecting certificate: principal '{}' not found in {}", username, cert.getPrincipals());
                return false;
            }
            LOG.info("Accepted certificate authentication for user '{}' (cert id: {})", username, cert.getId());
            return true;
        };
    }

    // ========================================================================
    // Positive test: certFile parameter
    // ========================================================================

    @Test
    public void testCertificateAuthWithCertFile() throws Exception {
        assumeTrue(isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        String uri = getScpUri()
                     + "?username=admin"
                     + "&privateKeyFile=src/test/resources/cert_user_key"
                     + "&certFile=src/test/resources/cert_user_key-cert.pub"
                     + "&knownHostsFile=" + getKnownHostsFile()
                     + "&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Positive test: certUri parameter (classpath)
    // ========================================================================

    @Test
    public void testCertificateAuthWithCertUri() throws Exception {
        assumeTrue(isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        String uri = getScpUri()
                     + "?username=admin"
                     + "&privateKeyFile=src/test/resources/cert_user_key"
                     + "&certUri=classpath:cert_user_key-cert.pub"
                     + "&knownHostsFile=" + getKnownHostsFile()
                     + "&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Positive test: certBytes parameter (bean reference)
    // ========================================================================

    @org.apache.camel.BindToRegistry("userCertBytes")
    public byte[] certBytes() throws IOException {
        return Files.readAllBytes(Paths.get("src/test/resources/cert_user_key-cert.pub"));
    }

    @Test
    public void testCertificateAuthWithCertBytes() throws Exception {
        assumeTrue(isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        String uri = getScpUri()
                     + "?username=admin"
                     + "&privateKeyFile=src/test/resources/cert_user_key"
                     + "&certBytes=#userCertBytes"
                     + "&knownHostsFile=" + getKnownHostsFile()
                     + "&useUserKnownHostsFile=false";

        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Negative test: raw public key rejected (no certificate)
    // ========================================================================

    @Test
    public void testAuthFailsWithoutCertificate() throws Exception {
        assumeTrue(isSetupComplete());

        String uri = getScpUri()
                     + "?username=admin"
                     + "&privateKeyFile=src/test/resources/cert_user_key"
                     + "&knownHostsFile=" + getKnownHostsFile()
                     + "&useUserKnownHostsFile=false";

        CamelExecutionException thrown = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt"));
        assertInstanceOf(GenericFileOperationFailedException.class, thrown.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + getScpPath() + "?recursive=true&delete=true")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }
}
