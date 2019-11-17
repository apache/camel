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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatScriptBot {

    private static final transient Logger LOG = LoggerFactory.getLogger(ChatScriptBot.class);
    String host;
    int port;
    String message;
    String botName;
    String userName;
    boolean initialized;

    public ChatScriptBot(String iHost, int port, String iBotName, String iUserName) {
        this.host = iHost;
        this.port = port;
        this.botName = iBotName;
        this.userName = iUserName;
    }

    public String sendChat(String input) throws Exception {
        if (!initialized) {
            return init(null);
        }
        ChatScriptMessage g = new ChatScriptMessage(this.userName, this.botName, input);
        return doMessage(g.toCSFormat());
    }

    public String sendChat(ChatScriptMessage input) throws Exception {
        if (!initialized) {
            return init(input);
        }
        return doMessage(input.toCSFormat());
    }

    private String doMessage(ChatScriptMessage msg) throws Exception {
        return doMessage(msg.toCSFormat());
    }

    private String doMessage(String msg) throws Exception {
        Socket echoSocket;
        String resp = "";

        try {
            echoSocket = new Socket(this.host, this.port);
            PrintWriter out = new PrintWriter(echoSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
            out.println(msg);
            resp = in.readLine();
            echoSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new Exception("Unable to send message to ChatScript Server. Reason:" + e.getMessage());
        }

        return resp;

    }

    public String init(ChatScriptMessage input) throws Exception {
        ChatScriptMessage g = new ChatScriptMessage(input.getUserName(), this.botName, null);
        String response = doMessage(g);
        LOG.info("Conversation started between the bot " + this.botName + " and " + input.getUserName());
        initialized = true;
        return response;
    }

    public String getBotType() {
        return "ChatSCript";
    }

    public void reset() {
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int iPort) {
        this.port = iPort;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String iMessage) {
        this.message = iMessage;
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String iBotName) {
        this.botName = iBotName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String iUserName) {
        this.userName = iUserName;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

}
