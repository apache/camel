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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
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
import org.apache.camel.component.telegram.model.WebhookInfo;
import org.apache.camel.component.telegram.model.WebhookResult;
import org.apache.camel.support.GZIPHelper;
import org.apache.camel.util.IOHelper;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.asynchttpclient.request.body.multipart.ByteArrayPart;
import org.asynchttpclient.request.body.multipart.StringPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.asynchttpclient.util.HttpUtils.extractContentTypeCharsetAttribute;
import static org.asynchttpclient.util.MiscUtils.withDefault;

/**
 * Adapts the {@code RestBotAPI} to the {@code TelegramService} interface.
 */
public class TelegramServiceRestBotAPIAdapter implements TelegramService {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramServiceRestBotAPIAdapter.class);

    private final Map<Class<?>, OutgoingMessageHandler<?>> handlers;
    private final AsyncHttpClient asyncHttpClient;
    private final ObjectMapper mapper;
    private final String baseUri;

    public TelegramServiceRestBotAPIAdapter(AsyncHttpClient asyncHttpClient, int bufferSize, String telegramBaseUri,
            String authorizationToken) {
        this.asyncHttpClient = asyncHttpClient;
        this.baseUri = telegramBaseUri + "/bot" + authorizationToken;
        this.mapper = new ObjectMapper();
        final Map<Class<?>, OutgoingMessageHandler<?>> m = new HashMap<>();
        m.put(OutgoingTextMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/sendMessage"));
        m.put(OutgoingPhotoMessage.class, new OutgoingPhotoMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri));
        m.put(OutgoingAudioMessage.class, new OutgoingAudioMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri));
        m.put(OutgoingVideoMessage.class, new OutgoingVideoMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri));
        m.put(OutgoingDocumentMessage.class, new OutgoingDocumentMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri));
        m.put(OutgoingStickerMessage.class, new OutgoingStickerMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri));
        m.put(OutgoingGameMessage.class,
            new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/sendGame"));
        m.put(SendLocationMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/sendLocation"));
        m.put(EditMessageLiveLocationMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/editMessageLiveLocation"));
        m.put(StopMessageLiveLocationMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/stopMessageLiveLocation"));
        m.put(SendVenueMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/sendVenue"));
        m.put(EditMessageTextMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/editMessageText"));
        m.put(EditMessageCaptionMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/editMessageCaption"));
        m.put(EditMessageMediaMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/editMessageMedia"));
        m.put(EditMessageDelete.class, new OutgoingPlainMessageHandler(
                asyncHttpClient, bufferSize, mapper, baseUri + "/deleteMessage"));
        m.put(EditMessageReplyMarkupMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/editMessageReplyMarkup"));
        m.put(OutgoingCallbackQueryMessage.class, new OutgoingPlainMessageHandler(
                asyncHttpClient, bufferSize, mapper, baseUri + "/answerCallbackQuery"));
        m.put(OutgoingSetGameScoreMessage.class,
                new OutgoingPlainMessageHandler(asyncHttpClient, bufferSize, mapper, baseUri + "/setGameScore"));
        m.put(OutgoingGetGameHighScoresMessage.class, new OutgoingPlainMessageHandler(
                asyncHttpClient, bufferSize, mapper, baseUri + "/getGameHighScores", MessageResultGameScores.class));
        m.put(OutgoingAnswerInlineQuery.class, new OutgoingPlainMessageHandler(
            asyncHttpClient, bufferSize, mapper, baseUri + "/answerInlineQuery"));
        this.handlers = m;
    }

    @Override
    public UpdateResult getUpdates(Long offset, Integer limit, Integer timeoutSeconds) {
        final String uri = baseUri + "/getUpdates";
        final RequestBuilder request = new RequestBuilder("GET")
                .setUrl(uri);
        if (offset != null) {
            request.addQueryParam("offset", String.valueOf(offset));
        }
        if (limit != null) {
            request.addQueryParam("limit", String.valueOf(limit));
        }
        if (timeoutSeconds != null) {
            request.addQueryParam("timeout", String.valueOf(timeoutSeconds));
        }
        return sendSyncRequest(request.build(), UpdateResult.class);
    }

    <T> T sendSyncRequest(final Request request, Class<T> resultType) {
        try {
            final Response response = asyncHttpClient.executeRequest(request).get();
            int code = response.getStatusCode();
            if (code >= 200 && code < 300) {
                try {
                    final String responseBody = response.getResponseBody();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Received body for {} {}: {}", request.getMethod(), request.getUrl(), responseBody);
                    }
                    return mapper.readValue(responseBody, resultType);
                } catch (IOException e) {
                    throw new RuntimeException(
                            "Could not parse the response from " + request.getMethod() + " " + request.getUrl(), e);
                }
            } else {
                throw new RuntimeException(
                        "Could not " + request.getMethod() + " " + request.getUrl() + ": " + response.getStatusCode() + " "
                                + response.getStatusText());
            }
        } catch (ExecutionException e) {
            throw new RuntimeException("Could not request " + request.getMethod() + " " + request.getUrl(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean setWebhook(String url) {
        final String uri = baseUri + "/setWebhook";
        final RequestBuilder request = new RequestBuilder("POST")
                .setUrl(uri);
        final WebhookInfo message = new WebhookInfo(url);
        try {
            final String body = mapper.writeValueAsString(message);
            request.setBody(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not serialize " + message);
        }
        WebhookResult res = sendSyncRequest(request.build(), WebhookResult.class);
        return res.isOk() && res.isResult();
    }

    @Override
    public boolean removeWebhook() {
        return setWebhook("");
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

        public OutgoingPlainMessageHandler(AsyncHttpClient asyncHttpClient, int bufferSize, ObjectMapper mapper,
                                           String uri, Class<? extends MessageResult> returnType) {
            super(asyncHttpClient, bufferSize, mapper, uri, "application/json", returnType);
        }

        public OutgoingPlainMessageHandler(AsyncHttpClient asyncHttpClient, int bufferSize, ObjectMapper mapper,
                                           String uri) {
            this(asyncHttpClient, bufferSize, mapper, uri, MessageResult.class);
        }

        @Override
        protected void addBody(RequestBuilder builder, OutgoingMessage message) {
            try {
                final String body = mapper.writeValueAsString(message);
                builder.setBody(body);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Could not serialize " + message);
            }
        }

    }

    static class OutgoingAudioMessageHandler extends OutgoingMessageHandler<OutgoingAudioMessage> {

        public OutgoingAudioMessageHandler(AsyncHttpClient asyncHttpClient, int bufferSize, ObjectMapper mapper,
                String baseUri) {
            super(asyncHttpClient, bufferSize, mapper, baseUri + "/sendAudio", null, MessageResult.class);
        }

        @Override
        protected void addBody(RequestBuilder builder, OutgoingAudioMessage message) {
            fillCommonMediaParts(builder, message);
            buildMediaPart(builder, "audio", message.getFilenameWithExtension(), message.getAudio());
            buildTextPart(builder, "title", message.getTitle());
            buildTextPart(builder, "duration", message.getDurationSeconds());
            buildTextPart(builder, "performer", message.getPerformer());
            buildTextPart(builder, "reply_markup", message.replyMarkupJson());
        }

    }

    static class OutgoingVideoMessageHandler extends OutgoingMessageHandler<OutgoingVideoMessage> {

        public OutgoingVideoMessageHandler(AsyncHttpClient asyncHttpClient, int bufferSize, ObjectMapper mapper,
                String baseUri) {
            super(asyncHttpClient, bufferSize, mapper, baseUri + "/sendVideo", null, MessageResult.class);
        }

        @Override
        protected void addBody(RequestBuilder builder, OutgoingVideoMessage message) {
            fillCommonMediaParts(builder, message);
            buildMediaPart(builder, "video", message.getFilenameWithExtension(), message.getVideo());
            buildTextPart(builder, "caption", message.getCaption());
            buildTextPart(builder, "duration", message.getDurationSeconds());
            buildTextPart(builder, "width", message.getWidth());
            buildTextPart(builder, "height", message.getHeight());
            buildTextPart(builder, "reply_markup", message.replyMarkupJson());
        }

    }

    static class OutgoingDocumentMessageHandler extends OutgoingMessageHandler<OutgoingDocumentMessage> {

        public OutgoingDocumentMessageHandler(AsyncHttpClient asyncHttpClient, int bufferSize, ObjectMapper mapper,
                String baseUri) {
            super(asyncHttpClient, bufferSize, mapper, baseUri + "/sendDocument", null, MessageResult.class);
        }

        @Override
        protected void addBody(RequestBuilder builder, OutgoingDocumentMessage message) {
            fillCommonMediaParts(builder, message);
            buildMediaPart(builder, "document", message.getFilenameWithExtension(), message.getDocument());
            buildTextPart(builder, "caption", message.getCaption());
            buildTextPart(builder, "reply_markup", message.replyMarkupJson());
        }

    }

    static class OutgoingPhotoMessageHandler extends OutgoingMessageHandler<OutgoingPhotoMessage> {

        public OutgoingPhotoMessageHandler(AsyncHttpClient asyncHttpClient, int bufferSize, ObjectMapper mapper,
                String baseUri) {
            super(asyncHttpClient, bufferSize, mapper, baseUri + "/sendPhoto", null, MessageResult.class);
        }

        @Override
        protected void addBody(RequestBuilder builder, OutgoingPhotoMessage message) {
            fillCommonMediaParts(builder, message);
            buildMediaPart(builder, "photo", message.getFilenameWithExtension(), message.getPhoto());
            buildTextPart(builder, "caption", message.getCaption());
            buildTextPart(builder, "reply_markup", message.replyMarkupJson());
        }

    }

    static class OutgoingStickerMessageHandler extends OutgoingMessageHandler<OutgoingStickerMessage> {
        public OutgoingStickerMessageHandler(AsyncHttpClient asyncHttpClient, int bufferSize, ObjectMapper mapper,
                                             String baseUri) {
            super(asyncHttpClient, bufferSize, mapper, baseUri + "/sendSticker", null, MessageResult.class);
        }

        @Override
        protected void addBody(RequestBuilder builder, OutgoingStickerMessage message) {
            fillCommonMediaParts(builder, message);
            if (message.getSticker() != null) {
                buildTextPart(builder, "sticker", message.getSticker());
            } else {
                buildMediaPart(builder, "sticker", message.getFilenameWithExtension(), message.getStickerImage());
            }
        }
    }

    abstract static class OutgoingMessageHandler<T extends OutgoingMessage> {
        protected final ObjectMapper mapper;
        private final AsyncHttpClient asyncHttpClient;
        private final int bufferSize;
        private final String contentType;
        private final String uri;
        private final Class<? extends MessageResult> resultClass;

        public OutgoingMessageHandler(AsyncHttpClient asyncHttpClient, int bufferSize, ObjectMapper mapper, String uri,
                String contentType, Class<? extends MessageResult> resultClass) {
            this.resultClass = resultClass;
            this.asyncHttpClient = asyncHttpClient;
            this.bufferSize = bufferSize;
            this.mapper = mapper;
            this.uri = uri;
            this.contentType = contentType;
        }

        public void sendMessage(Exchange exchange, AsyncCallback callback, T message) {
            final RequestBuilder builder = new RequestBuilder("POST")
                    .setUrl(uri);
            if (contentType != null) {
                builder.setHeader("Content-Type", contentType);
            }
            builder.setHeader("Accept", "application/json");
            addBody(builder, message);
            asyncHttpClient.executeRequest(builder.build(),
                    new TelegramAsyncHandler(exchange, callback, uri, bufferSize, mapper, resultClass));
        }

        protected abstract void addBody(RequestBuilder builder, T message);

        protected void fillCommonMediaParts(RequestBuilder builder, OutgoingMessage message) {
            buildTextPart(builder, "chat_id", message.getChatId());
            buildTextPart(builder, "reply_to_message_id", message.getReplyToMessageId());
            buildTextPart(builder, "disable_notification", message.getDisableNotification());
        }

        protected void buildTextPart(RequestBuilder builder, String name, Object value) {
            if (value != null) {
                builder.addBodyPart(new StringPart(name, String.valueOf(value), "text/plain", StandardCharsets.UTF_8));
            }
        }

        protected void buildMediaPart(RequestBuilder builder, String name, String fileNameWithExtension, byte[] value) {
            builder.addBodyPart(
                    new ByteArrayPart(name, value, "application/octet-stream", StandardCharsets.UTF_8, fileNameWithExtension));
        }

    }

    private static final class TelegramAsyncHandler implements AsyncHandler<Exchange> {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final String url;
        private final ByteArrayOutputStream os;
        private final ObjectMapper mapper;
        private int statusCode;
        private String statusText;
        private String contentType;
        private String contentEncoding;
        private Charset charset;
        private Class<? extends MessageResult> onCompletedType;

        private TelegramAsyncHandler(Exchange exchange, AsyncCallback callback, String url, int bufferSize,
                ObjectMapper mapper, Class<? extends MessageResult> onCompletedType) {
            this.onCompletedType = onCompletedType;
            this.exchange = exchange;
            this.callback = callback;
            this.url = url;
            this.os = new ByteArrayOutputStream(bufferSize);
            this.mapper = mapper;
        }

        @Override
        public void onThrowable(Throwable t) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("{} onThrowable {}", exchange.getExchangeId(), t);
            }
            exchange.setException(t);
            callback.done(false);
        }

        @Override
        public Exchange onCompleted() throws Exception {
            if (LOG.isTraceEnabled()) {
                LOG.trace("{} onCompleted", exchange.getExchangeId());
            }
            try {
                // copy from output stream to input stream
                os.flush();
                os.close();
                final boolean success = statusCode >= 200 && statusCode < 300;
                try (InputStream maybeGzStream = new ByteArrayInputStream(os.toByteArray());
                        InputStream is = GZIPHelper.uncompressGzip(contentEncoding, maybeGzStream);
                        Reader r = new InputStreamReader(is, charset)) {

                    if (success) {
                        final Object result;
                        if (LOG.isTraceEnabled()) {
                            final String body = IOHelper.toString(r);
                            LOG.trace("Received body for {}: {}", url, body);
                            result = mapper.readValue(body, onCompletedType);
                        } else {
                            result = mapper.readValue(r, onCompletedType);
                        }

                        exchange.getMessage().setBody(result);
                    } else {
                        throw new RuntimeException(
                                url + " responded: " + statusCode + " " + statusText + " " + IOHelper.toString(r));
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Could not parse the response from " + url, e);
                }
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                // signal we are done
                callback.done(false);
            }
            return exchange;
        }

        @Override
        public String toString() {
            return "AhcAsyncHandler for exchangeId: " + exchange.getExchangeId() + " -> " + url;
        }

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart bodyPart)
                throws Exception {
            // write body parts to stream, which we will bind to the Camel Exchange in onComplete
            os.write(bodyPart.getBodyPartBytes());
            if (LOG.isTraceEnabled()) {
                LOG.trace("{} onBodyPartReceived {} bytes", exchange.getExchangeId(), bodyPart.length());
            }
            return State.CONTINUE;
        }

        @Override
        public State onStatusReceived(HttpResponseStatus responseStatus) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("{} onStatusReceived {}", exchange.getExchangeId(), responseStatus);
            }
            statusCode = responseStatus.getStatusCode();
            statusText = responseStatus.getStatusText();
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(HttpHeaders headers) {
            contentEncoding = headers.get("Content-Encoding");
            contentType = headers.get("Content-Type");
            charset = withDefault(extractContentTypeCharsetAttribute(contentType), StandardCharsets.UTF_8);
            return State.CONTINUE;
        }
    }
}
