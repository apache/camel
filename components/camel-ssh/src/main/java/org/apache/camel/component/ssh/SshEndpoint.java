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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ssh component enables access to SSH servers such that you can send an SSH
 * command, and process the response.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "ssh", title = "SSH", syntax = "ssh:host:port", alternativeSyntax = "ssh:username:password@host:port", consumerClass = SshConsumer.class, label = "file")
public class SshEndpoint extends ScheduledPollEndpoint {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @UriParam
    private SshConfiguration sshConfiguration;

    public SshEndpoint() {
    }

    public SshEndpoint(String uri, SshComponent component) {
        super(uri, component);
    }

    public SshEndpoint(String uri, SshComponent component, SshConfiguration configuration) {
        super(uri, component);
        this.sshConfiguration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SshProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SshConsumer consumer = new SshConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isSingleton() {
        // SshClient is not thread-safe to be shared
        return true;
    }

    public SshConfiguration getConfiguration() {
        return sshConfiguration;
    }

    public void setConfiguration(SshConfiguration configuration) {
        this.sshConfiguration = configuration;
    }

    public String getHost() {
        return getConfiguration().getHost();
    }

    public void setHost(String host) {
        getConfiguration().setHost(host);
    }

    public int getPort() {
        return getConfiguration().getPort();
    }

    public void setPort(int port) {
        getConfiguration().setPort(port);
    }

    public String getUsername() {
        return getConfiguration().getUsername();
    }

    public void setUsername(String username) {
        getConfiguration().setUsername(username);
    }

    public String getPassword() {
        return getConfiguration().getPassword();
    }

    public void setPassword(String password) {
        getConfiguration().setPassword(password);
    }

    public String getPollCommand() {
        return getConfiguration().getPollCommand();
    }

    public void setPollCommand(String pollCommand) {
        getConfiguration().setPollCommand(pollCommand);
    }

    public KeyPairProvider getKeyPairProvider() {
        return getConfiguration().getKeyPairProvider();
    }

    public void setKeyPairProvider(KeyPairProvider keyPairProvider) {
        getConfiguration().setKeyPairProvider(keyPairProvider);
    }

    public String getKeyType() {
        return getConfiguration().getKeyType();
    }

    public void setKeyType(String keyType) {
        getConfiguration().setKeyType(keyType);
    }

    public long getTimeout() {
        return getConfiguration().getTimeout();
    }

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
     * @deprecated As of version 2.11, replaced by {@link #setCertResource(String)}
     */
    @Deprecated
    public void setCertFilename(String certFilename) {
        getConfiguration().setCertFilename(certFilename);
    }

    public String getCertResource() {
        return getConfiguration().getCertResource();
    }

    public void setCertResource(String certResource) {
        getConfiguration().setCertResource(certResource);
    }

    public String getKnownHostsResource() {
        return getConfiguration().getKnownHostsResource();
    }

    public void setKnownHostsResource(String knownHostsResource) {
        getConfiguration().setKnownHostsResource(knownHostsResource);
    }

    public boolean isFailOnUnknownHost() {
        return getConfiguration().isFailOnUnknownHost();
    }

    public void setFailOnUnknownHost(boolean failOnUnknownHost) {
        getConfiguration().setFailOnUnknownHost(failOnUnknownHost);
    }

}
