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
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.pubsub.packet.PubSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppPubSubProducer extends DefaultProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(XmppPrivateChatProducer.class);
    private final XmppEndpoint endpoint;
    private XMPPTCPConnection connection;

    public XmppPubSubProducer(XmppEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        LOG.debug("Creating XmppPresenceProducer");
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
            throw new RuntimeExchangeException("Cannot connect to XMPP Server: "
                    + ((connection != null) ? XmppEndpoint.getConnectionMessage(connection) : endpoint.getHost()), exchange, e);
        }

        try {
            Object body = exchange.getIn().getBody(Object.class);
            if (body instanceof PubSub) {
                PubSub pubsubpacket = (PubSub) body;
                endpoint.getBinding().populateXmppStanza(pubsubpacket, exchange);
                exchange.getIn().setHeader(XmppConstants.DOC_HEADER, pubsubpacket);
                connection.sendStanza(pubsubpacket);
            } else {
                throw new Exception("Message does not contain a pubsub packet");
            }
        } catch (XMPPException xmppe) {
            throw new RuntimeExchangeException("Cannot send XMPP pubsub: from " + endpoint.getUser()
                    + " to: " + XmppEndpoint.getConnectionMessage(connection), exchange, xmppe);
        }
    }

}
