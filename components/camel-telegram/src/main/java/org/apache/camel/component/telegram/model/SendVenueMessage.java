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

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendVenueMessage extends OutgoingMessage {
    @JsonProperty("longitude")
    private double longitude;

    @JsonProperty("latitude")
    private double latitude;

    @JsonProperty("title")
    private String title;

    @JsonProperty("address")
    private String address;

    @JsonProperty("foursquare_id")
    private String foursquareId;

    @JsonProperty("foursquare_type")
    private String foursquareType;

    @JsonProperty("reply_markup")
    private ReplyMarkup replyMarkup;

    public SendVenueMessage() {
    }

    public SendVenueMessage(double latitude, double longitude, String title, String address) {
        this.setLatitude(latitude);
        this.setLongitude(longitude);
        this.setTitle(title);
        this.setAddress(address);
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
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

    public ReplyMarkup getReplyMarkup() {
        return replyMarkup;
    }

    public void setReplyMarkup(ReplyMarkup replyMarkup) {
        this.replyMarkup = replyMarkup;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SendLocationMessage{");
        sb.append("latitude=").append(latitude).append('\'');
        sb.append(", longitude=").append(longitude).append('\'');
        sb.append(", title=").append(title).append('\'');
        sb.append(", address=").append(address).append('\'');
        sb.append(", foursquareId=").append(foursquareId).append('\'');
        sb.append(", foursquareType=").append(foursquareType).append('\'');
        sb.append(", disableNotification=").append(disableNotification).append('\'');
        sb.append(", replyToMessageId=").append(replyToMessageId).append('\'');
        sb.append(", replyMarkup=").append(replyMarkup);
        sb.append('}');
        return sb.toString();
    }
}
