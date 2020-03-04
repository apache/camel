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
 * Message used this method to set the score of the specified user in a game.
 *
 * @see <a href="https://core.telegram.org/bots/api#setgamescore">https://core.telegram.org/bots/api#setgamescore</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutgoingSetGameScoreMessage extends OutgoingMessage {

    @JsonProperty("user_id")
    private Long userId;

    private Long score;

    private Boolean force;

    @JsonProperty("disable_edit_message")
    private Boolean disableEditMessage;

    @JsonProperty("message_id")
    private Long messageId;

    @JsonProperty("inline_message_id")
    private String inlineMessageId;

    /**
     * Builds {@link OutgoingSetGameScoreMessage} instance.
     *
     * @param userId             User identifier
     * @param score              New score, must be non-negative
     * @param force              Pass True, if the high score is allowed to decreases
     * @param disableEditMessage Pass True, if the game message should not be automatically edited to
     *                           include the current scoreboard
     * @param messageId          Required if inline_message_id is not specified. Identifier of the sent message
     * @param inlineMessageId    Required if chat_id and message_id are not specified. Identifier of the inline message
     */
    public OutgoingSetGameScoreMessage(String chatId, Long userId, Long score, Boolean force, Boolean disableEditMessage,
                                       Long messageId, String inlineMessageId) {
        this.userId = userId;
        this.score = score;
        this.force = force;
        this.disableEditMessage = disableEditMessage;
        this.messageId = messageId;
        this.inlineMessageId = inlineMessageId;
    }

    public OutgoingSetGameScoreMessage() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getScore() {
        return score;
    }

    public void setScore(Long score) {
        this.score = score;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }

    public Boolean getDisableEditMessage() {
        return disableEditMessage;
    }

    public void setDisableEditMessage(Boolean disableEditMessage) {
        this.disableEditMessage = disableEditMessage;
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
        final StringBuilder sb = new StringBuilder("OutgoingSetGameScoreMessage{");
        sb.append("userId=").append(userId);
        sb.append(", score=").append(score);
        sb.append(", force=").append(force);
        sb.append(", disableEditMessage=").append(disableEditMessage);
        sb.append(", messageId=").append(messageId);
        sb.append(", inlineMessageId='").append(inlineMessageId).append('\'');
        sb.append(", chatId='").append(chatId).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
