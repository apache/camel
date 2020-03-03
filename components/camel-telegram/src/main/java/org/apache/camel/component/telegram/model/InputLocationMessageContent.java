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
 * Represents the content of a location message to be sent as the result of an inline query.
 *
 * @see <a href="https://core.telegram.org/bots/api#inputlocationmessagecontent">
 *     https://core.telegram.org/bots/api#inputlocationmessagecontent</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputLocationMessageContent implements InputMessageContent {

    private Float latitude;

    private Float longitude;

    @JsonProperty("live_period")
    private Integer livePeriod;

    public InputLocationMessageContent() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Float latitude;
        private Float longitude;
        private Integer livePeriod;

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

        public Builder livePeriod(Integer livePeriod) {
            this.livePeriod = livePeriod;
            return this;
        }

        public InputLocationMessageContent build() {
            InputLocationMessageContent inputLocationMessageContent = new InputLocationMessageContent();
            inputLocationMessageContent.setLatitude(latitude);
            inputLocationMessageContent.setLongitude(longitude);
            inputLocationMessageContent.setLivePeriod(livePeriod);
            return inputLocationMessageContent;
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

    public Integer getLivePeriod() {
        return livePeriod;
    }

    public void setLivePeriod(Integer livePeriod) {
        this.livePeriod = livePeriod;
    }
}
