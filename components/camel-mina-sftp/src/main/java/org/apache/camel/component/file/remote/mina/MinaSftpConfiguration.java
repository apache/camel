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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.file.remote.BaseSftpConfiguration;
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
public class MinaSftpConfiguration extends BaseSftpConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MinaSftpConfiguration.class);

    // MINA-specific: custom server key verifier
    @UriParam(label = "security",
              description = "Custom ServerKeyVerifier for host key verification. When provided, this verifier is used "
                            + "exclusively, ignoring strictHostKeyChecking, knownHostsFile, and other host key options.")
    private ServerKeyVerifier serverKeyVerifier;

    // MINA-specific: ciphers, key exchange, server host keys as List<String>
    @UriParam(label = "security", javaType = "java.lang.String")
    private List<String> ciphers;
    @UriParam(label = "security", javaType = "java.lang.String")
    private List<String> keyExchangeProtocols;
    @UriParam(label = "security", javaType = "java.lang.String")
    private List<String> serverHostKeys;

    // MINA-specific: buffer sizes
    @UriParam(label = "advanced")
    private Integer readBufferSize;
    @UriParam(label = "advanced")
    private Integer writeBufferSize;

    public MinaSftpConfiguration() {
        setProtocol("mina-sftp");
    }

    public MinaSftpConfiguration(URI uri) {
        super(uri);
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

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link #setReadBufferSize(Integer)} and {@link #setWriteBufferSize(Integer)} instead. Each bulk
     *             request corresponds to approximately 32KB of buffer. For example, bulkRequests=4 is equivalent to
     *             readBufferSize=131072 and writeBufferSize=131072.
     */
    @Override
    @Deprecated(since = "4.18")
    public void setBulkRequests(Integer bulkRequests) {
        super.setBulkRequests(bulkRequests);
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

    @Deprecated(since = "4.18")
    @UriParam(label = "advanced",
              description = "Deprecated: JSch-specific parameter, ignored by mina-sftp. "
                            + "Configure logging via your logging framework instead.")
    private String jschLoggingLevel;

    @Deprecated(since = "4.18")
    @UriParam(label = "advanced",
              description = "Deprecated: JSch-specific parameter, ignored by mina-sftp. "
                            + "Configure logging via your logging framework instead.")
    private String serverMessageLoggingLevel;

    @Deprecated(since = "4.18")
    @UriParam(label = "advanced",
              description = "Deprecated: JSch-specific parameter, ignored by mina-sftp. "
                            + "MINA SSHD uses stat() for directory existence checks.")
    private Boolean existDirCheckUsingLs;

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. MINA SSHD
     *             uses SLF4J natively - configure logging via your logging framework (log4j, logback) instead.
     */
    @Deprecated(since = "4.18")
    public void setJschLoggingLevel(String level) {
        this.jschLoggingLevel = level;
        LOG.warn("The 'jschLoggingLevel' parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. "
                 + "MINA SSHD uses SLF4J natively - configure logging via your logging framework (log4j, logback) instead.");
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. Configure
     *             logging via your logging framework instead.
     */
    @Deprecated(since = "4.18")
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
    @Deprecated(since = "4.18")
    public void setExistDirCheckUsingLs(boolean value) {
        this.existDirCheckUsingLs = value;
        LOG.warn(
                "The 'existDirCheckUsingLs' parameter is specific to the JSch-based sftp component and is ignored by mina-sftp. "
                 + "MINA SSHD uses stat() for directory existence checks which is more reliable.");
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp.
     */
    @Deprecated(since = "4.18")
    public String getJschLoggingLevel() {
        return jschLoggingLevel;
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp.
     */
    @Deprecated(since = "4.18")
    public String getServerMessageLoggingLevel() {
        return serverMessageLoggingLevel;
    }

    /**
     * @deprecated This parameter is specific to the JSch-based sftp component and is ignored by mina-sftp.
     */
    @Deprecated(since = "4.18")
    public Boolean getExistDirCheckUsingLs() {
        return existDirCheckUsingLs;
    }
}
