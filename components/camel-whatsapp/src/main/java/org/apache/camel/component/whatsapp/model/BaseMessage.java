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
package org.apache.camel.component.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BaseMessage {

    /**
     * Messaging service used for the request. Use "whatsapp".
     */
    @JsonProperty("messaging_product")
    private String messagingProduct = "whatsapp";

    /**
     * Currently, you can only send messages to individuals. Set this as individual.
     */
    @JsonProperty("recipient_type")
    private String recipientType = "individual";

    /**
     * WhatsApp ID or phone number for the person you want to send a message to.
     */
    private String to;

    /**
     * The type of message you want to send. Default: text Supported Options audio: for audio messages. contacts: for
     * contact messages. document: for document messages. image: for image messages. interactive: for list and reply
     * button messages. location: for location messages. sticker: for sticker messages. template: for template messages.
     * Text and media (images and documents) message templates are supported. text: for text messages.
     */
    private String type = "text";

    public BaseMessage() {
    }

    public String getMessagingProduct() {
        return messagingProduct;
    }

    public void setMessagingProduct(String messagingProduct) {
        this.messagingProduct = messagingProduct;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(String recipientType) {
        this.recipientType = recipientType;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
