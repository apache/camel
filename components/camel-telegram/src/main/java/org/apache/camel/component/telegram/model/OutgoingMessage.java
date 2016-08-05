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
package org.apache.camel.component.telegram.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The superclass of all outgoing messages.
 */
public abstract class OutgoingMessage implements Serializable {

    private static final long serialVersionUID = -5958829164103569292L;

    protected String chatId;

    @JsonProperty("disable_notification")
    protected Boolean disableNotification;

    @JsonProperty("reply_to_message_id")
    protected Long replyToMessageId;

    public OutgoingMessage() {
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public Boolean getDisableNotification() {
        return disableNotification;
    }

    public void setDisableNotification(Boolean disableNotification) {
        this.disableNotification = disableNotification;
    }

    public Long getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(Long replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OutgoingMessage{");
        sb.append("chatId='").append(chatId).append('\'');
        sb.append(", disableNotification=").append(disableNotification);
        sb.append(", replyToMessageId=").append(replyToMessageId);
        sb.append('}');
        return sb.toString();
    }
}
