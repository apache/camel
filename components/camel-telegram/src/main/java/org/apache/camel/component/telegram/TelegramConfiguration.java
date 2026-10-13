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
package org.apache.camel.component.telegram;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Bean holding the configuration of the telegram component.
 */
@UriParams
public class TelegramConfiguration {

    public static final String ENDPOINT_TYPE_BOTS = "bots";

    @UriPath(description = "The endpoint type. Currently, only the 'bots' type is supported.", enums = ENDPOINT_TYPE_BOTS)
    @Metadata(required = true)
    private String type;

    @UriParam(description = "The authorization token for using the bot (ask the BotFather)", label = "security", secret = true)
    @Metadata(required = true)
    private String authorizationToken;

    @UriParam(description = "HTTP proxy host which could be used when sending out the message.", label = "proxy")
    private String proxyHost;

    @UriParam(description = "HTTP proxy port which could be used when sending out the message.", label = "proxy")
    private Integer proxyPort;

    @UriParam(description = "HTTP proxy type which could be used when sending out the message.", label = "proxy",
              defaultValue = "HTTP")
    private TelegramProxyType proxyType = TelegramProxyType.HTTP;

    @UriParam(description = "The identifier of the chat that will receive the produced messages. Chat ids can be first obtained from incoming messages "
                            + "(eg. when a telegram user starts a conversation with a bot, its client sends automatically a '/start' message containing the chat id). "
                            + "It is an optional parameter, as the chat id can be set dynamically for each outgoing message (using body or headers).",
              label = "producer")
    private String chatId;

    @UriParam(description = "Timeout in seconds for long polling. Put 0 for short polling or a bigger number for long polling. Long polling produces shorter response time.",
              optionalPrefix = "consumer.", defaultValue = "30", label = "consumer")
    private Integer timeout = 30;

    @UriParam(description = "Limit on the number of updates that can be received in a single polling request.",
              optionalPrefix = "consumer.", defaultValue = "100", label = "consumer")
    private Integer limit = 100;

    @UriParam(description = "The secret token that Telegram sends in the X-Telegram-Bot-Api-Secret-Token header of every webhook request."
                            + " In webhook mode, Camel registers the webhook with this token, and rejects a webhook request that does not"
                            + " carry it with HTTP 403. Only the characters A-Z, a-z, 0-9, _ and - are allowed, up to 256 characters."
                            + " When not set, a token derived from the authorization token is used. Set it when the webhook is"
                            + " registered outside Camel, and register the webhook with the same secret_token.",
              label = "consumer,security", security = "secret")
    private String webhookSecretToken;

    @UriParam(description = "Whether, in webhook mode, Camel registers the webhook with a secret token and rejects a webhook request"
                            + " that does not carry it. Set it to false to restore the behaviour of earlier versions, where the webhook"
                            + " was registered without a secret token and webhook requests were not verified.",
              label = "consumer,security", defaultValue = "true", security = "insecure:dev", insecureValue = "false")
    private boolean webhookSecretTokenEnabled = true;

    @UriParam(label = "advanced",
              description = "Can be used to set an alternative base URI, e.g. when you want to test the component against a mock Telegram API")
    private String baseUri;

    public TelegramConfiguration() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public void setAuthorizationToken(String authorizationToken) {
        this.authorizationToken = authorizationToken;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public TelegramProxyType getProxyType() {
        return proxyType;
    }

    public void setProxyType(TelegramProxyType proxyType) {
        this.proxyType = proxyType;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getWebhookSecretToken() {
        return webhookSecretToken;
    }

    public void setWebhookSecretToken(String webhookSecretToken) {
        this.webhookSecretToken = webhookSecretToken;
    }

    public boolean isWebhookSecretTokenEnabled() {
        return webhookSecretTokenEnabled;
    }

    public void setWebhookSecretTokenEnabled(boolean webhookSecretTokenEnabled) {
        this.webhookSecretTokenEnabled = webhookSecretTokenEnabled;
    }

    public String getBaseUri() {
        return baseUri;
    }

    /**
     * Set an alternative base URI, e.g. when you want to test the component against a mock Telegram API.
     */
    public void setBaseUri(String telegramBaseUri) {
        this.baseUri = telegramBaseUri;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TelegramConfiguration{");
        sb.append("type='").append(type).append('\'');
        sb.append(", authorizationToken='").append(authorizationToken).append('\'');
        sb.append(", chatId='").append(chatId).append('\'');
        sb.append(", timeout=").append(timeout);
        sb.append(", limit=").append(limit);
        sb.append('}');
        return sb.toString();
    }
}
