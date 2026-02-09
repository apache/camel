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
package org.apache.camel.component.file.remote.mina;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.FtpConstants;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.SftpModuleProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.apache.sshd.sftp.client.SftpClient.OpenMode;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SFTP remote file operations using Apache MINA SSHD.
 *
 * The SSHD session and SFTP client are not thread-safe so we need to synchronize access to using this operation.
 */
public class MinaSftpOperations implements RemoteFileOperations<SftpRemoteFile> {

    private static final Logger LOG = LoggerFactory.getLogger(MinaSftpOperations.class);
    private static final Pattern UP_DIR_PATTERN = Pattern.compile("/[^/]+");

    /**
     * Size of one bulk request in bytes (32KB). Used for backward compatibility when converting deprecated bulkRequests
     * parameter to buffer sizes.
     */
    private static final int BULK_REQUEST_SIZE = 32 * 1024; // 32KB

    /**
     * Maximum recommended buffer size in bytes (126KB = 124 * 1024 + 512). Values above this may cause data corruption
     * in MINA SSHD due to SSH packet size limits. See SSHD-1069 for details.
     */
    private static final int MAX_BUFFER_SIZE = 126 * 1024 + 512; // ~126KB

    private MinaSftpEndpoint endpoint;
    private SshClient sshClient;
    private ClientSession session;
    private SftpClient sftpClient;
    private final Lock lock = new ReentrantLock();
    /**
     * Tracks the current working directory for stepwise navigation.
     * <p/>
     * Unlike JSch's ChannelSftp which has a cd() method, MINA SSHD's SftpClient is stateless and has no concept of
     * current directory. The SFTP protocol itself is stateless - each operation takes an explicit path. The
     * canonicalPath(".") method always returns the user's home directory, not a dynamic current directory.
     * <p/>
     * This manual tracking is required for Camel's stepwise directory traversal pattern used by RemoteFileConsumer.
     * MINA SSHD's own CLI (SftpCommandMain) uses the same approach with its cwdRemote field.
     *
     * @see <a href=
     *      "https://github.com/apache/mina-sshd/blob/master/sshd-cli/src/main/java/org/apache/sshd/cli/client/SftpCommandMain.java">MINA
     *      SSHD SftpCommandMain</a>
     */
    private volatile String currentDirectory;

    private static class TaskPayload {
        final RemoteFileConfiguration configuration;
        private Exception exception;

        public TaskPayload(RemoteFileConfiguration configuration) {
            this.configuration = configuration;
        }
    }

    public MinaSftpOperations() {
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<SftpRemoteFile> endpoint) {
        this.endpoint = (MinaSftpEndpoint) endpoint;
    }

    @Override
    public GenericFile<SftpRemoteFile> newGenericFile() {
        return new RemoteFile<>();
    }

    @Override
    public boolean connect(RemoteFileConfiguration configuration, Exchange exchange)
            throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (isConnected()) {
                return true;
            }

            BlockingTask task = Tasks.foregroundTask()
                    .withBudget(Budgets.iterationBudget()
                            .withMaxIterations(Budgets.atLeastOnce(endpoint.getMaximumReconnectAttempts()))
                            .withInterval(Duration.ofMillis(endpoint.getReconnectDelay()))
                            .build())
                    .build();

            TaskPayload payload = new TaskPayload(configuration);

            if (!task.run(endpoint.getCamelContext(), this::tryConnect, payload)) {
                throw new GenericFileOperationFailedException(
                        "Cannot connect to " + configuration.remoteServerInformation(),
                        payload.exception);
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    private boolean tryConnect(TaskPayload payload) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Reconnect attempt to {}", payload.configuration.remoteServerInformation());
        }

        try {
            if (sftpClient == null || !sftpClient.isOpen()) {
                if (session == null || !session.isOpen()) {
                    LOG.trace("Session isn't connected, trying to recreate and connect.");

                    session = createSession(payload.configuration);
                }

                LOG.trace("SFTP client isn't connected, trying to recreate and connect.");
                sftpClient = SftpClientFactory.instance().createSftpClient(session);

                // Configure filename encoding if specified
                configureFilenameEncoding();

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Connected to {}", payload.configuration.remoteServerInformation());
                }
            }
        } catch (IOException e) {
            payload.exception = e;
            return false;
        }

        return true;
    }

    private ClientSession createSession(RemoteFileConfiguration configuration) throws IOException {
        MinaSftpConfiguration sftpConfig = getMinaSftpConfiguration();

        // Create and configure SSH client if not already done
        if (sshClient == null) {
            sshClient = createAndConfigureSshClient(sftpConfig);
        }

        // Connect to the server
        ClientSession newSession = connectToServer(configuration, sftpConfig);

        // Configure and authenticate the session
        authenticateSession(newSession, configuration, sftpConfig);

        // Post-authentication configuration
        if (sftpConfig.getCompression() > 0) {
            logCompressionNegotiationResult(newSession, sftpConfig);
        }
        configureBufferSizes(newSession, sftpConfig);

        return newSession;
    }

    /**
     * Creates and configures a new SSH client with all cryptographic and connection settings.
     *
     * @param  sftpConfig the SFTP configuration
     * @return            the configured and started SSH client
     */
    private SshClient createAndConfigureSshClient(MinaSftpConfiguration sftpConfig) {
        SshClient client = SshClient.setUpDefaultClient();

        // Configure host key verification
        ServerKeyVerifier verifier = createServerKeyVerifier(sftpConfig);
        client.setServerKeyVerifier(verifier);

        // Configure heartbeat/keep-alive
        configureHeartbeat(client, sftpConfig);

        // Configure compression
        configureCompression(client, sftpConfig);

        // Configure cryptographic algorithms
        configureCiphers(client, sftpConfig);
        configureKeyExchangeProtocols(client, sftpConfig);
        configureServerHostKeys(client, sftpConfig);

        client.start();
        return client;
    }

    /**
     * Configures heartbeat/keep-alive settings on the SSH client.
     */
    private void configureHeartbeat(SshClient client, MinaSftpConfiguration config) {
        if (config.getServerAliveInterval() > 0) {
            LOG.debug("Setting heartbeat interval to {} ms with max {} unanswered",
                    config.getServerAliveInterval(), config.getServerAliveCountMax());
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(client,
                    Duration.ofMillis(config.getServerAliveInterval()));
            CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client,
                    config.getServerAliveCountMax());
        }
    }

    /**
     * Configures compression on the SSH client.
     * <p/>
     * Unlike JSch which requires an external zlib JAR, Apache MINA SSHD has built-in compression support.
     */
    private void configureCompression(SshClient client, MinaSftpConfiguration config) {
        if (config.getCompression() > 0) {
            LOG.debug("Using compression level: {}", config.getCompression());
            client.setCompressionFactories(List.of(
                    BuiltinCompressions.delayedZlib,
                    BuiltinCompressions.zlib,
                    BuiltinCompressions.none));
        }
    }

    /**
     * Configures cipher algorithms on the SSH client.
     */
    private void configureCiphers(SshClient client, MinaSftpConfiguration config) {
        if (ObjectHelper.isNotEmpty(config.getCiphers())) {
            LOG.debug("Using ciphers: {}", config.getCiphers());
            List<NamedFactory<Cipher>> cipherFactories = new ArrayList<>();
            for (String cipherName : config.getCiphers()) {
                BuiltinCiphers cipher = BuiltinCiphers.fromFactoryName(cipherName);
                if (cipher != null && cipher.isSupported()) {
                    cipherFactories.add(cipher);
                } else {
                    throw new GenericFileOperationFailedException(
                            "Unknown or unsupported cipher: " + cipherName
                                                                  + ". Available ciphers: " + getAvailableCipherNames());
                }
            }
            if (!cipherFactories.isEmpty()) {
                client.setCipherFactories(cipherFactories);
            }
        }
    }

    /**
     * Configures key exchange protocols on the SSH client.
     */
    private void configureKeyExchangeProtocols(SshClient client, MinaSftpConfiguration config) {
        if (ObjectHelper.isNotEmpty(config.getKeyExchangeProtocols())) {
            LOG.debug("Using key exchange protocols: {}", config.getKeyExchangeProtocols());
            List<KeyExchangeFactory> kexFactories = new ArrayList<>();
            for (String kexName : config.getKeyExchangeProtocols()) {
                BuiltinDHFactories kex = BuiltinDHFactories.fromFactoryName(kexName);
                if (kex != null && kex.isSupported()) {
                    kexFactories.add(ClientBuilder.DH2KEX.apply(kex));
                } else {
                    throw new GenericFileOperationFailedException(
                            "Unknown or unsupported key exchange protocol: " + kexName
                                                                  + ". Available protocols: "
                                                                  + getAvailableKeyExchangeNames());
                }
            }
            if (!kexFactories.isEmpty()) {
                client.setKeyExchangeFactories(kexFactories);
            }
        }
    }

    /**
     * Configures server host key algorithms on the SSH client.
     */
    private void configureServerHostKeys(SshClient client, MinaSftpConfiguration config) {
        if (ObjectHelper.isNotEmpty(config.getServerHostKeys())) {
            LOG.debug("Using server host keys: {}", config.getServerHostKeys());
            List<NamedFactory<Signature>> sigFactories = new ArrayList<>();
            for (String sigName : config.getServerHostKeys()) {
                BuiltinSignatures sig = BuiltinSignatures.fromFactoryName(sigName);
                if (sig != null && sig.isSupported()) {
                    sigFactories.add(sig);
                } else {
                    throw new GenericFileOperationFailedException(
                            "Unknown or unsupported server host key algorithm: " + sigName
                                                                  + ". Available algorithms: "
                                                                  + getAvailableSignatureNames());
                }
            }
            if (!sigFactories.isEmpty()) {
                client.setSignatureFactories(sigFactories);
            }
        }
    }

    /**
     * Connects to the SFTP server and returns a new client session.
     *
     * @param  configuration the remote file configuration with host, port, username
     * @param  sftpConfig    the SFTP-specific configuration
     * @return               a new connected (but not yet authenticated) client session
     * @throws IOException   if connection fails
     */
    private ClientSession connectToServer(RemoteFileConfiguration configuration, MinaSftpConfiguration sftpConfig)
            throws IOException {
        String host = configuration.getHost();
        int port = configuration.getPort();
        String username = configuration.getUsername();

        LOG.trace("Connecting to {}@{}:{}", username, host, port);

        // Prepare local bind address if configured
        InetSocketAddress localAddress = null;
        if (ObjectHelper.isNotEmpty(sftpConfig.getBindAddress())) {
            localAddress = parseBindAddress(sftpConfig.getBindAddress());
            LOG.debug("Using bind address: {} port: {}",
                    localAddress.getAddress().getHostAddress(),
                    localAddress.getPort() == 0 ? "ephemeral" : localAddress.getPort());
        }

        // Connect to server with optional timeout
        if (configuration.getConnectTimeout() > 0) {
            return sshClient.connect(username, host, port, null, localAddress)
                    .verify(configuration.getConnectTimeout(), TimeUnit.MILLISECONDS)
                    .getSession();
        } else {
            return sshClient.connect(username, host, port, null, localAddress)
                    .verify()
                    .getSession();
        }
    }

    /**
     * Configures authentication methods and authenticates the session.
     *
     * @param  session       the client session to authenticate
     * @param  configuration the remote file configuration
     * @param  sftpConfig    the SFTP-specific configuration
     * @throws IOException   if authentication fails
     */
    private void authenticateSession(
            ClientSession session, RemoteFileConfiguration configuration,
            MinaSftpConfiguration sftpConfig)
            throws IOException {
        // Configure preferred authentication methods if specified
        if (ObjectHelper.isNotEmpty(sftpConfig.getPreferredAuthentications())) {
            LOG.debug("Using preferred authentications: {}", sftpConfig.getPreferredAuthentications());
            session.setUserAuthFactoriesNameList(sftpConfig.getPreferredAuthentications());
        }

        // Configure public key accepted algorithms if specified
        if (ObjectHelper.isNotEmpty(sftpConfig.getPublicKeyAcceptedAlgorithms())) {
            LOG.debug("Using public key accepted algorithms: {}", sftpConfig.getPublicKeyAcceptedAlgorithms());
            session.setSignatureFactoriesNameList(sftpConfig.getPublicKeyAcceptedAlgorithms());
        }

        // Add authentication methods (public key first, then password as fallback)
        addPublicKeyAuthentication(session, sftpConfig);
        addPasswordAuthentication(session, configuration);

        // Authenticate with optional timeout
        LOG.trace("Authenticating session...");
        if (configuration.getConnectTimeout() > 0) {
            session.auth().verify(configuration.getConnectTimeout(), TimeUnit.MILLISECONDS);
        } else {
            session.auth().verify();
        }

        LOG.trace("Successfully authenticated to {}@{}:{}",
                configuration.getUsername(), configuration.getHost(), configuration.getPort());
    }

    /**
     * Configure the SFTP buffer sizes based on configuration parameters.
     * <p/>
     * If readBufferSize or writeBufferSize are explicitly set, they take precedence. For backward compatibility, if
     * bulkRequests is set (deprecated), it will be converted to buffer sizes. Each bulk request corresponds to
     * {@link #BULK_REQUEST_SIZE} bytes.
     * <p/>
     * The maximum buffer size is capped at {@link #MAX_BUFFER_SIZE} to avoid data corruption issues in MINA SSHD.
     */
    private void configureBufferSizes(ClientSession session, MinaSftpConfiguration config) {
        // Priority: explicit buffer sizes > bulkRequests (deprecated) > defaults
        Integer readBuffer = config.getReadBufferSize();
        Integer writeBuffer = config.getWriteBufferSize();

        // Backward compatibility: convert bulkRequests to buffer sizes if not explicitly set
        if (config.getBulkRequests() != null && config.getBulkRequests() > 0) {
            // Calculate buffer size: bulkRequests * 32KB, capped at MAX_BUFFER_SIZE
            int bulkBuffer
                    = Math.min(MAX_BUFFER_SIZE, Math.max(BULK_REQUEST_SIZE, config.getBulkRequests() * BULK_REQUEST_SIZE));
            if (readBuffer == null) {
                readBuffer = bulkBuffer;
                LOG.debug("Using bulkRequests to set read buffer size: {} bytes (deprecated, use readBufferSize instead)",
                        readBuffer);
            }
            if (writeBuffer == null) {
                writeBuffer = bulkBuffer;
                LOG.debug("Using bulkRequests to set write buffer size: {} bytes (deprecated, use writeBufferSize instead)",
                        writeBuffer);
            }
        }

        // Apply read buffer size
        if (readBuffer != null && readBuffer > 0) {
            LOG.debug("Configuring read buffer size: {} bytes", readBuffer);
            SftpModuleProperties.READ_BUFFER_SIZE.set(session, readBuffer);
        }

        // Apply write buffer size
        if (writeBuffer != null && writeBuffer > 0) {
            LOG.debug("Configuring write buffer size: {} bytes", writeBuffer);
            SftpModuleProperties.WRITE_BUFFER_SIZE.set(session, writeBuffer);
        }
    }

    /**
     * Parse a bind address string that may optionally include a port.
     * <p/>
     * Supported formats:
     * <ul>
     * <li>{@code 192.168.1.100} - IPv4 address, ephemeral port</li>
     * <li>{@code 192.168.1.100:5000} - IPv4 address with port</li>
     * <li>{@code ::1} - IPv6 address, ephemeral port</li>
     * <li>{@code [::1]:5000} - IPv6 address with port (bracketed notation)</li>
     * <li>{@code localhost} - hostname, ephemeral port</li>
     * <li>{@code localhost:5000} - hostname with port</li>
     * </ul>
     * <p/>
     * Note: This is an enhancement over the JSch-based sftp component which only supports IP/hostname without port.
     *
     * @param  bindAddress                         the bind address string to parse
     * @return                                     InetSocketAddress with the parsed address and port (0 for ephemeral)
     * @throws GenericFileOperationFailedException if the address is invalid
     */
    private InetSocketAddress parseBindAddress(String bindAddress) {
        try {
            String host;
            int port = 0; // Default to ephemeral port

            // Check for IPv6 bracketed notation: [::1]:port or [::1]
            if (bindAddress.startsWith("[")) {
                int closeBracket = bindAddress.indexOf(']');
                if (closeBracket == -1) {
                    throw new IllegalArgumentException("Invalid IPv6 address format: missing closing bracket");
                }
                host = bindAddress.substring(1, closeBracket);
                // Check for port after the bracket
                if (closeBracket + 1 < bindAddress.length()) {
                    if (bindAddress.charAt(closeBracket + 1) == ':') {
                        port = Integer.parseInt(bindAddress.substring(closeBracket + 2));
                    } else {
                        throw new IllegalArgumentException("Invalid format after IPv6 address");
                    }
                }
            } else {
                // Check for port separator - but be careful with IPv6 addresses without brackets
                int lastColon = bindAddress.lastIndexOf(':');
                if (lastColon != -1) {
                    // Check if this looks like an IPv6 address (multiple colons)
                    int firstColon = bindAddress.indexOf(':');
                    if (firstColon == lastColon) {
                        // Only one colon - this is host:port format
                        host = bindAddress.substring(0, lastColon);
                        port = Integer.parseInt(bindAddress.substring(lastColon + 1));
                    } else {
                        // Multiple colons - this is an IPv6 address without brackets (no port)
                        host = bindAddress;
                    }
                } else {
                    // No colon - just a hostname or IPv4 address
                    host = bindAddress;
                }
            }

            return new InetSocketAddress(host, port);
        } catch (NumberFormatException e) {
            throw new GenericFileOperationFailedException(
                    "Invalid port in bind address: " + bindAddress + ". Port must be a valid number.", e);
        } catch (Exception e) {
            throw new GenericFileOperationFailedException(
                    "Invalid bind address: " + bindAddress
                                                          + ". Supported formats: host, host:port, [ipv6], [ipv6]:port",
                    e);
        }
    }

    /**
     * Apply chmod permissions to a file after upload if configured.
     *
     * @param path the file path to apply permissions to
     */
    private void applyChmod(String path) {
        MinaSftpConfiguration config = getMinaSftpConfiguration();
        String chmod = config.getChmod();
        if (ObjectHelper.isEmpty(chmod)) {
            return;
        }

        try {
            int permissions = parseOctalPermissions(chmod);
            LOG.trace("Setting chmod {} on file: {}", chmod, path);

            SftpClient.Attributes attrs = new SftpClient.Attributes();
            attrs.setPermissions(permissions);
            sftpClient.setStat(path, attrs);

            LOG.debug("Applied chmod {} to file: {}", chmod, path);
        } catch (NumberFormatException e) {
            throw new GenericFileOperationFailedException(
                    "Invalid chmod value: " + chmod + ". Must be a valid octal number (e.g., 644, 755).", e);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot set permissions on file: " + path, e);
        }
    }

    /**
     * Apply chmod permissions to a directory after creation if configured.
     *
     * @param path the directory path to apply permissions to
     */
    private void applyChmodDirectory(String path) {
        MinaSftpConfiguration config = getMinaSftpConfiguration();
        String chmodDirectory = config.getChmodDirectory();
        if (ObjectHelper.isEmpty(chmodDirectory)) {
            return;
        }

        try {
            int permissions = parseOctalPermissions(chmodDirectory);
            LOG.trace("Setting chmodDirectory {} on directory: {}", chmodDirectory, path);

            SftpClient.Attributes attrs = new SftpClient.Attributes();
            attrs.setPermissions(permissions);
            sftpClient.setStat(path, attrs);

            LOG.debug("Applied chmodDirectory {} to directory: {}", chmodDirectory, path);
        } catch (NumberFormatException e) {
            throw new GenericFileOperationFailedException(
                    "Invalid chmodDirectory value: " + chmodDirectory + ". Must be a valid octal number (e.g., 755, 700).",
                    e);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot set permissions on directory: " + path, e);
        }
    }

    /**
     * Parse an octal permission string (e.g., "644", "755") to an integer.
     *
     * @param  octalString           the octal permission string
     * @return                       the integer value of the permissions
     * @throws NumberFormatException if the string is not a valid octal number
     */
    private int parseOctalPermissions(String octalString) {
        return Integer.parseInt(octalString, 8);
    }

    /**
     * Configure the filename encoding (charset) for the SFTP client if specified.
     * <p/>
     * By default, MINA SSHD uses UTF-8 for filename encoding. Some legacy SFTP servers use different encodings (e.g.,
     * GBK for Chinese, Shift-JIS for Japanese). This method allows overriding the default encoding.
     */
    private void configureFilenameEncoding() {
        MinaSftpConfiguration config = getMinaSftpConfiguration();
        String encoding = config.getFilenameEncoding();
        if (ObjectHelper.isEmpty(encoding)) {
            return;
        }

        try {
            Charset charset = Charset.forName(encoding);
            LOG.debug("Setting filename encoding to: {}", charset.name());
            sftpClient.setNameDecodingCharset(charset);
        } catch (UnsupportedCharsetException e) {
            throw new GenericFileOperationFailedException(
                    "Unsupported filename encoding: " + encoding
                                                          + ". Please specify a valid charset name (e.g., UTF-8, GBK, ISO-8859-1).",
                    e);
        }
    }

    /**
     * Add public key authentication to the session if configured. Public key auth is attempted first before password.
     * If a certificate is configured, it is combined with the private key for certificate-based authentication.
     */
    private void addPublicKeyAuthentication(ClientSession session, MinaSftpConfiguration config) {
        try {
            KeyPair keyPair = MinaSftpKeyPairProvider.loadKeyPair(config, endpoint.getCamelContext());
            if (keyPair != null) {
                // Check if a certificate is also configured
                OpenSshCertificate certificate = MinaSftpCertificateProvider.loadCertificate(
                        config, endpoint.getCamelContext());

                if (certificate != null) {
                    // Certificate-based authentication: combine certificate with private key
                    LOG.debug("Using certificate-based authentication with OpenSSH certificate");
                    keyPair = new KeyPair(certificate, keyPair.getPrivate());
                } else {
                    LOG.debug("Using public key authentication with {} key",
                            keyPair.getPublic().getAlgorithm());
                }

                session.addPublicKeyIdentity(keyPair);
            } else {
                // Check if certificate is configured without private key
                if (hasCertificateConfigured(config)) {
                    throw new GenericFileOperationFailedException(
                            "Certificate is configured but no private key is provided. "
                                                                  + "Configure privateKeyFile, privateKeyUri, privateKey, or keyPair option.");
                }
            }
        } catch (GenericFileOperationFailedException e) {
            // Re-throw as-is
            throw e;
        } catch (Exception e) {
            LOG.warn("Failed to load authentication credentials: {}", e.getMessage());
            throw new GenericFileOperationFailedException(
                    "Failed to configure public key authentication: " + e.getMessage(), e);
        }
    }

    /**
     * Check if any certificate option is configured.
     */
    private boolean hasCertificateConfigured(MinaSftpConfiguration config) {
        return (config.getCertBytes() != null && config.getCertBytes().length > 0)
                || (config.getCertUri() != null && !config.getCertUri().isEmpty())
                || (config.getCertFile() != null && !config.getCertFile().isEmpty());
    }

    /**
     * Add password authentication to the session if configured. Password is used as fallback after public key.
     */
    private void addPasswordAuthentication(ClientSession session, RemoteFileConfiguration configuration) {
        String password = configuration.getPassword();
        if (password != null) {
            LOG.trace("Adding password authentication for user '{}'", configuration.getUsername());
            session.addPasswordIdentity(password);
        }
    }

    private MinaSftpConfiguration getMinaSftpConfiguration() {
        return (MinaSftpConfiguration) endpoint.getConfiguration();
    }

    /**
     * Returns a comma-separated list of available cipher names for error messages.
     */
    private static String getAvailableCipherNames() {
        return BuiltinCiphers.VALUES.stream()
                .filter(BuiltinCiphers::isSupported)
                .map(BuiltinCiphers::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns a comma-separated list of available key exchange protocol names for error messages.
     */
    private static String getAvailableKeyExchangeNames() {
        return BuiltinDHFactories.VALUES.stream()
                .filter(BuiltinDHFactories::isSupported)
                .map(BuiltinDHFactories::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns a comma-separated list of available signature/host key algorithm names for error messages.
     */
    private static String getAvailableSignatureNames() {
        return BuiltinSignatures.VALUES.stream()
                .filter(BuiltinSignatures::isSupported)
                .map(BuiltinSignatures::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Log the result of compression negotiation. Logs a WARNING if compression was requested but the server negotiated
     * to 'none'.
     */
    private void logCompressionNegotiationResult(ClientSession session, MinaSftpConfiguration config) {
        try {
            // Get the negotiated compression algorithms (client-to-server and server-to-client)
            String c2sCompression = session.getKexState() != null
                    ? session.getNegotiatedKexParameter(org.apache.sshd.common.kex.KexProposalOption.C2SCOMP)
                    : null;
            String s2cCompression = session.getKexState() != null
                    ? session.getNegotiatedKexParameter(org.apache.sshd.common.kex.KexProposalOption.S2CCOMP)
                    : null;

            boolean compressionNone = "none".equals(c2sCompression) || "none".equals(s2cCompression);

            if (compressionNone) {
                LOG.warn("Compression was requested (level={}) but server does not support compression. "
                         + "Falling back to uncompressed transfer. Negotiated: c2s={}, s2c={}",
                        config.getCompression(), c2sCompression, s2cCompression);
            } else {
                LOG.debug("Compression negotiated successfully: c2s={}, s2c={}",
                        c2sCompression, s2cCompression);
            }
        } catch (Exception e) {
            // Don't fail the connection just because we couldn't log compression info
            LOG.trace("Could not determine negotiated compression: {}", e.getMessage());
        }
    }

    /**
     * Create the server key verifier based on configuration.
     * <p/>
     * If a custom ServerKeyVerifier is provided via configuration, it is used exclusively, ignoring all other host key
     * options.
     * <p/>
     * If no known hosts source is configured and strictHostKeyChecking is disabled, returns AcceptAllServerKeyVerifier
     * for backward compatibility.
     */
    private ServerKeyVerifier createServerKeyVerifier(MinaSftpConfiguration config) {
        // Check for custom verifier first - takes precedence over all other options
        if (config.getServerKeyVerifier() != null) {
            LOG.debug("Using custom ServerKeyVerifier: {}", config.getServerKeyVerifier().getClass().getName());
            return config.getServerKeyVerifier();
        }

        // Check if any host key verification source is configured
        boolean hasKnownHostsSource = config.getKnownHosts() != null && config.getKnownHosts().length > 0
                || config.getKnownHostsUri() != null && !config.getKnownHostsUri().isEmpty()
                || config.getKnownHostsFile() != null && !config.getKnownHostsFile().isEmpty()
                || config.isUseUserKnownHostsFile();

        boolean strictChecking = "yes".equalsIgnoreCase(config.getStrictHostKeyChecking());

        // If no sources configured and strict checking is disabled, accept all (backward compatible)
        if (!hasKnownHostsSource && !strictChecking) {
            LOG.debug("No known hosts configured and strictHostKeyChecking=no, using AcceptAllServerKeyVerifier");
            return AcceptAllServerKeyVerifier.INSTANCE;
        }

        // Use our built-in verifier
        LOG.debug("Using built-in MinaSftpServerKeyVerifier with strictHostKeyChecking={}", config.getStrictHostKeyChecking());
        return new MinaSftpServerKeyVerifier(endpoint.getCamelContext(), config);
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            return session != null && session.isOpen() && sftpClient != null && sftpClient.isOpen();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (sftpClient != null && sftpClient.isOpen()) {
                try {
                    sftpClient.close();
                } catch (IOException e) {
                    LOG.debug("Error closing SFTP client: {}", e.getMessage(), e);
                }
            }
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                } catch (IOException e) {
                    LOG.debug("Error closing session: {}", e.getMessage(), e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (sftpClient != null) {
                try {
                    sftpClient.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (session != null) {
                try {
                    session.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (sshClient != null) {
                try {
                    sshClient.stop();
                } catch (Exception e) {
                    // ignore
                }
            }
        } finally {
            sftpClient = null;
            session = null;
            sshClient = null;
            lock.unlock();
        }
    }

    private void reconnectIfNecessary(Exchange exchange) {
        if (!isConnected()) {
            connect(endpoint.getConfiguration(), exchange);
        }
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.debug("Deleting file: {}", name);
            reconnectIfNecessary(null);
            sftpClient.remove(name);
            return true;
        } catch (IOException e) {
            LOG.debug("Cannot delete file {}: {}", name, e.getMessage(), e);
            throw new GenericFileOperationFailedException("Cannot delete file: " + name, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.debug("Renaming file: {} to: {}", from, to);
            reconnectIfNecessary(null);
            to = FileUtil.compactPath(to, '/');
            sftpClient.rename(from, to);
            return true;
        } catch (IOException e) {
            LOG.debug("Cannot rename file from: {} to: {}", from, to, e);
            throw new GenericFileOperationFailedException("Cannot rename file from: " + from + " to: " + to, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            directory = endpoint.getConfiguration().normalizePath(directory);
            LOG.trace("buildDirectory({},{})", directory, absolute);

            boolean success = false;

            try {
                // maybe the full directory already exists
                try {
                    // Use stat to check if the directory exists - stat throws if path doesn't exist
                    // Note: readDir doesn't throw for non-existent directories in MINA SSHD
                    SftpClient.Attributes attrs = sftpClient.stat(directory);
                    if (attrs != null && attrs.isDirectory()) {
                        success = true;
                    }
                } catch (Exception e) {
                    // ignore, we could not stat directory so try to create it
                }

                if (!success) {
                    LOG.debug("Trying to build remote directory: {}", directory);
                    try {
                        sftpClient.mkdir(directory);
                        // Apply chmodDirectory after successfully creating directory
                        applyChmodDirectory(directory);
                        success = true;
                    } catch (IOException e) {
                        // we are here if the server side doesn't create
                        // intermediate folders so create the folder one by one
                        success = buildDirectoryChunks(directory);
                    }
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException("Cannot build directory: " + directory, e);
            }

            return success;
        } finally {
            lock.unlock();
        }
    }

    private boolean buildDirectoryChunks(String dirName) throws IOException {
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("/|\\\\");

        boolean success = false;
        boolean first = true;
        for (String dir : dirs) {
            if (first) {
                first = false;
            } else {
                sb.append('/');
            }
            sb.append(dir);

            String directory = endpoint.getConfiguration().normalizePath(sb.toString());

            if (!(directory.equals("/") || directory.equals("\\"))) {
                try {
                    LOG.trace("Trying to build remote directory by chunk: {}", directory);
                    sftpClient.mkdir(directory);
                    // Apply chmodDirectory after successfully creating directory
                    applyChmodDirectory(directory);
                    success = true;
                } catch (IOException e) {
                    // ignore keep trying to create the rest of the path
                }
            }
        }

        return success;
    }

    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("getCurrentDirectory()");
            // Return the tracked current directory if set, otherwise get it from the server
            if (currentDirectory != null) {
                LOG.trace("Current dir (tracked): {}", currentDirectory);
                return currentDirectory;
            }
            String answer = sftpClient.canonicalPath(".");
            LOG.trace("Current dir (from server): {}", answer);
            return answer;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot get current directory", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("changeCurrentDirectory({})", path);
            if (ObjectHelper.isEmpty(path)) {
                return;
            }

            // must compact path so SFTP server can traverse correctly, make use of
            // the '/' separator
            String before = path;
            char separatorChar = '/';
            path = FileUtil.compactPath(path, separatorChar);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Compacted path: {} -> {} using separator: {}", before, path, separatorChar);
            }

            // not stepwise should change directory in one operation
            if (!endpoint.getConfiguration().isStepwise()) {
                doChangeDirectory(path);
                return;
            }
            if (getCurrentDirectory().startsWith(path)) {
                // extract the path segment relative to the target path and make
                // sure it keeps the preceding '/' for the regex op
                String p = getCurrentDirectory().substring(path.length() - (path.endsWith("/") ? 1 : 0));
                if (p.isEmpty()) {
                    return;
                }
                // the first character must be '/' and hence removed
                path = UP_DIR_PATTERN.matcher(p).replaceAll("/..").substring(1);
            }

            // if it starts with the root path then a little special handling for
            // that
            if (FileUtil.hasLeadingSeparator(path)) {
                // change to root path
                if (!path.matches("^[a-zA-Z]:(//|\\\\).*$")) {
                    doChangeDirectory(path.substring(0, 1));
                    path = path.substring(1);
                } else {
                    if (path.matches("^[a-zA-Z]:(//).*$")) {
                        doChangeDirectory(path.substring(0, 3));
                        path = path.substring(3);
                    } else if (path.matches("^[a-zA-Z]:(\\\\).*$")) {
                        doChangeDirectory(path.substring(0, 4));
                        path = path.substring(4);
                    }
                }
            }

            // split into multiple dirs
            final String[] dirs = path.split("/|\\\\");

            if (dirs == null || dirs.length == 0) {
                // path was just a relative single path
                doChangeDirectory(path);
                return;
            }

            // there are multiple dirs so do this in chunks
            for (String dir : dirs) {
                doChangeDirectory(dir);
            }
        } finally {
            lock.unlock();
        }
    }

    private void doChangeDirectory(String path) {
        if (path == null || ".".equals(path) || ObjectHelper.isEmpty(path)) {
            return;
        }
        LOG.trace("Changing directory: {}", path);

        // MINA SSHD doesn't have a "cd" command like JSch's channel.cd().
        // We track the current directory and verify it exists.
        String newDirectory;
        if (FileUtil.hasLeadingSeparator(path)) {
            // Absolute path
            newDirectory = path;
        } else if (currentDirectory != null) {
            // Relative path - combine with current directory
            newDirectory = FileUtil.compactPath(currentDirectory + "/" + path, '/');
        } else {
            // No current directory set yet, use as-is
            newDirectory = path;
        }

        LOG.trace("doChangeDirectory: path={}, currentDirectory={}, newDirectory={}", path, currentDirectory, newDirectory);

        // Verify the directory exists using stat() - more reliable than readDir()
        // Note: readDir doesn't throw for non-existent directories in MINA SSHD
        try {
            SftpClient.Attributes attrs = sftpClient.stat(newDirectory);
            if (attrs == null || !attrs.isDirectory()) {
                throw new GenericFileOperationFailedException(
                        "Cannot change directory to: " + path + " (path is not a directory)");
            }
            currentDirectory = newDirectory;
            LOG.trace("doChangeDirectory: stat succeeded, updated currentDirectory={}", currentDirectory);
        } catch (IOException e) {
            LOG.trace("doChangeDirectory: stat failed for {}: {}", newDirectory, e.getMessage());
            throw new GenericFileOperationFailedException("Cannot change directory to: " + path, e);
        }
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("changeToParentDirectory()");
            String current = getCurrentDirectory();
            // Preserve whether the path was absolute or relative
            boolean wasAbsolute = current.startsWith("/");
            String parent = FileUtil.compactPath(current + "/..");
            // Only add leading / if the original path was absolute
            if (wasAbsolute && !parent.startsWith("/")) {
                parent = "/" + parent;
            }
            changeCurrentDirectory(parent);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SftpRemoteFile[] listFiles() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // Use the tracked current directory if set, otherwise use "."
            String dir = currentDirectory != null ? currentDirectory : ".";
            return listFiles(dir);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SftpRemoteFile[] listFiles(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("Listing remote files from path {}", path);
            reconnectIfNecessary(null);
            if (ObjectHelper.isEmpty(path)) {
                path = ".";
            }

            List<MinaSftpRemoteFile> result = new ArrayList<>();
            for (DirEntry entry : sftpClient.readDir(path)) {
                result.add(new MinaSftpRemoteFile(entry));
            }

            return result.toArray(new MinaSftpRemoteFile[0]);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot list directory: " + path, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size)
            throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("retrieveFile({})", name);
            if (ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
                return retrieveFileToFileInLocalWorkDirectory(name, exchange);
            } else {
                return retrieveFileToStreamInBody(name, exchange);
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        try {
            GenericFile<SftpRemoteFile> target
                    = (GenericFile<SftpRemoteFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

            InputStream is = sftpClient.read(name);

            if (endpoint.getConfiguration().isStreamDownload()) {
                // Streaming mode: pass InputStream directly to downstream processors
                // Keep stream open - it will be closed by releaseRetrievedFileResources()
                target.setBody(is);
                exchange.getIn().setHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, is);
            } else {
                // Non-streaming mode: buffer entire file into memory
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    IOHelper.copyAndCloseInput(is, bos);
                    IOHelper.close(bos);
                    target.setBody(bos.toByteArray());
                } catch (Exception e) {
                    IOHelper.close(is);
                    throw e;
                }
            }

            return true;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange)
            throws GenericFileOperationFailedException {
        File temp;
        File local = new File(endpoint.getLocalWorkDirectory());
        GenericFile<SftpRemoteFile> file
                = (GenericFile<SftpRemoteFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        ObjectHelper.notNull(file, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

        try {
            String relativeName = file.getRelativeFilePath();
            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);
            local.mkdirs();

            if (temp.exists()) {
                if (!FileUtil.deleteFile(temp)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + temp);
                }
            }
            if (local.exists()) {
                if (!FileUtil.deleteFile(local)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + local);
                }
            }

            // create parent folders
            temp.getParentFile().mkdirs();

            try (InputStream is = sftpClient.read(name);
                 OutputStream os = new FileOutputStream(temp)) {
                IOHelper.copy(is, os);
            }

            LOG.debug("Retrieve file to local work file result: true");

            if (temp.exists()) {
                if (!FileUtil.renameFile(temp, local, false)) {
                    throw new GenericFileOperationFailedException(
                            "Cannot rename local work file from: " + temp + " to: " + local);
                }
            }

            file.setBody(local);
            return true;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        }
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            InputStream is = exchange.getIn().getHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, InputStream.class);
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new GenericFileOperationFailedException(e.getMessage(), e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            return doStoreFile(name, exchange);
        } finally {
            lock.unlock();
        }
    }

    private boolean doStoreFile(String name, Exchange exchange) throws GenericFileOperationFailedException {
        LOG.trace("storeFile({})", name);

        reconnectIfNecessary(exchange);

        name = endpoint.getConfiguration().normalizePath(name);

        // build directory if auto create is enabled
        if (endpoint.isAutoCreate()) {
            String dir = FileUtil.onlyPath(name);
            if (dir != null) {
                buildDirectory(dir, false);
            }
        }

        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore || endpoint.getFileExist() == GenericFileExist.Fail
                || endpoint.getFileExist() == GenericFileExist.Move) {
            boolean existFile = existsFile(name);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                LOG.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Move) {
                // move any existing file first
                endpoint.getMoveExistingFileStrategy().moveExistingFile(endpoint, this, name);
            }
        }

        try {
            InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);

            boolean append = endpoint.getFileExist() == GenericFileExist.Append;

            EnumSet<OpenMode> modes;
            if (append) {
                modes = EnumSet.of(OpenMode.Write, OpenMode.Create, OpenMode.Append);
            } else {
                modes = EnumSet.of(OpenMode.Write, OpenMode.Create, OpenMode.Truncate);
            }

            try (OutputStream os = sftpClient.write(name, modes)) {
                IOHelper.copy(is, os);
            } finally {
                IOHelper.close(is);
            }

            // Apply chmod after successful upload if configured
            applyChmod(name);

            LOG.debug("Store file: {} successful", name);
            return true;
        } catch (InvalidPayloadException | IOException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        }
    }

    @Override
    public boolean storeFileDirectly(String name, String payload) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.debug("Storing file directly: {}", name);
            reconnectIfNecessary(null);

            try (OutputStream os = sftpClient.write(name)) {
                os.write(payload.getBytes());
            }
            return true;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot store file directly: " + name, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("existsFile({})", name);
            reconnectIfNecessary(null);

            try {
                sftpClient.stat(name);
                return true;
            } catch (IOException e) {
                // file does not exist
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (isConnected()) {
                try {
                    // Send a stat command to the root as a noop
                    sftpClient.stat("/");
                    return true;
                } catch (IOException e) {
                    LOG.debug("SFTP NOOP failed: {}", e.getMessage(), e);
                    return false;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Site commands are an FTP-specific feature (RFC 959) and are not part of the SFTP protocol.
     * <p/>
     * This method is a no-op that always returns {@code true} for backward compatibility. The original JSch-based
     * SftpOperations uses the same approach. Throwing {@link UnsupportedOperationException} was considered but rejected
     * to avoid breaking existing code that may call this method.
     *
     * @param  command the site command (ignored)
     * @return         always {@code true}
     */
    @Override
    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        LOG.trace("sendSiteCommand({}) - SFTP does not support site commands, ignoring", command);
        return true;
    }
}
