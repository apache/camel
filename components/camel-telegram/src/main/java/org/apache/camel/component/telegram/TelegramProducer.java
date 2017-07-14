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
package org.apache.camel.component.telegram;

import org.apache.camel.Exchange;
import org.apache.camel.component.telegram.model.OutgoingMessage;
import org.apache.camel.impl.DefaultProducer;

/**
 * A producer that sends messages to Telegram through the bot API.
 */
public class TelegramProducer extends DefaultProducer {

    private TelegramEndpoint endpoint;

    public TelegramProducer(TelegramEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        if (exchange.getIn().getBody() == null) {
            // fail fast
            log.debug("Received exchange with empty body, skipping");
            return;
        }

        TelegramConfiguration config = endpoint.getConfiguration();

        // Tries to get a message in its OutgoingMessage format
        // Automatic conversion applies here
        OutgoingMessage message = exchange.getIn().getBody(OutgoingMessage.class);
        if (message == null) {
            throw new IllegalArgumentException("Cannot convert the content to a Telegram OutgoingMessage");
        }

        if (message.getChatId() == null) {
            log.debug("Chat id is null on outgoing message, trying resolution");
            String chatId = resolveChatId(config, message, exchange);
            log.debug("Resolved chat id is {}", chatId);
            message.setChatId(chatId);
        }

        TelegramService service = TelegramServiceProvider.get().getService();

        log.debug("Message being sent is: {}", message);
        log.debug("Headers of message being sent are: {}", exchange.getIn().getHeaders());

        service.sendMessage(config.getAuthorizationToken(), message);
    }

    private String resolveChatId(TelegramConfiguration config, OutgoingMessage message, Exchange exchange) {
        String chatId;

        // Try to get the chat id from the message body
        chatId = message.getChatId();

        // Get the chat id from headers
        if (chatId == null) {
            chatId = (String) exchange.getIn().getHeader(TelegramConstants.TELEGRAM_CHAT_ID);
        }

        // If not present in the headers, use the configured value for chat id
        if (chatId == null) {
            chatId = config.getChatId();
        }

        // Chat id is mandatory
        if (chatId == null) {
            throw new IllegalStateException("Chat id is not set in message headers or route configuration");
        }

        return chatId;
    }

}
