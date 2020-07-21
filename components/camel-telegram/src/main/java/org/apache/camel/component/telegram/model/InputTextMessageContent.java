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
 * Represents the content of a text message to be sent as the result of an inline query.
 *
 * @see <a href="https://core.telegram.org/bots/api#inputtextmessagecontent">
 *     https://core.telegram.org/bots/api#inputtextmessagecontent</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputTextMessageContent implements InputMessageContent {

    @JsonProperty("message_text")
    private String messageText;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("disable_web_page_preview")
    private Boolean disableWebPagePreview;

    public InputTextMessageContent(String messageText, String parseMode, Boolean disableWebPagePreview) {
        this.messageText = messageText;
        this.parseMode = parseMode;
        this.disableWebPagePreview = disableWebPagePreview;
    }

    public InputTextMessageContent() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String messageText;
        private String parseMode;
        private Boolean disableWebPagePreview;

        private Builder() {
        }

        public Builder messageText(String messageText) {
            this.messageText = messageText;
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

        public InputTextMessageContent build() {
            InputTextMessageContent inputTextMessageContent = new InputTextMessageContent();
            inputTextMessageContent.setMessageText(messageText);
            inputTextMessageContent.setParseMode(parseMode);
            inputTextMessageContent.setDisableWebPagePreview(disableWebPagePreview);
            return inputTextMessageContent;
        }
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
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
}
