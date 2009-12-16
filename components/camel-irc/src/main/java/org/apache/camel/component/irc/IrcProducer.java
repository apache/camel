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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.schwering.irc.lib.IRCConnection;

public class IrcProducer extends DefaultProducer {

    public static final String[] COMMANDS = new String[]{"AWAY", "INVITE", "ISON", "JOIN", "KICK", "LIST", "NAMES",
        "PRIVMSG", "MODE", "NICK", "NOTICE", "PART", "PONG", "QUIT", "TOPIC", "WHO", "WHOIS", "WHOWAS", "USERHOST"};

    private static final transient Log LOG = LogFactory.getLog(IrcProducer.class);

    private IRCConnection connection;
    private IrcEndpoint endpoint;

    public IrcProducer(IrcEndpoint endpoint, IRCConnection connection) {
        super(endpoint);
        this.endpoint = endpoint;
        this.connection = connection;
    }

    public void process(Exchange exchange) throws Exception {
        final String msg = exchange.getIn().getBody(String.class);
        final String targetChannel = exchange.getIn().getHeader(IrcConstants.IRC_TARGET, String.class);
        if (isMessageACommand(msg)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending command: " + msg);
            }
            connection.send(msg);
        } else if (targetChannel != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending to: " + targetChannel + " message: " + msg);
            }
            connection.doPrivmsg(targetChannel, msg);
        } else {
            for (String channel : endpoint.getConfiguration().getChannels()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending to: " + channel + " message: " + msg);
                }
                connection.doPrivmsg(channel, msg);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        List<String> channels = endpoint.getConfiguration().getChannels();
        for (String channel : endpoint.getConfiguration().getChannels()) {

            // find key for channel
            int ndx = channels.indexOf(channel);
            String key = null;
            if (ndx >= 0) {
                List<String> keys = endpoint.getConfiguration().getKeys();
                if (keys.size() > 0 && ndx < keys.size()) {
                    key = keys.get(ndx);
                }
            }

            if (ObjectHelper.isNotEmpty(key)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Joining: " + channel + " using " + connection.getClass().getName() + " with key " + key);
                }
                connection.doJoin(channel, key);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Joining: " + channel + " using " + connection.getClass().getName());
                }
                connection.doJoin(channel);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (connection != null) {
            for (String channel : endpoint.getConfiguration().getChannels()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Parting: " + channel);
                }
                connection.doPart(channel);
            }
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

}
