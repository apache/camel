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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ExchangeHelper;
import org.schwering.irc.lib.IRCUser;

public class IrcMessage extends DefaultMessage {
    private String messageType;
    private String target;
    private IRCUser user;
    private String whoWasKickedNick;
    private String message;
    private int num;
    private String value;

    public IrcMessage(CamelContext camelContext) {
        super(camelContext);
    }

    public IrcMessage(CamelContext camelContext, String messageType, IRCUser user, String message) {
        super(camelContext);
        this.messageType = messageType;
        this.user = user;
        this.message = message;
        setBody(message);
    }

    public IrcMessage(CamelContext camelContext, String messageType, String target, IRCUser user, String message) {
        super(camelContext);
        this.messageType = messageType;
        this.target = target;
        this.user = user;
        this.message = message;
        setBody(message);
    }

    public IrcMessage(CamelContext camelContext, String messageType, String target, IRCUser user, String whoWasKickedNick, String message) {
        super(camelContext);
        this.messageType = messageType;
        this.target = target;
        this.user = user;
        this.whoWasKickedNick = whoWasKickedNick;
        this.message = message;
        setBody(message);
    }

    public IrcMessage(CamelContext camelContext, String messageType, String target, IRCUser user) {
        super(camelContext);
        this.messageType = messageType;
        this.target = target;
        this.user = user;
    }

    public IrcMessage(CamelContext camelContext, String messageType, int num, String value, String message) {
        super(camelContext);
        this.messageType = messageType;
        this.num = num;
        this.value = value;
        this.message = message;
        setBody(message);
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public IRCUser getUser() {
        return user;
    }

    public void setUser(IRCUser user) {
        this.user = user;
    }

    public String getWhoWasKickedNick() {
        return whoWasKickedNick;
    }

    public void setWhoWasKickedNick(String whoWasKickedNick) {
        this.whoWasKickedNick = whoWasKickedNick;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    protected Object createBody() {
        Exchange exchange = getExchange();
        IrcBinding binding = ExchangeHelper.getBinding(getExchange(), IrcBinding.class);
        return binding != null ? binding.extractBodyFromIrc(exchange, this) : null;
    }

    @Override
    public IrcMessage newInstance() {
        return new IrcMessage(getCamelContext());
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        map.put(IrcConstants.IRC_MESSAGE_TYPE, messageType);
        if (target != null) {
            map.put(IrcConstants.IRC_TARGET, target);
        }
        if (whoWasKickedNick != null) {
            map.put(IrcConstants.IRC_USER_KICKED, whoWasKickedNick);
        }
        if (user != null) {
            map.put(IrcConstants.IRC_USER_HOST, user.getHost());
            map.put(IrcConstants.IRC_USER_NICK, user.getNick());
            map.put(IrcConstants.IRC_USER_SERVERNAME, user.getServername());
            map.put(IrcConstants.IRC_USER_USERNAME, user.getUsername());
        }
        if (value != null) {
            map.put(IrcConstants.IRC_NUM, num);
            map.put(IrcConstants.IRC_VALUE, value);
        }
    }

    @Override
    public String toString() {
        if (message != null) {
            return "IrcMessage: " + message;
        } else {
            return "IrcMessage: " + getBody();
        }
    }
}
