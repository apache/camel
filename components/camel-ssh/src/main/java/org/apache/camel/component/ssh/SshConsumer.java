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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;

public class SshConsumer extends ScheduledPollConsumer {
    private final SshEndpoint endpoint;

    public SshConsumer(SshEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected int poll() throws Exception {
        ClientSession session = endpoint.createSession();

        ClientChannel channel = session.createChannel(ClientChannel.CHANNEL_EXEC, endpoint.getPollCommand());

        // TODO: do I need to put command into Channel IN for CHANNEL_EXEC?
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(baos);
        w.append(endpoint.getPollCommand());
        w.close();
        ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
        channel.setIn(in);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOut(out);

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setErr(err);

        channel.open().await();

        channel.waitFor(ClientChannel.CLOSED, 0);
        channel.close(false);

        session.close(false);

        Exchange exchange = endpoint.createExchange();

        // create a message body
        exchange.getIn().setBody(out.toByteArray());

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
