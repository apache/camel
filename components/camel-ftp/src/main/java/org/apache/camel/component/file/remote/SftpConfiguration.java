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

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Secure FTP configuration
 */
@UriParams
public class SftpConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_SFTP_PORT = 22;

    @UriParam
    private String knownHostsFile;
    @UriParam
    private String knownHostsUri;
    private byte[] knownHosts;
    @UriParam
    private String privateKeyFile;
    @UriParam
    private String privateKeyUri;
    private byte[] privateKey;
    @UriParam
    private String privateKeyPassphrase;
    private KeyPair keyPair;
    @UriParam(defaultValue = "no")
    private String strictHostKeyChecking = "no";
    @UriParam
    private int serverAliveInterval;
    @UriParam(defaultValue = "1")
    private int serverAliveCountMax = 1;
    @UriParam
    private String chmod;
    // comma separated list of ciphers. 
    // null means default jsch list will be used
    @UriParam
    private String ciphers;
    @UriParam
    private int compression;
    @UriParam
    private String preferredAuthentications;

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

    public void setKnownHostsFile(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public String getKnownHostsUri() {
        return knownHostsUri;
    }

    public void setKnownHostsUri(String knownHostsUri) {
        this.knownHostsUri = knownHostsUri;
    }

    public byte[] getKnownHosts() {
        return knownHosts;
    }

    public void setKnownHosts(byte[] knownHosts) {
        this.knownHosts = knownHosts;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public String getPrivateKeyUri() {
        return privateKeyUri;
    }

    public void setPrivateKeyUri(String privateKeyUri) {
        this.privateKeyUri = privateKeyUri;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    public String getPrivateKeyPassphrase() {
        return privateKeyPassphrase;
    }

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

    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public String getStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    public void setStrictHostKeyChecking(String strictHostKeyChecking) {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    public void setServerAliveInterval(int serverAliveInterval) {
        this.serverAliveInterval = serverAliveInterval;
    }

    public int getServerAliveInterval() {
        return serverAliveInterval;
    }

    public void setServerAliveCountMax(int serverAliveCountMax) {
        this.serverAliveCountMax = serverAliveCountMax;
    }

    public int getServerAliveCountMax() {
        return serverAliveCountMax;
    }

    public void setChmod(String chmod) {
        this.chmod = chmod;
    }

    public String getChmod() {
        return chmod;
    }

    public void setCiphers(String ciphers) {
        this.ciphers = ciphers;
    }

    public String getCiphers() {
        return ciphers;
    }

    public int getCompression() {
        return compression;
    }

    public void setCompression(int compression) {
        this.compression = compression;
    }
    
    public void setPreferredAuthentications(String pAuthentications) {
        this.preferredAuthentications = pAuthentications;
    }
    
    public String getPreferredAuthentications() {
        return preferredAuthentications;
    }
}
