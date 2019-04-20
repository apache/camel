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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.camel.component.telegram.TelegramService;
import org.apache.camel.component.telegram.model.EditMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.OutgoingAudioMessage;
import org.apache.camel.component.telegram.model.OutgoingDocumentMessage;
import org.apache.camel.component.telegram.model.OutgoingMessage;
import org.apache.camel.component.telegram.model.OutgoingPhotoMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.OutgoingVideoMessage;
import org.apache.camel.component.telegram.model.SendLocationMessage;
import org.apache.camel.component.telegram.model.SendVenueMessage;
import org.apache.camel.component.telegram.model.StopMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.model.WebhookInfo;
import org.apache.camel.component.telegram.model.WebhookResult;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;

/**
 * Adapts the {@code RestBotAPI} to the {@code TelegramService} interface.
 */
public class TelegramServiceRestBotAPIAdapter implements TelegramService {

    private RestBotAPI api;

    public TelegramServiceRestBotAPIAdapter() {
        this.api = JAXRSClientFactory.create(RestBotAPI.BOT_API_DEFAULT_URL, RestBotAPI.class, Collections.singletonList(providerByCustomObjectMapper()));
        WebClient.getConfig(this.api).getHttpConduit().getClient().setAllowChunking(false);
    }

    public TelegramServiceRestBotAPIAdapter(RestBotAPI api) {
        this.api = api;
    }

    @Override
    public UpdateResult getUpdates(String authorizationToken, Long offset, Integer limit, Integer timeoutSeconds) {
        return api.getUpdates(authorizationToken, offset, limit, timeoutSeconds);
    }

    @Override
    public boolean setWebhook(String authorizationToken, String url) {
        WebhookResult res = api.setWebhook(authorizationToken, new WebhookInfo(url));
        return res.isOk() && res.isResult();
    }

    @Override
    public boolean removeWebhook(String authorizationToken) {
        WebhookResult res = api.setWebhook(authorizationToken, new WebhookInfo(""));
        return res.isOk() && res.isResult();
    }

    @Override
    public Object sendMessage(String authorizationToken, OutgoingMessage message) {
        Object resultMessage;

        if (message instanceof OutgoingTextMessage) {
            resultMessage = this.sendMessage(authorizationToken, (OutgoingTextMessage) message);
        } else if (message instanceof OutgoingPhotoMessage) {
            resultMessage = this.sendMessage(authorizationToken, (OutgoingPhotoMessage) message);
        } else if (message instanceof OutgoingAudioMessage) {
            resultMessage = this.sendMessage(authorizationToken, (OutgoingAudioMessage) message);
        } else if (message instanceof OutgoingVideoMessage) {
            resultMessage = this.sendMessage(authorizationToken, (OutgoingVideoMessage) message);
        } else if (message instanceof OutgoingDocumentMessage) {
            resultMessage = this.sendMessage(authorizationToken, (OutgoingDocumentMessage) message);
        } else if (message instanceof SendLocationMessage) {
            resultMessage = api.sendLocation(authorizationToken, (SendLocationMessage) message);
        } else if (message instanceof EditMessageLiveLocationMessage) {
            resultMessage = api.editMessageLiveLocation(authorizationToken, (EditMessageLiveLocationMessage) message);
        } else if (message instanceof StopMessageLiveLocationMessage) {
            resultMessage = api.stopMessageLiveLocation(authorizationToken, (StopMessageLiveLocationMessage) message);
        } else if (message instanceof SendVenueMessage) {
            resultMessage = api.sendVenue(authorizationToken, (SendVenueMessage) message);
        } else {
            throw new IllegalArgumentException("Unsupported message type " + (message != null ? message.getClass().getName() : null));
        }

        return resultMessage;
    }

    private MessageResult sendMessage(String authorizationToken, OutgoingTextMessage message) {
        return api.sendMessage(authorizationToken, message);
    }

    private MessageResult sendMessage(String authorizationToken, OutgoingPhotoMessage message) {
        List<Attachment> parts = new LinkedList<>();

        fillCommonMediaParts(parts, message);

        parts.add(buildMediaPart("photo", message.getFilenameWithExtension(), message.getPhoto()));
        if (message.getCaption() != null) {
            parts.add(buildTextPart("caption", message.getCaption()));
        }

        return api.sendPhoto(authorizationToken, parts);
    }

    private MessageResult sendMessage(String authorizationToken, OutgoingAudioMessage message) {
        List<Attachment> parts = new LinkedList<>();

        fillCommonMediaParts(parts, message);

        parts.add(buildMediaPart("audio", message.getFilenameWithExtension(), message.getAudio()));
        if (message.getTitle() != null) {
            parts.add(buildTextPart("title", message.getTitle()));
        }
        if (message.getDurationSeconds() != null) {
            parts.add(buildTextPart("duration", String.valueOf(message.getDurationSeconds())));
        }
        if (message.getPerformer() != null) {
            parts.add(buildTextPart("performer", message.getPerformer()));
        }

        return api.sendAudio(authorizationToken, parts);
    }

    private MessageResult sendMessage(String authorizationToken, OutgoingVideoMessage message) {
        List<Attachment> parts = new LinkedList<>();

        fillCommonMediaParts(parts, message);

        parts.add(buildMediaPart("video", message.getFilenameWithExtension(), message.getVideo()));
        if (message.getCaption() != null) {
            parts.add(buildTextPart("caption", message.getCaption()));
        }
        if (message.getDurationSeconds() != null) {
            parts.add(buildTextPart("duration", String.valueOf(message.getDurationSeconds())));
        }
        if (message.getWidth() != null) {
            parts.add(buildTextPart("width", String.valueOf(message.getWidth())));
        }
        if (message.getHeight() != null) {
            parts.add(buildTextPart("height", String.valueOf(message.getHeight())));
        }

        return api.sendVideo(authorizationToken, parts);
    }

    private MessageResult sendMessage(String authorizationToken, OutgoingDocumentMessage message) {
        List<Attachment> parts = new LinkedList<>();

        fillCommonMediaParts(parts, message);

        parts.add(buildMediaPart("document", message.getFilenameWithExtension(), message.getDocument()));
        if (message.getCaption() != null) {
            parts.add(buildTextPart("caption", message.getCaption()));
        }

        return api.sendDocument(authorizationToken, parts);
    }

    private void fillCommonMediaParts(List<Attachment> parts, OutgoingMessage message) {
        parts.add(buildTextPart("chat_id", message.getChatId()));

        if (message.getReplyToMessageId() != null) {
            parts.add(buildTextPart("reply_to_message_id", String.valueOf(message.getReplyToMessageId())));
        }
        if (message.getDisableNotification() != null) {
            parts.add(buildTextPart("disable_notification", String.valueOf(message.getDisableNotification())));
        }
    }

    private Attachment buildTextPart(String name, String value) {
        MultivaluedMap m = new MultivaluedHashMap<>();
        m.putSingle("Content-Type", "text/plain");
        m.putSingle("Content-Disposition", "form-data; name=\"" + escapeMimeName(name) + "\"");

        Attachment a = new Attachment(m, value);
        return a;
    }

    private Attachment buildMediaPart(String name, String fileNameWithExtension, byte[] value) {
        Attachment a = new Attachment(name, new ByteArrayInputStream(value),
                new ContentDisposition("form-data; name=\"" + escapeMimeName(name) + "\"; filename=\"" + escapeMimeName(fileNameWithExtension) + "\""));
        return a;
    }

    private String escapeMimeName(String name) {
        return name.replace("\"", "");
    }
    
    private JacksonJsonProvider providerByCustomObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        return new JacksonJsonProvider(mapper);
    }    
}