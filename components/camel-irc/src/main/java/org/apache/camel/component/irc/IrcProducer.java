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
package org.apache.camel.component.irc;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultProducer;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventAdapter;
import org.schwering.irc.lib.IRCUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IrcProducer extends DefaultProducer {

    public static final String[] COMMANDS = new String[] {"AWAY", "INVITE", "ISON", "JOIN", "KICK", "LIST", "NAMES",
        "PRIVMSG", "MODE", "NICK", "NOTICE", "PART", "PONG", "QUIT", "TOPIC", "WHO", "WHOIS", "WHOWAS", "USERHOST"};

    private static final Logger LOG = LoggerFactory.getLogger(IrcProducer.class);

    private IRCConnection connection;
    private IrcEndpoint endpoint;
    private IRCEventAdapter listener;

    public IrcProducer(IrcEndpoint endpoint, IRCConnection connection) {
        super(endpoint);
        this.endpoint = endpoint;
        this.connection = connection;
    }

    public void process(Exchange exchange) throws Exception {
        final String msg = exchange.getIn().getBody(String.class);
        final String targetChannel = exchange.getIn().getHeader(IrcConstants.IRC_TARGET, String.class);

        if (!connection.isConnected()) {
            throw new RuntimeCamelException("Lost connection to " + connection.getHost());
        }

        if (msg != null) {
            if (isMessageACommand(msg)) {
                LOG.debug("Sending command: {}", msg);
                connection.send(msg);
            } else if (targetChannel != null) {
                LOG.debug("Sending to: {} message: {}", targetChannel, msg);
                connection.doPrivmsg(targetChannel, msg);
            } else {
                for (IrcChannel channel : endpoint.getConfiguration().getChannels()) {
                    LOG.debug("Sending to: {} message: {}", channel, msg);
                    connection.doPrivmsg(channel.getName(), msg);
                }
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        listener = getListener();
        connection.addIRCEventListener(listener);
        endpoint.joinChannels();
    }

    @Override
    protected void doStop() throws Exception {
        if (connection != null) {
            for (IrcChannel channel : endpoint.getConfiguration().getChannels()) {
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
        if (listener == null) {
            listener = new FilteredIRCEventAdapter();
        }
        return listener;
    }

    public void setListener(IRCEventAdapter listener) {
        this.listener = listener;
    }

    class FilteredIRCEventAdapter extends IRCEventAdapter {

        @Override
        public void onKick(String channel, IRCUser user, String passiveNick, String msg) {

            // check to see if I got kick and if so rejoin if autoRejoin is on
            if (passiveNick.equals(connection.getNick()) && endpoint.getConfiguration().isAutoRejoin()) {
                endpoint.joinChannel(channel);
            }
        }


        @Override
        public void onError(int num, String msg) {
            IrcProducer.this.endpoint.handleIrcError(num, msg);
        }

    }

}
