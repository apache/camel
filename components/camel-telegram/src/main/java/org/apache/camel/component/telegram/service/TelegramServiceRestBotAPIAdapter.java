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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.telegram.TelegramException;
import org.apache.camel.component.telegram.TelegramService;
import org.apache.camel.component.telegram.model.EditMessageCaptionMessage;
import org.apache.camel.component.telegram.model.EditMessageDelete;
import org.apache.camel.component.telegram.model.EditMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.EditMessageMediaMessage;
import org.apache.camel.component.telegram.model.EditMessageReplyMarkupMessage;
import org.apache.camel.component.telegram.model.EditMessageTextMessage;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.MessageResultGameScores;
import org.apache.camel.component.telegram.model.OutgoingAnswerInlineQuery;
import org.apache.camel.component.telegram.model.OutgoingAudioMessage;
import org.apache.camel.component.telegram.model.OutgoingCallbackQueryMessage;
import org.apache.camel.component.telegram.model.OutgoingDocumentMessage;
import org.apache.camel.component.telegram.model.OutgoingGameMessage;
import org.apache.camel.component.telegram.model.OutgoingGetGameHighScoresMessage;
import org.apache.camel.component.telegram.model.OutgoingMessage;
import org.apache.camel.component.telegram.model.OutgoingPhotoMessage;
import org.apache.camel.component.telegram.model.OutgoingSetGameScoreMessage;
import org.apache.camel.component.telegram.model.OutgoingStickerMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.OutgoingVideoMessage;
import org.apache.camel.component.telegram.model.SendLocationMessage;
import org.apache.camel.component.telegram.model.SendVenueMessage;
import org.apache.camel.component.telegram.model.StopMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.model.WebhookResult;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts the {@code RestBotAPI} to the {@code TelegramService} interface.
 */
public class TelegramServiceRestBotAPIAdapter implements TelegramService {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramServiceRestBotAPIAdapter.class);

    private final Map<Class<?>, OutgoingMessageHandler<?>> handlers;
    private final HttpClient client;

    @Deprecated
    private final ObjectMapper mapper;
    private final String baseUri;

    public TelegramServiceRestBotAPIAdapter(HttpClient client, String telegramBaseUri,
                                            String authorizationToken, int bufferSize) {
        this.client = client;

        this.baseUri = telegramBaseUri + "/bot" + authorizationToken;
        this.mapper = new ObjectMapper();
        final Map<Class<?>, OutgoingMessageHandler<?>> m = new HashMap<>();
        m.put(OutgoingTextMessage.class, new OutgoingPlainMessageHandler(client, mapper, baseUri + "/sendMessage", bufferSize));
        m.put(OutgoingPhotoMessage.class, new OutgoingPhotoMessageHandler(client, mapper, baseUri, bufferSize));
        m.put(OutgoingAudioMessage.class, new OutgoingAudioMessageHandler(client, mapper, baseUri, bufferSize));
        m.put(OutgoingVideoMessage.class, new OutgoingVideoMessageHandler(client, mapper, baseUri, bufferSize));
        m.put(OutgoingDocumentMessage.class, new OutgoingDocumentMessageHandler(client, mapper, baseUri, bufferSize));
        m.put(OutgoingStickerMessage.class, new OutgoingStickerMessageHandler(client, mapper, baseUri, bufferSize));
        m.put(OutgoingGameMessage.class, new OutgoingPlainMessageHandler(client, mapper, baseUri + "/sendGame", bufferSize));
        m.put(SendLocationMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/sendLocation", bufferSize));
        m.put(EditMessageLiveLocationMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/editMessageLiveLocation", bufferSize));
        m.put(StopMessageLiveLocationMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/stopMessageLiveLocation", bufferSize));
        m.put(SendVenueMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/sendVenue", bufferSize));
        m.put(EditMessageTextMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/editMessageText", bufferSize));
        m.put(EditMessageCaptionMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/editMessageCaption", bufferSize));
        m.put(EditMessageMediaMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/editMessageMedia", bufferSize));
        m.put(EditMessageDelete.class, new OutgoingPlainMessageHandler(
                client,
                mapper, baseUri + "/deleteMessage", bufferSize));
        m.put(EditMessageReplyMarkupMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/editMessageReplyMarkup", bufferSize));
        m.put(OutgoingCallbackQueryMessage.class, new OutgoingPlainMessageHandler(
                client,
                mapper, baseUri + "/answerCallbackQuery", bufferSize));
        m.put(OutgoingSetGameScoreMessage.class,
                new OutgoingPlainMessageHandler(client, mapper, baseUri + "/setGameScore", bufferSize));
        m.put(OutgoingGetGameHighScoresMessage.class, new OutgoingPlainMessageHandler(
                client,
                mapper, baseUri + "/getGameHighScores", MessageResultGameScores.class, bufferSize));
        m.put(OutgoingAnswerInlineQuery.class, new OutgoingPlainMessageHandler(
                client,
                mapper, baseUri + "/answerInlineQuery", bufferSize));
        this.handlers = m;
    }

    @Override
    public UpdateResult getUpdates(Long offset, Integer limit, Integer timeoutSeconds) {
        Map<String, Object> parameters = new HashMap<>();
        if (offset != null) {
            parameters.put("offset", String.valueOf(offset));
        }
        if (limit != null) {
            parameters.put("limit", String.valueOf(limit));
        }
        if (timeoutSeconds != null) {
            parameters.put("timeout", String.valueOf(timeoutSeconds));
        }

        try {
            String uri = URISupport.appendParametersToURI(baseUri + "/getUpdates", parameters);

            final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            return sendSyncRequest(request, UpdateResult.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    <T> T sendSyncRequest(final HttpRequest request, Class<T> resultType) {
        try {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                final String responseBody = response.body();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Received body for {} {}: {}", request.method(), request.uri(), responseBody);
                }
                return mapper.readValue(responseBody, resultType);
            } else {
                throw new TelegramException(
                        "Could not " + request.method() + " " + request.uri() + ": " + response.statusCode() + " "
                                            + response.body(),
                        response.statusCode(), response.body());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeCamelException(
                    "Could not parse the response from " + request.method() + " " + request.uri(), e);
        } catch (IOException e) {
            throw new RuntimeCamelException("Could not request " + request.method() + " " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public boolean setWebhook(String url) {
        final String uri = baseUri + "/setWebhook?url=" + url;

        final HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(uri)).GET();

        WebhookResult res = sendSyncRequest(request.build(), WebhookResult.class);
        return res.isOk() && res.isResult();
    }

    @Override
    public boolean removeWebhook() {
        final String uri = baseUri + "/deleteWebhook";

        final HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(uri)).GET();

        WebhookResult res = sendSyncRequest(request.build(), WebhookResult.class);
        return res.isOk() && res.isResult();
    }

    @Override
    public void sendMessage(Exchange exchange, AsyncCallback callback, OutgoingMessage message) {
        @SuppressWarnings("unchecked")
        final OutgoingMessageHandler<OutgoingMessage> handler = (OutgoingMessageHandler<OutgoingMessage>) handlers
                .get(message.getClass());
        if (handler == null) {
            throw new IllegalArgumentException(
                    "Unsupported message type " + (message.getClass().getName()));
        }
        handler.sendMessage(exchange, callback, message);
    }

    static class OutgoingPlainMessageHandler extends OutgoingMessageHandler<OutgoingMessage> {

        public OutgoingPlainMessageHandler(HttpClient client, ObjectMapper mapper,
                                           String uri, Class<? extends MessageResult> returnType, int bufferSize) {
            super(client, mapper, uri, "application/json", returnType, bufferSize);
        }

        public OutgoingPlainMessageHandler(HttpClient client, ObjectMapper mapper,
                                           String uri, int bufferSize) {
            this(client, mapper, uri, MessageResult.class, bufferSize);
        }

        @Override
        protected void addBody(OutgoingMessage message) {
            try {
                final String body = mapper.writeValueAsString(message);

                bodyPublisher.addBodyPart(new TelegramBodyPublisher.SingleBodyPart(body));

            } catch (JsonProcessingException e) {
                throw new RuntimeCamelException("Could not serialize " + message);
            }
        }

    }

    static class OutgoingAudioMessageHandler extends OutgoingMessageHandler<OutgoingAudioMessage> {

        public OutgoingAudioMessageHandler(HttpClient client, ObjectMapper mapper,
                                           String baseUri, int bufferSize) {
            super(client, mapper, baseUri + "/sendAudio", null, MessageResult.class, bufferSize);
        }

        @Override
        protected void addBody(OutgoingAudioMessage message) {
            fillCommonMediaParts(message);
            buildMediaPart("audio", message.getFilenameWithExtension(), message.getAudio());
            buildTextPart("title", message.getTitle());
            buildTextPart("duration", message.getDurationSeconds());
            buildTextPart("performer", message.getPerformer());
            buildTextPart("reply_markup", message.replyMarkupJson());
        }

    }

    static class OutgoingVideoMessageHandler extends OutgoingMessageHandler<OutgoingVideoMessage> {

        public OutgoingVideoMessageHandler(HttpClient client, ObjectMapper mapper,
                                           String baseUri, int bufferSize) {
            super(client, mapper, baseUri + "/sendVideo", null, MessageResult.class, bufferSize);
        }

        @Override
        protected void addBody(OutgoingVideoMessage message) {
            fillCommonMediaParts(message);
            buildMediaPart("video", message.getFilenameWithExtension(), message.getVideo());
            buildTextPart("caption", message.getCaption());
            buildTextPart("duration", message.getDurationSeconds());
            buildTextPart("width", message.getWidth());
            buildTextPart("height", message.getHeight());
            buildTextPart("reply_markup", message.replyMarkupJson());
        }
    }

    static class OutgoingDocumentMessageHandler extends OutgoingMessageHandler<OutgoingDocumentMessage> {

        public OutgoingDocumentMessageHandler(HttpClient client, ObjectMapper mapper,
                                              String baseUri, int bufferSize) {
            super(client, mapper, baseUri + "/sendDocument", null, MessageResult.class, bufferSize);
        }

        @Override
        protected void addBody(OutgoingDocumentMessage message) {
            fillCommonMediaParts(message);
            buildMediaPart("document", message.getFilenameWithExtension(), message.getDocument());
            buildTextPart("caption", message.getCaption());
            buildTextPart("reply_markup", message.replyMarkupJson());
        }

    }

    static class OutgoingPhotoMessageHandler extends OutgoingMessageHandler<OutgoingPhotoMessage> {

        public OutgoingPhotoMessageHandler(HttpClient client, ObjectMapper mapper,
                                           String baseUri, int bufferSize) {
            super(client, mapper, baseUri + "/sendPhoto", null, MessageResult.class, bufferSize);
        }

        @Override
        protected void addBody(OutgoingPhotoMessage message) {
            fillCommonMediaParts(message);
            buildMediaPart("photo", message.getFilenameWithExtension(), message.getPhoto());
            buildTextPart("caption", message.getCaption());
            buildTextPart("reply_markup", message.replyMarkupJson());
        }

    }

    static class OutgoingStickerMessageHandler extends OutgoingMessageHandler<OutgoingStickerMessage> {
        public OutgoingStickerMessageHandler(HttpClient client, ObjectMapper mapper,
                                             String baseUri, int bufferSize) {
            super(client, mapper, baseUri + "/sendSticker", null, MessageResult.class, bufferSize);
        }

        @Override
        protected void addBody(OutgoingStickerMessage message) {
            fillCommonMediaParts(message);
            if (message.getSticker() != null) {
                buildTextPart("sticker", message.getSticker());
            } else {
                buildMediaPart("sticker", message.getFilenameWithExtension(), message.getStickerImage());
            }
        }
    }

}
