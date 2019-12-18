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
package org.apache.camel.component.telegram.util;

import org.apache.camel.component.telegram.TelegramComponent;

public class TelegramApiConfig {

    private final String authorizationToken;
    private final int port;
    private final String baseUri;
    private final String chatId;

    public TelegramApiConfig(String baseUri, int port, String authorizationToken, String chatId) {
        super();
        this.baseUri = baseUri;
        this.port = port;
        this.authorizationToken = authorizationToken;
        this.chatId = chatId;
    }

    public static TelegramApiConfig fromEnv() {
        final String authorizationToken = System.getenv("TELEGRAM_AUTHORIZATION_TOKEN");
        final String chatId = System.getenv("TELEGRAM_CHAT_ID");
        return new TelegramApiConfig(TelegramComponent.BOT_API_DEFAULT_URL, 443, authorizationToken, chatId);
    }

    public static TelegramApiConfig mock(int port) {
        return new TelegramApiConfig("http://localhost:" + port, port, "mock-token", "-1");
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public String getChatId() {
        return chatId;
    }

    public int getPort() {
        return port;
    }
}
