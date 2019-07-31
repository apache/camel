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
package org.apache.camel.component.cm;

/**
 * Valid message to be serialized and sent to CM Endpoints. If the message only uses GSM 7-bit characters, then 160 characters will fit in 1 SMS part, and 153*n characters will fit in n SMS parts for
 * n>1. If the message contains other characters, then only 70 characters will fit in 1 SMS part, and 67*n characters will fit in n SMS parts for n>1. <br>
 * <br>
 * {@link https://dashboard.onlinesmsgateway.com/docs} <br>
 * {@link http://support.telerivet.com/customer/portal/articles/1957426-multipart-unicode-sms-messages}
 */
public class CMMessage {

    /**
     * Restrictions: 1 - 32 alphanumeric characters and reference will not work for demo accounts
     */
    // TODO: use a ID generator?
    private String idAsString;

    private String phoneNumber;
    private String message;

    private String sender;

    private boolean unicode;
    private int multipart = 1;

    public CMMessage(final String phoneNumber, final String message) {
        this.message = message;
        this.phoneNumber = phoneNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(final String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(final String sender) {
        this.sender = sender;
    }

    public String getIdAsString() {
        return idAsString;
    }

    public void setIdAsString(final String idAsString) {
        this.idAsString = idAsString;
    }

    public boolean isUnicode() {
        return unicode;
    }

    public void setUnicode(final boolean unicode) {
        this.unicode = unicode;
    }

    public boolean isMultipart() {
        return multipart > 1;
    }

    /**
     * For a CMMessage instance
     *
     * @param defaultMaxNumberOfParts
     */
    public void setUnicodeAndMultipart(int defaultMaxNumberOfParts) {

        // Set UNICODE and MULTIPART
        final String msg = getMessage();
        if (CMUtils.isGsm0338Encodeable(msg)) {

            // Not Unicode is Multipart?
            if (msg.length() > CMConstants.MAX_GSM_MESSAGE_LENGTH) {

                // Multiparts. 153 caracteres max per part
                int parts = msg.length() / CMConstants.MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART;
                if (msg.length() % CMConstants.MAX_GSM_MESSAGE_LENGTH_PER_PART_IF_MULTIPART != 0) {
                    parts++;
                }

                setMultiparts((parts > defaultMaxNumberOfParts) ? defaultMaxNumberOfParts : parts);
            } else { // Otherwise multipart = 1
                setMultiparts(1);
            }
        } else {
            // Unicode Message
            setUnicode(true);

            if (msg.length() > CMConstants.MAX_UNICODE_MESSAGE_LENGTH) {

                // Multiparts. 67 caracteres max per part
                int parts = msg.length() / CMConstants.MAX_UNICODE_MESSAGE_LENGTH_PER_PART_IF_MULTIPART;
                if (msg.length() % CMConstants.MAX_UNICODE_MESSAGE_LENGTH_PER_PART_IF_MULTIPART != 0) {
                    parts++;
                }

                setMultiparts((parts > defaultMaxNumberOfParts) ? defaultMaxNumberOfParts : parts);
            } else { // Otherwise multipart = 1
                setMultiparts(1);
            }
        }
    }

    public void setMultiparts(final int multipart) {
        this.multipart = multipart;
    }

    public int getMultiparts() {
        return multipart;
    }

    @Override
    public String toString() {

        StringBuffer sb = new StringBuffer(" {phoneNumber: " + phoneNumber + ", message: " + message + ", sender=" + sender + ", unicode: " + unicode + ", multipart: " + multipart);
        if (idAsString != null && !idAsString.isEmpty()) {
            sb.append(", idAsString=" + idAsString);
        }
        sb.append(" }");
        return sb.toString();
    }

}
