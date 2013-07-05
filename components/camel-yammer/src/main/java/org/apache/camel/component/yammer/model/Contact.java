/**
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
package org.apache.camel.component.yammer.model;

import java.util.List;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Contact {

    @JsonProperty("email_addresses")
    private List<EmailAddress> emailAddresses;
    @JsonProperty("has_fake_email")
    private Boolean hasFakeEmail;
    private Im im;
    @JsonProperty("phone_numbers")
    private List<String> phoneNumbers;

    public List<EmailAddress> getEmailAddresses() {
        return emailAddresses;
    }

    public void setEmailAddresses(List<EmailAddress> emailAddresses) {
        this.emailAddresses = emailAddresses;
    }

    public Boolean getHasFakeEmail() {
        return hasFakeEmail;
    }

    public void setHasFakeEmail(Boolean hasFakeEmail) {
        this.hasFakeEmail = hasFakeEmail;
    }

    public Im getIm() {
        return im;
    }

    public void setIm(Im im) {
        this.im = im;
    }

    public List<String> getPhoneNumbers() {
        return phoneNumbers;
    }

    public void setPhoneNumbers(List<String> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    @Override
    public String toString() {
        return "Contact [emailAddresses=" + emailAddresses + ", hasFakeEmail=" + hasFakeEmail + ", im=" + im + ", phoneNumbers=" + phoneNumbers + "]";
    }

}
