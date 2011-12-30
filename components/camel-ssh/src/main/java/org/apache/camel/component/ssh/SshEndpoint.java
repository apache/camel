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

import java.io.*;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.future.OpenFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an SSH endpoint.
 */
public class SshEndpoint extends ScheduledPollEndpoint {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());

    private SshClient client;
    private SshConfiguration sshConfiguration;
    private String pollCommand;

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
        return false;
    }

    public byte[] sendExecCommand(String command) throws Exception {
        byte[] result = null;

        ClientSession session = client.connect(sshConfiguration.getHost(), sshConfiguration.getPort()).await().getSession();
        session.authPassword(sshConfiguration.getUsername(), sshConfiguration.getPassword()).await().isSuccess();

        ClientChannel channel = session.createChannel(ClientChannel.CHANNEL_EXEC, command);

        ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{0});
        channel.setIn(in);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOut(out);

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setErr(err);

        OpenFuture openFuture = channel.open().await();
        if (openFuture.isOpened()) {
            int ret = channel.waitFor(ClientChannel.CLOSED, 0);
            log.info("ret = " + ret);

            //session.close(false);
            //session.waitFor(ClientSession.CLOSED, 0);

            result = out.toByteArray();
        }

        return result;
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
        if (sshConfiguration == null) {
            sshConfiguration = new SshConfiguration();
        }
        return sshConfiguration;
    }

    public void setConfiguration(SshConfiguration configuration) {
        this.sshConfiguration = configuration;
    }
}
