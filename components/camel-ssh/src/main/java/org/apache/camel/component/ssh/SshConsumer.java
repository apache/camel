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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.sshd.client.SshClient;

import static org.apache.camel.component.ssh.SshUtils.*;

public class SshConsumer extends ScheduledPollConsumer {
    private final SshEndpoint endpoint;

    private SshClient client;

    public SshConsumer(SshEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        if (this.endpoint.getConfiguration() == null || this.endpoint.getConfiguration().getClientBuilder() == null) {
            client = SshClient.setUpDefaultClient();
        } else {
            client = this.endpoint.getConfiguration().getClientBuilder().build(true);
        }
        SshConfiguration configuration = endpoint.getConfiguration();
        configureAlgorithms(configuration, client);

        client.start();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (client != null) {
            client.stop();
            client = null;
        }
    }

    @Override
    protected int poll() throws Exception {
        if (!isRunAllowed()) {
            return 0;
        }

        String command = endpoint.getPollCommand();
        Exchange exchange = createExchange(false);
        try {
            String knownHostResource = endpoint.getKnownHostsResource();
            if (knownHostResource != null) {
                client.setServerKeyVerifier(new ResourceBasedSSHKeyVerifier(
                        exchange.getContext(), knownHostResource,
                        endpoint.isFailOnUnknownHost()));
            }

            SshResult result = SshHelper.sendExecCommand(exchange.getIn().getHeaders(), command, endpoint, client);

            exchange.getIn().setBody(result.getStdout());
            exchange.getIn().setHeader(SshConstants.EXIT_VALUE, result.getExitValue());
            exchange.getIn().setHeader(SshConstants.STDERR, result.getStderr());

            // send message to next processor in the route
            getProcessor().process(exchange);
            return 1; // number of messages polled
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
            releaseExchange(exchange, false);
        }
    }
}
