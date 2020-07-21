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
 * Message to edit only the reply markup of messages.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditMessageReplyMarkupMessage extends OutgoingMessage {

    @JsonProperty("message_id")
    private Integer messageId;

    @JsonProperty("inline_message_id")
    private String inlineMessageId;

    @JsonProperty("reply_markup")
    private InlineKeyboardMarkup replyMarkup;

    /**
     * Builds {@link EditMessageReplyMarkupMessage} instance.
     *
     * @param chatId               Unique identifier for the target chat or username of the target channel.
     * @param messageId            Identifier of the message to edit. Required if inline_message_id is not specified.
     * @param inlineMessageId      Required if chat_id and message_id are not specified.
     *                             Identifier of the inline message.
     * @param replyMarkup          An inline keyboard that appears right next to the message it belongs to.
     */
    public EditMessageReplyMarkupMessage(String chatId, Integer messageId, String inlineMessageId,
                                         InlineKeyboardMarkup replyMarkup) {
        this.chatId = chatId;
        this.messageId = messageId;
        this.inlineMessageId = inlineMessageId;
        this.replyMarkup = replyMarkup;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public String getInlineMessageId() {
        return inlineMessageId;
    }

    public InlineKeyboardMarkup getReplyMarkup() {
        return replyMarkup;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        protected String chatId;
        private Integer messageId;
        private String inlineMessageId;
        private InlineKeyboardMarkup replyMarkup;

        public Builder() {
        }

        public Builder messageId(Integer messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder inlineMessageId(String inlineMessageId) {
            this.inlineMessageId = inlineMessageId;
            return this;
        }

        public Builder replyMarkup(InlineKeyboardMarkup replyMarkup) {
            this.replyMarkup = replyMarkup;
            return this;
        }

        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }

        public EditMessageReplyMarkupMessage build() {
            return new EditMessageReplyMarkupMessage(chatId, messageId, inlineMessageId, replyMarkup);
        }
    }


    @Override
    public String toString() {
        return "EditMessageReplyMarkupMessage{"
            + "messageId=" + messageId
            + ", inlineMessageId='" + inlineMessageId + '\''
            + ", replyMarkup=" + replyMarkup
            + ", chatId='" + chatId + '\''
            + '}';
    }
}

