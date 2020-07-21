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
 * Represents a link to an animated GIF file.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultgif">
 * https://core.telegram.org/bots/api#inlinequeryresultgif</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultGif extends InlineQueryResult {

    private static final String TYPE = "gif";

    @JsonProperty("gif_url")
    private String gifUrl;

    @JsonProperty("gif_width")
    private String gifWidth;

    @JsonProperty("gif_height")
    private Integer gifHeight;

    @JsonProperty("gif_duration")
    private Integer duration;

    @JsonProperty("thumb_url")
    private String thumbUrl;

    private String title;

    private String caption;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultGif() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String gifUrl;
        private String gifWidth;
        private Integer gifHeight;
        private Integer duration;
        private String thumbUrl;
        private String title;
        private String caption;
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

        public Builder gifUrl(String url) {
            this.gifUrl = url;
            return this;
        }

        public Builder gifWidth(String width) {
            this.gifWidth = width;
            return this;
        }

        public Builder gifHeight(Integer height) {
            this.gifHeight = height;
            return this;
        }

        public Builder duration(Integer duration) {
            this.duration = duration;
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

        public Builder parseMode(String parseMode) {
            this.parseMode = parseMode;
            return this;
        }

        public Builder inputMessageContext(InputMessageContent inputMessageContext) {
            this.inputMessageContext = inputMessageContext;
            return this;
        }

        public InlineQueryResultGif build() {
            InlineQueryResultGif inlineQueryResultGif = new InlineQueryResultGif();
            inlineQueryResultGif.setType(TYPE);
            inlineQueryResultGif.setId(id);
            inlineQueryResultGif.setReplyMarkup(replyMarkup);
            inlineQueryResultGif.gifWidth = this.gifWidth;
            inlineQueryResultGif.gifUrl = this.gifUrl;
            inlineQueryResultGif.gifHeight = this.gifHeight;
            inlineQueryResultGif.duration = this.duration;
            inlineQueryResultGif.caption = this.caption;
            inlineQueryResultGif.parseMode = this.parseMode;
            inlineQueryResultGif.thumbUrl = this.thumbUrl;
            inlineQueryResultGif.title = this.title;
            inlineQueryResultGif.inputMessageContext = this.inputMessageContext;
            return inlineQueryResultGif;
        }
    }

    public String getGifUrl() {
        return gifUrl;
    }

    public String getGifWidth() {
        return gifWidth;
    }

    public Integer getGifHeight() {
        return gifHeight;
    }

    public Integer getDuration() {
        return duration;
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

    public String getParseMode() {
        return parseMode;
    }

    public InputMessageContent getInputMessageContext() {
        return inputMessageContext;
    }

    public void setGifUrl(String gifUrl) {
        this.gifUrl = gifUrl;
    }

    public void setGifWidth(String gifWidth) {
        this.gifWidth = gifWidth;
    }

    public void setGifHeight(Integer gifHeight) {
        this.gifHeight = gifHeight;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
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

    public void setParseMode(String parseMode) {
        this.parseMode = parseMode;
    }

    public void setInputMessageContext(InputMessageContent inputMessageContext) {
        this.inputMessageContext = inputMessageContext;
    }
}
