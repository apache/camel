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
 * Represents a venue.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultvenue">
 * https://core.telegram.org/bots/api#inlinequeryresultvenue</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultVenue extends InlineQueryResult {

    private static final String TYPE = "venue";

    private Float latitude;

    private Float longitude;

    @JsonProperty("thumb_url")
    private String thumbUrl;

    @JsonProperty("thumb_width")
    private String thumbWidth;

    @JsonProperty("thumb_height")
    private String thumbHeight;

    private String title;

    private String address;

    @JsonProperty("foursquare_id")
    private String foursquareId;

    @JsonProperty("foursquare_type")
    private String foursquareType;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultVenue() {
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
        private String thumbUrl;
        private String thumbWidth;
        private String thumbHeight;
        private String title;
        private String address;
        private String foursquareId;
        private String foursquareType;
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

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder foursquareId(String foursquareId) {
            this.foursquareId = foursquareId;
            return this;
        }

        public Builder foursquareType(String foursquareType) {
            this.foursquareType = foursquareType;
            return this;
        }

        public Builder inputMessageContext(InputMessageContent inputMessageContext) {
            this.inputMessageContext = inputMessageContext;
            return this;
        }

        public InlineQueryResultVenue build() {
            InlineQueryResultVenue inlineQueryResultVenue = new InlineQueryResultVenue();
            inlineQueryResultVenue.setType(TYPE);
            inlineQueryResultVenue.setId(id);
            inlineQueryResultVenue.setReplyMarkup(replyMarkup);
            inlineQueryResultVenue.title = this.title;
            inlineQueryResultVenue.foursquareId = this.foursquareId;
            inlineQueryResultVenue.foursquareType = this.foursquareType;
            inlineQueryResultVenue.address = this.address;
            inlineQueryResultVenue.latitude = this.latitude;
            inlineQueryResultVenue.thumbWidth = this.thumbWidth;
            inlineQueryResultVenue.longitude = this.longitude;
            inlineQueryResultVenue.thumbUrl = this.thumbUrl;
            inlineQueryResultVenue.thumbHeight = this.thumbHeight;
            inlineQueryResultVenue.inputMessageContext = this.inputMessageContext;
            return inlineQueryResultVenue;
        }
    }

    public Float getLatitude() {
        return latitude;
    }

    public Float getLongitude() {
        return longitude;
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

    public String getAddress() {
        return address;
    }

    public String getFoursquareId() {
        return foursquareId;
    }

    public String getFoursquareType() {
        return foursquareType;
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

    public void setAddress(String address) {
        this.address = address;
    }

    public void setFoursquareId(String foursquareId) {
        this.foursquareId = foursquareId;
    }

    public void setFoursquareType(String foursquareType) {
        this.foursquareType = foursquareType;
    }

    public void setInputMessageContext(InputMessageContent inputMessageContext) {
        this.inputMessageContext = inputMessageContext;
    }
}
