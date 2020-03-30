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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.RuntimeCamelException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SshHelper {

    protected static final Logger LOG = LoggerFactory.getLogger(SshHelper.class);

    private SshHelper() {
    }

    public static SshResult sendExecCommand(Map<String, Object> headers, String command, SshEndpoint endpoint, SshClient client) throws Exception {
        SshConfiguration configuration = endpoint.getConfiguration();

        if (configuration == null) {
            throw new IllegalStateException("Configuration must be set");
        }

        String userName = configuration.getUsername();
        Object userNameHeaderObj = headers.get(SshConstants.USERNAME_HEADER);
        if (userNameHeaderObj instanceof String) {
            userName = (String) headers.get(SshConstants.USERNAME_HEADER);
        }

        ConnectFuture connectFuture = client.connect(userName, configuration.getHost(), configuration.getPort());

        // wait getTimeout milliseconds for connect operation to complete
        connectFuture.await(configuration.getTimeout());

        if (!connectFuture.isDone() || !connectFuture.isConnected()) {
            throw new RuntimeCamelException("Failed to connect to " + configuration.getHost() + ":" + configuration.getPort() + " within timeout " + configuration.getTimeout() + "ms");
        }

        LOG.debug("Connected to {}:{}", configuration.getHost(), configuration.getPort());

        ClientChannel channel = null;
        ClientSession session = null;

        try {
            AuthFuture authResult;
            session = connectFuture.getSession();

            KeyPairProvider keyPairProvider;
            final String certResource = configuration.getCertResource();
            if (certResource != null) {
                LOG.debug("Attempting to authenticate using ResourceKey '{}'...", certResource);
                if (endpoint.getCertResourcePassword() != null) {
                    Supplier<char[]> passwordFinder = () -> endpoint.getCertResourcePassword().toCharArray();
                    keyPairProvider = new ResourceHelperKeyPairProvider(new String[]{certResource}, passwordFinder, endpoint.getCamelContext());
                } else {
                    keyPairProvider = new ResourceHelperKeyPairProvider(new String[]{certResource}, endpoint.getCamelContext());
                }
            } else {
                keyPairProvider = configuration.getKeyPairProvider();
            }

            // either provide a keypair or password identity first
            if (keyPairProvider != null) {
                LOG.debug("Attempting to authenticate username '{}' using a key identity", userName);
                KeyPair pair = null;
                // If we have no configured key type then just use the first keypair
                if (configuration.getKeyType() == null) {
                    Iterator<KeyPair> iterator = keyPairProvider.loadKeys().iterator();
                    if (iterator.hasNext()) {
                        pair = iterator.next();
                    }
                } else {
                    pair = keyPairProvider.loadKey(configuration.getKeyType());
                }

                session.addPublicKeyIdentity(pair);
            } else {
                String password = configuration.getPassword();

                Object passwordHeaderObj = headers.get(SshConstants.PASSWORD_HEADER);
                if (passwordHeaderObj instanceof String) {
                    password = (String) headers.get(SshConstants.PASSWORD_HEADER);
                }

                LOG.debug("Attempting to authenticate username '{}' using a password identity", userName);
                session.addPasswordIdentity(password);
            }

            // now start the authentication process
            authResult = session.auth();

            authResult.await(configuration.getTimeout());

            if (!authResult.isDone() || authResult.isFailure()) {
                LOG.debug("Failed to authenticate");
                throw new RuntimeCamelException("Failed to authenticate username " + configuration.getUsername());
            }

            InputStream in = null;
            PipedOutputStream reply = new PipedOutputStream();

            // for now only two channel types are supported
            // shell option is added for specific purpose for now
            // may need further maintainance for further use cases
            if (Channel.CHANNEL_EXEC.equals(endpoint.getChannelType())) {
                channel = session.createChannel(Channel.CHANNEL_EXEC, command);
                in = new ByteArrayInputStream(new byte[]{0});
            } else if (Channel.CHANNEL_SHELL.equals(endpoint.getChannelType())) {
                // PipedOutputStream and PipedInputStream both are connected to each other to create a communication pipe
                // this approach is used to send the command and evaluate the response
                channel = session.createChannel(Channel.CHANNEL_SHELL);
                in = new PipedInputStream(reply);
            }

            channel.setIn(in);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channel.setOut(out);

            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setErr(err);
            OpenFuture openFuture = channel.open();
            openFuture.await(configuration.getTimeout());
            SshResult result = null;
            if (Channel.CHANNEL_EXEC.equals(endpoint.getChannelType())) {
                if (openFuture.isOpened()) {
                    Set<ClientChannelEvent> events = channel.waitFor(Arrays.asList(ClientChannelEvent.CLOSED), 0);
                    if (!events.contains(ClientChannelEvent.TIMEOUT)) {
                        result = new SshResult(command, channel.getExitStatus(),
                                new ByteArrayInputStream(out.toByteArray()),
                                new ByteArrayInputStream(err.toByteArray()));
                    }
                }
            } else if (Channel.CHANNEL_SHELL.equals(endpoint.getChannelType())) {
                getPrompt(channel, out, endpoint);
                reply.write(command.getBytes());
                reply.write(System.lineSeparator().getBytes());
                String response = getPrompt(channel, out, endpoint);
                result = new SshResult(command, channel.getExitStatus(),
                        new ByteArrayInputStream(response.getBytes()),
                        new ByteArrayInputStream(err.toByteArray()));
            }
            return result;
        } finally {
            if (channel != null) {
                channel.close(true);
            }
            // need to make sure the session is closed
            if (session != null) {
                session.close(false);
            }
        }

    }

    private static String getPrompt(ClientChannel channel, ByteArrayOutputStream output, SshEndpoint endpoint)
        throws UnsupportedEncodingException, InterruptedException {

        while (!channel.isClosed()) {

            String response = output.toString("UTF-8");
            if (response.trim().endsWith(endpoint.getShellPrompt())) {
                output.reset();
                return SshShellOutputStringHelper.betweenBeforeLast(response, System.lineSeparator(), System.lineSeparator());
            }

            // avoid cpu burning cycles
            Thread.sleep(endpoint.getSleepForShellPrompt());
        }
        return null;
    }
}
