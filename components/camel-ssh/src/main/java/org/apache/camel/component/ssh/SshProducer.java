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

import java.util.Map;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.sshd.client.SshClient;

public class SshProducer extends DefaultProducer {
    private SshEndpoint endpoint;
    private SshClient client;

    public SshProducer(SshEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        client = SshClient.setUpDefaultClient();
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
    public boolean isSingleton() {
        // SshClient is not thread-safe to be shared
        return true;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        String command = in.getMandatoryBody(String.class);

        final Map<String, Object> headers = exchange.getIn().getHeaders();

        try {
            String knownHostResource = endpoint.getKnownHostsResource();
            if (knownHostResource != null) {
                client.setServerKeyVerifier(new ResourceBasedSSHKeyVerifier(exchange.getContext(), knownHostResource,
                        endpoint.isFailOnUnknownHost()));
            }
            SshResult result = SshHelper.sendExecCommand(headers, command, endpoint, client);
            exchange.getOut().setBody(result.getStdout());
            exchange.getOut().setHeader(SshResult.EXIT_VALUE, result.getExitValue());
            exchange.getOut().setHeader(SshResult.STDERR, result.getStderr());
        } catch (Exception e) {
            throw new CamelExchangeException("Cannot execute command: " + command, exchange, e);
        }

        // propagate headers
        exchange.getOut().getHeaders().putAll(in.getHeaders());
    }
}
