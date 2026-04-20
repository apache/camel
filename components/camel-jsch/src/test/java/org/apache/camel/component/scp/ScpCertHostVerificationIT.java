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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for OpenSSH host certificate verification via the SCP component.
 * <p>
 * Verifies that a Camel SCP producer correctly validates the server's identity using an OpenSSH host certificate
 * (signed by a Host CA), accepting a server whose host key is signed by a trusted CA and rejecting one whose
 * certificate is signed by an untrusted CA.
 * <p>
 * The embedded server is configured to present a host certificate signed by a Host CA. The client uses a known_hosts
 * file with a {@code @cert-authority} entry pointing to the Host CA, and {@code strictHostKeyChecking=yes}.
 * <p>
 * Test resources generated with ssh-keygen:
 * <ul>
 * <li>cert_host_ca / cert_host_ca.pub — Host CA key pair (ed25519)</li>
 * <li>cert_host_key / cert_host_key.pub — Host key pair (RSA 2048)</li>
 * <li>cert_host_key-cert.pub — Host certificate signed by cert_host_ca, principal "localhost"</li>
 * <li>cert_user_ca.pub — User CA (used as wrong CA for negative test)</li>
 * </ul>
 */
public class ScpCertHostVerificationIT extends ScpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ScpCertHostVerificationIT.class);

    private static final String HOST_CA_PUB = "src/test/resources/cert_host_ca.pub";
    private static final String WRONG_CA_PUB = "src/test/resources/cert_user_ca.pub";
    private static final Path HOST_KEY_PATH = Paths.get("src/test/resources/cert_host_key");
    private static final Path HOST_CERT_PATH = Paths.get("src/test/resources/cert_host_key-cert.pub");

    public ScpCertHostVerificationIT() {
        // Don't auto-generate known_hosts — we provide our own @cert-authority entries
        super(false);
        this.serverConfigurer = sshd -> {
            // Present host certificate during SSH handshake
            sshd.setKeyPairProvider(createHostCertKeyPairProvider());

            // Add certificate signature factories
            List<NamedFactory<Signature>> signatureFactories = sshd.getSignatureFactories();
            signatureFactories.add(BuiltinSignatures.rsa_cert);
            signatureFactories.add(BuiltinSignatures.rsaSHA256_cert);
            signatureFactories.add(BuiltinSignatures.rsaSHA512_cert);
            signatureFactories.add(BuiltinSignatures.ed25519_cert);
            sshd.setSignatureFactories(signatureFactories);
        };
    }

    /**
     * Creates a KeyPairProvider that wraps the host private key with the OpenSSH host certificate. When MINA SSHD sees
     * an OpenSshCertificate as the public key, it will present the certificate during the SSH handshake.
     */
    private static KeyPairProvider createHostCertKeyPairProvider() {
        try {
            FileKeyPairProvider keyProvider = new FileKeyPairProvider(HOST_KEY_PATH);
            KeyPair originalKeyPair = keyProvider.loadKeys(null).iterator().next();

            String certLine = Files.readString(HOST_CERT_PATH).trim();
            PublicKey certKey = PublicKeyEntry.parsePublicKeyEntry(certLine).resolvePublicKey(null, null, null);

            if (!(certKey instanceof OpenSshCertificate)) {
                throw new IllegalStateException("Host certificate file does not contain an OpenSSH certificate");
            }

            KeyPair certKeyPair = new KeyPair(certKey, originalKeyPair.getPrivate());
            return KeyPairProvider.wrap(certKeyPair);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load host certificate key pair", e);
        }
    }

    /**
     * Creates a known_hosts file with a {@code @cert-authority} entry for the given CA public key.
     */
    private Path createCertAuthorityKnownHosts(String caPubKeyFile) throws IOException {
        String caPubKey = Files.readString(Paths.get(caPubKeyFile)).trim();
        String entry = String.format("@cert-authority [localhost]:%d %s", getPort(), caPubKey);
        Path knownHostsFile
                = Paths.get("target", "test-classes", "scp", getClass().getSimpleName() + "-config", "known_hosts_ca");
        Files.createDirectories(knownHostsFile.getParent());
        Files.writeString(knownHostsFile, entry + "\n");
        return knownHostsFile;
    }

    // ========================================================================
    // Positive test: host cert verified via @cert-authority in known_hosts
    // ========================================================================

    @Test
    public void testHostCertVerification() throws Exception {
        assumeTrue(isSetupComplete());

        Path knownHostsFile = createCertAuthorityKnownHosts(HOST_CA_PUB);
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        String uri = getScpUri()
                     + "?username=admin&password=admin"
                     + "&knownHostsFile=" + knownHostsFile
                     + "&strictHostKeyChecking=yes"
                     + "&useUserKnownHostsFile=false"
                     + "&caSignatureAlgorithms=ssh-ed25519,rsa-sha2-512,rsa-sha2-256";

        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Negative test: wrong CA in known_hosts — connection must fail
    // ========================================================================

    @Test
    public void testHostCertVerificationFailsWithWrongCA() throws Exception {
        assumeTrue(isSetupComplete());

        // Use the User CA as the "wrong" CA — it didn't sign the host certificate
        Path knownHostsFile = createCertAuthorityKnownHosts(WRONG_CA_PUB);

        String uri = getScpUri()
                     + "?username=admin&password=admin"
                     + "&knownHostsFile=" + knownHostsFile
                     + "&strictHostKeyChecking=yes"
                     + "&useUserKnownHostsFile=false";

        CamelExecutionException thrown = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt"));
        assertInstanceOf(GenericFileOperationFailedException.class, thrown.getCause());
    }

    // ========================================================================
    // Negative test: restricted caSignatureAlgorithms excludes the CA's algo
    // ========================================================================

    @Test
    public void testHostCertVerificationFailsWithRestrictedAlgorithms() throws Exception {
        assumeTrue(isSetupComplete());

        Path knownHostsFile = createCertAuthorityKnownHosts(HOST_CA_PUB);

        // The Host CA is ed25519, but we only allow rsa-sha2-512 — JSch should reject
        String uri = getScpUri()
                     + "?username=admin&password=admin"
                     + "&knownHostsFile=" + knownHostsFile
                     + "&strictHostKeyChecking=yes"
                     + "&useUserKnownHostsFile=false"
                     + "&caSignatureAlgorithms=rsa-sha2-512";

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
