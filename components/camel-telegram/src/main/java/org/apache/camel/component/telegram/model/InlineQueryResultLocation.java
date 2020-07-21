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
 * Represents a location on a map.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultlocation">
 * https://core.telegram.org/bots/api#inlinequeryresultlocation</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultLocation extends InlineQueryResult {

    private static final String TYPE = "location";

    private Float latitude;

    private Float longitude;

    @JsonProperty("live_period")
    private Integer livePeriod;

    @JsonProperty("thumb_url")
    private String thumbUrl;

    @JsonProperty("thumb_width")
    private String thumbWidth;

    @JsonProperty("thumb_height")
    private String thumbHeight;

    private String title;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultLocation() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private Float latitude;
        private Float longitude;
        private Integer livePeriod;
        private String thumbUrl;
        private String thumbWidth;
        private String thumbHeight;
        private String title;
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

        public Builder latitude(Float latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(Float longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder livePeriod(Integer livePeriod) {
            this.livePeriod = livePeriod;
            return this;
        }

        public Builder thumbUrl(String thumbUrl) {
            this.thumbUrl = thumbUrl;
            return this;
        }

        public Builder thumbWidth(String thumbWidth) {
            this.thumbWidth = thumbWidth;
            return this;
        }

        public Builder thumbHeight(String thumbHeight) {
            this.thumbHeight = thumbHeight;
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

        public InlineQueryResultLocation build() {
            InlineQueryResultLocation inlineQueryResultLocation = new InlineQueryResultLocation();
            inlineQueryResultLocation.setType(TYPE);
            inlineQueryResultLocation.setId(id);
            inlineQueryResultLocation.setReplyMarkup(replyMarkup);
            inlineQueryResultLocation.latitude = this.latitude;
            inlineQueryResultLocation.thumbHeight = this.thumbHeight;
            inlineQueryResultLocation.thumbWidth = this.thumbWidth;
            inlineQueryResultLocation.inputMessageContext = this.inputMessageContext;
            inlineQueryResultLocation.title = this.title;
            inlineQueryResultLocation.livePeriod = this.livePeriod;
            inlineQueryResultLocation.longitude = this.longitude;
            inlineQueryResultLocation.thumbUrl = this.thumbUrl;
            return inlineQueryResultLocation;
        }
    }

    public Float getLatitude() {
        return latitude;
    }

    public Float getLongitude() {
        return longitude;
    }

    public Integer getLivePeriod() {
        return livePeriod;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public String getThumbWidth() {
        return thumbWidth;
    }

    public String getThumbHeight() {
        return thumbHeight;
    }

    public String getTitle() {
        return title;
    }

    public InputMessageContent getInputMessageContext() {
        return inputMessageContext;
    }

    public void setLatitude(Float latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(Float longitude) {
        this.longitude = longitude;
    }

    public void setLivePeriod(Integer livePeriod) {
        this.livePeriod = livePeriod;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public void setThumbWidth(String thumbWidth) {
        this.thumbWidth = thumbWidth;
    }

    public void setThumbHeight(String thumbHeight) {
        this.thumbHeight = thumbHeight;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setInputMessageContext(InputMessageContent inputMessageContext) {
        this.inputMessageContext = inputMessageContext;
    }
}
