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
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCConstants;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/irc.html">IRC Endpoint</a>
 *
 * @version 
 */
@UriEndpoint(scheme = "irc", title = "IRC", syntax = "irc:hostname:port", alternativeSyntax = "irc:username:password@hostname:port",
        consumerClass = IrcConsumer.class, label = "chat")
public class IrcEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(IrcEndpoint.class);

    @UriParam
    private IrcConfiguration configuration;
    private IrcBinding binding;
    private IrcComponent component;

    public IrcEndpoint(String endpointUri, IrcComponent component, IrcConfiguration configuration) {
        super(UnsafeUriCharactersEncoder.encode(endpointUri), component);
        this.component = component;
        this.configuration = configuration;
    }

    public boolean isSingleton() {
        return true;
    }

    public Exchange createExchange(ExchangePattern pattern) {
        Exchange exchange = super.createExchange(pattern);
        exchange.setProperty(Exchange.BINDING, getBinding());
        return exchange;
    }

    public Exchange createOnPrivmsgExchange(String target, IRCUser user, String msg) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("PRIVMSG", target, user, msg));
        return exchange;
    }

    public Exchange createOnNickExchange(IRCUser user, String newNick) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("NICK", user, newNick));
        return exchange;
    }

    public Exchange createOnQuitExchange(IRCUser user, String msg) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("QUIT", user, msg));
        return exchange;
    }

    public Exchange createOnJoinExchange(String channel, IRCUser user) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("JOIN", channel, user));
        return exchange;
    }

    public Exchange createOnKickExchange(String channel, IRCUser user, String whoWasKickedNick, String msg) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("KICK", channel, user, whoWasKickedNick, msg));
        return exchange;
    }

    public Exchange createOnModeExchange(String channel, IRCUser user, IRCModeParser modeParser) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("MODE", channel, user, modeParser.getLine()));
        return exchange;
    }

    public Exchange createOnPartExchange(String channel, IRCUser user, String msg) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("PART", channel, user, msg));
        return exchange;
    }

    public Exchange createOnReplyExchange(int num, String value, String msg) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("REPLY", num, value, msg));
        return exchange;
    }

    public Exchange createOnTopicExchange(String channel, IRCUser user, String topic) {
        Exchange exchange = createExchange();
        exchange.setIn(new IrcMessage("TOPIC", channel, user, topic));
        return exchange;
    }

    public IrcProducer createProducer() throws Exception {
        return new IrcProducer(this, component.getIRCConnection(configuration));
    }

    public IrcConsumer createConsumer(Processor processor) throws Exception {
        IrcConsumer answer = new IrcConsumer(this, processor, component.getIRCConnection(configuration));
        configureConsumer(answer);
        return answer;
    }

    public IrcComponent getComponent() {
        return component;
    }

    public void setComponent(IrcComponent component) {
        this.component = component;
    }

    public IrcBinding getBinding() {
        if (binding == null) {
            binding = new IrcBinding();
        }
        return binding;
    }

    public void setBinding(IrcBinding binding) {
        this.binding = binding;
    }

    public IrcConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(IrcConfiguration configuration) {
        this.configuration = configuration;
    }

    public void handleIrcError(int num, String msg) {
        if (IRCConstants.ERR_NICKNAMEINUSE == num) {
            handleNickInUse();
        }
    }

    private void handleNickInUse() {
        IRCConnection connection = component.getIRCConnection(configuration);
        String nick = connection.getNick() + "-";

        // hackish but working approach to prevent an endless loop. Abort after 4 nick attempts.
        if (nick.endsWith("----")) {
            LOG.error("Unable to set nick: " + nick + " disconnecting");
        } else {
            LOG.warn("Unable to set nick: " + nick + " Retrying with " + nick + "-");
            connection.doNick(nick);
            // if the nick failure was doing startup channels weren't joined. So join
            // the channels now. It's a no-op if the channels are already joined.
            joinChannels();
        }
    }

    public void joinChannels() {
        for (IrcChannel channel : configuration.getChannels()) {
            joinChannel(channel);
        }
    }

    public void joinChannel(String name) {
        joinChannel(configuration.findChannel(name));
    }

    public void joinChannel(IrcChannel channel) {
        if (channel == null) {
            return;
        }

        IRCConnection connection = component.getIRCConnection(configuration);

        String chn = channel.getName();
        String key = channel.getKey();

        if (ObjectHelper.isNotEmpty(key)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Joining: {} using {} with secret key", channel, connection.getClass().getName());
            }
            connection.doJoin(chn, key);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Joining: {} using {}", channel, connection.getClass().getName());
            }
            connection.doJoin(chn);
        }
    }
}

