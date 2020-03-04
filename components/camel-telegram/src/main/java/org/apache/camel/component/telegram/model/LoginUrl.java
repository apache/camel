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
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a parameter of the inline keyboard button used to automatically authorize a user.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginUrl implements Serializable {

    private static final long serialVersionUID = 4046971763350434361L;

    private String url;
    @JsonProperty("forward_text")
    private String forwardText;
    @JsonProperty("bot_username")
    private String botUsername;
    @JsonProperty("request_write_access")
    private Boolean requestWriteAccess;

    /**
     * Builds {@link LoginUrl} instance.
     *
     * @param url                An HTTP URL to be opened with user authorization data added to the query string
     *                           when the button is pressed
     * @param forwardText        Optional. New text of the button in forwarded messages.
     * @param botUsername        Optional. Username of a bot, which will be used for user authorization.
     * @param requestWriteAccess Optional. Pass True to request the permission for your bot to send messages to the user.
     */
    public LoginUrl(String url, String forwardText, String botUsername, Boolean requestWriteAccess) {
        this.url = url;
        this.forwardText = forwardText;
        this.botUsername = botUsername;
        this.requestWriteAccess = requestWriteAccess;
    }

    public LoginUrl() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getForwardText() {
        return forwardText;
    }

    public void setForwardText(String forwardText) {
        this.forwardText = forwardText;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        this.botUsername = botUsername;
    }

    public Boolean getRequestWriteAccess() {
        return requestWriteAccess;
    }

    public void setRequestWriteAccess(Boolean requestWriteAccess) {
        this.requestWriteAccess = requestWriteAccess;
    }

    @Override
    public String toString() {
        return "LoginUrl{"
            + "url='" + url + '\''
            + ", forwardText='" + forwardText + '\''
            + ", botUsername='" + botUsername + '\''
            + ", requestWriteAccess=" + requestWriteAccess
            + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LoginUrl loginUrl = (LoginUrl) o;
        return Objects.equals(url, loginUrl.url)
            && Objects.equals(forwardText, loginUrl.forwardText)
            && Objects.equals(botUsername, loginUrl.botUsername)
            && Objects.equals(requestWriteAccess, loginUrl.requestWriteAccess);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, forwardText, botUsername, requestWriteAccess);
    }
}
