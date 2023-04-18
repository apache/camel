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
package org.apache.camel.component.irc;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultProducer;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventAdapter;
import org.schwering.irc.lib.IRCUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IrcProducer extends DefaultProducer {

    public static final String[] COMMANDS = new String[] {
            "AWAY", "INVITE", "ISON", "JOIN", "KICK", "LIST", "NAMES",
            "PRIVMSG", "MODE", "NICK", "NOTICE", "PART", "PONG", "QUIT", "TOPIC", "WHO", "WHOIS", "WHOWAS", "USERHOST" };

    private static final Logger LOG = LoggerFactory.getLogger(IrcProducer.class);

    private transient IRCConnection connection;
    private IRCEventAdapter listener = new FilteredIRCEventAdapter();

    public IrcProducer(IrcEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public IrcEndpoint getEndpoint() {
        return (IrcEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final String msg = exchange.getIn().getBody(String.class);
        final String sendTo = exchange.getIn().getHeader(IrcConstants.IRC_SEND_TO, String.class);

        if (connection == null || !connection.isConnected()) {
            reconnect();
        }
        if (connection == null || !connection.isConnected()) {
            throw new RuntimeCamelException("Lost connection" + (connection == null ? "" : " to " + connection.getHost()));
        }

        if (msg != null) {
            if (isMessageACommand(msg)) {
                LOG.debug("Sending command: {}", msg);
                connection.send(msg);
            } else if (sendTo != null) {
                LOG.debug("Sending to: {} message: {}", sendTo, msg);
                connection.doPrivmsg(sendTo, msg);
            } else {
                for (IrcChannel channel : getEndpoint().getConfiguration().getChannelList()) {
                    LOG.debug("Sending to: {} message: {}", channel, msg);
                    connection.doPrivmsg(channel.getName(), msg);
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        reconnect();
    }

    protected void reconnect() {
        // create new connection
        if (connection == null || connection.isConnected()) {
            connection = getEndpoint().getComponent().getIRCConnection(getEndpoint().getConfiguration());
        } else {
            // reconnecting
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reconnecting to {}:{}", getEndpoint().getConfiguration().getHostname(),
                        getEndpoint().getConfiguration().getNickname());
            }
            getEndpoint().getComponent().closeConnection(connection);
            connection = getEndpoint().getComponent().getIRCConnection(getEndpoint().getConfiguration());
        }
        connection.addIRCEventListener(listener);
        LOG.debug("Sleeping for {} seconds before sending commands.",
                getEndpoint().getConfiguration().getCommandTimeout() / 1000);
        // sleep for a few seconds as the server sometimes takes a moment to fully connect, print banners, etc after connection established
        try {
            Thread.sleep(getEndpoint().getConfiguration().getCommandTimeout());
        } catch (InterruptedException ex) {
            // ignore
        }
        getEndpoint().joinChannels();
    }

    @Override
    protected void doStop() throws Exception {
        if (connection != null) {
            for (IrcChannel channel : getEndpoint().getConfiguration().getChannelList()) {
                LOG.debug("Parting: {}", channel);
                connection.doPart(channel.getName());
            }
            connection.removeIRCEventListener(listener);
        }
        super.doStop();
    }

    protected boolean isMessageACommand(String msg) {
        for (String command : COMMANDS) {
            if (msg.startsWith(command)) {
                return true;
            }
        }
        return false;
    }

    public IRCEventAdapter getListener() {
        return listener;
    }

    public void setListener(IRCEventAdapter listener) {
        this.listener = listener;
    }

    class FilteredIRCEventAdapter extends IRCEventAdapter {

        @Override
        public void onKick(String channel, IRCUser user, String passiveNick, String msg) {

            // check to see if I got kick and if so rejoin if autoRejoin is on
            if (passiveNick.equals(connection.getNick()) && getEndpoint().getConfiguration().isAutoRejoin()) {
                getEndpoint().joinChannel(channel);
            }
        }

        @Override
        public void onError(int num, String msg) {
            IrcProducer.this.getEndpoint().handleIrcError(num);
        }

    }

}
