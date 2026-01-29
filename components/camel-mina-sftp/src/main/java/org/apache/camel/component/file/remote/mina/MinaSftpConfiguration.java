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

import java.net.URI;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.support.ObjectHelper;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MINA SFTP configuration using Apache MINA SSHD library.
 */
@UriParams
public class MinaSftpConfiguration extends RemoteFileConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MinaSftpConfiguration.class);

    public static final int DEFAULT_SFTP_PORT = 22;

    // Known hosts options
    @UriParam(label = "security", secret = true)
    private String knownHostsFile;
    @UriParam(label = "security", defaultValue = "true")
    private boolean useUserKnownHostsFile = true;
    @UriParam(label = "security", defaultValue = "false")
    private boolean autoCreateKnownHostsFile;
    @UriParam(label = "security", secret = true)
    @Metadata(supportFileReference = true)
    private String knownHostsUri;
    @UriParam(label = "security", secret = true)
    private byte[] knownHosts;
    @UriParam(defaultValue = "no", enums = "no,yes", label = "security")
    private String strictHostKeyChecking = "no";
    @UriParam(label = "security",
              description = "Custom ServerKeyVerifier for host key verification. When provided, this verifier is used "
                            + "exclusively, ignoring strictHostKeyChecking, knownHostsFile, and other host key options.")
    private ServerKeyVerifier serverKeyVerifier;

    // Public key authentication options
    @UriParam(label = "security", secret = true,
              description = "Set the private key file path so that the SFTP endpoint can do public key authentication")
    private String privateKeyFile;
    @UriParam(label = "security", secret = true,
              description = "Set the private key as a classpath: or file: URI (e.g., classpath:keys/id_rsa)")
    @Metadata(supportFileReference = true)
    private String privateKeyUri;
    @UriParam(label = "security", secret = true,
              description = "Set the private key as byte array for public key authentication")
    private byte[] privateKey;
    @UriParam(label = "security", secret = true,
              description = "Set the passphrase for decrypting an encrypted private key")
    private String privateKeyPassphrase;
    @UriParam(label = "security", secret = true,
              description = "Set a java.security.KeyPair directly for public key authentication")
    private KeyPair keyPair;

    // OpenSSH certificate authentication options (MINA SSHD specific)
    @UriParam(label = "security", secret = true,
              description = "Set the OpenSSH certificate file path for certificate-based authentication")
    private String certFile;
    @UriParam(label = "security", secret = true,
              description = "Set the OpenSSH certificate as a classpath: or file: URI")
    @Metadata(supportFileReference = true)
    private String certUri;
    @UriParam(label = "security", secret = true,
              description = "Set the OpenSSH certificate as byte array")
    private byte[] certBytes;

    // Connection options
    @UriParam(label = "advanced")
    private int serverAliveInterval;
    @UriParam(defaultValue = "1", label = "advanced")
    private int serverAliveCountMax = 1;
    @UriParam(label = "advanced")
    private int compression;
    @UriParam(label = "security", javaType = "java.lang.String")
    private List<String> ciphers;
    @UriParam(label = "security")
    private String preferredAuthentications;
    @UriParam(label = "security", javaType = "java.lang.String")
    private List<String> keyExchangeProtocols;
    @UriParam(label = "security", javaType = "java.lang.String")
    private List<String> serverHostKeys;
    @UriParam(label = "security")
    private String publicKeyAcceptedAlgorithms;
    @UriParam(label = "advanced")
    private String bindAddress;
    @UriParam(label = "advanced")
    private Integer bulkRequests;
    @UriParam(label = "advanced")
    private Integer readBufferSize;
    @UriParam(label = "advanced")
    private Integer writeBufferSize;

    // File options
    @UriParam(label = "producer,advanced")
    private String chmod;
    @UriParam(label = "producer,advanced")
    private String chmodDirectory;
    @UriParam(label = "advanced")
    private String filenameEncoding;

    public MinaSftpConfiguration() {
        setProtocol("mina-sftp");
    }

    public MinaSftpConfiguration(URI uri) {
        super(uri);
    }

    @Override
    protected void setDefaultPort() {
        setPort(DEFAULT_SFTP_PORT);
    }

    public String getKnownHostsFile() {
        return knownHostsFile;
    }

    /**
     * Sets the known_hosts file, so that the SFTP endpoint can do host key verification.
     */
    public void setKnownHostsFile(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public String getKnownHostsUri() {
        return knownHostsUri;
    }

    /**
     * Sets the known_hosts file (loaded from classpath by default), so that the SFTP endpoint can do host key
     * verification.
     */
    public void setKnownHostsUri(String knownHostsUri) {
        this.knownHostsUri = knownHostsUri;
    }

    public boolean isUseUserKnownHostsFile() {
        return useUserKnownHostsFile;
    }

    /**
     * If knownHostFile has not been explicit configured then use the host file from
     * System.getProperty(user.home)/.ssh/known_hosts
     */
    public void setUseUserKnownHostsFile(boolean useUserKnownHostsFile) {
        this.useUserKnownHostsFile = useUserKnownHostsFile;
    }

    public boolean isAutoCreateKnownHostsFile() {
        return autoCreateKnownHostsFile;
    }

    /**
     * If knownHostFile does not exist, then attempt to auto-create the path and file (beware that the file will be
     * created by the current user of the running Java process, which may not have file permission).
     */
    public void setAutoCreateKnownHostsFile(boolean autoCreateKnownHostsFile) {
        this.autoCreateKnownHostsFile = autoCreateKnownHostsFile;
    }

    public byte[] getKnownHosts() {
        return knownHosts;
    }

    /**
     * Sets the known_hosts from the byte array, so that the SFTP endpoint can do host key verification.
     */
    public void setKnownHosts(byte[] knownHosts) {
        this.knownHosts = knownHosts;
    }

    public String getStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    /**
     * Sets whether to use strict host key checking.
     */
    public void setStrictHostKeyChecking(String strictHostKeyChecking) {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    public ServerKeyVerifier getServerKeyVerifier() {
        return serverKeyVerifier;
    }

    /**
     * Sets a custom ServerKeyVerifier for host key verification.
     * <p/>
     * When provided, this verifier is used exclusively for host key verification, ignoring strictHostKeyChecking,
     * knownHostsFile, and other host key options.
     */
    public void setServerKeyVerifier(ServerKeyVerifier serverKeyVerifier) {
        this.serverKeyVerifier = serverKeyVerifier;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    /**
     * Set the private key file path so that the SFTP endpoint can do public key authentication.
     */
    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public String getPrivateKeyUri() {
        return privateKeyUri;
    }

    /**
     * Set the private key as a classpath: or file: URI (e.g., classpath:keys/id_rsa).
     */
    public void setPrivateKeyUri(String privateKeyUri) {
        this.privateKeyUri = privateKeyUri;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    /**
     * Set the private key as byte array for public key authentication.
     */
    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    public String getPrivateKeyPassphrase() {
        return privateKeyPassphrase;
    }

    /**
     * Set the passphrase for decrypting an encrypted private key.
     */
    public void setPrivateKeyPassphrase(String privateKeyPassphrase) {
        this.privateKeyPassphrase = privateKeyPassphrase;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Set a java.security.KeyPair directly for public key authentication.
     */
    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public String getCertFile() {
        return certFile;
    }

    /**
     * Set the OpenSSH certificate file path for certificate-based authentication.
     */
    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    public String getCertUri() {
        return certUri;
    }

    /**
     * Set the OpenSSH certificate as a classpath: or file: URI.
     */
    public void setCertUri(String certUri) {
        this.certUri = certUri;
    }

    public byte[] getCertBytes() {
        return certBytes;
    }

    /**
     * Set the OpenSSH certificate as byte array.
     */
    public void setCertBytes(byte[] certBytes) {
        this.certBytes = certBytes;
    }

    public int getServerAliveInterval() {
        return serverAliveInterval;
    }

    /**
     * Sets the interval (millis) to send a keep-alive message. If zero is specified, any keep-alive message must not be
     * sent. The default interval is zero.
     */
    public void setServerAliveInterval(int serverAliveInterval) {
        this.serverAliveInterval = serverAliveInterval;
    }

    public int getServerAliveCountMax() {
        return serverAliveCountMax;
    }

    /**
     * Sets the number of keep-alive messages which may be sent without receiving any messages back from the server. If
     * this threshold is reached while keep-alive messages are being sent, the connection will be disconnected. The
     * default value is one.
     */
    public void setServerAliveCountMax(int serverAliveCountMax) {
        this.serverAliveCountMax = serverAliveCountMax;
    }

    public int getCompression() {
        return compression;
    }

    /**
     * To use compression. Specify a level from 1 to 10.
     */
    public void setCompression(int compression) {
        this.compression = compression;
    }

    public List<String> getCiphers() {
        return ciphers;
    }

    /**
     * Set the list of ciphers that will be used in order of preference. Possible cipher names are defined by Apache
     * MINA SSHD. Some examples include: aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc. If
     * not specified the default list from MINA SSHD will be used.
     */
    public void setCiphers(List<String> ciphers) {
        this.ciphers = ciphers;
    }

    /**
     * Set a comma separated list of ciphers that will be used in order of preference. Possible cipher names are defined
     * by Apache MINA SSHD. Some examples include:
     * aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc. If not specified the default list
     * from MINA SSHD will be used.
     */
    public void setCiphers(String ciphers) {
        this.ciphers = csvToList(ciphers);
    }

    public String getPreferredAuthentications() {
        return preferredAuthentications;
    }

    /**
     * Set the preferred authentications which SFTP endpoint will used. Some example include: password,publickey. If not
     * specified the default list from MINA SSHD will be used.
     */
    public void setPreferredAuthentications(String preferredAuthentications) {
        this.preferredAuthentications = preferredAuthentications;
    }

    public List<String> getKeyExchangeProtocols() {
        return keyExchangeProtocols;
    }

    /**
     * Set the list of key exchange protocols that will be used in order of preference. If not specified the default
     * list from MINA SSHD will be used.
     */
    public void setKeyExchangeProtocols(List<String> keyExchangeProtocols) {
        this.keyExchangeProtocols = keyExchangeProtocols;
    }

    /**
     * Set a comma separated list of key exchange protocols that will be used in order of preference. If not specified
     * the default list from MINA SSHD will be used.
     */
    public void setKeyExchangeProtocols(String keyExchangeProtocols) {
        this.keyExchangeProtocols = csvToList(keyExchangeProtocols);
    }

    public List<String> getServerHostKeys() {
        return serverHostKeys;
    }

    /**
     * Set the list of algorithms supported for the server host key. Some examples include:
     * ssh-dss,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521. If not specified the default list
     * from MINA SSHD will be used.
     */
    public void setServerHostKeys(List<String> serverHostKeys) {
        this.serverHostKeys = serverHostKeys;
    }

    /**
     * Set a comma separated list of algorithms supported for the server host key. Some examples include:
     * ssh-dss,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521. If not specified the default list
     * from MINA SSHD will be used.
     */
    public void setServerHostKeys(String serverHostKeys) {
        this.serverHostKeys = csvToList(serverHostKeys);
    }

    public String getPublicKeyAcceptedAlgorithms() {
        return publicKeyAcceptedAlgorithms;
    }

    /**
     * Set a comma separated list of public key accepted algorithms. If not specified the default list from MINA SSHD
     * will be used.
     */
    public void setPublicKeyAcceptedAlgorithms(String publicKeyAcceptedAlgorithms) {
        this.publicKeyAcceptedAlgorithms = publicKeyAcceptedAlgorithms;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    /**
     * Specifies the address of the local interface against which the connection should bind.
     */
    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public Integer getBulkRequests() {
        return bulkRequests;
    }

    /**
     * Specifies how many requests may be outstanding at any one time. Increasing this value may slightly improve file
     * transfer speed but will increase memory usage.
     *
     * @deprecated Use {@link #setReadBufferSize(Integer)} and {@link #setWriteBufferSize(Integer)} instead. Each bulk
     *             request corresponds to approximately 32KB of buffer. For example, bulkRequests=4 is equivalent to
     *             readBufferSize=131072 and writeBufferSize=131072.
     */
    @Deprecated
    public void setBulkRequests(Integer bulkRequests) {
        this.bulkRequests = bulkRequests;
    }

    public Integer getReadBufferSize() {
        return readBufferSize;
    }

    /**
     * Sets the buffer size in bytes used for reading data from SFTP connections. If not specified, the MINA SSHD
     * default buffer size is used. Larger values may improve transfer speed for large files but will increase memory
     * usage. Maximum recommended value is 126976 bytes (124KB) to avoid data corruption issues. This parameter maps
     * directly to Apache MINA SSHD's READ_BUFFER_SIZE property.
     */
    public void setReadBufferSize(Integer readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public Integer getWriteBufferSize() {
        return writeBufferSize;
    }

    /**
     * Sets the buffer size in bytes used for writing data to SFTP connections. If not specified, the MINA SSHD default
     * buffer size is used. Larger values may improve transfer speed for large files but will increase memory usage.
     * Maximum recommended value is 126976 bytes (124KB) to avoid data corruption issues. This parameter maps directly
     * to Apache MINA SSHD's WRITE_BUFFER_SIZE property.
     */
    public void setWriteBufferSize(Integer writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    public String getChmod() {
        return chmod;
    }

    /**
     * Allows you to set chmod on the stored file. For example chmod=640.
     */
    public void setChmod(String chmod) {
        this.chmod = chmod;
    }

    public String getChmodDirectory() {
        return chmodDirectory;
    }

    /**
     * Allows you to set chmod during path creation. For example chmod=640.
     */
    public void setChmodDirectory(String chmodDirectory) {
        this.chmodDirectory = chmodDirectory;
    }

    public String getFilenameEncoding() {
        return filenameEncoding;
    }

    /**
     * Encoding to use for FTP client when parsing filenames. By default, UTF-8 is used.
     */
    public void setFilenameEncoding(String filenameEncoding) {
        this.filenameEncoding = filenameEncoding;
    }

    /**
     * Converts a comma-separated string to a List of trimmed, non-empty strings.
     *
     * @param  csv the comma-separated string
     * @return     list of trimmed values, or null if input is null/empty
     */
    private static List<String> csvToList(String csv) {
        if (csv == null || csv.isEmpty()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String s : ObjectHelper.createIterable(csv, ",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? null : result;
    }

    // ========================================
    // DEPRECATED JSch-SPECIFIC PARAMETERS
    // These are provided for migration compatibility from the JSch-based sftp component.
    // They are ignored but log a warning to help users update their configuration.
    // ========================================

    @Deprecated
    @UriParam(label = "advanced",
              description = "Deprecated: JSch-specific parameter, ignored by mina-sftp. "
                            + "Configure logging via your logging framework instead.")
    private String jschLoggingLevel;

    @Deprecated
    @UriParam(label = "advanced",
              description = "Deprecated: JSch-specific parameter, ignored by mina-sftp. "
                            + "Configure logging via your logging framework instead.")
    private String serverMessageLoggingLevel;

    @Deprecated
    @UriParam(label = "advanced",
              description = "Deprecated: JSch-specific parameter, ignored by mina-sftp. "
                            + "MINA SSHD uses stat() for directory existence checks.")
    private Boolean existDirCheckUsingLs;

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. MINA SSHD
     *             uses SLF4J natively - configure logging via your logging framework (log4j, logback) instead.
     */
    @Deprecated
    public void setJschLoggingLevel(String level) {
        this.jschLoggingLevel = level;
        LOG.warn("The 'jschLoggingLevel' parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. "
                 + "MINA SSHD uses SLF4J natively - configure logging via your logging framework (log4j, logback) instead.");
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. Configure
     *             logging via your logging framework instead.
     */
    @Deprecated
    public void setServerMessageLoggingLevel(String level) {
        this.serverMessageLoggingLevel = level;
        LOG.warn(
                "The 'serverMessageLoggingLevel' parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. "
                 + "Configure logging via your logging framework instead.");
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. MINA SSHD
     *             uses a different approach for directory existence checks.
     */
    @Deprecated
    public void setExistDirCheckUsingLs(boolean value) {
        this.existDirCheckUsingLs = value;
        LOG.warn(
                "The 'existDirCheckUsingLs' parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. "
                 + "MINA SSHD uses stat() for directory existence checks which is more reliable.");
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp.
     */
    @Deprecated
    public String getJschLoggingLevel() {
        return jschLoggingLevel;
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp.
     */
    @Deprecated
    public String getServerMessageLoggingLevel() {
        return serverMessageLoggingLevel;
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp.
     */
    @Deprecated
    public Boolean getExistDirCheckUsingLs() {
        return existDirCheckUsingLs;
    }
}
