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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execute commands on remote hosts using SSH.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "ssh", title = "SSH", syntax = "ssh:host:port",
             alternativeSyntax = "ssh:username:password@host:port", category = { Category.FILE })
public class SshEndpoint extends ScheduledPollEndpoint {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @UriParam
    private SshConfiguration configuration;

    public SshEndpoint() {
    }

    public SshEndpoint(String uri, SshComponent component) {
        super(uri, component);
    }

    public SshEndpoint(String uri, SshComponent component, SshConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public boolean isSingletonProducer() {
        // SshClient is not thread-safe to be shared
        return false;
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

    public SshConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(SshConfiguration configuration) {
        this.configuration = configuration;
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

    public String getCertResource() {
        return getConfiguration().getCertResource();
    }

    public void setCertResource(String certResource) {
        getConfiguration().setCertResource(certResource);
    }

    public String getCertResourcePassword() {
        return getConfiguration().getCertResourcePassword();
    }

    public void setCertResourcePassword(String certResourcePassword) {
        getConfiguration().setCertResourcePassword(certResourcePassword);
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

    public String getChannelType() {
        return getConfiguration().getChannelType();
    }

    public void setChannelType(String channelType) {
        getConfiguration().setChannelType(channelType);
    }

    public String getShellPrompt() {
        return getConfiguration().getShellPrompt();
    }

    public void setShellPrompt(String shellPrompt) {
        getConfiguration().setShellPrompt(shellPrompt);
    }

    public long getSleepForShellPrompt() {
        return getConfiguration().getSleepForShellPrompt();
    }

    public void setSleepForShellPrompt(long sleepForShellPrompt) {
        getConfiguration().setSleepForShellPrompt(sleepForShellPrompt);
    }

}
