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
package org.apache.camel.component.cm.client;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.apache.camel.component.cm.validation.constraints.E164;

/**
 * Immutable. The message instance provided by the client.
 */
public class SMSMessage {

    /**
     * Required MSISDN. E164 value. The destination phone number. Format with a '+' and country code.
     *
     * @see <a href="https://en.wikipedia.org/wiki/E.164">https://en.wikipedia.org/wiki/E.164</a>
     */
    @E164
    private final String phoneNumber;

    /**
     * This is the message to be sent. 2 options:
     * <ul>
     * <li>If the message is GSM 0038 encodeable the gateway will first check if a message is larger than 160 characters, if so, the message will be cut into multiple 153 characters parts limited by
     * defaultMaxNumberOfParts set in the component uri.</li>
     * <li>Otherwise, the gateway will check if a message is larger than 70 characters, if so, the message will be cut into multiple 67 characters parts to a maximum of defaultMaxNumberOfParts set in
     * the component uri.</li>
     * </ul>
     *
     * @see <a href="https://en.wikipedia.org/wiki/GSM_03.38">E.164</a>
     */
    @NotNull
    private final String message;

    /**
     * This is an optional dynamic sender name.
     * <p>
     * 1 - 11 alphanumeric characters and + char. Not Empty Strings. This field has a maximum length of 11 characters. If it is not set the defaultFrom required to configure the component will be set.
     */
    @Size(min = 1, max = 11)
    @Pattern(regexp = "^[A-Za-z0-9]+$")
    private final String from;

    /**
     * Unique identifier for a message.
     * <p>
     * 1 - 32 alphanumeric characters. Not Empty Strings. Will not work for demo accounts. This field corresponds to REFERENCE parameter in CM Api.
     */
    @Size(min = 1, max = 32)
    @Pattern(regexp = "^[A-Za-z0-9]+$")
    private final String id;

    public SMSMessage(final String message, final String phoneNumber) {
        this(null, message, phoneNumber, null);
    }

    public SMSMessage(String id, final String message, final String phoneNumber) {
        this(id, message, phoneNumber, null);
    }

    public SMSMessage(final String id, final String message, final String phoneNumber, final String from) {
        this.id = id;
        this.message = message;
        this.phoneNumber = phoneNumber;
        this.from = from;
    }

    public String getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getFrom() {
        return from;
    }

    @Override
    public String toString() {
        StringBuffer toS = new StringBuffer("{phoneNumber: " + phoneNumber + ", message: " + message);
        if (from != null && !from.isEmpty()) {
            toS.append(", from: " + from);
        }
        if (id != null && !id.isEmpty()) {
            toS.append(", id: " + id);
        }
        toS.append(" }");
        return toS.toString();
    }

}
