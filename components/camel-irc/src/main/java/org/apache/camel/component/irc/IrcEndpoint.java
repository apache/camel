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

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;

/**
 * Defines the <a href="http://activemq.apache.org/camel/irc.html">IRC Endpoint</a>
 *
 * @version $Revision:$
 */
public class IrcEndpoint extends DefaultEndpoint<IrcExchange> {
    private IrcBinding binding;
    private IrcConfiguration configuration;
    private IrcComponent component;

    public IrcEndpoint(String endpointUri, IrcComponent component, IrcConfiguration configuration) {
        super(endpointUri, component);
        this.component = component;
        this.configuration = configuration;
    }

    public boolean isSingleton() {
        return true;
    }

    public IrcExchange createExchange() {
        return new IrcExchange(getContext(), getBinding());
    }

    public IrcExchange createOnPrivmsgExchange(String target, IRCUser user, String msg) {
        return new IrcExchange(getContext(), getBinding(), new IrcMessage("PRIVMSG", target, user, msg));
    }

    public IrcExchange createOnNickExchange(IRCUser user, String newNick) {
        return new IrcExchange(getContext(), getBinding(), new IrcMessage("NICK", user, newNick));
    }

    public IrcExchange createOnQuitExchange(IRCUser user, String msg) {
        return new IrcExchange(getContext(), getBinding(), new IrcMessage("QUIT", user, msg));
    }

    public IrcExchange createOnJoinExchange(String channel, IRCUser user) {
        return new IrcExchange(getContext(), getBinding(), new IrcMessage("JOIN", channel, user));
    }

    public IrcExchange createOnKickExchange(String channel, IRCUser user, String whoWasKickedNick, String msg) {
        return new IrcExchange(getContext(), getBinding(), new IrcMessage("KICK", channel, user, whoWasKickedNick, msg));
    }

    public IrcExchange createOnModeExchange(String channel, IRCUser user, IRCModeParser modeParser) {
        return new IrcExchange(getContext(), getBinding(), new IrcMessage("MODE", channel, user, modeParser.getLine()));
    }

    public IrcExchange createOnPartExchange(String channel, IRCUser user, String msg) {
        return new IrcExchange(getContext(), getBinding(), new IrcMessage("PART", channel, user, msg));
    }

    public IrcExchange createOnTopicExchange(String channel, IRCUser user, String topic) {
        return new IrcExchange(getContext(), getBinding(), new IrcMessage("TOPIC", channel, user, topic));
    }

    public IrcProducer createProducer() throws Exception {
        return new IrcProducer(this, component.getIRCConnection(configuration));
    }

    public IrcConsumer createConsumer(Processor processor) throws Exception {
        return new IrcConsumer(this, processor, component.getIRCConnection(configuration));
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
}

