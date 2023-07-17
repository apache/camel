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
import java.util.Collections;

import org.apache.camel.component.telegram.TelegramParseMode;
import org.apache.camel.component.telegram.model.CallbackGame;
import org.apache.camel.component.telegram.model.EditMessageCaptionMessage;
import org.apache.camel.component.telegram.model.EditMessageDelete;
import org.apache.camel.component.telegram.model.EditMessageMediaMessage;
import org.apache.camel.component.telegram.model.EditMessageReplyMarkupMessage;
import org.apache.camel.component.telegram.model.EditMessageTextMessage;
import org.apache.camel.component.telegram.model.ForceReply;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;
import org.apache.camel.component.telegram.model.InputMediaAnimation;
import org.apache.camel.component.telegram.model.InputMediaAudio;
import org.apache.camel.component.telegram.model.InputMediaPhoto;
import org.apache.camel.component.telegram.model.InputMediaVideo;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.MessageResultGameScores;
import org.apache.camel.component.telegram.model.OutgoingAudioMessage;
import org.apache.camel.component.telegram.model.OutgoingDocumentMessage;
import org.apache.camel.component.telegram.model.OutgoingGameMessage;
import org.apache.camel.component.telegram.model.OutgoingGetGameHighScoresMessage;
import org.apache.camel.component.telegram.model.OutgoingMessage;
import org.apache.camel.component.telegram.model.OutgoingPhotoMessage;
import org.apache.camel.component.telegram.model.OutgoingSetGameScoreMessage;
import org.apache.camel.component.telegram.model.OutgoingStickerMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.OutgoingVideoMessage;
import org.apache.camel.component.telegram.model.ReplyKeyboardMarkup;
import org.apache.camel.component.telegram.model.ReplyKeyboardRemove;
import org.apache.camel.component.telegram.model.ReplyMarkup;
import org.apache.camel.component.telegram.model.SendLocationMessage;
import org.apache.camel.component.telegram.model.SendVenueMessage;
import org.apache.camel.component.telegram.util.TelegramApiConfig;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfEnvironmentVariable(named = "TELEGRAM_AUTHORIZATION_TOKEN", matches = ".*")
public class TelegramServiceIT extends TelegramTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramServiceIT.class);

    protected TelegramApiConfig getTelegramApiConfig() {
        return TelegramApiConfig.fromEnv();
    }

    @Test
    public void testGetUpdates() {
        /* Telegram bots by design see neither their own messages nor other bots' messages.
         * So, for this test to succeed a human should have sent some messages to the bot manually
         * before running the test */
        IncomingMessage res = consumer.receiveBody("telegram://bots", 5000, IncomingMessage.class);
        LOG.debug("Chat ID: {} - use this for running the tests", res.getChat().getId());
        assertNotNull(res);
    }

    @Test
    public void testSendMessage() {

        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is an auto-generated message from the Bot");
        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
    }

    @Test
    public void testSendMessageHtml() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is a <b>HTML</b> <i>auto-generated</i> message from the Bot");
        msg.setParseMode(TelegramParseMode.HTML.getCode());

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
    }

    @Test
    public void testSendMessageMarkdown() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is a *Markdown* _auto-generated_ message from the Bot");
        msg.setParseMode(TelegramParseMode.MARKDOWN.getCode());

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
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

        msg.setReplyMarkup(replyMarkup);

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
    }

    @Test
    public void testSendMessageWithForceReply() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("Choose one option!");
        msg.setReplyMarkup(new ForceReply(true));

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendMessageWithReplyKeyboardRemove() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("Choose one option!");
        msg.setReplyMarkup(new ReplyKeyboardRemove(true));

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendMessageDisablingCustomKeyboard() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("Your answer was accepted!");

        ReplyKeyboardMarkup replyMarkup = ReplyKeyboardMarkup.builder()
                .removeKeyboard(true)
                .build();

        msg.setReplyMarkup(replyMarkup);

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
    }

    @Test
    public void testSendFull() {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setChatId(chatId);
        msg.setText("This is an *auto-generated* message from the Bot");
        msg.setDisableWebPagePreview(true);
        msg.setParseMode("Markdown");
        msg.setDisableNotification(false);

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
    }

    @Test
    public void testSendPhoto() throws IOException {
        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
    }

    @Test
    public void testSendPhotoWithForceReply() throws IOException {
        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");
        msg.setReplyMarkup(
                new ForceReply(true));

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendPhotoWithInlineKeyboardMarkup() throws IOException {
        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.png");
        msg.setReplyMarkup(
                InlineKeyboardMarkup.builder()
                        .addRow(Collections.singletonList(InlineKeyboardButton.builder().text("test")
                                .url("https://camel.apache.org").build()))
                        .build());

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
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

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
    }

    @Test
    public void testSendAudio() throws IOException {
        byte[] audio = TelegramTestUtil.createSampleAudio();

        OutgoingAudioMessage msg = new OutgoingAudioMessage();
        msg.setAudio(audio);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("audio.mp3");

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
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
        ReplyMarkup replyMarkup = InlineKeyboardMarkup.builder()
                .addRow(Collections.singletonList(InlineKeyboardButton.builder().text("test")
                        .url("https://camel.apache.org").build()))
                .build();
        msg.setReplyMarkup(replyMarkup);

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendVideo() throws IOException {
        byte[] video = TelegramTestUtil.createSampleVideo();

        OutgoingVideoMessage msg = new OutgoingVideoMessage();
        msg.setVideo(video);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("video.mp4");

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
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

        ReplyMarkup replyMarkup = InlineKeyboardMarkup.builder()
                .addRow(Collections.singletonList(InlineKeyboardButton.builder().text("test")
                        .url("https://camel.apache.org").build()))
                .build();
        msg.setReplyMarkup(replyMarkup);

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendDocument() throws IOException {
        byte[] document = TelegramTestUtil.createSampleDocument();

        OutgoingDocumentMessage msg = new OutgoingDocumentMessage();
        msg.setDocument(document);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.txt");

        Assertions.assertDoesNotThrow(() -> template.requestBody(String.format("telegram://bots?chatId=%s", chatId), msg));
    }

    @Test
    public void testSendDocumentFull() throws IOException {
        byte[] document = TelegramTestUtil.createSampleDocument();

        OutgoingDocumentMessage msg = new OutgoingDocumentMessage();
        msg.setDocument(document);
        msg.setChatId(chatId);
        msg.setFilenameWithExtension("file.txt");
        msg.setCaption("A document");
        ReplyMarkup replyMarkup = InlineKeyboardMarkup.builder()
                .addRow(Collections.singletonList(InlineKeyboardButton.builder().text("test")
                        .url("https://camel.apache.org").build()))
                .build();
        msg.setReplyMarkup(
                replyMarkup);

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendLocationFull() {

        double latitude = 39.220409;
        double longitude = -8.894500;
        SendLocationMessage msg = new SendLocationMessage();
        msg.setLatitude(latitude);
        msg.setLongitude(longitude);
        msg.setReplyMarkup(InlineKeyboardMarkup.builder()
                .addRow(Collections.singletonList(InlineKeyboardButton.builder().text("test")
                        .url("https://camel.apache.org").build()))
                .build());

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendVenueFull() {

        double latitude = 39.220409;
        double longitude = -8.894500;
        SendVenueMessage msg = new SendVenueMessage();
        msg.setLatitude(latitude);
        msg.setLongitude(longitude);
        msg.setReplyMarkup(InlineKeyboardMarkup.builder()
                .addRow(Collections.singletonList(InlineKeyboardButton.builder().text("test")
                        .url("https://camel.apache.org").build()))
                .build());

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendStickerViaImage() throws IOException {
        byte[] document = TelegramTestUtil.createSampleImage("WEBP");

        OutgoingStickerMessage msg = OutgoingStickerMessage.createWithImage(document, "file.webp", null, null, null);

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendStickerViaFileId() {
        String fileId = "CAADBAADEQADmDVxAkmg3XnDZam0FgQ";

        OutgoingStickerMessage msg = OutgoingStickerMessage.createWithFileId(fileId, null, null, null);

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testSendStickerViaUrl() {
        String imageUri = "https://www.gstatic.com/webp/gallery/1.sm.webp?dcb_=0.7185987052045011";

        OutgoingStickerMessage msg = OutgoingStickerMessage.createWithUrl(imageUri, null, null, null);

        MessageResult result = sendMessage(msg);
        Assertions.assertTrue(result.isOk());
    }

    @Test
    public void testEditTextMessage() {

        // Given
        final String originalText = "ORIGINAL";
        final String newText = "NEW";

        // Send message
        Integer messageId = sendSampleTextMessageAndGetMessageId(originalText);

        // Edit message
        EditMessageTextMessage msg = EditMessageTextMessage.builder()
                .chatId(chatId)
                .text(newText)
                .messageId(messageId)
                .build();

        MessageResult response = sendMessage(msg);

        Assertions.assertEquals(newText, response.getMessage().getText());
    }

    @Test
    public void testEditTextMessageWithUrl() {

        //Given
        final String originalText = "ORIGINAL";
        final String urlDescription = "Inline URL";
        final String url = "http://www.example.com/";
        final String newTextWithUrl = String.format("<a href=\"%s\">%s</a>", url, urlDescription);

        // Send message
        Integer messageId = sendSampleTextMessageAndGetMessageId(originalText);

        // Edit message
        EditMessageTextMessage msg = EditMessageTextMessage.builder()
                .chatId(chatId)
                .text(newTextWithUrl)
                .parseMode("HTML")
                .messageId(messageId)
                .build();

        MessageResult response = sendMessage(msg);

        Assertions.assertEquals(urlDescription, response.getMessage().getText());
        Assertions.assertEquals(url, response.getMessage().getEntities().get(0).getUrl());
        Assertions.assertEquals("text_link", response.getMessage().getEntities().get(0).getType());
    }

    @Test
    void testEditCaptionMessage() throws IOException {

        String originalCaption = "original caption";
        String newCaption = "edited caption";

        //Send message with caption
        Integer messageId = sendSamplePhotoMessageAndGetMessageId(originalCaption);

        //edit message
        EditMessageCaptionMessage editMessageCaptionMessage = EditMessageCaptionMessage.builder()
                .caption(newCaption)
                .messageId(messageId)
                .build();

        MessageResult message = sendMessage(editMessageCaptionMessage);
        Assertions.assertEquals(newCaption, message.getMessage().getCaption());
    }

    @Test
    void testEditCaptionMessageWithUrl() throws IOException {

        //Given
        String originalCaption = "original caption";
        final String urlDescription = "Inline URL";
        final String url = "http://www.example.com/";
        final String newCaptionWithUrl = String.format("<a href=\"%s\">%s</a>", url, urlDescription);

        //Send message
        Integer messageId = sendSamplePhotoMessageAndGetMessageId(originalCaption);

        //edit message
        EditMessageCaptionMessage editMessageCaptionMessage = EditMessageCaptionMessage.builder()
                .caption(newCaptionWithUrl)
                .messageId(messageId)
                .parseMode("HTML")
                .build();

        MessageResult message = sendMessage(editMessageCaptionMessage);
        IncomingMessage incomingMessage = message.getMessage();
        Assertions.assertEquals(urlDescription, incomingMessage.getCaption());
        Assertions.assertEquals(url, incomingMessage.getCaptionEntities().get(0).getUrl());
        Assertions.assertEquals("text_link", incomingMessage.getCaptionEntities().get(0).getType());
    }

    @Disabled("Unlike testEditMediaToAnimation, this does not work. It needs to be investigated")
    @Test
    void testEditMediaToAudio() throws IOException {

        //given
        String mediaUrl = "https://file-examples.com/wp-content/uploads/2017/11/file_example_MP3_700KB.mp3";
        String caption = "caption";

        //send photo message
        Integer messageId = sendSamplePhotoMessageAndGetMessageId();

        //update to audio message
        InputMediaAudio inputMediaAudio = new InputMediaAudio();
        inputMediaAudio.setCaption(caption);
        inputMediaAudio.setMedia(mediaUrl);

        EditMessageMediaMessage editMessageMediaMessage = EditMessageMediaMessage.builder()
                .messageId(messageId)
                .media(inputMediaAudio)
                .build();

        MessageResult message = sendMessage(editMessageMediaMessage);
        IncomingMessage incomingMessage = message.getMessage();
        Assertions.assertNull(incomingMessage.getPhoto());
        Assertions.assertNotNull(incomingMessage.getAudio());
        Assertions.assertEquals(caption, incomingMessage.getCaption());
    }

    @Test
    void testEditMediaToAnimation() throws IOException {

        //given
        String mediaUrl = "http://file-examples.com/storage/fe783a5cbb6323602a28c66/2017/10/file_example_GIF_500kB.gif";
        String caption = "caption";

        //send photo message
        Integer messageId = sendSamplePhotoMessageAndGetMessageId();

        //update to animation
        InputMediaAnimation inputMediaAnimation = new InputMediaAnimation();
        inputMediaAnimation.setCaption(caption);
        inputMediaAnimation.setMedia(mediaUrl);

        EditMessageMediaMessage editMessageMediaMessage = EditMessageMediaMessage.builder()
                .messageId(messageId)
                .media(inputMediaAnimation)
                .build();

        MessageResult message = sendMessage(editMessageMediaMessage);
        IncomingMessage incomingMessage = message.getMessage();
        Assertions.assertNull(incomingMessage.getPhoto());
        Assertions.assertNotNull(incomingMessage.getDocument());
        Assertions.assertEquals(caption, incomingMessage.getCaption());
    }

    @Test
    void testEditMediaToVideo() throws IOException {

        //given
        String mediaUrl = "http://mirrors.standaloneinstaller.com/video-sample/small.mp4";
        String caption = "caption";

        //send photo message
        Integer messageId = sendSamplePhotoMessageAndGetMessageId();

        //update to video message
        InputMediaVideo inputMediaVideo = new InputMediaVideo();
        inputMediaVideo.setCaption(caption);
        inputMediaVideo.setMedia(mediaUrl);

        EditMessageMediaMessage editMessageMediaMessage = EditMessageMediaMessage.builder()
                .messageId(messageId)
                .media(inputMediaVideo)
                .build();

        MessageResult message = sendMessage(editMessageMediaMessage);
        IncomingMessage incomingMessage = message.getMessage();
        Assertions.assertNull(incomingMessage.getPhoto());
        Assertions.assertNotNull(incomingMessage.getVideo());
        Assertions.assertEquals(caption, incomingMessage.getCaption());
    }

    @Test
    void testEditMediaToPhoto() throws IOException {

        //given
        String mediaUrl = "https://sample-videos.com/img/Sample-jpg-image-50kb.jpg";
        String caption = "caption";

        //send photo message
        Integer messageId = sendSamplePhotoMessageAndGetMessageId();

        //update to another photo message
        InputMediaPhoto inputMediaPhoto = new InputMediaPhoto();
        inputMediaPhoto.setCaption(String.format("<b>%s</b>", caption));
        inputMediaPhoto.setParseMode("HTML");
        inputMediaPhoto.setMedia(mediaUrl);

        EditMessageMediaMessage editMessageMediaMessage = EditMessageMediaMessage.builder()
                .messageId(messageId)
                .media(inputMediaPhoto)
                .build();

        MessageResult message = sendMessage(editMessageMediaMessage);
        IncomingMessage incomingMessage = message.getMessage();
        Assertions.assertNotNull(incomingMessage.getPhoto());
        Assertions.assertEquals(caption, incomingMessage.getCaption());
        Assertions.assertEquals("bold", incomingMessage.getCaptionEntities().get(0).getType());
    }

    @Test
    void testEditMarkupMessage() throws IOException {

        //send message
        Integer messageId = sendSamplePhotoMessageAndGetMessageId();

        //edit markup
        InlineKeyboardButton buttonOptionOneI = InlineKeyboardButton.builder()
                .text("camel")
                .url("https://camel.apache.org/")
                .build();

        InlineKeyboardMarkup replyMarkup = InlineKeyboardMarkup.builder()
                .addRow(Collections.singletonList(buttonOptionOneI))
                .build();

        EditMessageReplyMarkupMessage editMessageReplyMarkupMessage = EditMessageReplyMarkupMessage.builder()
                .messageId(messageId)
                .replyMarkup(replyMarkup)
                .build();

        MessageResult incomingMessage = sendMessage(editMessageReplyMarkupMessage);
        Assertions.assertTrue(incomingMessage.isOk());

        Assertions.assertEquals(buttonOptionOneI,
                ((InlineKeyboardMarkup) incomingMessage.getMessage().getReplyMarkup()).getInlineKeyboard()
                        .get(0).get(0));
    }

    @Test
    void testDeleteMessage() {

        //send message
        Integer messageId = sendSampleTextMessageAndGetMessageId("text");

        //delete message
        EditMessageDelete messageDelete = new EditMessageDelete(chatId, messageId);

        MessageResult incomingMessage = sendMessage(messageDelete);
        Assertions.assertTrue(incomingMessage.isOk());
        Assertions.assertTrue(incomingMessage.isResult());
    }

    @Disabled("This one requires manual setup of a game")
    @Test
    void testSendGameMessage() {
        String gameShortName = "gameShortName";
        String gameText = "<game-text>";
        long gameScore = 15L;

        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setCallbackGame(new CallbackGame(gameShortName));
        inlineKeyboardButton.setText(gameText);
        InlineKeyboardMarkup inlineKeyboardMarkup
                = InlineKeyboardMarkup.builder().addRow(Collections.singletonList(inlineKeyboardButton)).build();

        OutgoingGameMessage outgoingGameMessage = new OutgoingGameMessage();
        outgoingGameMessage.setGameShortName(gameShortName);
        outgoingGameMessage.setReplyMarkup(inlineKeyboardMarkup);

        // send game
        MessageResult message = sendMessage(outgoingGameMessage);
        IncomingMessage incomingMessage = message.getMessage();

        // set game score
        OutgoingSetGameScoreMessage outgoingSetGameScoreMessage = new OutgoingSetGameScoreMessage();
        outgoingSetGameScoreMessage.setMessageId(incomingMessage.getMessageId());
        outgoingSetGameScoreMessage.setScore(gameScore);
        outgoingSetGameScoreMessage.setUserId(incomingMessage.getFrom().getId());
        MessageResult setGameScoreMessage = sendMessage(outgoingSetGameScoreMessage);

        // get game high scores
        OutgoingGetGameHighScoresMessage outgoingGetGameHighScoresMessage = new OutgoingGetGameHighScoresMessage();
        outgoingGetGameHighScoresMessage.setMessageId(incomingMessage.getMessageId());
        outgoingGetGameHighScoresMessage.setUserId(incomingMessage.getFrom().getId());

        MessageResultGameScores getGameHighScores = sendMessage(outgoingGetGameHighScoresMessage);

        Assertions.assertNotNull(incomingMessage.getGame());
        Assertions.assertNotNull(incomingMessage.getReplyMarkup());
        Assertions.assertTrue(setGameScoreMessage.isOk());
        Assertions.assertTrue(getGameHighScores.isOk());
        Assertions.assertNotNull(getGameHighScores.getGameHighScores());
    }

    private Integer sendSamplePhotoMessageAndGetMessageId(String caption) throws IOException {
        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setFilenameWithExtension("file.png");
        msg.setCaption(caption);

        MessageResult message = sendMessage(msg);
        return message.getMessage().getMessageId().intValue();
    }

    private Integer sendSampleTextMessageAndGetMessageId(String text) {
        OutgoingTextMessage msg = new OutgoingTextMessage();
        msg.setText(text);
        MessageResult message = sendMessage(msg);
        return message.getMessage().getMessageId().intValue();
    }

    private Integer sendSamplePhotoMessageAndGetMessageId() throws IOException {
        byte[] image = TelegramTestUtil.createSampleImage("PNG");

        OutgoingPhotoMessage msg = new OutgoingPhotoMessage();
        msg.setPhoto(image);
        msg.setFilenameWithExtension("file.png");
        MessageResult message = sendMessage(msg);
        return message.getMessage().getMessageId().intValue();
    }

    private <T extends MessageResult> T sendMessage(OutgoingMessage outgoingMessage) {
        return (T) template.requestBody(String.format("telegram://bots?chatId=%s", chatId), outgoingMessage);
    }
}
