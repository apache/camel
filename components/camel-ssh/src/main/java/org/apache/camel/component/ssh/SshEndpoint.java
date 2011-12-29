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
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;

/**
 * Represents an SSH endpoint.
 */
public class SshEndpoint extends ScheduledPollEndpoint {
    private SshClient client;
    private SshConfiguration configuration;
    private String pollCommand;

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
    public Producer createProducer() throws Exception {
        return new SshProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new SshConsumer(this, processor);
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    ClientSession createSession() throws Exception {
        ClientSession session = client.connect(configuration.getHost(), configuration.getPort()).await().getSession();
        session.authPassword(configuration.getUsername(), configuration.getPassword()).await().isSuccess();

        return session;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        client = SshClient.setUpDefaultClient();
        client.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.stop();
            client = null;
        }

        super.doStop();
    }

    public String getPollCommand() {
        return pollCommand;
    }

    public void setPollCommand(String pollCommand) {
        this.pollCommand = pollCommand;
    }

    public SshConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new SshConfiguration();
        }
        return configuration;
    }

    public void setConfiguration(SshConfiguration configuration) {
        this.configuration = configuration;
    }
}
