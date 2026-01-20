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
package org.apache.camel.component.telegram.model.payments;

import java.io.Serial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.camel.component.telegram.model.Chat;
import org.apache.camel.component.telegram.model.Gift;

/**
 * Describes a transaction with a chat.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#transactionpartnerchat">https://core.telegram.org/bots/api#transactionpartnerchat</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionPartnerChat extends TransactionPartner {

    @Serial
    private static final long serialVersionUID = 3115200430210876874L;

    /**
     * Information about the chat.
     */
    private Chat chat;

    /**
     * The gift sent to the chat by the bot.
     */
    private Gift gift;

    public TransactionPartnerChat() {
        super("chat");
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public Gift getGift() {
        return gift;
    }

    public void setGift(Gift gift) {
        this.gift = gift;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TransactionPartnerChat{");
        sb.append("type='").append(getType()).append('\'');
        sb.append(", chat=").append(chat);
        sb.append(", gift=").append(gift);
        sb.append('}');
        return sb.toString();
    }
}
