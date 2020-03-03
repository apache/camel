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

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an update with reference to the previous state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Update implements Serializable {

    private static final long serialVersionUID = -4001092937174853655L;

    @JsonProperty("update_id")
    private Long updateId;

    private IncomingMessage message;

    @JsonProperty("channel_post")
    private IncomingMessage channelpost;

    @JsonProperty("callback_query")
    private IncomingCallbackQuery callbackQuery;

    @JsonProperty("inline_query")
    private IncomingInlineQuery inlineQuery;

    public Update() {
    }

    public Long getUpdateId() {
        return updateId;
    }

    public void setUpdateId(Long updateId) {
        this.updateId = updateId;
    }

    public IncomingMessage getMessage() {
        return message;
    }

    public void setMessage(IncomingMessage message) {
        this.message = message;
    }

    public IncomingMessage getChannelPost() {
        return channelpost;
    }

    public void setChannelpost(IncomingMessage channelpost) {
        this.channelpost = channelpost;
    }

    public IncomingCallbackQuery getCallbackQuery() {
        return callbackQuery;
    }

    public void setCallbackQuery(IncomingCallbackQuery callbackQuery) {
        this.callbackQuery = callbackQuery;
    }

    public IncomingInlineQuery getIncomingInlineQuery() {
        return inlineQuery;
    }

    public void setIncomingInlineQuery(IncomingInlineQuery incomingInlineQuery) {
        this.inlineQuery = incomingInlineQuery;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Update{");
        sb.append("updateId=").append(updateId);
        sb.append(", message=").append(message);
        sb.append(", channel_post=").append(channelpost);
        sb.append(", callbackQuery=").append(callbackQuery);
        sb.append(", inlineQuery=").append(inlineQuery);
        sb.append('}');
        return sb.toString();
    }
}
