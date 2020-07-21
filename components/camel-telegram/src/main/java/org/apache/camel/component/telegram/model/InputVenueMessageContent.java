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
 * Represents the content of a venue message to be sent as the result of an inline query.
 *
 * @see <a href="https://core.telegram.org/bots/api#inputvenuemessagecontent">
 *     https://core.telegram.org/bots/api#inputvenuemessagecontent</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputVenueMessageContent implements InputMessageContent {

    private Float latitude;

    private Float longitude;

    private String title;

    private String address;

    @JsonProperty("foursquare_id")
    private String foursquareId;

    @JsonProperty("foursquare_type")
    private String foursquareType;

    public InputVenueMessageContent() {
    }

    public InputVenueMessageContent(Float latitude, Float longitude, String title, String address, String foursquareId,
                                    String foursquareType) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.title = title;
        this.address = address;
        this.foursquareId = foursquareId;
        this.foursquareType = foursquareType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Float latitude;
        private Float longitude;
        private String title;
        private String address;
        private String foursquareId;
        private String foursquareType;

        private Builder() {
        }

        public Builder latitude(Float latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(Float longitude) {
            this.longitude = longitude;
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

        public InputVenueMessageContent build() {
            InputVenueMessageContent inputVenueMessageContent = new InputVenueMessageContent();
            inputVenueMessageContent.setLatitude(latitude);
            inputVenueMessageContent.setLongitude(longitude);
            inputVenueMessageContent.setTitle(title);
            inputVenueMessageContent.setAddress(address);
            inputVenueMessageContent.setFoursquareId(foursquareId);
            inputVenueMessageContent.setFoursquareType(foursquareType);
            return inputVenueMessageContent;
        }
    }

    public Float getLatitude() {
        return latitude;
    }

    public void setLatitude(Float latitude) {
        this.latitude = latitude;
    }

    public Float getLongitude() {
        return longitude;
    }

    public void setLongitude(Float longitude) {
        this.longitude = longitude;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getFoursquareId() {
        return foursquareId;
    }

    public void setFoursquareId(String foursquareId) {
        this.foursquareId = foursquareId;
    }

    public String getFoursquareType() {
        return foursquareType;
    }

    public void setFoursquareType(String foursquareType) {
        this.foursquareType = foursquareType;
    }
}
