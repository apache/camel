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
    int port = 1024;
    String message;
    String botname;
    String username;
    boolean initialized;

    public ChatScriptBot(String ihost, int port, String ibotname, String iusername) {
        this.host = ihost;
        this.port = port;
        this.botname = ibotname;
        this.username = iusername;
    }

    public String sendChat(String input) {
        if (!initialized) {
            return init(null);
        }
        ChatScriptMessage g = new ChatScriptMessage(this.username, this.botname, input);
        return doMessage(g.toCSFormat());
    }

    public String sendChat(ChatScriptMessage input) {
        if (!initialized) {
            return init(input);
        }
        return doMessage(input.toCSFormat());
    }

    private String doMessage(ChatScriptMessage msg) {
        return doMessage(msg.toCSFormat());
    }

    private String doMessage(String string) {
        Socket echoSocket;
        String resp = "";

        try {
            echoSocket = new Socket(this.host, this.port);
            PrintWriter out = new PrintWriter(echoSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
            out.println(string);
            resp = in.readLine();
            echoSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            LOG.error("Error: " + e.getMessage());
        }

        return resp;

    }

    public String init(ChatScriptMessage input) {
        ChatScriptMessage g = new ChatScriptMessage(input.getUsername(), this.botname, null);
        String response = doMessage(g);
        LOG.info("Conversation started between the bot " + this.botname + " and " + input.getUsername());
        initialized = true;
        return response;
    }

    public String getBotType() {
        return "ChatSCript";
    }

    public void reset() {
        // Message g=new Message("xx",this.botname,":reset");
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

    public void setPort(int port) {
        this.port = port;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBotname() {
        return botname;
    }

    public void setBotname(String botname) {
        this.botname = botname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

}