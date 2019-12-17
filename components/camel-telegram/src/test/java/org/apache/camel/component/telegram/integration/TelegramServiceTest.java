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
package org.apache.camel.component.telegram.integration;

import java.io.IOException;
import java.util.Arrays;

import org.apache.camel.component.telegram.TelegramParseMode;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.OutgoingAudioMessage;
import org.apache.camel.component.telegram.model.OutgoingDocumentMessage;
import org.apache.camel.component.telegram.model.OutgoingPhotoMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.OutgoingVideoMessage;
import org.apache.camel.component.telegram.model.ReplyKeyboardMarkup;
import org.apache.camel.component.telegram.util.TelegramApiConfig;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TelegramServiceTest extends TelegramTestSupport {

    protected TelegramApiConfig getTelegramApiConfig() {
        return TelegramApiConfig.fromEnv();
    }

    @Test
    public void testGetUpdates() {
        /* Telegram bots by design see neither their own messages nor other bots' messages.
         * So, for this test to succeed a human should have sent some messages to the bot manually
         * before running the test */
        IncomingMessage res = consumer.receiveBody("telegram://bots", 5000, IncomingMessage.class);
        assertNotNull(res);
    }

    @Test
    public void testSendMessage() {

        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is an auto-generated message from the Bot");
        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendMessageHtml() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is a <b>HTML</b> <i>auto-generated</i> message from the Bot");
        msg.setParseMode(TelegramParseMode.HTML.getCode());

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendMessageMarkdown() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is a *Markdown* _auto-generated_ message from the Bot");
        msg.setParseMode(TelegramParseMode.MARKDOWN.getCode());

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendMessageWithKeyboard() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("Choose one option!");

        InlineKeyboardButton buttonOptionOneI = InlineKeyboardButton.builder()
                .text("Option One - I").build();

        InlineKeyboardButton buttonOptionOneII = InlineKeyboardButton.builder()
                .text("Option One - II").build();

        InlineKeyboardButton buttonOptionTwoI = InlineKeyboardButton.builder()
                .text("Option Two - I").build();

        InlineKeyboardButton buttonOptionThreeI = InlineKeyboardButton.builder()
                .text("Option Three - I").build();

        InlineKeyboardButton buttonOptionThreeII = InlineKeyboardButton.builder()
                .text("Option Three - II").build();

        ReplyKeyboardMarkup replyMarkup = ReplyKeyboardMarkup.builder()
                .keyboard()
                    .addRow(Arrays.asList(buttonOptionOneI, buttonOptionOneII))
                    .addRow(Arrays.asList(buttonOptionTwoI))
                    .addRow(Arrays.asList(buttonOptionThreeI, buttonOptionThreeII))
                    .close()
                .oneTimeKeyboard(true)
                .build();

        msg.setReplyKeyboardMarkup(replyMarkup);

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendMessageDisablingCustomKeyboard() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("Your answer was accepted!");

        ReplyKeyboardMarkup replyMarkup = ReplyKeyboardMarkup.builder()
                .removeKeyboard(true)
                .build();

        msg.setReplyKeyboardMarkup(replyMarkup);

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendFull() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is an *auto-generated* message from the Bot");
        msg.setDisableWebPagePreview(true);
        msg.setParseMode("Markdown");
        msg.setDisableNotification(false);

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendPhoto() throws IOException {
        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendPhotoFull() throws IOException {
        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");
        msg.setCaption("Photo");
        msg.setDisableNotification(false);

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendAudio() throws IOException {
        byte[] audio = TelegramTestUtil.createSampleAudio();

        OutgoingAudioMessage msg = new OutgoingAudioMessage();
        msg.setAudio(audio);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("audio.mp3");

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendAudioFull() throws IOException {
        byte[] audio = TelegramTestUtil.createSampleAudio();

        OutgoingAudioMessage msg = new OutgoingAudioMessage();
        msg.setAudio(audio);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("audio.mp3");
        msg.setTitle("Audio");
        msg.setDurationSeconds(5);
        msg.setPerformer("Myself");

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendVideo() throws IOException {
        byte[] video = TelegramTestUtil.createSampleVideo();

        OutgoingVideoMessage msg = new OutgoingVideoMessage();
        msg.setVideo(video);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("video.mp4");

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendVideoFull() throws IOException {
        byte[] video = TelegramTestUtil.createSampleVideo();

        OutgoingVideoMessage msg = new OutgoingVideoMessage();
        msg.setVideo(video);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("video.mp4");
        msg.setDurationSeconds(2);
        msg.setCaption("A Video");
        msg.setWidth(90);
        msg.setHeight(50);

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendDocument() throws IOException {
        byte[] document = TelegramTestUtil.createSampleDocument();

        OutgoingDocumentMessage msg = new OutgoingDocumentMessage();
        msg.setDocument(document);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.txt");

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

    @Test
    public void testSendDocumentFull() throws IOException {
        byte[] document = TelegramTestUtil.createSampleDocument();

        OutgoingDocumentMessage msg = new OutgoingDocumentMessage();
        msg.setDocument(document);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");
        msg.setCaption("A document");

        template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg);
    }

}
