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
 * Represents a contact with a phone number.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultcontact">
 * https://core.telegram.org/bots/api#inlinequeryresultcontact</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultContact extends InlineQueryResult {

    private static final String TYPE = "contact";

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String vcard;

    @JsonProperty("thumb_url")
    private String thumbUrl;

    @JsonProperty("thumb_width")
    private String thumbWidth;

    @JsonProperty("thumb_height")
    private String thumbHeight;

    @JsonProperty("input_message_content")
    private InputMessageContent inputMessageContext;

    public InlineQueryResultContact() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String phoneNumber;
        private String firstName;
        private String lastName;
        private String vcard;
        private String thumbUrl;
        private String thumbWidth;
        private String thumbHeight;
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

        public Builder inputMessageContext(InputMessageContent inputMessageContext) {
            this.inputMessageContext = inputMessageContext;
            return this;
        }

        public InlineQueryResultContact build() {
            InlineQueryResultContact inlineQueryResultContact = new InlineQueryResultContact();
            inlineQueryResultContact.setType(TYPE);
            inlineQueryResultContact.setId(id);
            inlineQueryResultContact.setReplyMarkup(replyMarkup);
            inlineQueryResultContact.thumbWidth = this.thumbWidth;
            inlineQueryResultContact.lastName = this.lastName;
            inlineQueryResultContact.vcard = this.vcard;
            inlineQueryResultContact.inputMessageContext = this.inputMessageContext;
            inlineQueryResultContact.firstName = this.firstName;
            inlineQueryResultContact.phoneNumber = this.phoneNumber;
            inlineQueryResultContact.thumbHeight = this.thumbHeight;
            inlineQueryResultContact.thumbUrl = this.thumbUrl;
            return inlineQueryResultContact;
        }
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getVcard() {
        return vcard;
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

    public InputMessageContent getInputMessageContext() {
        return inputMessageContext;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setVcard(String vcard) {
        this.vcard = vcard;
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

    public void setInputMessageContext(InputMessageContent inputMessageContext) {
        this.inputMessageContext = inputMessageContext;
    }
}
