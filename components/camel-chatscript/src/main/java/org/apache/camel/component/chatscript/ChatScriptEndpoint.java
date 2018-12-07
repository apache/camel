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
package org.apache.camel.component.chatscript;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.component.chatscript.utils.ChatScriptConstants.*;

/**
 * Represents a ChatScript endpoint.
 */
@UriEndpoint(firstVersion = "2.24.0", scheme = "chatscript", title = "ChatScript", syntax = "chatscript:host:port/botname",  producerOnly = true, label = "ai,chatscript")
public class ChatScriptEndpoint extends DefaultEndpoint { 
    private static final String URI_ERROR = "Invalid URI. Format must be of the form chatscript://host[:port]/botname?[options...]";
    @UriPath (description = "Hostname or IP of the server on which CS server is running") 
    @Metadata(required = "true")
    private String host;
    @UriPath(description = "Port on which ChatScript is listening to", defaultValue = "" + DEFAULT_PORT)
    private int port;
    @UriPath(description = "Name of the Bot in CS to converse with")
    @Metadata(required = "true")
    private String botname;
    @UriParam(description = "Username who initializes the CS conversation. To be set when chat is initialized from camel route", label = "username")
    private String chatusername;
    @UriParam (description = "Issues :reset command to start a new conversation everytime", label = "reset", defaultValue = "false")
    private boolean resetchat;
    private ChatScriptBot bot;

    public ChatScriptEndpoint(String endpointUri) {
        super(endpointUri);
    }
    public ChatScriptEndpoint() {
    }

    public ChatScriptEndpoint(String uri, String remaining,
            ChatScriptComponent component) throws URISyntaxException {
        super(uri, component);

        URI remainingUri = new URI("tcp://" + remaining);
        port = remainingUri.getPort() == -1 ? DEFAULT_PORT : remainingUri.getPort();
        if (ObjectHelper.isEmpty(remainingUri.getPath())) {
            throw new IllegalArgumentException(URI_ERROR);
        }
        host = remainingUri.getHost();
        if (ObjectHelper.isEmpty(host)) { 
            throw new IllegalArgumentException(URI_ERROR);
        }
        botname = remainingUri.getPath();
        if (ObjectHelper.isEmpty(botname)) {
            throw new IllegalArgumentException(URI_ERROR);
        }
        botname = botname.substring(1);
        setbot(new ChatScriptBot(getHostName(), getPort(), getBotName(), ""));

    }
    public boolean isResetChat() {
        return resetchat;
    }

    public void setResetchat(boolean resetChat) {
        this.resetchat = resetChat;
    }

    public String getChatUserName() {
        return chatusername;
    }

    public void setChatUserName(String chatusername) {
        this.chatusername = chatusername;
    }

    public Producer createProducer() throws Exception {
        return new ChatScriptProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Chatscript consumer not supported");
    }

    public boolean isSingleton() {
        return true;
    }

    public String getHostName() {
        return host;
    }

    public void setHostName(String hostName) {
        this.host = hostName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBotName() {
        return botname;
    }

    public void setBotName(String botname) {
        this.botname = botname;
    }

    public static int getDefaultPort() {
        return DEFAULT_PORT;
    }

    public ChatScriptBot getBot() {
        return bot;
    }

    public void setbot(ChatScriptBot thisBot) {
        this.bot = thisBot;
    }
}