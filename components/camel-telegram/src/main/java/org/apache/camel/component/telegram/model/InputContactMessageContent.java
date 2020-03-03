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
 * Represents the content of a contact message to be sent as the result of an inline query.
 *
 * @see <a href="https://core.telegram.org/bots/api#inputcontactmessagecontent">
 *     https://core.telegram.org/bots/api#inputcontactmessagecontent</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputContactMessageContent implements InputMessageContent {

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String vcard;

    public InputContactMessageContent() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String phoneNumber;
        private String firstName;
        private String lastName;
        private String vcard;

        private Builder() {
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder vcard(String vcard) {
            this.vcard = vcard;
            return this;
        }

        public InputContactMessageContent build() {
            InputContactMessageContent inputContactMessageContent = new InputContactMessageContent();
            inputContactMessageContent.setPhoneNumber(phoneNumber);
            inputContactMessageContent.setFirstName(firstName);
            inputContactMessageContent.setLastName(lastName);
            inputContactMessageContent.setVcard(vcard);
            return inputContactMessageContent;
        }
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getVcard() {
        return vcard;
    }

    public void setVcard(String vcard) {
        this.vcard = vcard;
    }
}
