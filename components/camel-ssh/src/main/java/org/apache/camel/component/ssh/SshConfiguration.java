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
package org.apache.camel.component.ssh;

import java.net.URI;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.StringHelper;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.keyprovider.KeyPairProvider;

@UriParams
public class SshConfiguration implements Cloneable {
    public static final int DEFAULT_SSH_PORT = 22;

    @UriPath
    @Metadata(required = true)
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
    @UriParam(label = "security")
    private String keyType;
    @UriParam(label = "security")
    private String certResource;
    @UriParam(label = "security", secret = true)
    private String certResourcePassword;
    @UriParam(defaultValue = "30000")
    private long timeout = 30000;
    @UriParam()
    private String knownHostsResource;
    @UriParam(defaultValue = "false")
    private boolean failOnUnknownHost;
    @UriParam(label = "advanced", defaultValue = Channel.CHANNEL_EXEC)
    private String channelType = Channel.CHANNEL_EXEC;
    @UriParam(label = "advanced")
    private String shellPrompt;
    @UriParam(label = "advanced", defaultValue = "100")
    private long sleepForShellPrompt;

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
            pw = StringHelper.after(username, ":");
            username = StringHelper.before(username, ":");
        }
        if (username != null) {
            setUsername(username);
        }
        if (pw != null) {
            setPassword(pw);
        }

        if (getHost() == null && uri.getHost() != null) {
            setHost(uri.getHost());
        }

        // URI.getPort returns -1 if port not defined, else use default port
        int uriPort = uri.getPort();
        if (getPort() == DEFAULT_SSH_PORT && uriPort != -1) {
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
     * @param username
     *            String representing login username.
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
     * @param host
     *            String representing hostname of SSH server.
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
     * @param port
     *            int representing port number on remote host. Defaults to 22.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Sets the password to use in connecting to remote SSH server. Requires
     * keyPairProvider to be set to null.
     *
     * @param password
     *            String representing password for username at remote host.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getPollCommand() {
        return pollCommand;
    }

    /**
     * Sets the command string to send to the remote SSH server during every poll
     * cycle. Only works with camel-ssh component being used as a consumer, i.e.
     * from("ssh://...") You may need to end your command with a newline, and that
     * must be URL encoded %0A
     *
     * @param pollCommand
     *            String representing the command to send.
     */
    public void setPollCommand(String pollCommand) {
        this.pollCommand = pollCommand;
    }

    public KeyPairProvider getKeyPairProvider() {
        return keyPairProvider;
    }

    /**
     * Sets the KeyPairProvider reference to use when connecting using Certificates
     * to the remote SSH Server.
     *
     * @param keyPairProvider
     *            KeyPairProvider reference to use in authenticating. If set to
     *            'null', then will attempt to connect using username/password
     *            settings.
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
     * KeyPairProvider.loadKey(...) will be passed this value. From Camel 3.0.0 / 2.25.0,
     * by default Camel will select the first available KeyPair that is loaded. Prior to
     * this, a KeyType of 'ssh-rsa' was enforced by default.
     *
     * @param keyType
     *            String defining the type of KeyPair to use for authentication.
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
     * Sets the timeout in milliseconds to wait in establishing the remote SSH
     * server connection. Defaults to 30000 milliseconds.
     *
     * @param timeout
     *            long milliseconds to wait.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getCertResource() {
        return certResource;
    }

    /**
     * Sets the resource path of the certificate to use for Authentication. Will use
     * {@link ResourceHelperKeyPairProvider} to resolve file based certificate, and
     * depends on keyType setting.
     *
     * @param certResource
     *            String file, classpath, or http url for the certificate
     */
    public void setCertResource(String certResource) {
        this.certResource = certResource;
    }

    public String getCertResourcePassword() {
        return certResourcePassword;
    }

    /**
     * Sets the password to use in loading certResource, if certResource is an encrypted key.
     *
     * @param certResourcePassword
     *            String representing password use to load the certResource key
     */
    public void setCertResourcePassword(String certResourcePassword) {
        this.certResourcePassword = certResourcePassword;
    }

    public String getKnownHostsResource() {
        return knownHostsResource;
    }

    /**
     * Sets the resource path for a known_hosts file
     *
     * @param knownHostsResource
     *            String file, classpath, or http url for the certificate
     */
    public void setKnownHostsResource(String knownHostsResource) {
        this.knownHostsResource = knownHostsResource;
    }

    public boolean isFailOnUnknownHost() {
        return failOnUnknownHost;
    }

    /**
     * Specifies whether a connection to an unknown host should fail or not. This
     * value is only checked when the property knownHosts is set.
     *
     * @param failOnUnknownHost
     *            boolean flag, whether a connection to an unknown host should fail
     */
    public void setFailOnUnknownHost(boolean failOnUnknownHost) {
        this.failOnUnknownHost = failOnUnknownHost;
    }

    public String getChannelType() {
        return channelType;
    }

    /**
     * Sets the channel type to pass to the Channel as part of command execution.
     * Defaults to "exec".
     *
     * @param channelType
     *            String defining the type of Channel to use for command execution.
     *
     * @see org.apache.sshd.common.channel.Channel
     */
    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getShellPrompt() {
        return shellPrompt;
    }

    /**
     * Sets the shellPrompt to be dropped when response is read after command execution
     *
     * @param shellPrompt
     *            String defining ending string of command line which has to be dropped when response is
     *            read after command execution.
     */
    public void setShellPrompt(String shellPrompt) {
        this.shellPrompt = shellPrompt;
    }

    public long getSleepForShellPrompt() {
        return sleepForShellPrompt;
    }

    /**
     * Sets the sleep period in milliseconds to wait reading response from shell prompt.
     * Defaults to 100 milliseconds.
     *
     * @param sleepForShellPrompt
     *            long milliseconds to wait.
     */
    public void setSleepForShellPrompt(long sleepForShellPrompt) {
        this.sleepForShellPrompt = sleepForShellPrompt;
    }
}
