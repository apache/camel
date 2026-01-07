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

import java.io.Serial;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Use this method when you need to tell the user that something is happening on the bot's side. The status is set for 5
 * seconds or less (when a message arrives from your bot, Telegram clients clear its typing status).
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#sendchataction">https://core.telegram.org/bots/api#sendchataction</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendChatActionMessage extends OutgoingMessage {

    @Serial
    private static final long serialVersionUID = 7645218383562920862L;

    /**
     * Unique identifier of the business connection on behalf of which the action will be sent.
     */
    @JsonProperty("business_connection_id")
    private String businessConnectionId;

    /**
     * Unique identifier for the target message thread; for supergroups only.
     */
    @JsonProperty("message_thread_id")
    private Integer messageThreadId;

    /**
     * Type of action to broadcast. Choose one, depending on what the user is about to receive: typing for text
     * messages, upload_photo for photos, record_video or upload_video for videos, record_voice or upload_voice for
     * voice notes, upload_document for general files, choose_sticker for stickers, find_location for location data,
     * record_video_note or upload_video_note for video notes.
     */
    private Action action;

    public SendChatActionMessage() {
    }

    public SendChatActionMessage(Action action) {
        this.action = action;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public Integer getMessageThreadId() {
        return messageThreadId;
    }

    public void setMessageThreadId(Integer messageThreadId) {
        this.messageThreadId = messageThreadId;
    }

    public String getBusinessConnectionId() {
        return businessConnectionId;
    }

    public void setBusinessConnectionId(String businessConnectionId) {
        this.businessConnectionId = businessConnectionId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SendChatActionMessage{");
        sb.append("chatId='").append(chatId).append('\'');
        sb.append(", action=").append(action);
        sb.append(", messageThreadId=").append(messageThreadId);
        sb.append(", businessConnectionId='").append(businessConnectionId).append('\'');
        sb.append('}');
        return sb.toString();
    }

    /**
     * Type of action to broadcast.
     */
    public enum Action {
        TYPING("typing"),
        UPLOAD_PHOTO("upload_photo"),
        RECORD_VIDEO("record_video"),
        UPLOAD_VIDEO("upload_video"),
        RECORD_VOICE("record_voice"),
        UPLOAD_VOICE("upload_voice"),
        UPLOAD_DOCUMENT("upload_document"),
        CHOOSE_STICKER("choose_sticker"),
        FIND_LOCATION("find_location"),
        RECORD_VIDEO_NOTE("record_video_note"),
        UPLOAD_VIDEO_NOTE("upload_video_note");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }
}
