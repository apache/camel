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
 * Represents a link to a file. By default, this file will be sent by the user with an optional caption.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultdocument">
 * https://core.telegram.org/bots/api#inlinequeryresultdocument</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultDocument extends InlineQueryResult {

    private static final String TYPE = "document";

    @JsonProperty("document_url")
    private String documentUrl;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("thumb_width")
    private Integer thumbWidth;

    @JsonProperty("thumb_height")
    private Integer thumbHeight;

    @JsonProperty("thumb_url")
    private String thumbUrl;

    private String title;

    private String caption;

    private String description;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultDocument() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String documentUrl;
        private String mimeType;
        private Integer thumbWidth;
        private Integer thumbHeight;
        private String thumbUrl;
        private String title;
        private String caption;
        private String description;
        private String parseMode;
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

        public Builder documentUrl(String url) {
            this.documentUrl = url;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder width(Integer width) {
            this.thumbWidth = width;
            return this;
        }

        public Builder height(Integer height) {
            this.thumbHeight = height;
            return this;
        }

        public Builder thumbUrl(String thumbUrl) {
            this.thumbUrl = thumbUrl;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder caption(String caption) {
            this.caption = caption;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parseMode(String parseMode) {
            this.parseMode = parseMode;
            return this;
        }

        public Builder inputMessageContext(InputMessageContent inputMessageContext) {
            this.inputMessageContext = inputMessageContext;
            return this;
        }

        public InlineQueryResultDocument build() {
            InlineQueryResultDocument inlineQueryResultDocument = new InlineQueryResultDocument();
            inlineQueryResultDocument.setType(TYPE);
            inlineQueryResultDocument.setId(id);
            inlineQueryResultDocument.setReplyMarkup(replyMarkup);
            inlineQueryResultDocument.mimeType = this.mimeType;
            inlineQueryResultDocument.caption = this.caption;
            inlineQueryResultDocument.inputMessageContext = this.inputMessageContext;
            inlineQueryResultDocument.parseMode = this.parseMode;
            inlineQueryResultDocument.thumbUrl = this.thumbUrl;
            inlineQueryResultDocument.thumbWidth = this.thumbWidth;
            inlineQueryResultDocument.title = this.title;
            inlineQueryResultDocument.thumbHeight = this.thumbHeight;
            inlineQueryResultDocument.documentUrl = this.documentUrl;
            inlineQueryResultDocument.description = this.description;
            return inlineQueryResultDocument;
        }
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Integer getThumbWidth() {
        return thumbWidth;
    }

    public Integer getThumbHeight() {
        return thumbHeight;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getCaption() {
        return caption;
    }

    public String getDescription() {
        return description;
    }

    public String getParseMode() {
        return parseMode;
    }

    public InputMessageContent getInputMessageContext() {
        return inputMessageContext;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setThumbWidth(Integer thumbWidth) {
        this.thumbWidth = thumbWidth;
    }

    public void setThumbHeight(Integer thumbHeight) {
        this.thumbHeight = thumbHeight;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setParseMode(String parseMode) {
        this.parseMode = parseMode;
    }

    public void setInputMessageContext(InputMessageContent inputMessageContext) {
        this.inputMessageContext = inputMessageContext;
    }
}
