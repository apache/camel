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
public class SendLocationMessage extends OutgoingMessage {
    @JsonProperty("longitude")
    private double longitude;

    @JsonProperty("latitude")
    private double latitude;

    @JsonProperty("live_period")
    private Integer livePeriod;

    @JsonProperty("reply_markup")
    private ReplyMarkup replyMarkup;

    public SendLocationMessage() {
    }

    public SendLocationMessage(double latitude, double longitude) {
        this.setLatitude(latitude);
        this.setLongitude(longitude);
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setLivePeriod(Integer livePeriod) {
        this.livePeriod = livePeriod;
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
        sb.append(", livePeriod=").append(livePeriod).append('\'');
        sb.append(", disableNotification=").append(disableNotification).append('\'');
        sb.append(", replyToMessageId=").append(replyToMessageId).append('\'');
        sb.append(", replyMarkup=").append(replyMarkup);
        sb.append('}');
        return sb.toString();
    }
}
