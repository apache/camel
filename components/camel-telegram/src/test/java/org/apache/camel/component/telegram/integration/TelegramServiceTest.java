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
package org.apache.camel.component.telegram.integration;

import java.io.IOException;

import org.apache.camel.component.telegram.TelegramParseMode;
import org.apache.camel.component.telegram.TelegramService;
import org.apache.camel.component.telegram.TelegramServiceProvider;
import org.apache.camel.component.telegram.model.OutgoingAudioMessage;
import org.apache.camel.component.telegram.model.OutgoingDocumentMessage;
import org.apache.camel.component.telegram.model.OutgoingPhotoMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.OutgoingVideoMessage;
import org.apache.camel.component.telegram.model.UpdateResult;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests if the BotAPI are working correctly.
 */
public class TelegramServiceTest {

    private static String authorizationToken;

    private static String chatId;

    @BeforeClass
    public static void init() {
        authorizationToken = System.getenv("TELEGRAM_AUTHORIZATION_TOKEN");
        chatId = System.getenv("TELEGRAM_CHAT_ID");
    }

    @Test
    public void testGetUpdates() {
        TelegramService service = TelegramServiceProvider.get().getService();

        UpdateResult res = service.getUpdates(authorizationToken, null, null, null);

        Assert.assertNotNull(res);
        Assert.assertTrue(res.isOk());
    }

    @Test
    public void testSendMessage() {
        TelegramService service = TelegramServiceProvider.get().getService();

        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is an auto-generated message from the Bot");

        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendMessageHtml() {
        TelegramService service = TelegramServiceProvider.get().getService();

        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is a <b>HTML</b> <i>auto-generated</i> message from the Bot");
        msg.setParseMode(TelegramParseMode.HTML.getCode());

        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendMessageMarkdown() {
        TelegramService service = TelegramServiceProvider.get().getService();

        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is a *Markdown* _auto-generated_ message from the Bot");
        msg.setParseMode(TelegramParseMode.MARKDOWN.getCode());

        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendFull() {
        TelegramService service = TelegramServiceProvider.get().getService();

        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is an *auto-generated* message from the Bot");
        msg.setDisableWebPagePreview(true);
        msg.setParseMode("Markdown");
        msg.setDisableNotification(false);

        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendPhoto() throws IOException {
        TelegramService service = TelegramServiceProvider.get().getService();

        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");


        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendPhotoFull() throws IOException {
        TelegramService service = TelegramServiceProvider.get().getService();

        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");
        msg.setCaption("Photo");
        msg.setDisableNotification(false);


        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendAudio() throws IOException {
        TelegramService service = TelegramServiceProvider.get().getService();

        byte[] audio = TelegramTestUtil.createSampleAudio();

        OutgoingAudioMessage msg = new OutgoingAudioMessage();
        msg.setAudio(audio);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("audio.mp3");


        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendAudioFull() throws IOException {
        TelegramService service = TelegramServiceProvider.get().getService();

        byte[] audio = TelegramTestUtil.createSampleAudio();

        OutgoingAudioMessage msg = new OutgoingAudioMessage();
        msg.setAudio(audio);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("audio.mp3");
        msg.setTitle("Audio");
        msg.setDurationSeconds(5);
        msg.setPerformer("Myself");

        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendVideo() throws IOException {
        TelegramService service = TelegramServiceProvider.get().getService();

        byte[] video = TelegramTestUtil.createSampleVideo();

        OutgoingVideoMessage msg = new OutgoingVideoMessage();
        msg.setVideo(video);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("video.mp4");


        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendVideoFull() throws IOException {
        TelegramService service = TelegramServiceProvider.get().getService();

        byte[] video = TelegramTestUtil.createSampleVideo();

        OutgoingVideoMessage msg = new OutgoingVideoMessage();
        msg.setVideo(video);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("video.mp4");
        msg.setDurationSeconds(2);
        msg.setCaption("A Video");
        msg.setWidth(90);
        msg.setHeight(50);

        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendDocument() throws IOException {
        TelegramService service = TelegramServiceProvider.get().getService();

        byte[] document = TelegramTestUtil.createSampleDocument();

        OutgoingDocumentMessage msg = new OutgoingDocumentMessage();
        msg.setDocument(document);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.txt");

        service.sendMessage(authorizationToken, msg);
    }

    @Test
    public void testSendDocumentFull() throws IOException {
        TelegramService service = TelegramServiceProvider.get().getService();

        byte[] document = TelegramTestUtil.createSampleDocument();

        OutgoingDocumentMessage msg = new OutgoingDocumentMessage();
        msg.setDocument(document);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");
        msg.setCaption("A document");

        service.sendMessage(authorizationToken, msg);
    }

}
