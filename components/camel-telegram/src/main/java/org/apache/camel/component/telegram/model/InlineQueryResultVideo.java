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
 * Represents a link to a page containing an embedded video player or a video file.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultvideo">
 * https://core.telegram.org/bots/api#inlinequeryresultvideo</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultVideo extends InlineQueryResult {

    private static final String TYPE = "video";

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("video_width")
    private String videoWidth;

    @JsonProperty("video_height")
    private Integer videoHeight;

    @JsonProperty("video_duration")
    private Integer duration;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("thumb_url")
    private String thumbUrl;

    private String title;

    private String caption;

    private String description;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultVideo() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String videoUrl;
        private String videoWidth;
        private Integer videoHeight;
        private Integer duration;
        private String mimeType;
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

        public Builder videoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
            return this;
        }

        public Builder videoWidth(String videoWidth) {
            this.videoWidth = videoWidth;
            return this;
        }

        public Builder videoHeight(Integer videoHeight) {
            this.videoHeight = videoHeight;
            return this;
        }

        public Builder duration(Integer duration) {
            this.duration = duration;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
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

        public InlineQueryResultVideo build() {
            InlineQueryResultVideo inlineQueryResultVideo = new InlineQueryResultVideo();
            inlineQueryResultVideo.setType(TYPE);
            inlineQueryResultVideo.setId(id);
            inlineQueryResultVideo.setReplyMarkup(replyMarkup);
            inlineQueryResultVideo.mimeType = this.mimeType;
            inlineQueryResultVideo.videoWidth = this.videoWidth;
            inlineQueryResultVideo.caption = this.caption;
            inlineQueryResultVideo.videoHeight = this.videoHeight;
            inlineQueryResultVideo.duration = this.duration;
            inlineQueryResultVideo.inputMessageContext = this.inputMessageContext;
            inlineQueryResultVideo.videoUrl = this.videoUrl;
            inlineQueryResultVideo.description = this.description;
            inlineQueryResultVideo.parseMode = this.parseMode;
            inlineQueryResultVideo.title = this.title;
            inlineQueryResultVideo.thumbUrl = this.thumbUrl;
            return inlineQueryResultVideo;
        }
    }

    public String getViedoUrl() {
        return videoUrl;
    }

    public String getVideoWidth() {
        return videoWidth;
    }

    public Integer getVideoHeight() {
        return videoHeight;
    }

    public Integer getDuration() {
        return duration;
    }

    public String getMimeType() {
        return mimeType;
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

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public void setVideoWidth(String videoWidth) {
        this.videoWidth = videoWidth;
    }

    public void setVideoHeight(Integer videoHeight) {
        this.videoHeight = videoHeight;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
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
