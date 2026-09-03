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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * The ChatScript producer.
 */
public class ChatScriptProducer extends DefaultProducer {
    private ChatScriptEndpoint endpoint;
    private ObjectMapper mapper = new ObjectMapper();

    public ChatScriptProducer(ChatScriptEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Start a fresh conversation on every exchange when requested (resetChat).
        if (endpoint.isResetChat()) {
            endpoint.getBot().reset();
        }

        Object body = exchange.getIn().getBody();
        ChatScriptMessage inputMessage;
        /* use can pass message object as json or the object it self */
        if (!(body instanceof ChatScriptMessage)) {
            inputMessage = buildMessage(body);
        } else {
            inputMessage = (ChatScriptMessage) body;
        }
        inputMessage.setBotName(endpoint.getBotName());
        // Honour the configured chatUserName as the conversation user when the message does not carry one.
        if (ObjectHelper.isEmpty(inputMessage.getUserName())) {
            inputMessage.setUserName(endpoint.getBot().getUserName());
        }
        String response = this.endpoint.getBot().sendChat(inputMessage);
        inputMessage.setReply(response);
        exchange.getOut().setBody(inputMessage);
    }

    private ChatScriptMessage buildMessage(Object body) throws Exception {
        if (body instanceof String) {
            return createMessage(String.valueOf(body));
        }
        throw new IllegalArgumentException(
                "Unsupported ChatScript body type: " + (body == null ? "null" : body.getClass().getName())
                                           + ". The body must be a ChatScriptMessage or a String (its JSON form).");
    }

    private ChatScriptMessage createMessage(String message) throws Exception {
        ChatScriptMessage ret = null;
        try {
            ret = mapper.readValue(message, ChatScriptMessage.class);
        } catch (Exception e) {
            throw new Exception("Unable to parse the input message. Error Message" + e.getMessage(), e);
        }
        return ret;
    }

}
