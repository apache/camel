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
package org.apache.camel.component.telegram.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.telegram.TelegramMessage;
import org.apache.camel.component.telegram.model.MessageResult;

final class TelegramHandlerRegistry {
    private final Map<Class<?>, TelegramMessageHandler<?>> handlers;

    private TelegramHandlerRegistry(Map<Class<?>, TelegramMessageHandler<?>> handlers) {
        this.handlers = Collections.unmodifiableMap(handlers);
    }

    TelegramMessageHandler<?> get(Class<?> messageClass) {
        return handlers.get(messageClass);
    }

    static Builder builder(TelegramApiClient apiClient) {
        return new Builder(apiClient);
    }

    static final class Builder {
        private final Map<Class<?>, TelegramMessageHandler<?>> handlers;
        private final TelegramApiClient apiClient;

        private Builder(TelegramApiClient apiClient) {
            this.handlers = new HashMap<>();
            this.apiClient = apiClient;
        }

        Builder register(Class<?> messageClass, TelegramMessageHandler<?> handler) {
            handlers.put(messageClass, handler);
            return this;
        }

        Builder register(Class<?> messageClass, String endpoint) {
            handlers.put(messageClass, new JsonMessageHandler(
                    apiClient, apiClient.baseUri() + "/" + endpoint));
            return this;
        }

        Builder register(Class<?> messageClass, String endpoint, Class<? extends MessageResult> resultType) {
            handlers.put(messageClass, new JsonMessageHandler(
                    apiClient, apiClient.baseUri() + "/" + endpoint, resultType));
            return this;
        }

        TelegramHandlerRegistry build() {
            return new TelegramHandlerRegistry(new HashMap<>(handlers));
        }
    }

    static class JsonMessageHandler extends TelegramMessageHandler<TelegramMessage> {

        JsonMessageHandler(TelegramApiClient apiClient, String uri, Class<? extends MessageResult> returnType) {
            super(apiClient, uri, "application/json", returnType);
        }

        JsonMessageHandler(TelegramApiClient apiClient, String uri) {
            this(apiClient, uri, MessageResult.class);
        }

        @Override
        protected void addBody(TelegramMessage message) {
            try {
                final String body = mapper.writeValueAsString(message);
                bodyPublisher.addBodyPart(new TelegramBodyPublisher.SingleBodyPart(body));
            } catch (JsonProcessingException e) {
                throw new RuntimeCamelException("Could not serialize " + message);
            }
        }

    }
}
