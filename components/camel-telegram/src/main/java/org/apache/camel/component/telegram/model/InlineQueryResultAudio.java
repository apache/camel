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
 * Represents a link to an MP3 audio file.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultaudio">
 * https://core.telegram.org/bots/api#inlinequeryresultaudio</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultAudio extends InlineQueryResult {

    private static final String TYPE = "audio";

    @JsonProperty("audio_url")
    private String audioUrl;

    @JsonProperty("audio_duration")
    private Integer audioDuration;

    private String title;

    private String caption;

    private String performer;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultAudio() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String audioUrl;
        private Integer audioDuration;
        private String title;
        private String caption;
        private String performer;
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

        public Builder audioUrl(String url) {
            this.audioUrl = url;
            return this;
        }

        public Builder audioDuration(Integer duration) {
            this.audioDuration = duration;
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

        public Builder performer(String performer) {
            this.performer = performer;
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

        public InlineQueryResultAudio build() {
            InlineQueryResultAudio inlineQueryResultAudio = new InlineQueryResultAudio();
            inlineQueryResultAudio.setId(id);
            inlineQueryResultAudio.setType(TYPE);
            inlineQueryResultAudio.setReplyMarkup(replyMarkup);
            inlineQueryResultAudio.parseMode = this.parseMode;
            inlineQueryResultAudio.title = this.title;
            inlineQueryResultAudio.caption = this.caption;
            inlineQueryResultAudio.performer = this.performer;
            inlineQueryResultAudio.audioUrl = this.audioUrl;
            inlineQueryResultAudio.audioDuration = this.audioDuration;
            inlineQueryResultAudio.inputMessageContext = this.inputMessageContext;
            return inlineQueryResultAudio;
        }
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public Integer getAudioDuration() {
        return audioDuration;
    }

    public String getTitle() {
        return title;
    }

    public String getCaption() {
        return caption;
    }

    public String getPerformer() {
        return performer;
    }

    public String getParseMode() {
        return parseMode;
    }

    public InputMessageContent getInputMessageContext() {
        return inputMessageContext;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public void setAudioDuration(Integer audioDuration) {
        this.audioDuration = audioDuration;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setPerformer(String performer) {
        this.performer = performer;
    }

    public void setParseMode(String parseMode) {
        this.parseMode = parseMode;
    }

    public void setInputMessageContext(InputMessageContent inputMessageContext) {
        this.inputMessageContext = inputMessageContext;
    }
}
