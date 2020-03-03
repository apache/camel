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
 * Used to send answers to callback queries sent from inline keyboards.
 * The answer will be displayed to the user as a notification at the top of the chat screen or as an alert.
 * Returns {@link MessageResult}
 *
 * @see <a href="https://core.telegram.org/bots/api#answercallbackquery">
 * https://core.telegram.org/bots/api#answercallbackquery</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutgoingCallbackQueryMessage extends OutgoingMessage {

    @JsonProperty("callback_query_id")
    private String callbackQueryId;

    private String text;

    @JsonProperty("show_alert")
    private Boolean showAlert;

    private String url;

    @JsonProperty("cache_time")
    private Integer cacheTime;

    public OutgoingCallbackQueryMessage() {

    }

    public String getCallbackQueryId() {
        return callbackQueryId;
    }

    public void setCallbackQueryId(String callbackQueryId) {
        this.callbackQueryId = callbackQueryId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getShowAlert() {
        return showAlert;
    }

    public void setShowAlert(Boolean showAlert) {
        this.showAlert = showAlert;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getCacheTime() {
        return cacheTime;
    }

    public void setCacheTime(Integer cacheTime) {
        this.cacheTime = cacheTime;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OutgoingCallbackQueryMessage{");
        sb.append("callbackQueryId='").append(callbackQueryId).append('\'');
        sb.append(", text='").append(text).append('\'');
        sb.append(", showAlert=").append(showAlert);
        sb.append(", url='").append(url).append('\'');
        sb.append(", cacheTime=").append(cacheTime);
        sb.append('}');
        return sb.toString();
    }
}
