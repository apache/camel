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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.apache.camel.RuntimeCamelException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSession;
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
                keyPairProvider = new ResourceHelperKeyPairProvider(new String[]{certResource}, endpoint.getCamelContext().getClassResolver());
            } else {
                keyPairProvider = configuration.getKeyPairProvider();
            }

            // either provide a keypair or password identity first
            if (keyPairProvider != null) {
                LOG.debug("Attempting to authenticate username '{}' using a key identity", userName);
                KeyPair pair = keyPairProvider.loadKey(configuration.getKeyType());
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
        
            channel = session.createChannel(ClientChannel.CHANNEL_EXEC, command);

            ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{0});
            channel.setIn(in);
    
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            channel.setOut(out);
    
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setErr(err);
            OpenFuture openFuture = channel.open();
            openFuture.await(configuration.getTimeout());
            SshResult result = null;
            if (openFuture.isOpened()) {
                Set<ClientChannelEvent> events = channel.waitFor(Arrays.asList(ClientChannelEvent.CLOSED), 0);
                if (!events.contains(ClientChannelEvent.TIMEOUT)) {
                    result = new SshResult(command, channel.getExitStatus(),
                            new ByteArrayInputStream(out.toByteArray()),
                            new ByteArrayInputStream(err.toByteArray()));
                }
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

}
