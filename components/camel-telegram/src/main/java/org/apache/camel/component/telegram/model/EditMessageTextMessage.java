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
 * Message to edit text and game messages.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditMessageTextMessage extends OutgoingMessage {

    @JsonProperty("message_id")
    private Integer messageId;

    @JsonProperty("inline_message_id")
    private String inlineMessageId;

    private String text;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("disable_web_page_preview")
    private Boolean disableWebPagePreview;

    @JsonProperty("reply_markup")
    private InlineKeyboardMarkup replyMarkup;

    /**
     * Builds {@link EditMessageTextMessage} instance.
     *
     * @param chatId                Unique identifier for the target chat or username of the target channel.
     * @param messageId             Identifier of the message to edit. Required if inline_message_id is not specified.
     * @param inlineMessageId       Required if chat_id and message_id are not specified.
     *                              Identifier of the inline message.
     * @param text                  New text of the message.
     * @param parseMode             Send Markdown or HTML, if you want Telegram apps to show bold, italic,
     *                              fixed-width text or inline URLs in your bot's message.
     * @param disableWebPagePreview Disables link previews for links in this message.
     * @param replyMarkup           An inline keyboard that appears right next to the message it belongs to.
     */
    public EditMessageTextMessage(String chatId, Integer messageId, String inlineMessageId, String text,
                                  String parseMode, Boolean disableWebPagePreview, InlineKeyboardMarkup replyMarkup) {
        this.chatId = chatId;
        this.messageId = messageId;
        this.inlineMessageId = inlineMessageId;
        this.text = text;
        this.parseMode = parseMode;
        this.disableWebPagePreview = disableWebPagePreview;
        this.replyMarkup = replyMarkup;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public String getInlineMessageId() {
        return inlineMessageId;
    }

    public String getText() {
        return text;
    }

    public String getParseMode() {
        return parseMode;
    }

    public Boolean getDisableWebPagePreview() {
        return disableWebPagePreview;
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
        private String text;
        private String parseMode;
        private Boolean disableWebPagePreview;
        private InlineKeyboardMarkup replyMarkup;

        private Builder() {
        }

        public Builder messageId(Integer messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder inlineMessageId(String inlineMessageId) {
            this.inlineMessageId = inlineMessageId;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder parseMode(String parseMode) {
            this.parseMode = parseMode;
            return this;
        }

        public Builder disableWebPagePreview(Boolean disableWebPagePreview) {
            this.disableWebPagePreview = disableWebPagePreview;
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

        public EditMessageTextMessage build() {
            return new EditMessageTextMessage(
                chatId, messageId, inlineMessageId, text, parseMode, disableWebPagePreview, replyMarkup);
        }
    }
}
