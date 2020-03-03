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
 * Represents a link to a video animation (H.264/MPEG-4 AVC video without sound).
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultmpeg4gif">
 * https://core.telegram.org/bots/api#inlinequeryresultmpeg4gif</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultMpeg4Gif extends InlineQueryResult {

    private static final String TYPE = "mpeg4_gif";

    @JsonProperty("mpeg4_url")
    private String mpeg4Url;

    @JsonProperty("mpeg4_width")
    private String mpeg4Width;

    @JsonProperty("mpeg4_height")
    private Integer mpeg4Height;

    @JsonProperty("mpeg4_duration")
    private Integer mpeg4Duration;

    @JsonProperty("thumb_url")
    private String thumbUrl;

    private String title;

    private String caption;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultMpeg4Gif() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String mpeg4Url;
        private String mpeg4Width;
        private Integer mpeg4Height;
        private Integer mpeg4Duration;
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

        public Builder mpeg4Url(String url) {
            this.mpeg4Url = url;
            return this;
        }

        public Builder mpeg4Width(String width) {
            this.mpeg4Width = width;
            return this;
        }

        public Builder mpeg4Height(Integer height) {
            this.mpeg4Height = height;
            return this;
        }

        public Builder mpeg4Duration(Integer duration) {
            this.mpeg4Duration = duration;
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

        public InlineQueryResultMpeg4Gif build() {
            InlineQueryResultMpeg4Gif inlineQueryResultMpeg4Gif = new InlineQueryResultMpeg4Gif();
            inlineQueryResultMpeg4Gif.setType(TYPE);
            inlineQueryResultMpeg4Gif.setId(id);
            inlineQueryResultMpeg4Gif.setReplyMarkup(replyMarkup);
            inlineQueryResultMpeg4Gif.mpeg4Width = this.mpeg4Width;
            inlineQueryResultMpeg4Gif.mpeg4Height = this.mpeg4Height;
            inlineQueryResultMpeg4Gif.mpeg4Url = this.mpeg4Url;
            inlineQueryResultMpeg4Gif.mpeg4Duration = this.mpeg4Duration;
            inlineQueryResultMpeg4Gif.caption = this.caption;
            inlineQueryResultMpeg4Gif.parseMode = this.parseMode;
            inlineQueryResultMpeg4Gif.inputMessageContext = this.inputMessageContext;
            inlineQueryResultMpeg4Gif.thumbUrl = this.thumbUrl;
            inlineQueryResultMpeg4Gif.title = this.title;
            return inlineQueryResultMpeg4Gif;
        }
    }

    public String getMpeg4Url() {
        return mpeg4Url;
    }

    public String getMpeg4Width() {
        return mpeg4Width;
    }

    public Integer getMpeg4Height() {
        return mpeg4Height;
    }

    public Integer getMpeg4Duration() {
        return mpeg4Duration;
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

    public void setMpeg4Url(String mpeg4Url) {
        this.mpeg4Url = mpeg4Url;
    }

    public void setMpeg4Width(String mpeg4Width) {
        this.mpeg4Width = mpeg4Width;
    }

    public void setMpeg4Height(Integer mpeg4Height) {
        this.mpeg4Height = mpeg4Height;
    }

    public void setMpeg4Duration(Integer mpeg4Duration) {
        this.mpeg4Duration = mpeg4Duration;
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
