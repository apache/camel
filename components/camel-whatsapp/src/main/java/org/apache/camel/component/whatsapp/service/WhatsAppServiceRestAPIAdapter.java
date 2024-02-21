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
package org.apache.camel.component.whatsapp.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.whatsapp.WhatsAppService;
import org.apache.camel.component.whatsapp.model.BaseMessage;
import org.apache.camel.component.whatsapp.model.ContactMessageRequest;
import org.apache.camel.component.whatsapp.model.InteractiveMessageRequest;
import org.apache.camel.component.whatsapp.model.LocationMessageRequest;
import org.apache.camel.component.whatsapp.model.MediaMessageRequest;
import org.apache.camel.component.whatsapp.model.MessageResponse;
import org.apache.camel.component.whatsapp.model.TemplateMessageRequest;
import org.apache.camel.component.whatsapp.model.TextMessageRequest;
import org.apache.camel.component.whatsapp.model.UploadMediaRequest;
import org.apache.camel.component.whatsapp.util.FileUploadStreamSupplier;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java11 Http Client implementation
 */
public class WhatsAppServiceRestAPIAdapter implements WhatsAppService {
    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppServiceRestAPIAdapter.class);

    private static final String MESSAGES_ENDPOINT = "/messages";
    private static final String MEDIA_ENDPOINT = "/media";

    private final Map<Class<?>, WhatsAppServiceRestAPIAdapter.OutgoingMessageHandler<?>> handlers;
    private final ObjectMapper mapper;
    private final String baseUri;
    private final String authorizationToken;

    public WhatsAppServiceRestAPIAdapter(HttpClient client, String baseUri, String apiVersion, String phoneNumberId,
                                         String authorizationToken) {
        this.baseUri = baseUri + "/" + apiVersion + "/" + phoneNumberId;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.authorizationToken = authorizationToken;

        final Map<Class<?>, WhatsAppServiceRestAPIAdapter.OutgoingMessageHandler<?>> m = new HashMap<>();
        m.put(TextMessageRequest.class, new OutgoingPlainMessageHandler(client, mapper, this.baseUri + MESSAGES_ENDPOINT));
        m.put(MediaMessageRequest.class, new OutgoingPlainMessageHandler(client, mapper, this.baseUri + MESSAGES_ENDPOINT));
        m.put(LocationMessageRequest.class, new OutgoingPlainMessageHandler(client, mapper, this.baseUri + MESSAGES_ENDPOINT));
        m.put(ContactMessageRequest.class, new OutgoingPlainMessageHandler(client, mapper, this.baseUri + MESSAGES_ENDPOINT));
        m.put(InteractiveMessageRequest.class,
                new OutgoingPlainMessageHandler(client, mapper, this.baseUri + MESSAGES_ENDPOINT));
        m.put(UploadMediaRequest.class, new OutgoingMediaMessageHandler(client, mapper, this.baseUri + MEDIA_ENDPOINT));
        m.put(TemplateMessageRequest.class, new OutgoingPlainMessageHandler(client, mapper, this.baseUri + MESSAGES_ENDPOINT));

        this.handlers = m;
    }

    @Override
    public void sendMessage(Exchange exchange, AsyncCallback callback, BaseMessage message) {
        @SuppressWarnings("unchecked")
        final WhatsAppServiceRestAPIAdapter.OutgoingMessageHandler<BaseMessage> handler
                = (WhatsAppServiceRestAPIAdapter.OutgoingMessageHandler<BaseMessage>) handlers
                        .get(message.getClass());

        ObjectHelper.notNull(handler, "handler");

        try {
            handler.sendMessage(exchange, callback, message, authorizationToken);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeCamelException("Could not send message " + message, e);
        } catch (IOException e) {
            throw new RuntimeCamelException("Could not send message " + message, e);
        }
    }

    static class OutgoingMediaMessageHandler extends WhatsAppServiceRestAPIAdapter.OutgoingMessageHandler<UploadMediaRequest> {

        public OutgoingMediaMessageHandler(HttpClient httpClient, ObjectMapper mapper, String uri,
                                           Class<? extends MessageResponse> resultClass) {
            super(httpClient, mapper, uri, null, resultClass);
        }

        public OutgoingMediaMessageHandler(HttpClient httpClient, ObjectMapper mapper, String uri) {
            this(httpClient, mapper, uri, MessageResponse.class);
        }

        @Override
        protected void addBody(Builder builder, UploadMediaRequest message) {
            Map<Object, Object> formData = new HashMap<>();
            formData.put("messaging_product", "whatsapp");
            formData.put("file", message.getUploadMedia());

            String boundary = new BigInteger(256, new Random()).toString();
            try {
                builder.POST(ofStreamPart(formData, boundary));
            } catch (IOException e) {
                throw new RuntimeCamelException("Could not serialize " + message, e);
            }

            builder.header("content-type", "multipart/form-data; boundary=" + boundary);
        }

        public static BodyPublisher ofStreamPart(Map<Object, Object> data, String boundary) throws IOException {
            Supplier<? extends InputStream> streamSupplier = new FileUploadStreamSupplier(data, boundary).generate();

            return BodyPublishers.ofInputStream(streamSupplier);
        }
    }

    static class OutgoingPlainMessageHandler extends WhatsAppServiceRestAPIAdapter.OutgoingMessageHandler<BaseMessage> {

        public OutgoingPlainMessageHandler(HttpClient httpClient, ObjectMapper mapper, String uri,
                                           Class<? extends MessageResponse> returnType) {
            super(httpClient, mapper, uri, "application/json", returnType);
        }

        public OutgoingPlainMessageHandler(HttpClient httpClient, ObjectMapper mapper, String uri) {
            this(httpClient, mapper, uri, MessageResponse.class);
        }

        @Override
        protected void addBody(Builder builder, BaseMessage message) {
            try {
                final String body = mapper.writeValueAsString(message);
                BodyPublisher bodyPublisher = BodyPublishers.ofString(body);
                builder.POST(bodyPublisher);
            } catch (JsonProcessingException e) {
                throw new RuntimeCamelException("Could not serialize " + message, e);
            }
        }

    }

    abstract static class OutgoingMessageHandler<T extends BaseMessage> {
        protected final ObjectMapper mapper;
        private final HttpClient httpClient;
        private final String contentType;
        private final String uri;
        private final Class<? extends MessageResponse> resultClass;

        public OutgoingMessageHandler(HttpClient httpClient, ObjectMapper mapper, String uri, String contentType,
                                      Class<? extends MessageResponse> resultClass) {
            this.resultClass = resultClass;
            this.httpClient = httpClient;
            this.mapper = mapper;
            this.uri = uri;
            this.contentType = contentType;
        }

        public void sendMessage(Exchange exchange, AsyncCallback callback, T message, String authorizationToken)
                throws IOException, InterruptedException {
            HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder();
            if (contentType != null) {
                httpRequestBuilder.header("content-type", contentType);
            }
            httpRequestBuilder.header("Authorization", "Bearer " + authorizationToken);
            httpRequestBuilder.header("Accept", "application/json");

            addBody(httpRequestBuilder, message);

            httpRequestBuilder.uri(URI.create(this.uri));

            CompletableFuture<HttpResponse<String>> asyncResponse
                    = httpClient.sendAsync(httpRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            asyncResponse.thenAccept(response -> {
                try {
                    int statusCode = response.statusCode();
                    LOG.info("Response received with status {} and body {}", statusCode, response.body());
                    final boolean success = statusCode >= 200 && statusCode < 300;
                    if (success) {
                        final Object result = mapper.readValue(response.body(), resultClass);

                        exchange.getMessage().setBody(result);
                    } else {
                        LOG.debug("Error response Headers {}", response.headers());
                        RuntimeException exception = new RuntimeCamelException(
                                uri + " responded: " + statusCode + " and body: " + response.body());
                        exchange.setException(exception);
                        throw exception;
                    }
                } catch (JsonProcessingException e) {
                    exchange.setException(e);
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                } finally {
                    callback.done(false);
                }
            });
        }

        protected abstract void addBody(HttpRequest.Builder builder, T message);
    }

}
