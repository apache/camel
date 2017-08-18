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
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.sshd.common.keyprovider.KeyPairProvider;

/**
 * Represents the component that manages {@link SshEndpoint}.
 */
public class SshComponent extends UriEndpointComponent {
    @Metadata(label = "advanced")
    private SshConfiguration configuration = new SshConfiguration();

    public SshComponent() {
        super(SshEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI u = new URI(uri);
        SshConfiguration newConfig = configuration.copy();
        newConfig.configure(u);

        SshEndpoint endpoint = new SshEndpoint(uri, this, newConfig);
        setProperties(endpoint.getConfiguration(), parameters);
        return endpoint;
    }

    public SshConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared SSH configuration
     */
    public void setConfiguration(SshConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getHost() {
        return getConfiguration().getHost();
    }

    /**
     * Sets the hostname of the remote SSH server.
     *
     * @param host String representing hostname of SSH server.
     */
    public void setHost(String host) {
        getConfiguration().setHost(host);
    }

    public int getPort() {
        return getConfiguration().getPort();
    }

    /**
     * Sets the port number for the remote SSH server.
     *
     * @param port int representing port number on remote host. Defaults to 22.
     */
    public void setPort(int port) {
        getConfiguration().setPort(port);
    }

    public String getUsername() {
        return getConfiguration().getUsername();
    }

    /**
     * Sets the username to use in logging into the remote SSH server.
     *
     * @param username String representing login username.
     */
    @Metadata(label = "security", secret = true)
    public void setUsername(String username) {
        getConfiguration().setUsername(username);
    }

    public String getPassword() {
        return getConfiguration().getPassword();
    }

    /**
     * Sets the password to use in connecting to remote SSH server.
     * Requires keyPairProvider to be set to null.
     *
     * @param password String representing password for username at remote host.
     */
    @Metadata(label = "security", secret = true)
    public void setPassword(String password) {
        getConfiguration().setPassword(password);
    }

    public String getPollCommand() {
        return getConfiguration().getPollCommand();
    }

    /**
     * Sets the command string to send to the remote SSH server during every poll cycle.
     * Only works with camel-ssh component being used as a consumer, i.e. from("ssh://...").
     * You may need to end your command with a newline, and that must be URL encoded %0A
     *
     * @param pollCommand String representing the command to send.
     */
    public void setPollCommand(String pollCommand) {
        getConfiguration().setPollCommand(pollCommand);
    }

    public KeyPairProvider getKeyPairProvider() {
        return getConfiguration().getKeyPairProvider();
    }

    /**
     * Sets the KeyPairProvider reference to use when connecting using Certificates to the remote SSH Server.
     *
     * @param keyPairProvider KeyPairProvider reference to use in authenticating. If set to 'null',
     *                        then will attempt to connect using username/password settings.
     *
     * @see KeyPairProvider
     */
    @Metadata(label = "security")
    public void setKeyPairProvider(KeyPairProvider keyPairProvider) {
        getConfiguration().setKeyPairProvider(keyPairProvider);
    }

    public String getKeyType() {
        return getConfiguration().getKeyType();
    }

    /**
     * Sets the key type to pass to the KeyPairProvider as part of authentication.
     * KeyPairProvider.loadKey(...) will be passed this value. Defaults to "ssh-rsa".
     *
     * @param keyType String defining the type of KeyPair to use for authentication.
     *
     * @see KeyPairProvider
     */
    @Metadata(label = "security")
    public void setKeyType(String keyType) {
        getConfiguration().setKeyType(keyType);
    }

    public long getTimeout() {
        return getConfiguration().getTimeout();
    }

    /**
     * Sets the timeout in milliseconds to wait in establishing the remote SSH server connection.
     * Defaults to 30000 milliseconds.
     *
     * @param timeout long milliseconds to wait.
     */
    public void setTimeout(long timeout) {
        getConfiguration().setTimeout(timeout);
    }

    /**
     * @deprecated As of version 2.11, replaced by {@link #getCertResource()}
     */
    @Deprecated
    public String getCertFilename() {
        return getConfiguration().getCertFilename();
    }

    /**
     * Sets the resource path of the certificate to use for Authentication.
     *
     * @deprecated As of version 2.11, replaced by {@link #setCertResource(String)}
     */
    @Deprecated
    @Metadata(label = "security")
    public void setCertFilename(String certFilename) {
        getConfiguration().setCertFilename(certFilename);
    }

    public String getCertResource() {
        return getConfiguration().getCertResource();
    }

    /**
     * Sets the resource path of the certificate to use for Authentication.
     * Will use {@link ResourceHelperKeyPairProvider} to resolve file based certificate, and depends on keyType setting.
     *
     * @param certResource String file, classpath, or http url for the certificate
     */
    @Metadata(label = "security")
    public void setCertResource(String certResource) {
        getConfiguration().setCertResource(certResource);
    }
}
