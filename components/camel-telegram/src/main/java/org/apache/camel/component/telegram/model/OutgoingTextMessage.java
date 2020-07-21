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
 * An outgoing text message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutgoingTextMessage extends OutgoingMessage {

    private static final long serialVersionUID = -8684079202025229263L;

    private String text;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("disable_web_page_preview")
    private Boolean disableWebPagePreview;

    @JsonProperty("reply_markup")
    private ReplyMarkup replyMarkup;

    public OutgoingTextMessage() {

    }

    public OutgoingTextMessage(String text, String parseMode, Boolean disableWebPagePreview,
            ReplyMarkup replyMarkup) {

        this.text = text;
        this.parseMode = parseMode;
        this.disableWebPagePreview = disableWebPagePreview;
        this.replyMarkup = replyMarkup;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getParseMode() {
        return parseMode;
    }

    public void setParseMode(String parseMode) {
        this.parseMode = parseMode;
    }

    public Boolean getDisableWebPagePreview() {
        return disableWebPagePreview;
    }

    public void setDisableWebPagePreview(Boolean disableWebPagePreview) {
        this.disableWebPagePreview = disableWebPagePreview;
    }

    public ReplyMarkup getReplyMarkup() {
        return replyMarkup;
    }

    public void setReplyMarkup(ReplyMarkup replyMarkup) {
        this.replyMarkup = replyMarkup;
    }
    
    public static Builder builder() {

        return new Builder();
    }

    public static class Builder {

        private String text;
        private String parseMode;
        private Boolean disableWebPagePreview;
        private ReplyMarkup replyMarkup;

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

        public Builder replyMarkup(ReplyMarkup replyMarkup) {

            this.replyMarkup = replyMarkup;
            return this;
        }

        public OutgoingTextMessage build() {

            return new OutgoingTextMessage(text, parseMode, disableWebPagePreview, replyMarkup);
        }
    }
}
