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
package org.apache.camel.component.soroushbot.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.component.soroushbot.IOUtils;
import org.apache.camel.component.soroushbot.models.MinorType;
import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.models.SoroushMessage;
import org.apache.camel.component.soroushbot.models.response.UploadFileResponse;
import org.apache.camel.component.soroushbot.utils.SoroushException;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.glassfish.jersey.media.sse.EventInput;
import org.glassfish.jersey.media.sse.InboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check if soroush BOT Api work as expected
 */
@Disabled("Need the token to work")
public class SoroushServiceTest {
    static String authorizationToken;
    static String receiverId;
    private static SoroushService soroushService;

    @BeforeAll
    public static void setUp() {
        authorizationToken = System.getenv("soroushBotAuthorizationToken");
        receiverId = System.getenv("soroushBotReceiverId");
        assertTrue(authorizationToken != null && receiverId != null,
                "you need to define `soroushBotAuthorizationToken` and "
                                                                     + "`soroushBotReceiverId` environment variable in order to do integration test ");
        soroushService = SoroushService.get();
        soroushService.setAlternativeUrl(null);
    }

    /**
     * try to connect to soroush. if any problem occurs an exception will throw and this test will fail.
     */
    @Test
    public void connectToGetMessageEndPoint() {
        Client client = ClientBuilder.newBuilder().register(SseFeature.class).build();
        client.property(ClientProperties.CONNECT_TIMEOUT, 2000);
        WebTarget target = client.target(soroushService.generateUrl(authorizationToken, SoroushAction.getMessage, null));
        EventInput eventInput = target.request().get(EventInput.class);
        eventInput.setChunkType(MediaType.SERVER_SENT_EVENTS);
        assertFalse(eventInput.isClosed());
    }

    @Test
    public void canNotReadMessageDueToWrongAuthorizationToken() {
        Client client = ClientBuilder.newBuilder().register(SseFeature.class).build();
        client.property(ClientProperties.CONNECT_TIMEOUT, 2000);
        WebTarget target
                = client.target(soroushService.generateUrl("bad_string" + authorizationToken, SoroushAction.getMessage, null));
        EventInput eventInput = target.request().get(EventInput.class);
        eventInput.setChunkType(MediaType.SERVER_SENT_EVENTS);
        assertFalse(eventInput.isClosed());
        InboundEvent read = eventInput.read();
        assertNull(read);
    }

    @Test
    public void sendMessageToAPerson() throws IOException, SoroushException {
        WebTarget target = soroushService.createSendMessageTarget(authorizationToken, 2000);
        SoroushMessage message = new SoroushMessage();
        message.setBody("content");
        message.setTo(receiverId);
        message.setType(MinorType.TEXT);
        Response response = target.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.json(message));
        soroushService.assertSuccessful(response, null);

    }

    @Test
    public void uploadAndDownloadFile() throws IOException, SoroushException {
        WebTarget target = soroushService.createUploadFileTarget(authorizationToken, 2000);
        MultiPart multipart = new MultiPart();
        multipart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        String fileData = "data";
        multipart.bodyPart(new StreamDataBodyPart(
                "file", new ByteArrayInputStream(fileData.getBytes()), null, MediaType.APPLICATION_OCTET_STREAM_TYPE));
        Response response = target.request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(multipart, multipart.getMediaType()));
        UploadFileResponse uploadFileResponse = soroushService.assertSuccessful(response, UploadFileResponse.class, null);
        assertNotNull(uploadFileResponse.getFileUrl());

        WebTarget downloadFileTarget
                = soroushService.createDownloadFileTarget(authorizationToken, uploadFileResponse.getFileUrl(), 2000);
        Response downloadResponse = downloadFileTarget.request().get();
        String remoteData = new String(IOUtils.readFully(downloadResponse.readEntity(InputStream.class), -1, false));
        assertEquals(fileData, remoteData, "file contents are identical");

    }
}
