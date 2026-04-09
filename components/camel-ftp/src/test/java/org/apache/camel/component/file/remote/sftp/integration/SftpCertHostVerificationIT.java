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
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.ftp.common.FtpProperties;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for OpenSSH host certificate verification via JSch.
 * <p>
 * Scenario 2: Verify that a Camel SFTP consumer can verify the server's identity using an OpenSSH host certificate
 * (signed by a Host CA), instead of relying on a known_hosts fingerprint. This exercises the
 * {@code caSignatureAlgorithms} parameter.
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
@DisabledOnOs(architectures = "s390x")
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpCertHostVerificationIT extends BaseServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SftpCertHostVerificationIT.class);

    private static final String HOST_CA_PUB = "src/test/resources/cert_host_ca.pub";
    private static final String WRONG_CA_PUB = "src/test/resources/cert_user_ca.pub";
    private static final Path HOST_KEY_PATH = Paths.get("src/test/resources/cert_host_key");
    private static final Path HOST_CERT_PATH = Paths.get("src/test/resources/cert_host_key-cert.pub");

    /**
     * Custom SFTP service that presents a host certificate during the SSH handshake. The server's key pair is a
     * combination of the host private key and the OpenSSH host certificate — MINA SSHD will automatically present the
     * certificate to clients.
     */
    @RegisterExtension
    protected SftpEmbeddedService service = new SftpEmbeddedService() {
        @Override
        public void setUpServer() throws Exception {
            sshd = SshServer.setUpDefaultServer();
            if (port > 0) {
                sshd.setPort(port);
            } else {
                sshd.setPort(ContainerEnvironmentUtil.getConfiguredPortOrRandom(FtpProperties.DEFAULT_SFTP_PORT));
            }

            // Load host key with certificate — MINA SSHD will present the cert during handshake
            sshd.setKeyPairProvider(createHostCertKeyPairProvider());
            sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
            sshd.setCommandFactory(new ScpCommandFactory());
            sshd.setPasswordAuthenticator((username, password, session) -> true);
            sshd.setPublickeyAuthenticator((username, key, session) -> true);

            List<NamedFactory<Signature>> signatureFactories = sshd.getSignatureFactories();
            signatureFactories.clear();
            signatureFactories.add(BuiltinSignatures.rsaSHA512);
            signatureFactories.add(BuiltinSignatures.nistp256);
            signatureFactories.add(BuiltinSignatures.nistp521);
            signatureFactories.add(BuiltinSignatures.ed25519);
            signatureFactories.add(BuiltinSignatures.rsaSHA512_cert);
            signatureFactories.add(BuiltinSignatures.nistp256_cert);
            signatureFactories.add(BuiltinSignatures.nistp521_cert);
            signatureFactories.add(BuiltinSignatures.ed25519_cert);
            sshd.setSignatureFactories(signatureFactories);

            sshd.start();
            port = sshd.getPort();
        }
    };

    /**
     * Creates a KeyPairProvider that wraps the host private key with the OpenSSH host certificate. When MINA SSHD sees
     * an OpenSshCertificate as the public key, it will present the certificate during the SSH handshake.
     */
    private static KeyPairProvider createHostCertKeyPairProvider() {
        try {
            // Load the host private key
            FileKeyPairProvider keyProvider = new FileKeyPairProvider(HOST_KEY_PATH);
            KeyPair originalKeyPair = keyProvider.loadKeys(null).iterator().next();

            // Load the host certificate
            String certLine = Files.readString(HOST_CERT_PATH).trim();
            PublicKey certKey = PublicKeyEntry.parsePublicKeyEntry(certLine).resolvePublicKey(null, null, null);

            if (!(certKey instanceof OpenSshCertificate)) {
                throw new IllegalStateException("Host certificate file does not contain an OpenSSH certificate");
            }

            // Create a key pair with the certificate as the public key
            KeyPair certKeyPair = new KeyPair(certKey, originalKeyPair.getPrivate());
            return KeyPairProvider.wrap(certKeyPair);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load host certificate key pair", e);
        }
    }

    /**
     * Creates a known_hosts file with a @cert-authority entry for the given CA public key.
     */
    private Path createCertAuthorityKnownHosts(String caPubKeyFile) throws IOException {
        String caPubKey = Files.readString(Paths.get(caPubKeyFile)).trim();
        // Format: @cert-authority [localhost]:PORT <key-type> <base64-key> <comment>
        String entry = String.format("@cert-authority [localhost]:%d %s", service.getPort(), caPubKey);
        Path knownHostsFile = Paths.get("target", "ftp", "cert-host-test", "known_hosts_ca");
        Files.createDirectories(knownHostsFile.getParent());
        Files.writeString(knownHostsFile, entry + "\n");
        return knownHostsFile;
    }

    protected Path ftpFile(String file) {
        return service.getFtpRootDir().resolve(file);
    }

    // ========================================================================
    // Positive test: host cert verified via @cert-authority in known_hosts
    // ========================================================================

    @Test
    public void testHostCertVerification() throws Exception {
        Path knownHostsFile = createCertAuthorityKnownHosts(HOST_CA_PUB);

        String expected = "Hello World";
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&password=admin"
                     + "&knownHostsFile=" + knownHostsFile
                     + "&strictHostKeyChecking=yes"
                     + "&useUserKnownHostsFile=false"
                     + "&caSignatureAlgorithms=ssh-ed25519,rsa-sha2-512,rsa-sha2-256"
                     + "&delay=10000&disconnect=true")
                        .routeId("hostCert").noAutoStartup()
                        .to("mock:result");
            }
        });

        context.getRouteController().startRoute("hostCert");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Negative test: wrong CA in known_hosts — connection must fail
    // ========================================================================

    @Test
    public void testHostCertVerificationFailsWithWrongCA() throws Exception {
        // Use the User CA as the "wrong" CA — it didn't sign the host certificate
        Path knownHostsFile = createCertAuthorityKnownHosts(WRONG_CA_PUB);

        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), "test", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:wrongCA");
        mock.expectedMessageCount(0);
        mock.setResultWaitTime(5000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&password=admin"
                     + "&knownHostsFile=" + knownHostsFile
                     + "&strictHostKeyChecking=yes"
                     + "&useUserKnownHostsFile=false"
                     + "&delay=10000&disconnect=true")
                        .routeId("wrongCA").noAutoStartup()
                        .to("mock:wrongCA");
            }
        });

        // This route should fail to connect because the host cert is signed by a different CA
        context.getRouteController().startRoute("wrongCA");

        MockEndpoint.assertIsSatisfied(context);
    }

    // ========================================================================
    // Negative test: restricted caSignatureAlgorithms excludes the CA's algo
    // ========================================================================

    @Test
    public void testHostCertVerificationFailsWithRestrictedAlgorithms() throws Exception {
        Path knownHostsFile = createCertAuthorityKnownHosts(HOST_CA_PUB);

        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), "test", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:restrictedAlgo");
        mock.expectedMessageCount(0);
        mock.setResultWaitTime(5000);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // The Host CA is ed25519, but we only allow rsa-sha2-512 — JSch should reject
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&password=admin"
                     + "&knownHostsFile=" + knownHostsFile
                     + "&strictHostKeyChecking=yes"
                     + "&useUserKnownHostsFile=false"
                     + "&caSignatureAlgorithms=rsa-sha2-512"
                     + "&delay=10000&disconnect=true")
                        .routeId("restrictedAlgo").noAutoStartup()
                        .to("mock:restrictedAlgo");
            }
        });

        // This route should fail because the ed25519 CA signature is not in the accepted list
        context.getRouteController().startRoute("restrictedAlgo");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // Routes are added dynamically in each test method because they need
        // the dynamically-generated known_hosts file path
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no-op
            }
        };
    }
}
