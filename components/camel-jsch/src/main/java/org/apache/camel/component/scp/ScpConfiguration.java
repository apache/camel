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
package org.apache.camel.component.scp;

import java.net.URI;

import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * SCP configuration
 */
@UriParams
public class ScpConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_SFTP_PORT = 22;
    public static final String DEFAULT_MOD = "664";
    @UriParam(label = "security", defaultValue = "true")
    private boolean useUserKnownHostsFile = true;
    @UriParam(label = "security", secret = true)
    private String knownHostsFile;
    @UriParam(label = "security", secret = true)
    private String privateKeyFile;
    @UriParam(label = "security", secret = true)
    private byte[] privateKeyBytes;
    @UriParam(label = "security", secret = true)
    private String privateKeyFilePassphrase;
    @UriParam(enums = "no,yes", defaultValue = "no")
    private String strictHostKeyChecking;
    @UriParam(defaultValue = DEFAULT_MOD)
    private String chmod = DEFAULT_MOD;
    // comma separated list of ciphers. 
    // null means default jsch list will be used
    @UriParam(label = "security,advanced")
    private String ciphers;
    @UriParam(label = "security", secret = true)
    private String preferredAuthentications;

    public ScpConfiguration() {
        setProtocol("scp");
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

    /**
     * Sets the known_hosts file, so that the jsch endpoint can do host key verification.
     * You can prefix with classpath: to load the file from classpath instead of file system.
     */
    public void setKnownHostsFile(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public boolean isUseUserKnownHostsFile() {
        return useUserKnownHostsFile;
    }

    /**
     * If knownHostFile has not been explicit configured, then use the host file from System.getProperty("user.home") + "/.ssh/known_hosts"
     */
    public void setUseUserKnownHostsFile(boolean useUserKnownHostsFile) {
        this.useUserKnownHostsFile = useUserKnownHostsFile;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    /**
     * Set the private key file to that the endpoint can do private key verification.
     * You can prefix with classpath: to load the file from classpath instead of file system.
     */
    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }    

    public byte[] getPrivateKeyBytes() {
        return privateKeyBytes;
    }

    /**
     * Set the private key bytes to that the endpoint can do private key verification.
     * This must be used only if privateKeyFile wasn't set. Otherwise the file will have the priority.
     */
    public void setPrivateKeyBytes(byte[] privateKeyBytes) {
        this.privateKeyBytes = privateKeyBytes;
    }

    public String getPrivateKeyFilePassphrase() {
        return privateKeyFilePassphrase;
    }

    /**
     * Set the private key file passphrase to that the endpoint can do private key verification.
     */
    public void setPrivateKeyFilePassphrase(String privateKeyFilePassphrase) {
        this.privateKeyFilePassphrase = privateKeyFilePassphrase;
    }

    public String getStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    /**
     * Sets whether to use strict host key checking. Possible values are: no, yes
     */
    public void setStrictHostKeyChecking(String strictHostKeyChecking) {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    /**
     * Allows you to set chmod on the stored file. For example chmod=664.
     */
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

    /**
     * Set a comma separated list of authentications that will be used in order of preference.
     * Possible authentication methods are defined by JCraft JSCH. Some examples include: gssapi-with-mic,publickey,keyboard-interactive,password
     * If not specified the JSCH and/or system defaults will be used.
     */
    public void setPreferredAuthentications(final String preferredAuthentications) {
        this.preferredAuthentications = preferredAuthentications;
    }

    public String getPreferredAuthentications() {
        return preferredAuthentications;
    }

}
