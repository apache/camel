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
 * Message to edit captions of messages.
 * On success, if edited message is sent by the bot, the edited Message is returned
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditMessageCaptionMessage extends OutgoingMessage {

    @JsonProperty("message_id")
    private Integer messageId;

    @JsonProperty("inline_message_id")
    private String inlineMessageId;

    private String caption;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("reply_markup")
    private ReplyKeyboardMarkup replyMarkup;

    /**
     * Constructs {@link EditMessageCaptionMessage} instance.
     *
     * @param chatId                Unique identifier for the target chat or username of the target channel.
     * @param messageId             Identifier of the message to edit. Required if inline_message_id is not specified.
     * @param inlineMessageId       Required if chat_id and message_id are not specified.
     *                              Identifier of the inline message.
     * @param caption               New caption of the message.
     * @param parseMode             Send Markdown or HTML, if you want Telegram apps to show bold, italic,
     *                              fixed-width text or inline URLs in your bot's message.
     * @param replyMarkup           A JSON-serialized object for an inline keyboard.
     */
    public EditMessageCaptionMessage(String chatId, Integer messageId, String inlineMessageId, String caption,
                                     String parseMode, ReplyKeyboardMarkup replyMarkup) {
        this.chatId = chatId;
        this.messageId = messageId;
        this.inlineMessageId = inlineMessageId;
        this.caption = caption;
        this.parseMode = parseMode;
        this.replyMarkup = replyMarkup;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public String getInlineMessageId() {
        return inlineMessageId;
    }

    public String getCaption() {
        return caption;
    }

    public String getParseMode() {
        return parseMode;
    }

    public ReplyKeyboardMarkup getReplyMarkup() {
        return replyMarkup;
    }

    public static final class Builder {
        protected String chatId;
        private Integer messageId;
        private String inlineMessageId;
        private String caption;
        private String parseMode;
        private ReplyKeyboardMarkup replyMarkup;

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

        public Builder caption(String caption) {
            this.caption = caption;
            return this;
        }

        public Builder parseMode(String parseMode) {
            this.parseMode = parseMode;
            return this;
        }

        public Builder replyMarkup(ReplyKeyboardMarkup replyMarkup) {
            this.replyMarkup = replyMarkup;
            return this;
        }

        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }

        public EditMessageCaptionMessage build() {
            return new EditMessageCaptionMessage(
                chatId, messageId, inlineMessageId, caption, parseMode, replyMarkup);
        }
    }
}
