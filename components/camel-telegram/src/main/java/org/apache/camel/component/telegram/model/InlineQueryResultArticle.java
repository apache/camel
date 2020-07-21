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
 * Represents a link to an article or web page.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultarticle">
 * https://core.telegram.org/bots/api#inlinequeryresultarticle</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultArticle extends InlineQueryResult {

    private static final String TYPE = "article";

    private String title;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    private String url;

    @JsonProperty("hide_url")
    private Boolean hideUrl;

    private String description;

    @JsonProperty("thumb_url")
    private String thumbUrl;

    @JsonProperty("thumb_width")
    private Integer thumbWidth;

    @JsonProperty("thumb_height")
    private Integer thumbHeight;

    public InlineQueryResultArticle() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String title;
        private InputMessageContent inputMessageContext;
        private String url;
        private Boolean hideUrl;
        private String description;
        private String thumbUrl;
        private Integer thumbWidth;
        private Integer thumbHeight;

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

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder inputMessageContext(InputMessageContent inputMessageContext) {
            this.inputMessageContext = inputMessageContext;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder hideUrl(Boolean hideUrl) {
            this.hideUrl = hideUrl;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder thumbUrl(String thumbUrl) {
            this.thumbUrl = thumbUrl;
            return this;
        }

        public Builder thumbWidth(Integer thumbWidth) {
            this.thumbWidth = thumbWidth;
            return this;
        }

        public Builder thumbHeight(Integer thumbHeight) {
            this.thumbHeight = thumbHeight;
            return this;
        }

        public InlineQueryResultArticle build() {
            InlineQueryResultArticle inlineResult = new InlineQueryResultArticle();
            inlineResult.setType(TYPE);
            inlineResult.setId(id);
            inlineResult.setReplyMarkup(replyMarkup);
            inlineResult.title = this.title;
            inlineResult.inputMessageContext = this.inputMessageContext;
            inlineResult.url = this.url;
            inlineResult.thumbWidth = this.thumbWidth;
            inlineResult.thumbUrl = this.thumbUrl;
            inlineResult.description = this.description;
            inlineResult.hideUrl = this.hideUrl;
            inlineResult.thumbHeight = this.thumbHeight;
            return inlineResult;
        }
    }

    public String getTitle() {
        return title;
    }

    public InputMessageContent getInputMessageContext() {
        return inputMessageContext;
    }

    public String getUrl() {
        return url;
    }

    public Boolean getHideUrl() {
        return hideUrl;
    }

    public String getDescription() {
        return description;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public Integer getThumbWidth() {
        return thumbWidth;
    }

    public Integer getThumbHeight() {
        return thumbHeight;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setInputMessageContext(InputMessageContent inputMessageContext) {
        this.inputMessageContext = inputMessageContext;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setHideUrl(Boolean hideUrl) {
        this.hideUrl = hideUrl;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public void setThumbWidth(Integer thumbWidth) {
        this.thumbWidth = thumbWidth;
    }

    public void setThumbHeight(Integer thumbHeight) {
        this.thumbHeight = thumbHeight;
    }
}
