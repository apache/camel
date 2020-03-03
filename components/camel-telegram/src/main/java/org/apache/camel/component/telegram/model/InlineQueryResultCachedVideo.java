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
 * Represents a link to a video file stored on the Telegram servers.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultcachedvideo">
 * https://core.telegram.org/bots/api#inlinequeryresultcachedvideo</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultCachedVideo extends InlineQueryResult {

    private static final String TYPE = "video";

    @JsonProperty("video_file_id")
    private String videoFileId;

    private String title;

    private String caption;

    private String description;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultCachedVideo() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String videoFileId;
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

        public Builder videoFileId(String videoFileId) {
            this.videoFileId = videoFileId;
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

        public InlineQueryResultCachedVideo build() {
            InlineQueryResultCachedVideo inlineQueryResultVideo = new InlineQueryResultCachedVideo();
            inlineQueryResultVideo.setType(TYPE);
            inlineQueryResultVideo.setId(id);
            inlineQueryResultVideo.setReplyMarkup(replyMarkup);
            inlineQueryResultVideo.videoFileId = this.videoFileId;
            inlineQueryResultVideo.caption = this.caption;
            inlineQueryResultVideo.inputMessageContext = this.inputMessageContext;
            inlineQueryResultVideo.description = this.description;
            inlineQueryResultVideo.parseMode = this.parseMode;
            inlineQueryResultVideo.title = this.title;
            return inlineQueryResultVideo;
        }
    }

    public String getVideoFileId() {
        return videoFileId;
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

    public void setVideoFileId(String videoFileId) {
        this.videoFileId = videoFileId;
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
