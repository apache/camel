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
import org.apache.camel.impl.DefaultProducer;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;

public class SshProducer extends DefaultProducer {
    private SshEndpoint endpoint;

    public SshProducer(SshEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ClientSession session = endpoint.createSession();

        String command = exchange.getIn().getBody(String.class);
        ClientChannel channel = session.createChannel(ClientChannel.CHANNEL_EXEC, command);

        // TODO: do I need to put command into Channel IN for CHANNEL_EXEC?
        ByteArrayInputStream in = new ByteArrayInputStream(exchange.getIn().getBody(byte[].class));
        channel.setIn(in);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOut(out);

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setErr(err);

        channel.open().await();

        channel.waitFor(ClientChannel.CLOSED, 0);
        channel.close(false);

        session.close(false);

        exchange.getOut().setBody(out.toByteArray());
    }
}
