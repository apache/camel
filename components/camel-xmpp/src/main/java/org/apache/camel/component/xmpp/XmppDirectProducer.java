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
package org.apache.camel.component.xmpp;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.support.DefaultProducer;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppDirectProducer extends DefaultProducer {

    private static final transient Logger LOG = LoggerFactory.getLogger(XmppDirectProducer.class);

    private final XmppEndpoint endpoint;

    private XMPPTCPConnection connection;

    public XmppDirectProducer(XmppEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        try {
            if (connection == null) {
                connection = endpoint.createConnection();
            }

            // make sure we are connected
            if (!connection.isConnected()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Reconnecting to: {}", XmppEndpoint.getConnectionMessage(connection));
                }

                connection.connect();
            }
        } catch (XMPPException e) {
            throw new RuntimeExchangeException(
                    "Cannot connect to XMPP Server: "
                                               + ((connection != null)
                                                       ? XmppEndpoint.getConnectionMessage(connection) : endpoint.getHost()),
                    exchange, e);
        }

        try {
            Object body = exchange.getIn().getBody();
            if (body instanceof Stanza) {
                connection.sendStanza((Stanza) body);

            } else if (body instanceof Stanza[]) {
                final Stanza[] packets = (Stanza[]) body;
                for (final Stanza packet : packets) {
                    connection.sendStanza(packet);
                }

            } else {
                throw new Exception("Body does not contain Stanza/Stanza[] object(s)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeExchangeException(
                    "Interrupted while sending XMPP direct: from " + endpoint.getUser() + " to: "
                                               + XmppEndpoint.getConnectionMessage(connection),
                    exchange, e);

        } catch (Exception e) {
            throw new RuntimeExchangeException(
                    "Cannot send XMPP direct: from " + endpoint.getUser() + " to: "
                                               + XmppEndpoint.getConnectionMessage(connection),
                    exchange, e);
        }
    }
}
