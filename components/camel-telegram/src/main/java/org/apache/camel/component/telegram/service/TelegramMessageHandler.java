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

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.telegram.TelegramMessage;
import org.apache.camel.component.telegram.model.MessageResult;

abstract class TelegramMessageHandler<T extends TelegramMessage> {
    protected final ObjectMapper mapper;

    private final TelegramApiClient apiClient;
    private final String contentType;
    private final String uri;
    private final Class<? extends MessageResult> resultClass;

    protected TelegramMessageHandler(TelegramApiClient apiClient, String uri,
                                     String contentType, Class<? extends MessageResult> resultClass) {
        this.apiClient = apiClient;
        this.mapper = apiClient.mapper();
        this.uri = uri;
        this.contentType = contentType;
        this.resultClass = resultClass;
    }

    public void sendMessage(Exchange exchange, AsyncCallback callback, T message) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(uri));

        // the body of this request only: the producer may send from several threads at once
        TelegramBodyPublisher bodyPublisher = new TelegramBodyPublisher(apiClient.bufferSize());
        addBody(bodyPublisher, message);

        if (bodyPublisher.getBodyParts().size() > 1) {
            builder.setHeader("Content-type", "multipart/form-data; boundary=\"" + bodyPublisher.getBoundary() + "\"");
        } else {
            if (contentType != null) {
                builder.setHeader("Content-type", contentType);
            }
        }

        builder.setHeader("Accept", "application/json");

        builder.POST(bodyPublisher.newPublisher());
        try {
            final TelegramAsyncHandler telegramAsyncHandler
                    = new TelegramAsyncHandler(uri, resultClass, mapper, exchange, callback);

            apiClient.client().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(telegramAsyncHandler::handleCompressedResponse).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void addBody(TelegramBodyPublisher bodyPublisher, T message);

    static <T> void buildTextPart(TelegramBodyPublisher bodyPublisher, String name, T value) {
        if (value != null) {
            TelegramBodyPublisher.MultilineBodyPart<T> bodyPart
                    = new TelegramBodyPublisher.MultilineBodyPart<>(name, value, "text/plain");

            bodyPublisher.addBodyPart(bodyPart);
        }
    }

    void buildMediaPart(TelegramBodyPublisher bodyPublisher, String name, String fileNameWithExtension, byte[] value) {
        TelegramBodyPublisher.MultilineBodyPart<byte[]> bodyPart
                = new TelegramBodyPublisher.MultilineBodyPart<>(name, value, "application/octet-stream", null);

        bodyPart.addHeader("filename", fileNameWithExtension);

        bodyPublisher.addBodyPart(bodyPart);
    }
}
