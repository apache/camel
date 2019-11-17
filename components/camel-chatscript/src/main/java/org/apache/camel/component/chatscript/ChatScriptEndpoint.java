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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.chatscript.utils.ChatScriptConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.chatscript.utils.ChatScriptConstants.DEFAULT_PORT;

/**
 * Represents a ChatScript endpoint.
 */
@UriEndpoint(firstVersion = "3.0.0", scheme = "chatscript", title = "ChatScript", syntax = "chatscript:host:port/botName",  producerOnly = true, label = "ai,chatscript")
public class ChatScriptEndpoint extends DefaultEndpoint {

    private ChatScriptBot bot;

    @UriPath (description = "Hostname or IP of the server on which CS server is running")
    @Metadata(required = true)
    private String host;
    @UriPath(description = "Port on which ChatScript is listening to", defaultValue = "" + DEFAULT_PORT)
    private int port;
    @UriPath(description = "Name of the Bot in CS to converse with")
    @Metadata(required = true)
    private String botName;
    @UriParam(description = "Username who initializes the CS conversation. To be set when chat is initialized from camel route")
    private String chatUserName;
    @UriParam (description = "Issues :reset command to start a new conversation everytime", defaultValue = "false")
    private boolean resetChat;

    public ChatScriptEndpoint() {
    }

    public ChatScriptEndpoint(String uri, String remaining,
            ChatScriptComponent component) throws URISyntaxException {
        super(uri, component);

        URI remainingUri = new URI("tcp://" + remaining);
        port = remainingUri.getPort() == -1 ? DEFAULT_PORT : remainingUri.getPort();
        if (ObjectHelper.isEmpty(remainingUri.getPath())) {
            throw new IllegalArgumentException(ChatScriptConstants.URI_ERROR);
        }
        host = remainingUri.getHost();
        if (ObjectHelper.isEmpty(host)) { 
            throw new IllegalArgumentException(ChatScriptConstants.URI_ERROR);
        }
        botName = remainingUri.getPath();
        if (ObjectHelper.isEmpty(botName)) {
            throw new IllegalArgumentException(ChatScriptConstants.URI_ERROR);
        }
        botName = botName.substring(1);
        setBot(new ChatScriptBot(getHost(), getPort(), getBotName(), ""));

    }
    public boolean isResetChat() {
        return resetChat;
    }

    public void setResetChat(boolean resetChat) {
        this.resetChat = resetChat;
    }

    public String getChatUserName() {
        return chatUserName;
    }

    public void setChatUserName(String chatusername) {
        this.chatUserName = chatusername;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ChatScriptProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Chatscript consumer not supported");
    }

    public String getHost() {
        return host;
    }

    public void setHost(String hostName) {
        this.host = hostName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String botname) {
        this.botName = botname;
    }

    public static int getDefaultPort() {
        return DEFAULT_PORT;
    }

    public ChatScriptBot getBot() {
        return bot;
    }

    public void setBot(ChatScriptBot thisBot) {
        this.bot = thisBot;
    }
}
