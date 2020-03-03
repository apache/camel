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
package org.apache.camel.component.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an incoming callback query from a callback button in an inline keyboard.
 *
 * @see <a href="https://core.telegram.org/bots/api#callbackquery">https://core.telegram.org/bots/api#callbackquery</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingCallbackQuery {

    private String id;

    private User from;

    private IncomingMessage message;

    @JsonProperty("inline_message_id")
    private String inlineMessageId;

    @JsonProperty("chat_instance")
    private String chatInstance;

    private String data;

    @JsonProperty("game_short_name")
    private String gameShortName;

    public IncomingCallbackQuery() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public User getFrom() {
        return from;
    }

    public void setFrom(User from) {
        this.from = from;
    }

    public IncomingMessage getMessage() {
        return message;
    }

    public void setMessage(IncomingMessage message) {
        this.message = message;
    }

    public String getInlineMessageId() {
        return inlineMessageId;
    }

    public void setInlineMessageId(String inlineMessageId) {
        this.inlineMessageId = inlineMessageId;
    }

    public String getChatInstance() {
        return chatInstance;
    }

    public void setChatInstance(String chatInstance) {
        this.chatInstance = chatInstance;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getGameShortName() {
        return gameShortName;
    }

    public void setGameShortName(String gameShortName) {
        this.gameShortName = gameShortName;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IncomingCallbackQuery{");
        sb.append("id='").append(id).append('\'');
        sb.append(", from=").append(from);
        sb.append(", message=").append(message);
        sb.append(", inlineMessageId='").append(inlineMessageId).append('\'');
        sb.append(", chatInstance='").append(chatInstance).append('\'');
        sb.append(", data='").append(data).append('\'');
        sb.append(", gameShortName='").append(gameShortName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
