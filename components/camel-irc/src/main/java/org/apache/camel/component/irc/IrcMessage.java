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

import org.apache.camel.impl.DefaultMessage;
import org.schwering.irc.lib.IRCUser;

import java.util.Map;

public class IrcMessage extends DefaultMessage {
    private String messageType;
    private String target;
    private IRCUser user;
    private String whoWasKickedNick;
    private String message;

    public IrcMessage() {
    }

    public IrcMessage(String messageType, IRCUser user, String message) {
        this.messageType = messageType;
        this.user = user;
        this.message = message;
    }

    public IrcMessage(String messageType, String target, IRCUser user, String message) {
        this.messageType = messageType;
        this.target = target;
        this.user = user;
        this.message = message;
    }

    public IrcMessage(String messageType, String target, IRCUser user, String whoWasKickedNick, String message) {
        this.messageType = messageType;
        this.target = target;
        this.user = user;
        this.whoWasKickedNick = whoWasKickedNick;
        this.message = message;
    }

    public IrcMessage(String messageType, String target, IRCUser user) {
        this.messageType = messageType;
        this.target = target;
        this.user = user;
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
    public IrcExchange getExchange() {
        return (IrcExchange) super.getExchange();
    }

    @Override
    protected Object createBody() {
        IrcExchange ircExchange = getExchange();
        IrcBinding binding = ircExchange.getBinding();
        return binding.extractBodyFromIrc(ircExchange, this);
    }

    @Override
    public IrcMessage newInstance() {
        return new IrcMessage();
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        map.put("irc.messageType", messageType);
        if (target != null) {
            map.put("irc.target", target);
        }
        if (whoWasKickedNick != null) {
            map.put("irc.user.kicked", whoWasKickedNick);
        }
        if (user != null) {
            map.put("irc.user.host", user.getHost());
            map.put("irc.user.nick", user.getNick());
            map.put("irc.user.servername", user.getServername());
            map.put("irc.user.username", user.getUsername());
        }
    }

    @Override
    public String toString() {
        if (message != null) {
            return "IrcMessage: " + message;
        }
        else {
            return "IrcMessage: " + getBody();
        }
    }
}
