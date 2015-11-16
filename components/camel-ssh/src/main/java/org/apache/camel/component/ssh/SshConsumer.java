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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.sshd.SshClient;

public class SshConsumer extends ScheduledPollConsumer {
    private final SshEndpoint endpoint;
    
    private SshClient client;

    public SshConsumer(SshEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
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

    @Override
    protected int poll() throws Exception {
        String command = endpoint.getPollCommand();
        SshResult result = SshHelper.sendExecCommand(command, endpoint, client);

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(result.getStdout());
        exchange.getIn().setHeader(SshResult.EXIT_VALUE, result.getExitValue());
        exchange.getIn().setHeader(SshResult.STDERR, result.getStderr());

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
            return 1; // number of messages polled
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
