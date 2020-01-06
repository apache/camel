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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Used to get data for high score tables.
 * Will return the score of the specified user and several of his neighbors in a game.
 * On success, returns a {@link MessageResultGameScores} object.
 *
 * @see <a href="https://core.telegram.org/bots/api#getgamehighscores">
 *     https://core.telegram.org/bots/api#getgamehighscores</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutgoingGetGameHighScoresMessage extends OutgoingMessage {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("message_id")
    private Long messageId;

    @JsonProperty("inline_message_id")
    private String inlineMessageId;

    /**
     * Builds {@link OutgoingGetGameHighScoresMessage} instance.
     *
     * @param userId             User identifier
     * @param messageId          Required if inline_message_id is not specified. Identifier of the sent message
     * @param inlineMessageId    Required if chat_id and message_id are not specified. Identifier of the inline message
     */
    public OutgoingGetGameHighScoresMessage(Long userId, Long messageId, String inlineMessageId) {
        this.userId = userId;
        this.messageId = messageId;
        this.inlineMessageId = inlineMessageId;
    }

    public OutgoingGetGameHighScoresMessage() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getInlineMessageId() {
        return inlineMessageId;
    }

    public void setInlineMessageId(String inlineMessageId) {
        this.inlineMessageId = inlineMessageId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OutgoingGetGameHighScoresMessage{");
        sb.append("userId=").append(userId);
        sb.append(", messageId=").append(messageId);
        sb.append(", inlineMessageId='").append(inlineMessageId).append('\'');
        sb.append(", chatId='").append(chatId).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
