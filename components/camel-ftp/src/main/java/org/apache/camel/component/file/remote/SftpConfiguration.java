/**
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

import java.net.URI;
import java.security.KeyPair;

import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Secure FTP configuration
 */
@UriParams
public class SftpConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_SFTP_PORT = 22;

    @UriParam(label = "security", secret = true)
    private String knownHostsFile;
    @UriParam(label = "security", defaultValue = "true")
    private boolean useUserKnownHostsFile = true;
    @UriParam(label = "security", secret = true)
    private String knownHostsUri;
    @UriParam(label = "security", secret = true)
    private byte[] knownHosts;
    @UriParam(label = "security", secret = true)
    private String privateKeyFile;
    @UriParam(label = "security", secret = true)
    private String privateKeyUri;
    @UriParam(label = "security", secret = true)
    private byte[] privateKey;
    @UriParam(label = "security", secret = true)
    private String privateKeyPassphrase;
    @UriParam(label = "security", secret = true)
    private KeyPair keyPair;
    @UriParam(defaultValue = "no", enums = "no,yes", label = "security")
    private String strictHostKeyChecking = "no";
    @UriParam(label = "advanced")
    private int serverAliveInterval;
    @UriParam(defaultValue = "1", label = "advanced")
    private int serverAliveCountMax = 1;
    @UriParam(label = "producer,advanced")
    private String chmod;
    // comma separated list of ciphers.
    // null means default jsch list will be used
    @UriParam(label = "security")
    private String ciphers;
    @UriParam(label = "advanced")
    private int compression;
    @UriParam(label = "security")
    private String preferredAuthentications;
    @UriParam(defaultValue = "WARN")
    private LoggingLevel jschLoggingLevel = LoggingLevel.WARN;
    @UriParam(label = "advanced")
    private Integer bulkRequests;

    public SftpConfiguration() {
        setProtocol("sftp");
    }

    public SftpConfiguration(URI uri) {
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

    public boolean isUseUserKnownHostsFile() {
        return useUserKnownHostsFile;
    }

    /**
     * If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts
     */
    public void setUseUserKnownHostsFile(boolean useUserKnownHostsFile) {
        this.useUserKnownHostsFile = useUserKnownHostsFile;
    }

    /**
     * Sets the known_hosts file (loaded from classpath by default), so that the SFTP endpoint can do host key verification.
     */
    public void setKnownHostsUri(String knownHostsUri) {
        this.knownHostsUri = knownHostsUri;
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

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    /**
     * Set the private key file so that the SFTP endpoint can do private key verification.
     */
    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public String getPrivateKeyUri() {
        return privateKeyUri;
    }

    /**
     * Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key verification.
     */
    public void setPrivateKeyUri(String privateKeyUri) {
        this.privateKeyUri = privateKeyUri;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    /**
     * Set the private key as byte[] so that the SFTP endpoint can do private key verification.
     */
    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    public String getPrivateKeyPassphrase() {
        return privateKeyPassphrase;
    }

    /**
     * Set the private key file passphrase so that the SFTP endpoint can do private key verification.
     */
    public void setPrivateKeyPassphrase(String privateKeyFilePassphrase) {
        this.privateKeyPassphrase = privateKeyFilePassphrase;
    }

    @Deprecated
    public String getPrivateKeyFilePassphrase() {
        return privateKeyPassphrase;
    }

    @Deprecated
    public void setPrivateKeyFilePassphrase(String privateKeyFilePassphrase) {
        this.privateKeyPassphrase = privateKeyFilePassphrase;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Sets a key pair of the public and private key so to that the SFTP endpoint can do public/private key verification.
     */
    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
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

    /**
     * Allows you to set the serverAliveInterval of the sftp session
     */
    public void setServerAliveInterval(int serverAliveInterval) {
        this.serverAliveInterval = serverAliveInterval;
    }

    public int getServerAliveInterval() {
        return serverAliveInterval;
    }

    /**
     * Allows you to set the serverAliveCountMax of the sftp session
     */
    public void setServerAliveCountMax(int serverAliveCountMax) {
        this.serverAliveCountMax = serverAliveCountMax;
    }

    public int getServerAliveCountMax() {
        return serverAliveCountMax;
    }

    /**
     * Allows you to set chmod on the stored file. For example chmod=640.
     */
    public void setChmod(String chmod) {
        this.chmod = chmod;
    }

    public String getChmod() {
        return chmod;
    }

    /**
     * Set a comma separated list of ciphers that will be used in order of preference.
     * Possible cipher names are defined by JCraft JSCH. Some examples include: aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc.
     * If not specified the default list from JSCH will be used.
     */
    public void setCiphers(String ciphers) {
        this.ciphers = ciphers;
    }

    public String getCiphers() {
        return ciphers;
    }

    public int getCompression() {
        return compression;
    }

    /**
     * To use compression. Specify a level from 1 to 10.
     * Important: You must manually add the needed JSCH zlib JAR to the classpath for compression support.
     */
    public void setCompression(int compression) {
        this.compression = compression;
    }

    /**
     * Set the preferred authentications which SFTP endpoint will used. Some example include:password,publickey.
     * If not specified the default list from JSCH will be used.
     */
    public void setPreferredAuthentications(String pAuthentications) {
        this.preferredAuthentications = pAuthentications;
    }

    public String getPreferredAuthentications() {
        return preferredAuthentications;
    }

    public LoggingLevel getJschLoggingLevel() {
        return jschLoggingLevel;
    }

    /**
     * The logging level to use for JSCH activity logging.
     * As JSCH is verbose at by default at INFO level the threshold is WARN by default.
     */
    public void setJschLoggingLevel(LoggingLevel jschLoggingLevel) {
        this.jschLoggingLevel = jschLoggingLevel;
    }

    /**
     * Specifies how many requests may be outstanding at any one time. Increasing this value may
     * slightly improve file transfer speed but will increase memory usage.
     */
    public void setBulkRequests(Integer bulkRequests) {
        this.bulkRequests = bulkRequests;
    }

    public Integer getBulkRequests() {
        return bulkRequests;
    }
}
