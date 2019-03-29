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
package org.apache.camel.component.chatscript;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatScriptMessage {
    @JsonProperty("username")
    private String username;
    @JsonProperty("botname")
    private String botname;
    @JsonProperty("message")
    private String body;
    @JsonProperty("response")
    private String reply;
    public ChatScriptMessage(final String userName, final String botName, final String iBody) {
        this.username = userName;
        this.botname = botName;
        this.body = iBody;
    }
    public ChatScriptMessage() {

    }

    public String getUserName() {
        return username;
    }

    public void setUserName(String userName) {
        this.username = userName;
    }

    public String getBotName() {
        return botname;
    }

    public void setBotName(String botName) {
        this.botname = botName;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String iBody) {
        this.body = iBody;
    }
    public String getReply() {
        return reply;
    }
    public void setReply(String iReply) {
        this.reply = iReply;
    }
    public String toCSFormat() {
        String s;
        final char nullChar = (char) 0;
        s = this.username + nullChar + this.botname + nullChar + this.body + nullChar;
        return s;
    }
    @Override
    public String toString() {
        return "ChatScriptMessage [username=" + username + ", botname=" + botname + ", message=" + body + ", reply=" + reply + "]";
    }


}