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
package org.apache.camel.component;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.chatscript.Bot.ChatScriptBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ChatScript producer.
 */
public class ChatScriptProducer extends DefaultProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(ChatScriptProducer.class);
    private ChatScriptEndpoint endpoint;
    private static  ChatScriptBot bot=null;
    public ChatScriptProducer(ChatScriptEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        bot=new ChatScriptBot(endpoint.getHostname(),
    			endpoint.getPort(),endpoint.getBotname(),
    			endpoint.getChatusername());//TODO set proxy headers for end user recognition
        bot.init(null);
        if (endpoint.isResetchat())
        {
        	bot.reset();
        }
    }

    public void process(Exchange exchange) throws Exception {
    	String chatmessage=String.valueOf(exchange.getIn().getBody());
    	String response=bot.sendChat(chatmessage);
    	exchange.getOut().setBody(response);
    }

}
