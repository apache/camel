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

import java.net.URI;
import java.security.KeyPair;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Base SFTP configuration shared between JSch and MINA SSHD implementations.
 */
@UriParams
public abstract class BaseSftpConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_SFTP_PORT = 22;

    @UriParam(label = "security", secret = true,
              description = "Sets the known_hosts file, so that the SFTP endpoint can do host key verification.")
    private String knownHostsFile;
    @UriParam(label = "security", defaultValue = "true",
              description = "If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts")
    private boolean useUserKnownHostsFile = true;
    @UriParam(label = "security", defaultValue = "false",
              description = "If knownHostFile does not exist, then attempt to auto-create the path and file (beware that the file will be created by the current user of the running Java process, which may not have file permission).")
    private boolean autoCreateKnownHostsFile;
    @UriParam(label = "security", secret = true,
              description = "Sets the known_hosts file (loaded from classpath by default), so that the SFTP endpoint can do host key verification.")
    @Metadata(supportFileReference = true)
    private String knownHostsUri;
    @UriParam(label = "security", secret = true,
              description = "Sets the known_hosts from the byte array, so that the SFTP endpoint can do host key verification.")
    private byte[] knownHosts;
    @UriParam(defaultValue = "no", enums = "no,yes", label = "security",
              description = "Sets whether to use strict host key checking.")
    private String strictHostKeyChecking = "no";
    @UriParam(label = "security", secret = true,
              description = "Set the private key file so that the SFTP endpoint can do private key verification.")
    private String privateKeyFile;
    @UriParam(label = "security", secret = true,
              description = "Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key verification.")
    @Metadata(supportFileReference = true)
    private String privateKeyUri;
    @UriParam(label = "security", secret = true,
              description = "Set the private key as byte[] so that the SFTP endpoint can do private key verification.")
    private byte[] privateKey;
    @UriParam(label = "security", secret = true,
              description = "Set the private key file passphrase so that the SFTP endpoint can do private key verification.")
    private String privateKeyPassphrase;
    @UriParam(label = "security", secret = true,
              description = "Set the OpenSSH certificate file path for certificate-based authentication.")
    private String certFile;
    @UriParam(label = "security", secret = true,
              description = "Set the OpenSSH certificate (loaded from classpath by default) for certificate-based authentication.")
    @Metadata(supportFileReference = true)
    private String certUri;
    @UriParam(label = "security", secret = true,
              description = "Set the OpenSSH certificate as a byte array for certificate-based authentication.")
    private byte[] certBytes;
    @UriParam(label = "security", secret = true,
              description = "Sets a key pair of the public and private key so to that the SFTP endpoint can do public/private key verification.")
    private KeyPair keyPair;
    @UriParam(label = "advanced",
              description = "Sets the interval (millis) to send a keep-alive message. If zero is specified, any keep-alive message must not be sent. The default interval is zero.")
    private int serverAliveInterval;
    @UriParam(defaultValue = "1", label = "advanced",
              description = "Sets the number of keep-alive messages which may be sent without receiving any messages back from the server. If this threshold is reached while keep-alive messages are being sent, the connection will be disconnected. The default value is one.")
    private int serverAliveCountMax = 1;
    @UriParam(label = "producer,advanced",
              description = "Allows you to set chmod on the stored file. For example chmod=640.")
    private String chmod;
    @UriParam(label = "producer,advanced",
              description = "Allows you to set chmod during path creation. For example chmod=640.")
    private String chmodDirectory;
    @UriParam(label = "advanced",
              description = "To use compression. Specify a level from 1 to 10.")
    private int compression;
    @UriParam(label = "security",
              description = "Set the preferred authentications which SFTP endpoint will used. Some example include: password,publickey. If not specified the default list will be used.")
    private String preferredAuthentications;
    @UriParam(label = "security",
              description = "Set a comma separated list of public key accepted algorithms. If not specified the default list will be used.")
    private String publicKeyAcceptedAlgorithms;
    @UriParam(label = "advanced",
              description = "Encoding to use for FTP client when parsing filenames. By default, UTF-8 is used.")
    private String filenameEncoding;
    @UriParam(label = "advanced",
              description = "Specifies the address of the local interface against which the connection should bind.")
    private String bindAddress;
    @UriParam(label = "advanced",
              description = "Specifies how many requests may be outstanding at any one time. Increasing this value may slightly improve file transfer speed but will increase memory usage.")
    private Integer bulkRequests;

    protected BaseSftpConfiguration() {
    }

    protected BaseSftpConfiguration(URI uri) {
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
     * Set the private key file (loaded from classpath by default) so that the SFTP endpoint can do private key
     * verification.
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
     * Set the OpenSSH certificate (loaded from classpath by default) for certificate-based authentication.
     */
    public void setCertUri(String certUri) {
        this.certUri = certUri;
    }

    public byte[] getCertBytes() {
        return certBytes;
    }

    /**
     * Set the OpenSSH certificate as a byte array for certificate-based authentication.
     */
    public void setCertBytes(byte[] certBytes) {
        this.certBytes = certBytes;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Sets a key pair of the public and private key so to that the SFTP endpoint can do public/private key
     * verification.
     */
    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
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

    public int getCompression() {
        return compression;
    }

    /**
     * To use compression. Specify a level from 1 to 10.
     */
    public void setCompression(int compression) {
        this.compression = compression;
    }

    public String getPreferredAuthentications() {
        return preferredAuthentications;
    }

    /**
     * Set the preferred authentications which SFTP endpoint will used. Some example include: password,publickey. If not
     * specified the default list will be used.
     */
    public void setPreferredAuthentications(String pAuthentications) {
        this.preferredAuthentications = pAuthentications;
    }

    public String getPublicKeyAcceptedAlgorithms() {
        return publicKeyAcceptedAlgorithms;
    }

    /**
     * Set a comma separated list of public key accepted algorithms. If not specified the default list will be used.
     */
    public void setPublicKeyAcceptedAlgorithms(String publicKeyAcceptedAlgorithms) {
        this.publicKeyAcceptedAlgorithms = publicKeyAcceptedAlgorithms;
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
     */
    public void setBulkRequests(Integer bulkRequests) {
        this.bulkRequests = bulkRequests;
    }
}
