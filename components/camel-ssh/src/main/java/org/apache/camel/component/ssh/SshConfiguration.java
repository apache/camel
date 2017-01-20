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
package org.apache.camel.component.ssh;

import java.net.URI;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.sshd.common.KeyPairProvider;

@UriParams
public class SshConfiguration implements Cloneable {
    public static final int DEFAULT_SSH_PORT = 22;

    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath(defaultValue = "" + DEFAULT_SSH_PORT)
    private int port = DEFAULT_SSH_PORT;
    @UriParam(label = "security", secret = true)
    private String username;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "consumer")
    private String pollCommand;
    @UriParam(label = "security")
    private KeyPairProvider keyPairProvider;
    @UriParam(label = "security", defaultValue = KeyPairProvider.SSH_RSA)
    private String keyType = KeyPairProvider.SSH_RSA;
    @UriParam(label = "security")
    private String certResource;
    @UriParam(defaultValue = "30000")
    private long timeout = 30000;

    public SshConfiguration() {
    }

    public SshConfiguration(URI uri) {
        configure(uri);
    }

    public void configure(URI uri) {
        // UserInfo can contain both username and password as: user:pwd@sshserver
        // see: http://en.wikipedia.org/wiki/URI_scheme
        String username = uri.getUserInfo();
        String pw = null;
        if (username != null && username.contains(":")) {
            pw = ObjectHelper.after(username, ":");
            username = ObjectHelper.before(username, ":");
        }
        if (username != null) {
            setUsername(username);
        }
        if (pw != null) {
            setPassword(pw);
        }

        setHost(uri.getHost());

        // URI.getPort returns -1 if port not defined, else use default port
        int uriPort = uri.getPort();
        if (uriPort != -1) {
            setPort(uriPort);
        }
    }

    public SshConfiguration copy() {
        try {
            return (SshConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public String getUsername() {
        return username;
    }

    /**
     * Sets the username to use in logging into the remote SSH server.
     *
     * @param username String representing login username.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getHost() {
        return host;
    }

    /**
     * Sets the hostname of the remote SSH server.
     *
     * @param host String representing hostname of SSH server.
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Sets the port number for the remote SSH server.
     *
     * @param port int representing port number on remote host. Defaults to 22.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Sets the password to use in connecting to remote SSH server.
     * Requires keyPairProvider to be set to null.
     *
     * @param password String representing password for username at remote host.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getPollCommand() {
        return pollCommand;
    }

    /**
     * Sets the command string to send to the remote SSH server during every poll cycle.
     * Only works with camel-ssh component being used as a consumer, i.e. from("ssh://...")
     * You may need to end your command with a newline, and that must be URL encoded %0A
     *
     * @param pollCommand String representing the command to send.
     */
    public void setPollCommand(String pollCommand) {
        this.pollCommand = pollCommand;
    }

    public KeyPairProvider getKeyPairProvider() {
        return keyPairProvider;
    }

    /**
     * Sets the KeyPairProvider reference to use when connecting using Certificates to the remote SSH Server.
     *
     * @param keyPairProvider KeyPairProvider reference to use in authenticating. If set to 'null',
     *                        then will attempt to connect using username/password settings.
     *
     * @see KeyPairProvider
     */
    public void setKeyPairProvider(KeyPairProvider keyPairProvider) {
        this.keyPairProvider = keyPairProvider;
    }

    public String getKeyType() {
        return keyType;
    }

    /**
     * Sets the key type to pass to the KeyPairProvider as part of authentication.
     * KeyPairProvider.loadKey(...) will be passed this value. Defaults to "ssh-rsa".
     *
     * @param keyType String defining the type of KeyPair to use for authentication.
     *
     * @see KeyPairProvider
     */
    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout in milliseconds to wait in establishing the remote SSH server connection.
     * Defaults to 30000 milliseconds.
     *
     * @param timeout long milliseconds to wait.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * @deprecated As of version 2.11, replaced by {@link #getCertResource()}
     */
    @Deprecated
    public String getCertFilename() {
        return ((certResource != null) && certResource.startsWith("file:")) ? certResource.substring(5) : null;
    }

    /**
     * @deprecated As of version 2.11, replaced by {@link #setCertResource(String)}
     */
    @Deprecated
    public void setCertFilename(String certFilename) {
        this.certResource = "file:" + certFilename;
    }

    public String getCertResource() {
        return certResource;
    }

    /**
     * Sets the resource path of the certificate to use for Authentication.
     * Will use {@link ResourceHelperKeyPairProvider} to resolve file based certificate, and depends on keyType setting.
     *
     * @param certResource String file, classpath, or http url for the certificate
     */
    public void setCertResource(String certResource) {
        this.certResource = certResource;
    }
}
