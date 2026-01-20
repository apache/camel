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

package org.apache.camel.component.telegram.model.payments;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This object represents a shipping address.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#shippingaddress">https://core.telegram.org/bots/api#shippingaddress</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShippingAddress implements Serializable {

    private static final long serialVersionUID = 6522567551386167131L;

    @JsonProperty("country_code")
    private String countryCode;

    private String state;

    private String city;

    @JsonProperty("street_line1")
    private String streetLine1;

    @JsonProperty("street_line2")
    private String streetLine2;

    @JsonProperty("post_code")
    private String postCode;

    public ShippingAddress(String countryCode, String state, String city, String streetLine1, String streetLine2,
                           String postCode) {
        this.countryCode = countryCode;
        this.state = state;
        this.city = city;
        this.streetLine1 = streetLine1;
        this.streetLine2 = streetLine2;
        this.postCode = postCode;
    }

    public ShippingAddress() {
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStreetLine1() {
        return streetLine1;
    }

    public void setStreetLine1(String streetLine1) {
        this.streetLine1 = streetLine1;
    }

    public String getStreetLine2() {
        return streetLine2;
    }

    public void setStreetLine2(String streetLine2) {
        this.streetLine2 = streetLine2;
    }

    public String getPostCode() {
        return postCode;
    }

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ShippingAddress{");
        sb.append("countryCode='").append(countryCode).append('\'');
        sb.append(", state='").append(state).append('\'');
        sb.append(", city='").append(city).append('\'');
        sb.append(", streetLine1='").append(streetLine1).append('\'');
        sb.append(", streetLine2='").append(streetLine2).append('\'');
        sb.append(", postCode='").append(postCode).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
