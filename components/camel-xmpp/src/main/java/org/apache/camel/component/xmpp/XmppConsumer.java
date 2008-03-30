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
package org.apache.camel.component.xmpp;

import java.util.Iterator;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.RosterPacket;

/**
 * A {@link org.apache.camel.Consumer Consumer} which listens to XMPP packets
 * 
 * @version $Revision$
 */
public class XmppConsumer extends DefaultConsumer<XmppExchange> implements PacketListener {
    private static final transient Log LOG = LogFactory.getLog(XmppConsumer.class);
    private final XmppEndpoint endpoint;

    public XmppConsumer(XmppEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.getConnection().addPacketListener(this, endpoint.getFilter());
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.getConnection().removePacketListener(this);
        super.doStop();
    }

    public void processPacket(Packet packet) {

        if (packet instanceof Message) {
            Message message = (Message)packet;
            if (LOG.isDebugEnabled()) {
                LOG.debug("<<<< message: " + message.getBody());
            }
            XmppExchange exchange = endpoint.createExchange(message);
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                // TODO: what should we do when a processing failure occurs??
                e.printStackTrace();
            }
        } else if (packet instanceof RosterPacket) {
            // TODO: what to do with a RosterPacket other than debug logging it
            RosterPacket rosterPacket = (RosterPacket)packet;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Roster packet with : " + rosterPacket.getRosterItemCount() + " item(s)");
                Iterator rosterItems = rosterPacket.getRosterItems();
                while (rosterItems.hasNext()) {
                    Object item = rosterItems.next();
                    LOG.debug("Roster item: " + item);
                }
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("<<<< ignored packet: " + packet);
            }

        }
    }
}
