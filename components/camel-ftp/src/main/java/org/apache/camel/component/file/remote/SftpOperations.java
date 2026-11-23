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
package org.apache.camel.component.file.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Proxy;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileHelper;
import org.apache.camel.component.file.GenericFileListingOperations;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HomeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.function.ThrowingRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * SFTP remote file operations
 * <p/>
 * The JSCH session and channel are not thread-safe so we need to synchronize access to using this operation.
 */
public class SftpOperations
        implements RemoteFileOperations<SftpRemoteFile>, SftpSymbolicLinkOperations,
        GenericFileListingOperations<SftpRemoteFile> {
    private static final Logger LOG = LoggerFactory.getLogger(SftpOperations.class);
    private static final Pattern UP_DIR_PATTERN = Pattern.compile("/[^/]+");
    private static final int OK_STATUS = 0;
    private static final String OK_MESSAGE = "OK";
    // while the stop of the producer waits for an operation of another thread, how often it checks whether the shutdown
    // is forced, and then how long it waits for the disconnect
    private static final long DISCONNECT_ON_STOP_WAIT_MILLIS = 1000;
    /**
     * A connection used for an upload, a download or a noop this recently ago is treated as alive: the noop before the
     * next upload is skipped, as it would not show anything new about the connection state. This matches the value used
     * by {@link FtpOperations}.
     */
    private static final long RECENTLY_USED_NANOS = TimeUnit.SECONDS.toNanos(1);
    /**
     * How many directories autoCreate remembers on a connection at most: the gain is for repeated uploads into the same
     * directories, and a producer that writes into a new directory for each message must not grow without limit.
     */
    private static final int MAX_BUILT_DIRECTORIES = 100;
    private Proxy proxy;
    private SftpEndpoint endpoint;
    private ChannelSftp channel;
    // volatile: disconnectOnStop reads it without the lock
    private volatile Session session;
    private final Lock lock = new ReentrantLock();
    // whether connect, with its reconnect attempts, holds the lock
    private volatile boolean connecting;
    /**
     * The directory to change back to before an operation that uses names relative to the directory where the
     * operations started: a stepwise upload or download stays in the directory of its file, so that the next file in
     * that directory needs no change of directory. {@code null} when there is nothing to change back. JSch keeps the
     * current directory on the client and sends absolute paths, but checks every change of directory with the server.
     */
    private String restoreDirectory;
    /**
     * The directory part of the file name (relative to {@link #restoreDirectory}, or absolute) that a stepwise upload
     * or download changed into. {@code null} when the connection is not in such a directory, also when the change
     * failed half way.
     */
    private String stepwiseDirectory;
    /**
     * Whether an upload or a download runs, with or without stepwise: the operations that it calls, such as the
     * existence check or the move of an existing file, use names relative to the directory of the file and must not
     * change back.
     */
    private boolean inStepwiseOperation;
    /**
     * The directories that a file was uploaded into on this connection, as absolute paths (see
     * {@link #builtDirectoryKey(String)}), so that autoCreate does not check them again before the next upload. Only an
     * upload adds a directory: the moves of a consumer check their directory each time. Holds the
     * {@link #MAX_BUILT_DIRECTORIES} most recently used ones.
     */
    private final Set<String> builtDirectories = Collections.newSetFromMap(LRUCacheFactory.newLRUCache(MAX_BUILT_DIRECTORIES));
    /**
     * The timestamp (in nanoseconds, from {@link System#nanoTime()}) of the last completed upload, download, or noop.
     * Zero means no operation has completed yet. Used by {@link #isRecentlyUsed()} to skip the pre-upload noop when the
     * connection was used a moment ago and is therefore known to be alive.
     */
    private volatile long lastCompletedNanos;

    private static class TaskPayload {
        final RemoteFileConfiguration configuration;
        private Exception exception;
        // the server rejected the login, which ends the reconnect attempts
        private boolean authenticationFailed;

        public TaskPayload(RemoteFileConfiguration configuration) {
            this.configuration = configuration;
        }
    }

    /**
     * Ends the session when the server asks for the password again: JSch asks for it again each time the server rejects
     * it, up to MaxAuthTries times per session, and each rejected password counts towards the lockout policy of the
     * server.
     */
    private static final class PasswordNotAcceptedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PasswordNotAcceptedException(String username) {
            super("Auth fail for user '" + username + "': the server did not accept the password");
        }
    }

    public SftpOperations() {
    }

    public SftpOperations(Proxy proxy) {
        this.proxy = proxy;
    }

    /**
     * Extended user info which supports interactive keyboard mode, by entering the password.
     */
    public interface ExtendedUserInfo extends UserInfo, UIKeyboardInteractive {
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<SftpRemoteFile> endpoint) {
        this.endpoint = (SftpEndpoint) endpoint;
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
                // already connected
                return true;
            }
            connecting = true;

            ScheduledExecutorService ses = endpoint.getCamelContext().getExecutorServiceManager()
                    .newSingleThreadScheduledExecutor(this, "SftpReconnect");
            try {
                BlockingTask task = Tasks.backgroundTask()
                        .withBudget(Budgets.iterationTimeBudget()
                                .withMaxIterations(Budgets.atLeastOnce(endpoint.getMaximumReconnectAttempts()))
                                .withUnlimitedDuration()
                                .withInterval(Duration.ofMillis(endpoint.getReconnectDelay()))
                                .build())
                        .withScheduledExecutor(ses)
                        .withName("SftpReconnect")
                        .build();

                TaskPayload payload = new TaskPayload(configuration);

                if (!task.run(endpoint.getCamelContext(), this::tryConnect, payload) || payload.authenticationFailed) {
                    throw new GenericFileOperationFailedException(
                            "Cannot connect to " + configuration.remoteServerInformation(),
                            payload.exception);
                }
            } finally {
                endpoint.getCamelContext().getExecutorServiceManager().shutdown(ses);
            }

            configureBulkRequests();
            // a new channel starts in the login directory
            resetDirectoryState();

            return true;
        } finally {
            connecting = false;
            lock.unlock();
        }
    }

    /**
     * The authentication methods of the preferredAuthentications option as JSch compares them with the methods that the
     * server offers, which JSch turns to lower case: in lower case, without the spaces around them. JSch skips a method
     * written in another case, such as {@code Password}, as one that the server does not offer.
     */
    static String normalizePreferredAuthentications(String methods) {
        return Arrays.stream(methods.split(","))
                .map(String::trim)
                .filter(method -> !method.isEmpty())
                .map(method -> method.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(","));
    }

    /**
     * The methods of the preferredAuthentications option that JSch has no implementation for, which it skips.
     */
    static List<String> unknownAuthenticationMethods(String methods) {
        return Arrays.stream(normalizePreferredAuthentications(methods).split(","))
                .filter(method -> !method.isEmpty() && JSch.getConfig("userauth." + method) == null)
                .toList();
    }

    /**
     * One attempt of the reconnect task.
     *
     * @return whether the task is done: connected, or the server rejected the login, which is not retried as each
     *         rejected login counts towards the lockout policy of the server
     */
    private boolean tryConnect(TaskPayload payload) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Reconnect attempt to {}", payload.configuration.remoteServerInformation());
        }

        try {
            if (channel == null || !channel.isConnected()) {
                if (session == null || !session.isConnected()) {
                    LOG.trace("Session isn't connected, trying to recreate and connect.");

                    session = createSession(payload.configuration);

                    if (endpoint.getConfiguration().getConnectTimeout() > 0) {
                        LOG.trace("Connecting use connectTimeout: {} ...", endpoint.getConfiguration().getConnectTimeout());
                        session.connect(endpoint.getConfiguration().getConnectTimeout());
                    } else {
                        LOG.trace("Connecting ...");
                        session.connect();
                    }
                }

                LOG.trace("Channel isn't connected, trying to recreate and connect.");
                channel = (ChannelSftp) session.openChannel("sftp");

                if (endpoint.getConfiguration().getFilenameEncoding() != null) {
                    Charset ch = Charset.forName(endpoint.getConfiguration().getFilenameEncoding());
                    LOG.trace("Using filename encoding: {}", ch);
                    channel.setFilenameEncoding(ch);
                }
                if (endpoint.getConfiguration().getConnectTimeout() > 0) {
                    LOG.trace("Connecting use connectTimeout: {} ...", endpoint.getConfiguration().getConnectTimeout());
                    channel.connect(endpoint.getConfiguration().getConnectTimeout());
                } else {
                    LOG.trace("Connecting ...");
                    channel.connect();
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Connected to {}", payload.configuration.remoteServerInformation());
                }
            }
        } catch (JSchException | PasswordNotAcceptedException e) {
            payload.exception = e;
            payload.authenticationFailed = isAuthenticationFailure(e);

            return payload.authenticationFailed;
        }

        return true;
    }

    private static boolean isAuthenticationFailure(Exception e) {
        // JSch fails with "Auth fail for methods ..." or "Auth cancel for methods ..." when no method authenticated
        String message = e.getMessage();
        return e instanceof PasswordNotAcceptedException
                || message != null && (message.startsWith("Auth fail") || message.startsWith("Auth cancel"));
    }

    private void configureBulkRequests() {
        try {
            tryConfigureBulkRequests();
        } catch (JSchException e) {
            throw new GenericFileOperationFailedException("Failed to configure number of bulk requests", e);
        }
    }

    private void tryConfigureBulkRequests() throws JSchException {
        Integer bulkRequests = endpoint.getConfiguration().getBulkRequests();

        if (bulkRequests != null) {
            LOG.trace("configuring channel to use up to {} bulk request(s)", bulkRequests);

            channel.setBulkRequests(bulkRequests);
        }
    }

    protected Session createSession(final RemoteFileConfiguration configuration) throws JSchException {
        final JSch jsch = new JSch();
        JSch.setLogger(new JSchLogger(endpoint.getConfiguration().getJschLoggingLevel()));

        SftpConfiguration sftpConfig = (SftpConfiguration) configuration;

        // Resolve certificate bytes once — used for both identity loading and key type detection
        byte[] certData = resolveCertificateBytes(sftpConfig);

        if (isNotEmpty(sftpConfig.getPrivateKeyFile())) {
            LOG.debug("Using private keyfile: {}", sftpConfig.getPrivateKeyFile());
            byte[] passphrase = null;
            if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
                passphrase = sftpConfig.getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            if (certData != null) {
                // Use byte-based overload for certificate — JSch's file-based
                // addIdentity(prvkey, pubkey, passphrase) treats the second parameter
                // as a public key file, not a certificate
                LOG.debug("Using OpenSSH certificate for authentication");
                try {
                    byte[] keyData = Files.readAllBytes(Paths.get(sftpConfig.getPrivateKeyFile()));
                    jsch.addIdentity(sftpConfig.getPrivateKeyFile(), keyData, certData, passphrase);
                } catch (IOException e) {
                    throw new JSchException("Cannot read private key file: " + sftpConfig.getPrivateKeyFile(), e);
                }
            } else {
                // No explicit cert — JSch auto-discovers <key>-cert.pub if it exists
                jsch.addIdentity(sftpConfig.getPrivateKeyFile(), passphrase);
            }
        }

        if (sftpConfig.getPrivateKey() != null) {
            LOG.debug("Using private key information from byte array");
            byte[] passphrase = null;
            if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
                passphrase = sftpConfig.getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            jsch.addIdentity("ID", sftpConfig.getPrivateKey(), certData, passphrase);
        }

        if (sftpConfig.getPrivateKeyUri() != null) {
            LOG.debug("Using private key uri : {}", sftpConfig.getPrivateKeyUri());
            byte[] passphrase = null;
            if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
                passphrase = sftpConfig.getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            try {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(),
                        sftpConfig.getPrivateKeyUri());
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOHelper.copyAndCloseInput(is, bos);
                jsch.addIdentity("ID", bos.toByteArray(), certData, passphrase);
            } catch (IOException e) {
                throw new JSchException("Cannot read resource: " + sftpConfig.getPrivateKeyUri(), e);
            }
        }

        if (sftpConfig.getKeyPair() != null) {
            LOG.debug("Using private key information from key pair");
            KeyPair keyPair = sftpConfig.getKeyPair();
            if (keyPair.getPrivate() != null) {
                // Encode the private key in PEM format for JSCH
                StringBuilder sb = new StringBuilder(256);
                sb.append("-----BEGIN PRIVATE KEY-----").append("\n");
                sb.append(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())).append("\n");
                sb.append("-----END PRIVATE KEY-----").append("\n");

                jsch.addIdentity("ID", sb.toString().getBytes(StandardCharsets.UTF_8), certData, null);
            } else {
                LOG.warn("PrivateKey in the KeyPair must be filled");
            }
        }

        if (isNotEmpty(sftpConfig.getKnownHostsFile())) {
            LOG.debug("Using knownhosts file: {}", sftpConfig.getKnownHostsFile());
            jsch.setKnownHosts(sftpConfig.getKnownHostsFile());
        }

        if (isNotEmpty(sftpConfig.getKnownHostsUri())) {
            LOG.debug("Using known hosts uri: {}", sftpConfig.getKnownHostsUri());
            try {
                InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(endpoint.getCamelContext(),
                        sftpConfig.getKnownHostsUri());
                jsch.setKnownHosts(is);
            } catch (IOException e) {
                throw new JSchException("Cannot read resource: " + sftpConfig.getKnownHostsUri(), e);
            }
        }

        if (sftpConfig.getKnownHosts() != null) {
            LOG.debug("Using known hosts information from byte array");
            jsch.setKnownHosts(new ByteArrayInputStream(sftpConfig.getKnownHosts()));
        }

        String knownHostsFile = sftpConfig.getKnownHostsFile();
        if (knownHostsFile == null && !isNotEmpty(sftpConfig.getKnownHostsUri())
                && sftpConfig.getKnownHosts() == null && sftpConfig.isUseUserKnownHostsFile()) {
            knownHostsFile = HomeHelper.resolveHomeDir() + "/.ssh/known_hosts";
            LOG.info("Known host file not configured, using user known host file: {}", knownHostsFile);
        }
        if (ObjectHelper.isNotEmpty(knownHostsFile)) {
            LOG.debug("Using known hosts information from file: {}", knownHostsFile);
            jsch.setKnownHosts(knownHostsFile);
        }

        final Session session = jsch.getSession(configuration.getUsername(), configuration.getHost(), configuration.getPort());

        if (isNotEmpty(sftpConfig.getCiphers())) {
            LOG.debug("Using ciphers: {}", sftpConfig.getCiphers());
            session.setConfig("cipher.s2c", sftpConfig.getCiphers());
            session.setConfig("cipher.c2s", sftpConfig.getCiphers());
        }

        if (isNotEmpty(sftpConfig.getKeyExchangeProtocols())) {
            LOG.debug("Using KEX: {}", sftpConfig.getKeyExchangeProtocols());
            session.setConfig("kex", sftpConfig.getKeyExchangeProtocols());
        }

        if (isNotEmpty(sftpConfig.getStrictHostKeyChecking())) {
            LOG.debug("Using StrictHostKeyChecking: {}", sftpConfig.getStrictHostKeyChecking());
            session.setConfig("StrictHostKeyChecking", sftpConfig.getStrictHostKeyChecking());
        }

        session.setServerAliveInterval(sftpConfig.getServerAliveInterval());
        session.setServerAliveCountMax(sftpConfig.getServerAliveCountMax());

        // compression
        if (sftpConfig.getCompression() > 0) {
            LOG.debug("Using compression: {}", sftpConfig.getCompression());
            session.setConfig("compression.s2c", "zlib@openssh.com,zlib,none");
            session.setConfig("compression.c2s", "zlib@openssh.com,zlib,none");
            session.setConfig("compression_level", Integer.toString(sftpConfig.getCompression()));
        }

        // set the PreferredAuthentications
        if (sftpConfig.getPreferredAuthentications() != null) {
            String preferredAuthentications = normalizePreferredAuthentications(sftpConfig.getPreferredAuthentications());
            LOG.debug("Using PreferredAuthentications: {}", preferredAuthentications);
            session.setConfig("PreferredAuthentications", preferredAuthentications);
        }

        // set the ServerHostKeys
        if (sftpConfig.getServerHostKeys() != null) {
            LOG.debug("Using ServerHostKeys: {}", sftpConfig.getServerHostKeys());
            session.setConfig("server_host_key", sftpConfig.getServerHostKeys());
        }

        // set the PublicKeyAcceptedAlgorithms
        if (sftpConfig.getPublicKeyAcceptedAlgorithms() != null) {
            LOG.debug("Using PublicKeyAcceptedAlgorithms: {}", sftpConfig.getPublicKeyAcceptedAlgorithms());
            session.setConfig("PubkeyAcceptedAlgorithms", sftpConfig.getPublicKeyAcceptedAlgorithms());
        }

        // Auto-configure PubkeyAcceptedAlgorithms for certificate authentication.
        // JSch's defaults exclude SHA-1 based algorithms (matching OpenSSH 8.2+ policy),
        // which includes ssh-rsa-cert-v01@openssh.com (or ssh-rsa-cert per newer RFC
        // drafts). If the loaded certificate uses a key type not in the accepted list,
        // JSch silently skips the certificate identity and auth fails.
        // Detect the cert type and add it if missing.
        if (sftpConfig.getPublicKeyAcceptedAlgorithms() == null) {
            String certKeyType = detectCertKeyType(certData);
            if (certKeyType != null) {
                String defaults = JSch.getConfig("PubkeyAcceptedAlgorithms");
                if (defaults != null && !defaults.contains(certKeyType)) {
                    session.setConfig("PubkeyAcceptedAlgorithms", certKeyType + "," + defaults);
                    LOG.debug("Added certificate key type {} to PubkeyAcceptedAlgorithms", certKeyType);
                }
            }
        }

        // set the CASignatureAlgorithms
        if (sftpConfig.getCaSignatureAlgorithms() != null) {
            LOG.debug("Using CASignatureAlgorithms: {}", sftpConfig.getCaSignatureAlgorithms());
            session.setConfig("ca_signature_algorithms", sftpConfig.getCaSignatureAlgorithms());
        }

        // set user information
        session.setUserInfo(new ExtendedUserInfo() {

            private final CamelLogger messageLogger
                    = new CamelLogger(LOG, ((SftpConfiguration) configuration).getServerMessageLoggingLevel());
            // the password is given once per session, with the password or the keyboard-interactive method
            private boolean passwordGiven;

            public String getPassphrase() {
                return null;
            }

            public String getPassword() {
                String password = configuration.getPassword();
                passwordGiven |= password != null;
                return password;
            }

            public boolean promptPassword(String s) {
                checkPasswordNotGiven();
                return true;
            }

            public boolean promptPassphrase(String s) {
                return true;
            }

            public boolean promptYesNo(String s) {
                // are we prompted because the known host files does not exist, and asked whether to auto-create the file
                boolean knownHostFile = s != null && s.endsWith("Are you sure you want to create it?");
                if (knownHostFile && ((SftpConfiguration) configuration).isAutoCreateKnownHostsFile()) {
                    LOG.warn("Server asks for confirmation (yes|no): {}. Camel will answer yes.", s);
                    return true;
                } else {
                    LOG.warn("Server asks for confirmation (yes|no): {}. Camel will answer no.", s);
                    // Return 'false' indicating modification of the hosts file is
                    // disabled.
                    return false;
                }
            }

            public void showMessage(String s) {
                messageLogger.log("FTP Server: " + s);
            }

            public String[] promptKeyboardInteractive(
                    String destination, String name, String instruction, String[] prompt, boolean[] echo) {
                // must return an empty array if password is null, or if the server asks nothing (only an instruction)
                if (configuration.getPassword() == null || prompt.length == 0) {
                    return new String[0];
                } else {
                    checkPasswordNotGiven();
                    passwordGiven = true;
                    return new String[] { configuration.getPassword() };
                }
            }

            private void checkPasswordNotGiven() {
                if (passwordGiven) {
                    // asked again: the server did not accept the password, so end the session instead of sending it
                    // once more (JSch passes the exception on from Session.connect, after it disconnected)
                    throw new PasswordNotAcceptedException(configuration.getUsername());
                }
            }

        });

        // set the SO_TIMEOUT for the time after the connect phase
        if (sftpConfig.getServerAliveInterval() == 0) {
            if (configuration.getSoTimeout() > 0) {
                session.setTimeout(configuration.getSoTimeout());
            }
        } else {
            LOG.debug(
                    "The Server Alive Internal is already set, the socket timeout won't be considered to avoid overidding the provided Server alive interval value");
        }

        // set proxy if configured
        if (proxy != null) {
            session.setProxy(proxy);
        }

        if (isNotEmpty(sftpConfig.getBindAddress())) {
            session.setSocketFactory(new SocketFactory() {

                @Override
                public OutputStream getOutputStream(Socket socket) throws IOException {
                    return socket.getOutputStream();
                }

                @Override
                public InputStream getInputStream(Socket socket) throws IOException {
                    return socket.getInputStream();
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    return createSocketUtil(host, port, sftpConfig.getBindAddress(), session.getTimeout());
                }
            });
        }

        return session;
    }

    /**
     * Resolves certificate bytes from the configuration's certFile, certUri, or certBytes. Returns null if no
     * certificate is configured.
     */
    private byte[] resolveCertificateBytes(BaseSftpConfiguration config) throws JSchException {
        if (isNotEmpty(config.getCertFile())) {
            try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(
                    endpoint.getCamelContext(), "file:" + config.getCertFile())) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOHelper.copyAndCloseInput(is, bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new JSchException("Cannot read certificate file: " + config.getCertFile(), e);
            }
        }
        if (isNotEmpty(config.getCertUri())) {
            try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(
                    endpoint.getCamelContext(), config.getCertUri())) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOHelper.copyAndCloseInput(is, bos);
                return bos.toByteArray();
            } catch (IOException e) {
                throw new JSchException("Cannot read certificate resource: " + config.getCertUri(), e);
            }
        }
        if (config.getCertBytes() != null) {
            return config.getCertBytes();
        }
        return null;
    }

    /**
     * Detects the OpenSSH certificate key type from the given certificate data. OpenSSH certificate files use text
     * format: "key-type base64-data [comment]".
     *
     * @return the certificate key type (e.g., "ssh-rsa-cert-v01@openssh.com" or "ssh-rsa-cert") or null
     */
    private static String detectCertKeyType(byte[] certData) {
        if (certData == null) {
            return null;
        }
        String certLine = new String(certData, StandardCharsets.UTF_8).trim();
        int space = certLine.indexOf(' ');
        if (space > 0) {
            String keyType = certLine.substring(0, space);
            if (
            // ssh key type format for rfc until draft 03
            // https://datatracker.ietf.org/doc/html/draft-miller-ssh-cert-03.html
            keyType.endsWith("-cert-v01@openssh.com") ||
            // ssh key type format for rfc from draft 04
            // https://datatracker.ietf.org/doc/html/draft-miller-ssh-cert-04.html
                    keyType.endsWith("-cert")) {

                return keyType;
            }
        }
        return null;
    }

    private static final class JSchLogger implements com.jcraft.jsch.Logger {

        private final LoggingLevel loggingLevel;

        private JSchLogger(LoggingLevel loggingLevel) {
            this.loggingLevel = loggingLevel;
        }

        @Override
        public boolean isEnabled(int level) {
            switch (level) {
                case FATAL:
                    // use ERROR as FATAL
                    return loggingLevel.isEnabled(LoggingLevel.ERROR) && LOG.isErrorEnabled();
                case ERROR:
                    return loggingLevel.isEnabled(LoggingLevel.ERROR) && LOG.isErrorEnabled();
                case WARN:
                    return loggingLevel.isEnabled(LoggingLevel.WARN) && LOG.isWarnEnabled();
                case INFO:
                    return loggingLevel.isEnabled(LoggingLevel.INFO) && LOG.isInfoEnabled();
                default:
                    return loggingLevel.isEnabled(LoggingLevel.DEBUG) && LOG.isDebugEnabled();
            }
        }

        @Override
        public void log(int level, String message) {
            switch (level) {
                case FATAL:
                    // use ERROR as FATAL
                    LOG.error("JSCH -> {}", message);
                    break;
                case ERROR:
                    LOG.error("JSCH -> {}", message);
                    break;
                case WARN:
                    LOG.warn("JSCH -> {}", message);
                    break;
                case INFO:
                    LOG.info("JSCH -> {}", message);
                    break;
                default:
                    LOG.debug("JSCH -> {}", message);
                    break;
            }
        }
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            return session != null && session.isConnected() && channel != null && channel.isConnected();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        } finally {
            resetDirectoryState();
            lock.unlock();
        }
    }

    /**
     * Disconnects when the producer or the consumer stops. Another thread can still run an operation that holds the
     * lock: an upload of the producer, or the poll of the consumer, which the stop of its scheduler interrupts, but
     * which then changes back to the directory it started from (stepwise), on a server that may not answer.
     * <p>
     * As long as the shutdown of Camel is not forced, this method waits for that operation, as {@link #disconnect()}
     * does: it can be a healthy but slow upload, when a producer cache evicts the producer, when an application stops a
     * {@code ProducerTemplate}, or during a graceful shutdown, which stops the producers of a route whose consumer it
     * stops right away (one that cannot be suspended) while its exchanges are still in flight.
     * <p>
     * Once the shutdown is forced, as its timeout elapsed, the operation is one that the shutdown gave up waiting for,
     * such as an upload that hangs as long as the server does not answer. This method then disconnects the session
     * without the lock: JSch's {@link Session#disconnect()} can be called from another thread (its own session thread
     * does), and closes the channel and its pipe, so that the operation fails and releases the lock. A connect in
     * progress is waited for: the connect timeout and the reconnect attempts bound it, and its next attempt would open
     * a new session.
     */
    @Override
    public void disconnectOnStop() throws GenericFileOperationFailedException {
        boolean locked = false;
        boolean interrupted = false;
        // a stop that began before the shutdown was forced (as above) is still waiting at that time, so check again
        while (!locked) {
            try {
                locked = lock.tryLock(DISCONNECT_ON_STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                if (!locked && !connecting && isForceShutdown()) {
                    break;
                }
            } catch (InterruptedException e) {
                // keep waiting, as lock() would: the shutdown task, which a shutdown cancels when it times out, is
                // interrupted before the shutdown is forced; the interrupt is restored at the end
                interrupted = true;
            }
        }
        try {
            if (locked) {
                try {
                    disconnect();
                } finally {
                    lock.unlock();
                }
            } else {
                disconnectWithoutLock();
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isForceShutdown() {
        return endpoint.getCamelContext().getShutdownStrategy().isForceShutdown();
    }

    private void disconnectWithoutLock() {
        Session current = session;
        if (current == null || !current.isConnected()) {
            return;
        }
        String server = endpoint.getConfiguration().remoteServerInformation();
        LOG.warn("The shutdown is forced: disconnecting from {} while another thread still runs an operation, such as an"
                 + " upload or a download that hangs. The operation fails.",
                server);
        // JSch sends the close of the channel under the lock of the session that a write to the socket holds: when the
        // operation is blocked writing to a server that no longer reads (a half-open connection), the disconnect waits
        // until that write fails, so it runs in its own thread and the stop waits for it a little
        Thread disconnector = endpoint.getCamelContext().getExecutorServiceManager()
                .newThread("SftpDisconnectOnStop", current::disconnect);
        disconnector.start();
        try {
            disconnector.join(DISCONNECT_ON_STOP_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (disconnector.isAlive()) {
            LOG.warn("Disconnecting from {} waits for a write to the connection that does not progress."
                     + " The disconnect completes in the background when the write fails.",
                    server);
        }
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (session != null) {
                session.disconnect();
            }
            if (channel != null) {
                channel.disconnect();
            }
        } finally {
            // ensure these
            session = null;
            channel = null;
            resetDirectoryState();
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
            // normalize the name as the store does, so the file that is deleted is the one that is checked and stored
            name = endpoint.getConfiguration().normalizePath(name);
            LOG.debug("Deleting file: {}", name);
            reconnectIfNecessary(null);
            leaveStepwiseDirectory();
            channel.rm(quotePathForJsch(name));
            return true;
        } catch (SftpException e) {
            LOG.debug("Cannot delete file {}: {}", name, e.getMessage(), e);
            throw operationFailed("Cannot delete file: " + name, e);
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
            leaveStepwiseDirectory();
            // make use of the '/' separator because JSch expects this
            // as the file separator even on Windows
            to = FileUtil.compactPath(to, '/');
            channel.rename(quotePathForJsch(from), quotePathForJsch(to));
            return true;
        } catch (SftpException e) {
            LOG.debug("Cannot rename file from: {} to: {}", from, to, e);
            throw operationFailed("Cannot rename file from: " + from + " to: " + to, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // must normalize directory first
            directory = endpoint.getConfiguration().normalizePath(directory);

            LOG.trace("buildDirectory({},{})", directory, absolute);
            String key = builtDirectoryKey(directory);
            if (builtDirectories.contains(key)) {
                LOG.trace("Directory {} was checked or created before on this connection", directory);
                return true;
            }
            leaveStepwiseDirectory();
            // ignore absolute as all dirs are relative with FTP
            boolean success = false;

            // whether to check for existing dir using CD or LS
            boolean cdCheck = !this.endpoint.getConfiguration().isExistDirCheckUsingLs();
            String originalDirectory = cdCheck ? getCurrentDirectory() : null;

            try {
                // maybe the full directory already exists
                try {
                    if (cdCheck) {
                        channel.cd(quotePathForJsch(directory));
                    } else {
                        // just do a fast listing
                        channel.ls(quotePathForJsch(directory), entry -> ChannelSftp.LsEntrySelector.BREAK);
                    }
                    success = true;
                } catch (SftpException e) {
                    // ignore, we could not change directory so try to create it
                    // instead
                }

                if (!success) {
                    LOG.debug("Trying to build remote directory: {}", directory);
                    try {
                        channel.mkdir(directory);
                        success = true;
                    } catch (SftpException e) {
                        // we are here if the server side doesn't create
                        // intermediate folders
                        // so create the folder one by one
                        success = buildDirectoryChunks(directory);
                    }

                    // only after successfully creating directory, we may set chmod on the file
                    if (success) {
                        chmodOfDirectory(directory);
                    }
                }

                // change back to original directory
                if (originalDirectory != null) {
                    changeCurrentDirectory(originalDirectory);
                }
            } catch (SftpException e) {
                throw operationFailed("Cannot build directory: " + directory, e);
            }

            return success;
        } finally {
            lock.unlock();
        }
    }

    private boolean buildDirectoryChunks(String dirName) throws SftpException {
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

            // must normalize the directory name
            String directory = endpoint.getConfiguration().normalizePath(sb.toString());

            // do not try to build root folder (/ or \)
            if (!(directory.equals("/") || directory.equals("\\"))) {
                try {
                    LOG.trace("Trying to build remote directory by chunk: {}", directory);

                    channel.mkdir(directory);
                    success = true;
                } catch (SftpException e) {
                    // ignore keep trying to create the rest of the path
                }

                // only after successfully creating directory, we may set chmod on the file
                if (success) {
                    chmodOfDirectory(directory);
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
            leaveStepwiseDirectory();
            String answer = pwd();
            LOG.trace("Current dir: {}", answer);
            return answer;
        } finally {
            lock.unlock();
        }
    }

    private String pwd() {
        try {
            // JSch keeps the current directory on the client: this sends nothing
            return channel.pwd();
        } catch (SftpException e) {
            throw operationFailed("Cannot get current directory", e);
        }
    }

    /**
     * Changes into the directory of a stepwise upload or download, unless a previous one left the connection there, and
     * remembers the directory to change back to. When the change fails half way, the next operation changes back.
     */
    private void changeToStepwiseDirectory(String path) {
        if (restoreDirectory != null && path.equals(stepwiseDirectory)) {
            LOG.trace("Already in directory: {}", path);
            return;
        }
        changeBackToRestoreDirectory();
        restoreDirectory = pwd();
        changeCurrentDirectory(path);
        stepwiseDirectory = path;
    }

    /**
     * Changes back to the directory where a stepwise upload or download started, before an operation that uses names
     * relative to it. Does nothing while a stepwise operation runs, as the operations it calls use names relative to
     * the directory of its file.
     */
    private void leaveStepwiseDirectory() {
        if (!inStepwiseOperation) {
            changeBackToRestoreDirectory();
        }
    }

    private void changeBackToRestoreDirectory() {
        String directory = restoreDirectory;
        if (directory == null) {
            return;
        }
        restoreDirectory = null;
        stepwiseDirectory = null;
        try {
            // one change to the absolute path that JSch gave, not up one directory at a time, as the directory that the
            // connection is in may have been removed
            LOG.trace("Changing back to directory: {}", directory);
            doChangeDirectory(directory);
        } catch (RuntimeException e) {
            // the next operation tries again
            restoreDirectory = directory;
            throw e;
        }
    }

    private void resetDirectoryState() {
        restoreDirectory = null;
        stepwiseDirectory = null;
        builtDirectories.clear();
        lastCompletedNanos = 0;
    }

    /**
     * The key of a directory that autoCreate checked or created: a relative directory is relative to the directory that
     * a relative name resolves against, once the connection changed back from a stepwise operation.
     */
    private String builtDirectoryKey(String directory) {
        if (FileUtil.hasLeadingSeparator(directory)) {
            return directory;
        }
        String base = !inStepwiseOperation && restoreDirectory != null ? restoreDirectory : pwd();
        return base + "/" + directory;
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("changeCurrentDirectory({})", path);
            if (ObjectHelper.isEmpty(path)) {
                return;
            }
            leaveStepwiseDirectory();

            // must compact path so SFTP server can traverse correctly, make use of
            // the '/'
            // separator because JSch expects this as the file separator even on
            // Windows
            String before = path;
            char separatorChar = '/';
            path = FileUtil.compactPath(path, separatorChar);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Compacted path: {} -> {} using separator: {}", before, path, separatorChar);
            }
            if (path.isEmpty()) {
                // the path is the current directory (such as sub/..), so stay in it
                return;
            }

            // not stepwise should change directory in one operation
            if (!endpoint.getConfiguration().isStepwise()) {
                doChangeDirectory(path);
                return;
            }
            String current = getCurrentDirectory();
            if (FtpUtils.isDirectoryOrParentOf(path, current)) {
                // extract the path segment relative to the target path and make
                // sure it keeps the preceding '/' for the regex op
                String p = current.substring(path.length() - (path.endsWith("/") ? 1 : 0));
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
        try {
            channel.cd(quotePathForJsch(path));
        } catch (SftpException e) {
            String status = sftpStatus(e);
            String message = "Cannot change directory to: " + path + ": " + status;
            if (e.getCause() == null && !FileUtil.hasLeadingSeparator(path)) {
                // the server answered: give the directory that a relative path starts from (the login directory, for
                // the directory of the endpoint)
                try {
                    message += ". The path is relative to the current directory: " + channel.pwd();
                } catch (SftpException pwdFailure) {
                    LOG.trace("Cannot get the current directory: {}", pwdFailure.getMessage(), pwdFailure);
                }
            }
            throw new GenericFileOperationFailedException(message, e.id, status, e);
        }
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("changeToParentDirectory()");
            String current = getCurrentDirectory();

            String parent = FileUtil.compactPath(current + "/..");
            // must start with absolute
            if (!parent.startsWith("/")) {
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
            return listFiles(".");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SftpRemoteFile[] listFiles(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("Listing remote files from path {}", path);
            if (ObjectHelper.isEmpty(path)) {
                // list current directory if file path is not given
                path = ".";
            }
            leaveStepwiseDirectory();

            Vector<?> files = channel.ls(quotePathForJsch(path));

            return files.stream()
                    .map(f -> new SftpRemoteFileJCraft((ChannelSftp.LsEntry) f))
                    .toArray(SftpRemoteFileJCraft[]::new);
        } catch (SftpException e) {
            throw operationFailed("Cannot list directory: " + path, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void listFiles(String path, Predicate<SftpRemoteFile> visitor) throws GenericFileOperationFailedException {
        lock.lock();
        String dir = ObjectHelper.isEmpty(path) ? "." : path;
        try {
            LOG.trace("Listing remote files from path {} one at a time", dir);
            leaveStepwiseDirectory();
            RuntimeException[] failure = { null };
            channel.ls(quotePathForJsch(dir), entry -> {
                try {
                    return visitor.test(new SftpRemoteFileJCraft(entry))
                            ? ChannelSftp.LsEntrySelector.CONTINUE : ChannelSftp.LsEntrySelector.BREAK;
                } catch (RuntimeException e) {
                    // thrown once the listing is over, as the channel is in the middle of reading a reply here
                    failure[0] = e;
                    return ChannelSftp.LsEntrySelector.BREAK;
                }
            });
            if (failure[0] != null) {
                throw failure[0];
            }
        } catch (SftpException e) {
            throw operationFailed("Cannot list directory: " + dir, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * JSch calls the visitor in the middle of reading a reply to the listing, in the buffer that every request uses, so
     * no other request can be sent until the listing is over.
     */
    @Override
    public boolean isReentrantListing() {
        return false;
    }

    @Override
    public String getHomeDirectory() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            return channel.getHome();
        } catch (SftpException e) {
            throw operationFailed("Cannot get the home directory", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isSymbolicLink(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            leaveStepwiseDirectory();
            return channel.lstat(quotePathForJsch(path)).isLink();
        } catch (SftpException e) {
            throw operationFailed("Cannot get the attributes of: " + path, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String readSymbolicLink(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            leaveStepwiseDirectory();
            return channel.readlink(quotePathForJsch(path));
        } catch (SftpException e) {
            throw operationFailed("Cannot read the symbolic link: " + path, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isTransportError(GenericFileOperationFailedException e) {
        if (!isConnected()) {
            return true;
        }
        // JSch reports a status sent by the server as an SftpException without a cause, and wraps an I/O or session
        // failure as an SftpException (SSH_FX_FAILURE) with that failure as its cause
        return e.getCause() instanceof SftpException sftp && sftp.getCause() != null;
    }

    /**
     * Escapes the characters that JSch interprets in a path argument, so that the server acts on the literal path.
     * <p/>
     * The {@link ChannelSftp} methods that take a remote path, such as {@code get}, {@code put}, {@code rm},
     * {@code rename}, {@code chmod}, {@code cd}, {@code ls}, {@code lstat} and {@code readlink}, resolve it with JSch's
     * own pattern matching, which treats {@code *} and {@code ?} as wildcards and {@code \} as an escape character that
     * is removed. Every such call in this class escapes its path, so that the server works on the file or directory
     * with exactly the given name, also when it contains {@code *} or {@code ?}, as the other file based components do.
     * A {@code \} is a path separator with the default {@code separator} option, and the name is normalized to
     * {@code /} before it gets here; a {@code \} that is left is escaped, so that JSch does not remove it.
     * {@link ChannelSftp#mkdir(String)} sends its path as it is, so it is not escaped. This mirrors JSch's own
     * (package-private) {@code Util.quote}.
     *
     * @param  path the literal path
     * @return      the path with {@code \}, {@code *} and {@code ?} escaped for JSch
     */
    private static String quotePathForJsch(String path) {
        if (path == null) {
            return null;
        }
        StringBuilder quoted = null;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || c == '*' || c == '?') {
                if (quoted == null) {
                    quoted = new StringBuilder(path.length() + 8);
                    quoted.append(path, 0, i);
                }
                quoted.append('\\');
            }
            if (quoted != null) {
                quoted.append(c);
            }
        }
        return quoted != null ? quoted.toString() : path;
    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size)
            throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("retrieveFile({})", name);
            inStepwiseOperation = true;
            boolean answer;
            if (ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
                // local work directory is configured so we should store file
                // content as files in this local directory
                answer = retrieveFileToFileInLocalWorkDirectory(name, exchange);
            } else {
                // store file content directory as stream on the body
                answer = retrieveFileToStreamInBody(name, exchange);
            }
            if (answer) {
                lastCompletedNanos = System.nanoTime();
            }
            return answer;
        } finally {
            inStepwiseOperation = false;
            lock.unlock();
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

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        try {
            GenericFile<ChannelSftp.LsEntry> target
                    = (GenericFile<ChannelSftp.LsEntry>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

            String remoteName = changeToDirectoryOf(name);

            // use input stream which works with Apache SSHD used for testing
            InputStream is = channel.get(quotePathForJsch(remoteName));

            if (endpoint.getConfiguration().isStreamDownload()) {
                target.setBody(is);
                exchange.getIn().setHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, is);
            } else {
                // read the entire file into memory in the byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOHelper.copyAndCloseInput(is, bos);
                // close the stream after done
                IOHelper.close(bos);

                target.setBody(bos.toByteArray());
            }

            createResultHeadersFromExchange(null, exchange);
            return true;
        } catch (SftpException e) {
            createResultHeadersFromExchange(e, exchange);
            throw operationFailed("Cannot retrieve file: " + name, e);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange)
            throws GenericFileOperationFailedException {
        File temp;
        File local = new File(endpoint.getLocalWorkDirectory());
        OutputStream os;
        GenericFile<ChannelSftp.LsEntry> file
                = (GenericFile<ChannelSftp.LsEntry>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        ObjectHelper.notNull(file, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
        try {
            // use relative filename in local work directory
            String relativeName = file.getRelativeFilePath();

            File localWorkDir = local;
            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // ensure the local work file stays within the local work directory (CAMEL-23765)
            if (endpoint.isJailStartingDirectory()) {
                GenericFileHelper.jailToLocalWorkDirectory(temp, localWorkDir);
                GenericFileHelper.jailToLocalWorkDirectory(local, localWorkDir);
            }

            // create directory to local work file
            local.mkdirs();

            // delete any existing files
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

            // create new temp local work file
            if (!temp.createNewFile()) {
                throw new GenericFileOperationFailedException("Cannot create new local work file: " + temp);
            }

            // store content as a file in the local work directory in the temp
            // handle
            os = new FileOutputStream(temp);

            // set header with the path to the local work file
            exchange.getIn().setHeader(FtpConstants.FILE_LOCAL_WORK_PATH, local.getPath());
        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local, e);
        }
        try {
            // store the java.io.File handle as the body
            file.setBody(local);

            String remoteName = changeToDirectoryOf(name);

            channel.get(quotePathForJsch(remoteName), os);
        } catch (SftpException e) {
            createResultHeadersFromExchange(e, exchange);
            LOG.trace("Error occurred during retrieving file: {} to local directory. Deleting local work file: {}", name, temp);
            // failed to retrieve the file so we need to close streams and
            // delete in progress file
            // must close stream before deleting file
            IOHelper.close(os, "retrieve: " + name, LOG);
            boolean deleted = FileUtil.deleteFile(temp);
            if (!deleted) {
                LOG.warn("Error occurred during retrieving file: {} to local directory. Cannot delete local work file: {}",
                        name, temp);
            }
            throw operationFailed("Cannot retrieve file: " + name, e);
        } finally {
            IOHelper.close(os, "retrieve: " + name, LOG);
        }

        createResultHeadersFromExchange(null, exchange);
        LOG.debug("Retrieve file to local work file result: true");

        // operation went okay so rename temp to local after we have retrieved
        // the data
        LOG.trace("Renaming local in progress file from: {} to: {}", temp, local);
        try {
            if (!FileUtil.renameFile(temp, local, false)) {
                throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local);
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local, e);
        }

        return true;
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size)
            throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // must normalize name first
            name = endpoint.getConfiguration().normalizePath(name);

            LOG.trace("storeFile({})", name);

            String directory = FileUtil.onlyPath(name);
            boolean answer;
            try {
                answer = storeFileInItsDirectory(name, exchange);
            } catch (GenericFileOperationFailedException e) {
                boolean missing;
                try {
                    missing = e.getCode() == ChannelSftp.SSH_FX_NO_SUCH_FILE && isBuiltDirectoryMissing(directory, exchange);
                } catch (RuntimeException checkFailure) {
                    e.addSuppressed(checkFailure);
                    throw e;
                }
                if (!missing) {
                    throw e;
                }
                answer = storeFileInBuiltDirectoryAgain(name, directory, exchange, e);
            }
            if (answer) {
                lastCompletedNanos = System.nanoTime();
                rememberBuiltDirectory(directory);
            }
            return answer;
        } finally {
            lock.unlock();
        }
    }

    private boolean storeFileInItsDirectory(String name, Exchange exchange) {
        inStepwiseOperation = true;
        try {
            String targetName = changeToDirectoryOf(name);
            return doStoreFile(name, targetName, exchange);
        } finally {
            inStepwiseOperation = false;
        }
    }

    private boolean storeFileInBuiltDirectoryAgain(
            String name, String directory, Exchange exchange, GenericFileOperationFailedException failure) {
        LOG.info("Cannot store file: {} as directory: {}, where a file was stored before on this connection, was removed"
                 + " since ({}). Creating the directory again and storing the file once more.",
                name, directory, failure.getMessage());
        try {
            if (!buildDirectory(directory, false)) {
                throw new GenericFileOperationFailedException(
                        "Cannot store file: " + name + " as directory: " + directory
                                                              + " was removed and cannot be created again");
            }
            if (exchange.getIn().getBody() instanceof StreamCache cache) {
                // the failed attempt may have read it
                cache.reset();
            }
            return storeFileInItsDirectory(name, exchange);
        } catch (GenericFileOperationFailedException retryFailure) {
            retryFailure.addSuppressed(failure);
            throw retryFailure;
        }
    }

    /**
     * Records that a file was stored in a directory, so that autoCreate does not check it again on this connection.
     */
    private void rememberBuiltDirectory(String directory) {
        if (directory != null) {
            builtDirectories.add(builtDirectoryKey(endpoint.getConfiguration().normalizePath(directory)));
        }
    }

    /**
     * Whether an upload failed as the directory where a file was stored before on this connection was removed since,
     * and can be tried again once the directory is created again: autoCreate is enabled, the body can be read again,
     * and the upload does not append (it could append twice). Changes back to the directory where the operations
     * started, forgets the directory so that the next upload checks it, and asks the server whether the directory
     * exists, as the same status can come from a step after the upload, such as the chmod of the file.
     */
    private boolean isBuiltDirectoryMissing(String directory, Exchange exchange) {
        if (directory == null || !endpoint.isAutoCreate()) {
            return false;
        }
        changeBackToRestoreDirectory();
        directory = endpoint.getConfiguration().normalizePath(directory);
        boolean known = builtDirectories.remove(builtDirectoryKey(directory));
        if (!known || !FtpUtils.isBodyReadableAgain(exchange.getIn().getBody())
                || endpoint.getFileExist() == GenericFileExist.Append) {
            return false;
        }
        try {
            channel.stat(quotePathForJsch(directory));
            return false;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return true;
            }
            throw operationFailed("Cannot get the attributes of: " + directory, e);
        }
    }

    /**
     * Changes into the directory of a file for a stepwise upload or download, as some servers cannot use a path in the
     * name of the file, unless the connection is already there, and returns the name relative to it. Without stepwise,
     * or for a file without a path, changes back to the directory where the operations started and returns the name.
     */
    private String changeToDirectoryOf(String name) {
        String path = FileUtil.onlyPath(name);
        if (path == null || !endpoint.getConfiguration().isStepwise()) {
            changeBackToRestoreDirectory();
            return name;
        }
        changeToStepwiseDirectory(path);
        return FileUtil.stripPath(name);
    }

    private boolean doStoreFile(String name, String targetName, Exchange exchange) throws GenericFileOperationFailedException {
        LOG.trace("doStoreFile({})", targetName);

        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore || endpoint.getFileExist() == GenericFileExist.Fail
                || endpoint.getFileExist() == GenericFileExist.Move) {
            boolean existFile = existsFile(targetName);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                LOG.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Move) {
                // move any existing file first
                this.endpoint.getMoveExistingFileStrategy().moveExistingFile(endpoint, this, targetName);
            }
        }

        InputStream is = null;
        if (exchange.getIn().getBody() == null) {
            // Do an explicit test for a null body and decide what to do
            if (endpoint.isAllowNullBody()) {
                LOG.trace("Writing empty file.");
                is = new ByteArrayInputStream(new byte[] {});
            } else {
                throw new GenericFileOperationFailedException("Cannot write null body to file: " + name);
            }
        }

        try {
            if (is == null) {
                String charset = endpoint.getCharset();
                if (charset != null) {
                    // charset configured so we must convert to the desired
                    // charset so we can write with encoding
                    is = GenericFileHelper.toInputStream(exchange, charset);
                    LOG.trace("Using InputStream {} with charset {}.", is, charset);
                } else {
                    is = GenericFileHelper.mandatoryBodyStream(exchange, exchange.getIn(), FileUtil.stripPath(name));
                }
            }

            final StopWatch watch = new StopWatch();
            LOG.debug("About to store file: {} using stream: {}", targetName, is);
            if (endpoint.getFileExist() == GenericFileExist.Append) {
                LOG.trace("Client appendFile: {}", targetName);
                channel.put(is, quotePathForJsch(targetName), ChannelSftp.APPEND);
            } else {
                LOG.trace("Client storeFile: {}", targetName);
                // override is default
                channel.put(is, quotePathForJsch(targetName));
            }
            if (LOG.isDebugEnabled()) {
                long time = watch.taken();
                LOG.debug("Took {} ({} millis) to store file: {} and FTP client returned: true",
                        TimeUtils.printDuration(time, true), time, targetName);
            }

            // after storing file, we may set chmod on the file
            String mode = endpoint.getConfiguration().getChmod();
            if (ObjectHelper.isNotEmpty(mode)) {
                // parse to int using 8bit mode
                int permissions = Integer.parseInt(mode, 8);
                LOG.trace("Setting chmod: {} on file: {}", mode, targetName);
                channel.chmod(permissions, quotePathForJsch(targetName));
            }

            createResultHeadersFromExchange(null, exchange);
            return true;

        } catch (SftpException e) {
            createResultHeadersFromExchange(e, exchange);
            throw operationFailed("Cannot store file: " + name, e);
        } catch (InvalidPayloadException e) {
            // say why: the body cannot be written as it is (CAMEL-27151)
            throw new GenericFileOperationFailedException("Cannot store file: " + name + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, LOG);
        }
    }

    @Override
    public boolean storeFileDirectly(String name, String payload) throws GenericFileOperationFailedException {
        lock.lock();
        ByteArrayInputStream bis = new ByteArrayInputStream(payload.getBytes());
        try {
            leaveStepwiseDirectory();
            channel.put(bis, quotePathForJsch(name));
            return true;
        } catch (SftpException e) {
            throw operationFailed("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(bis);
            lock.unlock();
        }
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("existsFile({})", name);
            leaveStepwiseDirectory();
            if (endpoint.isFastExistsCheck()) {
                return fastExistsFile(name);
            }
            return getFileAttributes(name) != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the attributes of a file with a single request, whatever the number of files in its directory.
     * <p/>
     * These are the attributes of the path itself, as in a directory listing: a symbolic link is not followed, so for a
     * link they are the size and modification time of the link, and a dangling link exists. When the server answers
     * that request with another status than no such file, such as when it does not permit or support it on the path,
     * the attributes are taken from a listing of the directory of the file instead.
     *
     * @param  name                                the name of the file, with its directory if any
     * @return                                     the attributes, or {@code null} if there is no such file
     * @throws GenericFileOperationFailedException if the attributes cannot be read
     */
    public FileAttributes getFileAttributes(String name) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // normalize the name as the store does, so the file that is checked is the one that is stored
            name = endpoint.getConfiguration().normalizePath(name);
            // the read lock of the consumer checks a file by its name relative to the starting directory, after the
            // download of the previous file left the connection in the directory of that file
            leaveStepwiseDirectory();
            SftpATTRS attrs;
            try {
                attrs = lstat(name);
            } catch (SftpException e) {
                if (!isLstatUnanswered(e)) {
                    throw operationFailed("Cannot get the attributes of: " + name, e);
                }
                LOG.debug("Cannot get the attributes of: {} due to: {}. Listing its directory instead.", name,
                        e.getMessage());
                attrs = listFileAttributes(name);
            }
            return attrs != null ? new FileAttributes(attrs.getSize(), attrs.getMTime() * 1000L) : null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The attributes of a file read by {@link #getFileAttributes(String)}.
     *
     * @param fileLength   the file size
     * @param lastModified the file modification timestamp (in millis)
     */
    public record FileAttributes(long fileLength, long lastModified) {
    }

    /**
     * Gets the attributes of a file with a single LSTAT request.
     *
     * @param  name          the name of the file, with its directory if any
     * @return               the attributes, or {@code null} if there is no such file
     * @throws SftpException if the server answers with another status, or the request fails
     */
    private SftpATTRS lstat(String name) throws SftpException {
        // the name starts after the last separator, either '/' or '\\', as in the store and in the listing of the
        // directory, and is joined to the directory with '/'. The directory itself is sent as it is given
        String directory = FileUtil.onlyPath(name);
        String onlyName = FileUtil.stripPath(name);
        String path;
        if (directory == null) {
            path = onlyName;
        } else if (directory.endsWith("/")) {
            path = directory + onlyName;
        } else {
            path = directory + "/" + onlyName;
        }
        try {
            return channel.lstat(quotePathForJsch(path));
        } catch (SftpException e) {
            if (ChannelSftp.SSH_FX_NO_SUCH_FILE == e.id) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Whether the server answered an LSTAT request with a status that does not tell whether the path exists, so that
     * the directory is listed instead, as before: any status other than no such file, which {@link #lstat(String)}
     * already answers. A server may not permit or support the request on a path that it lists, answer with the generic
     * failure for a path that does not exist, or send a status of a later version of the protocol.
     */
    private static boolean isLstatUnanswered(SftpException e) {
        // JSch reports a status sent by the server as an SftpException without a cause, and wraps an I/O or session
        // failure as an SftpException (SSH_FX_FAILURE) with that failure as its cause
        return e.getCause() == null;
    }

    /**
     * Gets the attributes of a file from a listing of its directory, comparing the names of the listed files.
     */
    private SftpATTRS listFileAttributes(String name) throws GenericFileOperationFailedException {
        String directory = FileUtil.onlyPath(name);
        if (directory == null) {
            // assume current dir if no path could be extracted
            directory = ".";
        }
        String onlyName = FileUtil.stripPath(name);

        try {
            @SuppressWarnings("rawtypes")
            List files = channel.ls(quotePathForJsch(directory));
            // can return either null or an empty list depending on FTP servers
            if (files == null) {
                return null;
            }
            for (Object file : files) {
                ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) file;
                String existing = entry.getFilename();
                LOG.trace("Existing file: {}, target file: {}", existing, name);
                existing = FileUtil.stripPath(existing);
                if (existing != null && existing.equals(onlyName)) {
                    return entry.getAttrs();
                }
            }
            return null;
        } catch (SftpException e) {
            // or an exception can be thrown with id 2 which means file does not
            // exists
            if (ChannelSftp.SSH_FX_NO_SUCH_FILE == e.id) {
                return null;
            }
            // otherwise its a more serious error so rethrow
            throw operationFailed("Cannot list directory: " + directory, e);
        }
    }

    protected boolean fastExistsFile(String name) throws GenericFileOperationFailedException {
        LOG.trace("fastExistsFile({})", name);
        // the same as without fastExistsCheck, also when the server does not answer the LSTAT: JSch ls(name) starts
        // with a STAT of the path, which such a server would most likely not answer either, and then lists the whole
        // directory of an existing file anyway
        return getFileAttributes(name) != null;
    }

    @Override
    public boolean isRecentlyUsed() {
        long last = lastCompletedNanos;
        if (last == 0 || System.nanoTime() - last >= RECENTLY_USED_NANOS) {
            return false;
        }
        // Even if recently used, verify the session is still connected: if the server restarted
        // within the last second, JSch may have already seen the EOF and isConnected() returns false.
        return isConnected();
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (isConnected()) {
                try {
                    // Use a round-trip SFTP operation (realpath) rather than the JSch keep-alive
                    // write (sendKeepAliveMsg), which is fire-and-forget and returns true even when
                    // the server has closed the connection but the kernel has not yet delivered the
                    // TCP RST to the JSch reader thread. A realpath request waits for the server's
                    // response, so it reliably detects a stale session after a server restart.
                    channel.realpath(".");
                    lastCompletedNanos = System.nanoTime();
                    return true;
                } catch (Exception e) {
                    LOG.debug("SFTP session noop failed. Ignoring this exception.", e);
                    return false;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Runs the check, such as the noop of the producer health check, unless another thread holds the lock, such as an
     * upload, which holds it for the whole transfer. The check runs with the lock held, so an upload that starts
     * meanwhile waits for it.
     */
    @Override
    public boolean runIfIdle(ThrowingRunnable<Exception> check) throws Exception {
        if (!lock.tryLock()) {
            return false;
        }
        try {
            check.run();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        // is not implemented
        return true;
    }

    /*
     * adapted from com.jcraft.jsch.Util.createSocket(String, int, int) added
     * possibility to specify the address of the local network interface,
     * against the connection should bind
     */
    static Socket createSocketUtil(final String host, final int port, final String bindAddress, final int timeout) {
        Socket socket;
        if (timeout == 0) {
            try {
                socket = new Socket(InetAddress.getByName(host), port, InetAddress.getByName(bindAddress), 0);
                return socket;
            } catch (Exception e) {
                String message = e.toString();
                throw new RuntimeCamelException(message, e);
            }
        }
        final Socket[] sockp = new Socket[1];
        final Exception[] ee = new Exception[1];
        String message = "";
        Thread tmp = new Thread(() -> {
            sockp[0] = null;
            try {
                sockp[0] = new Socket(InetAddress.getByName(host), port, InetAddress.getByName(bindAddress), 0);
            } catch (Exception e) {
                ee[0] = e;
                if (sockp[0] != null && sockp[0].isConnected()) {
                    IOHelper.close(sockp[0]);
                }
                sockp[0] = null;
            }
        });
        tmp.setName("Opening Socket " + host);
        tmp.start();
        try {
            tmp.join(timeout);
            message = "timeout: ";
        } catch (java.lang.InterruptedException eee) {
            Thread.currentThread().interrupt();
        }
        if (sockp[0] != null && sockp[0].isConnected()) {
            socket = sockp[0];
        } else {
            message += "socket is not established";
            if (ee[0] != null) {
                message = ee[0].toString();
            }
            tmp.interrupt();
            throw new RuntimeCamelException(message, ee[0]);
        }
        return socket;
    }

    /**
     * The failure of an SFTP request, with the status of the server after the given message, and as the code and reason
     * of the exception, as {@link FtpOperations} does with the reply code and reply text of an FTP server.
     */
    private static GenericFileOperationFailedException operationFailed(String message, SftpException e) {
        String status = sftpStatus(e);
        return new GenericFileOperationFailedException(message + ": " + status, e.id, status, e);
    }

    /**
     * The status of a failed SFTP request, such as {@code SSH_FX_NO_SUCH_FILE: No such file}.
     */
    static String sftpStatus(SftpException e) {
        String text = e.getMessage();
        if (ObjectHelper.isEmpty(text) && e.getCause() != null) {
            // JSch reports an I/O or session failure as SSH_FX_FAILURE, with that failure as its cause
            text = e.getCause().toString();
        }
        String name = sftpStatusName(e.id);
        if (ObjectHelper.isEmpty(text)) {
            return name;
        }
        // JSch starts the text of a status sent by the server with the name of the status, but not the text of a
        // failure it detects itself, such as "Can't change directory: <path>" for a path that is not a directory
        return text.startsWith(name) ? text : name + ": " + text;
    }

    private static String sftpStatusName(int id) {
        return switch (id) {
            case ChannelSftp.SSH_FX_OK -> "SSH_FX_OK";
            case ChannelSftp.SSH_FX_EOF -> "SSH_FX_EOF";
            case ChannelSftp.SSH_FX_NO_SUCH_FILE -> "SSH_FX_NO_SUCH_FILE";
            case ChannelSftp.SSH_FX_PERMISSION_DENIED -> "SSH_FX_PERMISSION_DENIED";
            case ChannelSftp.SSH_FX_FAILURE -> "SSH_FX_FAILURE";
            case ChannelSftp.SSH_FX_BAD_MESSAGE -> "SSH_FX_BAD_MESSAGE";
            case ChannelSftp.SSH_FX_NO_CONNECTION -> "SSH_FX_NO_CONNECTION";
            case ChannelSftp.SSH_FX_CONNECTION_LOST -> "SSH_FX_CONNECTION_LOST";
            case ChannelSftp.SSH_FX_OP_UNSUPPORTED -> "SSH_FX_OP_UNSUPPORTED";
            // as JSch names a status of a later version of the protocol
            default -> "Unknown code: (" + id + ")";
        };
    }

    /**
     * Helper method which gets result code and message from sftpException and puts it into header. In case that
     * exception is null, it sets successfully response.
     */
    private void createResultHeadersFromExchange(SftpException sftpException, Exchange exchange) {
        // if exception is null, it means that result was ok
        if (sftpException == null) {
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, OK_STATUS);
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, OK_MESSAGE);
        } else {
            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, sftpException.id);
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, sftpException.getMessage());
        }
    }

    /**
     * Helper method which sets the path permissions
     */
    private void chmodOfDirectory(String directory) {
        String chmodDirectory = endpoint.getConfiguration().getChmodDirectory();
        if (ObjectHelper.isNotEmpty(chmodDirectory)) {
            LOG.trace("Setting permission: {} on directory: {}", chmodDirectory, directory);
            // parse to int using 8bit mode
            int permissions = Integer.parseInt(chmodDirectory, 8);
            try {
                channel.chmod(permissions, quotePathForJsch(directory));
            } catch (SftpException e) {
                throw operationFailed("Cannot set permission on directory: " + directory, e);
            }
        }
    }
}
