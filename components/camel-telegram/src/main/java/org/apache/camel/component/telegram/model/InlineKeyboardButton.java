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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents one button of an inline keyboard. You must use exactly one of the optional fields.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinekeyboardbutton">
 *     https://core.telegram.org/bots/api#inlinekeyboardbutton</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InlineKeyboardButton implements Serializable {

    private static final long serialVersionUID = -5386858930543292655L;

    private String text;

    private String url;

    @JsonProperty("login_url")
    private LoginUrl loginUrl;

    @JsonProperty("callback_data")
    private String callbackData;

    @JsonProperty("callback_game")
    private CallbackGame callbackGame;

    public InlineKeyboardButton() {

    }

    /**
     * Builds {@link InlineKeyboardButton} instance.
     *
     * @param text         Label text on the button
     * @param url          Optional. HTTP or tg:// url to be opened when button is pressed
     * @param loginUrl     Optional. An HTTP URL used to automatically authorize the user.
     * @param callbackData Optional. Data to be sent in a callback query to the bot when button is pressed, 1-64 bytes
     * @param callbackGame Optional. Description of the game that will be launched when the user presses the button.
     */
    public InlineKeyboardButton(String text, String url, LoginUrl loginUrl,
                                String callbackData, CallbackGame callbackGame) {
        this.text = text;
        this.url = url;
        this.callbackData = callbackData;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LoginUrl getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(LoginUrl loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getCallbackData() {
        return callbackData;
    }

    public void setCallbackData(String callbackData) {
        this.callbackData = callbackData;
    }

    public CallbackGame getCallbackGame() {
        return callbackGame;
    }

    public void setCallbackGame(CallbackGame callbackGame) {
        this.callbackGame = callbackGame;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String text;
        private String url;
        private String callbackData;
        private LoginUrl loginUrl;
        private CallbackGame callbackGame;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder callbackData(String callbackData) {
            this.callbackData = callbackData;
            return this;
        }

        public Builder callbackGame(CallbackGame callbackGame) {
            this.callbackGame = callbackGame;
            return this;
        }

        public Builder loginUrl(LoginUrl loginUrl) {
            this.loginUrl = loginUrl;
            return this;
        }

        public InlineKeyboardButton build() {
            return new InlineKeyboardButton(text, url, loginUrl, callbackData, callbackGame);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InlineKeyboardButton{");
        sb.append("text='").append(text).append('\'');
        sb.append(", url='").append(url).append('\'');
        sb.append(", loginUrl=").append(loginUrl);
        sb.append(", callbackData='").append(callbackData).append('\'');
        sb.append(", callbackGame=").append(callbackGame);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InlineKeyboardButton that = (InlineKeyboardButton) o;
        return Objects.equals(text, that.text)
               && Objects.equals(url, that.url)
               && Objects.equals(loginUrl, that.loginUrl)
               && Objects.equals(callbackData, that.callbackData)
               && Objects.equals(callbackGame, that.callbackGame);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, url, loginUrl, callbackData, callbackGame);
    }
}
