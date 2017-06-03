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
    @Metadata(required = "true")
    private String type;

    @UriPath(label = "security", description = "The authorization token for using the bot (ask the BotFather), eg. 654321531:HGF_dTra456323dHuOedsE343211fqr3t-H.")
    @Metadata(required = "true")
    private String authorizationToken;

    @UriParam(description = "The identifier of the chat that will receive the produced messages. Chat ids can be first obtained from incoming messages "
            + "(eg. when a telegram user starts a conversation with a bot, its client sends automatically a '/start' message containing the chat id). "
            + "It is an optional parameter, as the chat id can be set dynamically for each outgoing message (using body or headers).", label = "producer")
    private String chatId;

    @UriParam(description = "Timeout in seconds for long polling. Put 0 for short polling or a bigger number for long polling. Long polling produces shorter response time.", optionalPrefix =
            "consumer.", defaultValue = "30", label = "consumer")
    private Integer timeout = 30;

    @UriParam(description = "Limit on the number of updates that can be received in a single polling request.", optionalPrefix = "consumer.", defaultValue = "100", label = "consumer")
    private Integer limit = 100;

    public TelegramConfiguration() {
    }

    /**
     * Sets the remaining configuration parameters available in the URI.
     *
     * @param remaining the URI part after the scheme
     * @param defaultAuthorizationToken the default authorization token to use if not present in the URI
     */
    public void updatePathConfig(String remaining, String defaultAuthorizationToken) {
        String[] parts = remaining.split("/");
        if (parts.length == 0 || parts.length > 2) {
            throw new IllegalArgumentException("Unexpected URI format. Expected 'bots' or 'bots/<authorizationToken>', found '" + remaining + "'");
        }

        String type = parts[0];
        if (!type.equals(ENDPOINT_TYPE_BOTS)) {
            throw new IllegalArgumentException("Unexpected endpoint type. Expected 'bots', found '" + type + "'");
        }

        String authorizationToken = defaultAuthorizationToken;
        if (parts.length > 1) {
            authorizationToken = parts[1];
        }

        if (authorizationToken == null || authorizationToken.trim().length() == 0) {
            throw new IllegalArgumentException("The authorization token must be provided and cannot be empty");
        }

        this.type = type;
        this.authorizationToken = authorizationToken;
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
