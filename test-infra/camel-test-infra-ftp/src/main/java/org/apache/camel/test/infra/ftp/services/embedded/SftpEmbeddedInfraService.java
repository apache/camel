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

package org.apache.camel.test.infra.ftp.services.embedded;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.test.infra.common.services.AbstractService;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.ftp.common.FtpProperties;
import org.apache.camel.test.infra.ftp.services.FtpInfraService;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.ClassLoadableResourceKeyPairProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InfraService(service = FtpInfraService.class,
              description = "Embedded SFTP Server",
              serviceAlias = { "sftp" })
public class SftpEmbeddedInfraService extends AbstractService implements FtpInfraService {
    private static final Logger LOG = LoggerFactory.getLogger(SftpEmbeddedInfraService.class);

    protected SshServer sshd;
    protected final boolean rootDirMode;
    protected int port;

    private Path rootDir;
    private Path knownHosts;
    private final EmbeddedConfiguration embeddedConfiguration;
    protected String testDirectory = "camel-test-infra-test-directory";

    public SftpEmbeddedInfraService() {
        this(false);
    }

    public SftpEmbeddedInfraService(boolean rootDirMode) {
        this(rootDirMode, EmbeddedConfigurationBuilder.defaultSftpConfiguration());
    }

    protected SftpEmbeddedInfraService(boolean rootDirMode, EmbeddedConfiguration embeddedConfiguration) {
        this.rootDirMode = rootDirMode;
        this.embeddedConfiguration = embeddedConfiguration;
    }

    public void setUp() throws Exception {
        rootDir = testDirectory().resolve(embeddedConfiguration.getTestDirectory());
        knownHosts = testDirectory().resolve(embeddedConfiguration.getKnownHostsPath());

        Files.createDirectories(knownHosts.getParent());
        Files.write(knownHosts, buildKnownHosts());

        setUpServer();
    }

    private Path testDirectory() {
        return Paths.get("target", "ftp", testDirectory);
    }

    public void setUpServer() throws Exception {
        sshd = SshServer.setUpDefaultServer();
        // If port was already assigned (restart scenario), reuse it; otherwise get a new one
        if (port > 0) {
            sshd.setPort(port);
        } else {
            sshd.setPort(ContainerEnvironmentUtil.getConfiguredPortOrRandom(FtpProperties.DEFAULT_SFTP_PORT));
        }

        sshd.setKeyPairProvider(createKeyPairProvider());
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setCommandFactory(new ScpCommandFactory());
        sshd.setPasswordAuthenticator((username, password, session) -> true);
        sshd.setPublickeyAuthenticator(getPublickeyAuthenticator());

        if (rootDirMode) {
            final File testDirectory = new File(embeddedConfiguration.getTestDirectory());

            sshd.setFileSystemFactory(new VirtualFileSystemFactory(
                    testDirectory().resolve(testDirectory.getParentFile().getName())
                            .toAbsolutePath()));
        }
        List<NamedFactory<Signature>> signatureFactories = sshd.getSignatureFactories();
        signatureFactories.clear();
        // use only one, quite strong signature algorithms for 3 kinds of keys - RSA, EC, EDDSA
        signatureFactories.add(BuiltinSignatures.rsaSHA512);
        signatureFactories.add(BuiltinSignatures.nistp256);
        signatureFactories.add(BuiltinSignatures.nistp521);
        signatureFactories.add(BuiltinSignatures.ed25519);
        // include both certificate and non-certificate variants so that OpenSSH certificate
        // authentication works (the server needs cert-specific verifiers for cert key types)
        signatureFactories.add(BuiltinSignatures.rsaSHA512_cert);
        signatureFactories.add(BuiltinSignatures.nistp256_cert);
        signatureFactories.add(BuiltinSignatures.nistp521_cert);
        signatureFactories.add(BuiltinSignatures.ed25519_cert);
        sshd.setSignatureFactories(signatureFactories);
        sshd.start();

        port = ((InetSocketAddress) sshd.getBoundAddresses().iterator().next()).getPort();
    }

    protected PublickeyAuthenticator getPublickeyAuthenticator() {
        return (username, key, session) -> true;
    }

    private KeyPairProvider createKeyPairProvider() {
        // 1. First try: Use existing file on disk if configured path exists
        Path keyPairPath = Paths.get(embeddedConfiguration.getKeyPairFile());
        if (Files.exists(keyPairPath)) {
            LOG.debug("Using existing host key file: {}", keyPairPath);
            return new FileKeyPairProvider(keyPairPath);
        }

        // 2. Second try: Load bundled host key from classpath
        KeyPairProvider classpathProvider = loadKeyFromClasspath();
        if (classpathProvider != null) {
            return classpathProvider;
        }

        // 3. Last resort: Generate a new host key
        Path generatedKeyPath = testDirectory().resolve("hostkey.ser");
        LOG.info("Host key file not found at {}. Generating new host key at: {}", keyPairPath, generatedKeyPath);
        SimpleGeneratorHostKeyProvider provider = new SimpleGeneratorHostKeyProvider(generatedKeyPath);
        provider.setAlgorithm("RSA");
        provider.setKeySize(2048);
        return provider;
    }

    /**
     * Attempts to load the bundled host key from the classpath.
     *
     * @return KeyPairProvider if the bundled key is available and can be loaded, null otherwise
     */
    private KeyPairProvider loadKeyFromClasspath() {
        try {
            ClassLoadableResourceKeyPairProvider provider
                    = new ClassLoadableResourceKeyPairProvider(EmbeddedConfiguration.BUNDLED_HOST_KEY_RESOURCE);

            // Verify the key can actually be loaded
            Iterable<KeyPair> keyPairs = provider.loadKeys(null);
            if (keyPairs != null && keyPairs.iterator().hasNext()) {
                LOG.info("Using bundled host key from classpath: {}",
                        EmbeddedConfiguration.BUNDLED_HOST_KEY_RESOURCE);
                return provider;
            }
        } catch (Exception e) {
            LOG.debug("Failed to load bundled host key from classpath: {}", e.getMessage());
        }
        return null;
    }

    public void tearDown() {
        tearDownServer();
    }

    public void tearDownServer() {
        try {
            // stop asap as we may hang forever
            if (sshd != null) {
                sshd.stop(true);
            }
        } catch (Exception e) {
            // ignore while shutting down as we could be polling during
            // shutdown
            // and get errors when the ftp server is stopping. This is only
            // an issue
            // since we host the ftp server embedded in the same jvm for
            // unit testing
            LOG.trace("Exception while shutting down: {}", e.getMessage(), e);
        } finally {
            sshd = null;
        }
    }

    // disconnect all existing SSH sessions to test reconnect functionality
    public void disconnectAllSessions() throws IOException {
        List<AbstractSession> sessions = sshd.getActiveSessions();
        for (AbstractSession session : sessions) {
            session.disconnect(4, "dummy");
        }
    }

    public byte[] buildKnownHosts() {
        return String.format(embeddedConfiguration.getKnownHosts(), port).getBytes();
    }

    public String getKnownHostsFile() {
        return knownHosts.toString();
    }

    @Override
    public Path getFtpRootDir() {
        return rootDir;
    }

    protected void registerProperties(BiConsumer<String, String> store) {
        store.accept(FtpProperties.SERVER_HOST, embeddedConfiguration.getServerAddress());
        store.accept(FtpProperties.SERVER_PORT, String.valueOf(port));
        store.accept(FtpProperties.ROOT_DIR, rootDir.toString());
    }

    @Override
    public void registerProperties() {
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public int port() {
        return port;
    }

    /**
     * Returns the SSH host key fingerprint in SHA256 format. This can be used to verify the server identity when
     * connecting.
     *
     * @return the host key fingerprint (e.g., "SHA256:...") or null if unavailable
     */
    @Override
    public String hostKeyFingerprint() {
        try {
            PublicKey publicKey = getHostPublicKey();
            if (publicKey != null) {
                return KeyUtils.getFingerPrint(publicKey);
            }
        } catch (Exception e) {
            LOG.warn("Failed to get host key fingerprint: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Returns the known_hosts entry for this server.
     *
     * @return the known_hosts entry or null if unavailable
     */
    @Override
    public String knownHostsEntry() {
        try {
            PublicKey publicKey = getHostPublicKey();
            if (publicKey != null) {
                StringBuilder sb = new StringBuilder();
                PublicKeyEntry.appendPublicKeyEntry(sb, publicKey);
                return String.format("[%s]:%d %s",
                        embeddedConfiguration.getServerAddress(), port, sb);
            }
        } catch (Exception e) {
            LOG.warn("Failed to get known_hosts entry: {}", e.getMessage(), e);
        }
        return null;
    }

    private PublicKey getHostPublicKey() {
        if (sshd == null) {
            return null;
        }
        try {
            Iterable<KeyPair> keyPairs = sshd.getKeyPairProvider().loadKeys(null);
            for (KeyPair keyPair : keyPairs) {
                return keyPair.getPublic();
            }
        } catch (Exception e) {
            LOG.debug("Failed to load host key: {}", e.getMessage(), e);
        }
        return null;
    }
}
