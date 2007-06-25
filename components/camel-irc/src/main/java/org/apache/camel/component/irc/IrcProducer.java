/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventListener;

public class IrcProducer extends DefaultProducer<IrcExchange> {
    private static final transient Log log = LogFactory.getLog(IrcProducer.class);
    private IRCConnection connection;
    private IrcEndpoint endpoint;
    private IRCEventListener ircErrorLogger;

    public IrcProducer(IrcEndpoint endpoint, IRCConnection connection) {
        super(endpoint);
        this.endpoint = endpoint;
        this.connection = connection;
    }

    public void process(Exchange exchange) throws Exception {
        try {
            final String msg = exchange.getIn().getBody(String.class);
            if (isMessageACommand(msg)) {
                connection.send(msg);
            }
            else {
                final String target = endpoint.getConfiguration().getTarget();

                if (log.isDebugEnabled()) {
                    log.debug("sending to: " + target + " message: " + msg);
                }

                connection.doPrivmsg(target, msg);
            }
        }
        catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ircErrorLogger = createIrcErrorLogger();
        connection.addIRCEventListener(ircErrorLogger);

        final String target = endpoint.getConfiguration().getTarget();

        log.debug("joining: " + target);
        connection.doJoin(target);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (connection != null) {
            connection.removeIRCEventListener(ircErrorLogger);
        }
    }

    protected boolean isMessageACommand(String msg) {
        for (String command : commands) {
            if (msg.startsWith(command)) {
                return true;
            }
        }
        return false;
    }

    protected IRCEventListener createIrcErrorLogger() {
        return new IrcErrorLogger(log);
    }

    public final String[] commands = new String[]{
            "AWAY", "INVITE", "ISON", "JOIN",
            "KICK", "LIST", "NAMES", "PRIVMSG",
            "MODE", "NICK", "NOTICE", "PART",
            "PONG", "QUIT", "TOPIC", "WHO",
            "WHOIS", "WHOWAS", "USERHOST"
    };
}
