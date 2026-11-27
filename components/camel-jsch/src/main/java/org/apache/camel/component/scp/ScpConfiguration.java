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
package org.apache.camel.component.scp;

import java.net.URI;

import org.apache.camel.component.file.remote.RemoteFileConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * SCP configuration
 */
@UriParams
public class ScpConfiguration extends RemoteFileConfiguration {

    public static final int DEFAULT_SFTP_PORT = 22;
    public static final String DEFAULT_MOD = "664";
    @UriParam(label = "security", defaultValue = "true",
              description = "Whether to use the known hosts file of the user, the .ssh/known_hosts file in the home directory"
                            + " (the user.home system property, or the HOME environment variable), when knownHostsFile is not"
                            + " set. If that file cannot be read, the producer fails to connect. If false and knownHostsFile is"
                            + " not set, no host is known, and strictHostKeyChecking decides whether the producer connects.")
    private boolean useUserKnownHostsFile = true;
    @UriParam(label = "security", security = "secret",
              description = "The known hosts file that JSch checks the key of the remote host against when the producer"
                            + " connects, in the OpenSSH known_hosts format. A path on the file system, or with the classpath:"
                            + " prefix a resource on the classpath. An @cert-authority entry lets the producer accept an"
                            + " OpenSSH host certificate signed by that CA. What happens with a host that is not in the file,"
                            + " or whose key changed, depends on strictHostKeyChecking.")
    @Metadata(supportFileReference = true)
    private String knownHostsFile;
    @UriParam(label = "security", security = "secret",
              description = "The private key file for public key authentication. A path on the file system, or with the"
                            + " classpath: prefix a resource on the classpath. The key must be in a format that JSch reads,"
                            + " such as OpenSSH, PEM or PuTTY. Takes precedence over privateKeyBytes. Use"
                            + " privateKeyFilePassphrase for an encrypted key.")
    private String privateKeyFile;
    @UriParam(label = "security", security = "secret",
              description = "The private key as a byte array, for public key authentication. Only used when privateKeyFile is"
                            + " not set. Use privateKeyFilePassphrase for an encrypted key.")
    private byte[] privateKeyBytes;
    @UriParam(label = "security", security = "secret",
              description = "The passphrase of an encrypted private key of privateKeyFile or privateKeyBytes. Not needed for a"
                            + " key that is not encrypted.")
    private String privateKeyFilePassphrase;
    @UriParam(label = "security", security = "secret",
              description = "The OpenSSH user certificate file for certificate-based authentication, for example"
                            + " id_ed25519-cert.pub. A path on the file system, or with the classpath: prefix a resource on the"
                            + " classpath. Only used together with privateKeyFile or privateKeyBytes, the private key of the"
                            + " certificate. Takes precedence over certUri and certBytes.")
    private String certFile;
    @UriParam(label = "security", security = "secret",
              description = "The OpenSSH user certificate for certificate-based authentication, loaded the same way as"
                            + " certFile: a path on the file system, or with the classpath: prefix a resource on the classpath."
                            + " Only used together with privateKeyFile or privateKeyBytes, and when certFile is not set. Takes"
                            + " precedence over certBytes.")
    @Metadata(supportFileReference = true)
    private String certUri;
    @UriParam(label = "security", security = "secret",
              description = "The OpenSSH user certificate as a byte array, for certificate-based authentication. Only used"
                            + " together with privateKeyFile or privateKeyBytes, and when neither certFile nor certUri is set.")
    private byte[] certBytes;
    @UriParam(label = "security",
              description = "A comma-separated list of the signature algorithms that are accepted for the CA signature of an"
                            + " OpenSSH host certificate, for example ssh-ed25519,rsa-sha2-512. The CA keys are the"
                            + " @cert-authority entries of the known hosts file. If not set, the JSch default list is used,"
                            + " which does not accept the SHA-1 based ssh-rsa.")
    private String caSignatureAlgorithms;
    @UriParam(label = "security", enums = "no,yes", defaultValue = "no", security = "insecure:ssl",
              description = "Sets whether to use strict host key checking. Setting this to 'no' (the default) disables"
                            + " host key verification and makes the connection vulnerable to man-in-the-middle attacks."
                            + " Use 'yes' in production environments.")
    private String strictHostKeyChecking = "no";
    @UriParam(defaultValue = DEFAULT_MOD,
              description = "The permissions of the file that the producer writes, as three octal digits, for example 644."
                            + " Any other value is replaced by 664. The producer sends it in the scp protocol line of the file,"
                            + " and the remote scp uses it when it creates the file. The directories that the producer creates"
                            + " for a file name with a path get 775.")
    private String chmod = DEFAULT_MOD;
    @UriParam(label = "security,advanced",
              description = "A comma-separated list of the ciphers for the SSH connection, in order of preference, for example"
                            + " aes128-ctr,aes256-ctr. The cipher names are those of JSch. If not set, the JSch default list is"
                            + " used.")
    private String ciphers;
    @UriParam(label = "security", security = "secret",
              description = "A comma-separated list of the SSH authentication methods to try, in order of preference, for"
                            + " example publickey,password. JSch supports gssapi-with-mic, publickey, keyboard-interactive and"
                            + " password. If not set, the JSch default gssapi-with-mic,publickey,keyboard-interactive,password"
                            + " is used.")
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

    public void setKnownHostsFile(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public boolean isUseUserKnownHostsFile() {
        return useUserKnownHostsFile;
    }

    public void setUseUserKnownHostsFile(boolean useUserKnownHostsFile) {
        this.useUserKnownHostsFile = useUserKnownHostsFile;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public byte[] getPrivateKeyBytes() {
        return privateKeyBytes;
    }

    public void setPrivateKeyBytes(byte[] privateKeyBytes) {
        this.privateKeyBytes = privateKeyBytes;
    }

    public String getPrivateKeyFilePassphrase() {
        return privateKeyFilePassphrase;
    }

    public void setPrivateKeyFilePassphrase(String privateKeyFilePassphrase) {
        this.privateKeyFilePassphrase = privateKeyFilePassphrase;
    }

    public String getCertFile() {
        return certFile;
    }

    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    public String getCertUri() {
        return certUri;
    }

    public void setCertUri(String certUri) {
        this.certUri = certUri;
    }

    public byte[] getCertBytes() {
        return certBytes;
    }

    public void setCertBytes(byte[] certBytes) {
        this.certBytes = certBytes;
    }

    public String getCaSignatureAlgorithms() {
        return caSignatureAlgorithms;
    }

    public void setCaSignatureAlgorithms(String caSignatureAlgorithms) {
        this.caSignatureAlgorithms = caSignatureAlgorithms;
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

    public void setPreferredAuthentications(final String preferredAuthentications) {
        this.preferredAuthentications = preferredAuthentications;
    }

    public String getPreferredAuthentications() {
        return preferredAuthentications;
    }

}
