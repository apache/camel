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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.chatscript.messages.ChatScriptMessage;

/**
 * The ChatScript producer.
 */
public class ChatScriptProducer extends DefaultProducer {
    private ChatScriptEndpoint endpoint;
    private ObjectMapper mapper = new ObjectMapper();
    public ChatScriptProducer(ChatScriptEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;

        if (endpoint.isResetchat()) {
            this.endpoint.getbot().reset();
        }
    }

    public void process(Exchange exchange) throws Exception {

        Object body = exchange.getIn().getBody();
        ChatScriptMessage inputMessage = new ChatScriptMessage();
        /* use can pass message object as json or the object it self */
        if (!(body instanceof ChatScriptMessage)) {
            inputMessage = buildMessage(body);
        } else {
            inputMessage = (ChatScriptMessage) body;
        }
        String response = this.endpoint.getbot().sendChat(inputMessage);
        inputMessage.setReply(response);
        exchange.getOut().setBody(inputMessage);
    }

    private ChatScriptMessage buildMessage(Object body) {

        if (body instanceof String) {
            return createMessage(body);
        }
        return null;
    }

    private ChatScriptMessage createMessage(Object body) {
        ChatScriptMessage ret = null;
        try {
            ret = mapper.readValue(String.valueOf(body), ChatScriptMessage.class);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } 

        return ret;
    }

}