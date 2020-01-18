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
 * Represents a link to a sticker stored on the Telegram servers.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultcachedsticker">
 * https://core.telegram.org/bots/api#inlinequeryresultcachedsticker</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultCachedSticker extends InlineQueryResult {

    private static final String TYPE = "sticker";

    @JsonProperty("sticker_file_id")
    private String stickerFileId;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultCachedSticker() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String stickerFileId;
        private InlineKeyboardMarkup replyMarkup;
        private InputMessageContent inputMessageContext;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder replyMarkup(InlineKeyboardMarkup replyMarkup) {
            this.replyMarkup = replyMarkup;
            return this;
        }

        public Builder stickerFileId(String stickerFileId) {
            this.stickerFileId = stickerFileId;
            return this;
        }

        public Builder inputMessageContext(InputMessageContent inputMessageContext) {
            this.inputMessageContext = inputMessageContext;
            return this;
        }

        public InlineQueryResultCachedSticker build() {
            InlineQueryResultCachedSticker inlineQueryResultDocument = new InlineQueryResultCachedSticker();
            inlineQueryResultDocument.setType(TYPE);
            inlineQueryResultDocument.setId(id);
            inlineQueryResultDocument.setReplyMarkup(replyMarkup);
            inlineQueryResultDocument.inputMessageContext = this.inputMessageContext;
            inlineQueryResultDocument.stickerFileId = this.stickerFileId;
            return inlineQueryResultDocument;
        }
    }

    public String getStickerFileId() {
        return stickerFileId;
    }

    public InputMessageContent getInputMessageContext() {
        return inputMessageContext;
    }

    public void setStickerFileId(String stickerFileId) {
        this.stickerFileId = stickerFileId;
    }

    public void setInputMessageContext(InputMessageContent inputMessageContext) {
        this.inputMessageContext = inputMessageContext;
    }
}
