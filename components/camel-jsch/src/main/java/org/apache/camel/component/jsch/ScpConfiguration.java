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
package org.apache.camel.component.jsch;

import java.net.URI;

import org.apache.camel.component.file.remote.RemoteFileConfiguration;

/**
 * Secure FTP configuration
 */
public class ScpConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_SFTP_PORT = 22;
    public static final String DEFAULT_MOD = "664";
    private String knownHostsFile;
    private String privateKeyFile;
    private String privateKeyFilePassphrase;
    private String strictHostKeyChecking;
    private int serverAliveInterval;
    private int serverAliveCountMax = 1;
    private String chmod = DEFAULT_MOD;
    // comma separated list of ciphers. 
    // null means default jsch list will be used
    private String ciphers;
    private int compression;

    public ScpConfiguration() {
        setProtocol("sftp");
    }

    public ScpConfiguration(URI uri) {
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

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public String getPrivateKeyFilePassphrase() {
        return privateKeyFilePassphrase;
    }

    public void setPrivateKeyFilePassphrase(String privateKeyFilePassphrase) {
        this.privateKeyFilePassphrase = privateKeyFilePassphrase;
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
        if (chmod.length() == 3) {
            for (byte c : chmod.getBytes()) {
                if (c < '0' || c > '7') {
                    chmod = DEFAULT_MOD;
                    break;
                }
            }
        } else {
            chmod = DEFAULT_MOD;
        }
        // May be interesting to log the fallback to DEFAULT_MOD for invalid configuration
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
}
