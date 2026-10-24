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
package org.apache.camel.component.telegram;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.ForceReply;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.IncomingPhotoSize;
import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;
import org.apache.camel.component.telegram.model.OutgoingAudioMessage;
import org.apache.camel.component.telegram.model.OutgoingGameMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.ReplyKeyboardMarkup;
import org.apache.camel.component.telegram.model.ReplyKeyboardRemove;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramMockRoutes.MockProcessor;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests a producer that sends media information.
 */
public class TelegramProducerMediaTest extends TelegramTestSupport {

    @EndpointInject("direct:telegram")
    private Endpoint endpoint;

    @TempDir
    Path tempDir;

    @Test
    public void testRouteWithTextBytesInTheCharsetOfTheExchange() {
        final MockProcessor<OutgoingTextMessage> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();
        final MockProcessor<byte[]> documents = getMockRoutes().getMock("sendDocument");
        documents.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.TEXT.name());
        ex.setProperty(Exchange.CHARSET_NAME, StandardCharsets.ISO_8859_1.name());
        ex.getIn().setBody("Grüße".getBytes(StandardCharsets.ISO_8859_1));

        template.send(endpoint, ex);

        assertNull(ex.getException());
        final OutgoingTextMessage message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertEquals("my-id", message.getChatId());
        assertEquals("Grüße", message.getText());
        assertTrue(documents.getRecordedMessages().isEmpty());
    }

    @Test
    public void testRouteWithPngImageStream() throws Exception {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendPhoto");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.PHOTO_PNG.name());
        byte[] image = TelegramTestUtil.createSampleImage("PNG");
        ex.getIn().setBody(new ByteArrayInputStream(image));

        template.send(endpoint, ex);

        assertNull(ex.getException());
        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertTrue(contains(message, image));
        assertMultipartFilename(message, "photo", "photo.png");
    }

    @Test
    public void testRouteWithDocumentFile() throws Exception {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendDocument");
        mockProcessor.clearRecordedMessages();

        byte[] document = TelegramTestUtil.createSampleDocument();
        File file = Files.write(tempDir.resolve("sample.txt"), document).toFile();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.DOCUMENT);
        ex.getIn().setBody(file);

        template.send(endpoint, ex);

        assertNull(ex.getException());
        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertTrue(contains(message, document));
        assertMultipartFilename(message, "document", "file");
    }

    @Test
    public void testRouteWithDocumentLargerThanTheBufferSize() throws Exception {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendDocument");
        mockProcessor.clearRecordedMessages();

        byte[] document = new byte[3 * 1024 * 1024];
        Arrays.fill(document, (byte) 'x');
        document[document.length - 1] = 'y';

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.DOCUMENT);
        ex.getIn().setBody(document);

        template.send(endpoint, ex);

        assertNull(ex.getException());
        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertTrue(message.length > document.length);
        assertTrue(contains(message, "xxy\r\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testRouteWithAudioWritesNumbersAndUtf8Text() throws Exception {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendAudio");
        mockProcessor.clearRecordedMessages();

        OutgoingAudioMessage audio = new OutgoingAudioMessage();
        audio.setAudio(TelegramTestUtil.createSampleAudio());
        audio.setFilenameWithExtension("audio.mp3");
        audio.setTitle("Grüße \u2603");
        audio.setDurationSeconds(42);

        Exchange ex = endpoint.createExchange();
        ex.getIn().setBody(audio);

        template.send(endpoint, ex);

        assertNull(ex.getException());
        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertMultipartText(message, "title", "Grüße \u2603");
        assertMultipartText(message, "duration", "42");
    }

    @Test
    public void testIncomingMessageWithoutTextIsNotSent() {
        final MockProcessor<OutgoingTextMessage> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();

        IncomingPhotoSize photo = new IncomingPhotoSize();
        photo.setFileId("f1");
        IncomingMessage incoming = new IncomingMessage();
        incoming.setMessageId(2L);
        incoming.setPhoto(Collections.singletonList(photo));

        Exchange ex = endpoint.createExchange();
        ex.getIn().setBody(incoming);

        template.send(endpoint, ex);

        assertNull(ex.getException());
        // the producer waits for the answer of a request, so a message sent would be recorded by now
        assertTrue(mockProcessor.getRecordedMessages().isEmpty());
    }

    @Test
    public void testRouteWithPngImage() throws Exception {

        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendPhoto");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Photo");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.PHOTO_PNG.name());
        byte[] image = TelegramTestUtil.createSampleImage("PNG");
        ex.getIn().setBody(image);

        template.send(endpoint, ex);

        /* message contains a multipart body */
        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, image));
        assertMultipartFilename(message, "photo", "photo.png");
        assertMultipartText(message, "caption", "Photo");
    }

    @Test
    public void testRouteWithJpgImage() throws Exception {

        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendPhoto");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Photo");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.PHOTO_JPG); // without using
                                                                                                 // .name()
        byte[] image = TelegramTestUtil.createSampleImage("JPG");
        ex.getIn().setBody(image);

        template.send(endpoint, ex);

        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, image));
        assertMultipartFilename(message, "photo", "photo.jpg");
        assertMultipartText(message, "caption", "Photo");
    }

    @Test
    public void testRouteWithJpgImageAndForceReply() throws IOException {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendPhoto");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Photo");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.PHOTO_JPG);
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_MARKUP, new ForceReply(true));

        byte[] image = TelegramTestUtil.createSampleImage("JPG");
        ex.getIn().setBody(image);

        template.send(endpoint, ex);

        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);

        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, image));
        assertMultipartFilename(message, "photo", "photo.jpg");
        assertMultipartText(message, "caption", "Photo");
        assertMultipartText(message, "reply_markup", new ForceReply(true).toJson());
    }

    @Test
    public void testRouteWithJpgAndReplyKeyboardRemove() throws IOException {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendPhoto");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Photo");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.PHOTO_JPG);
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_MARKUP, new ReplyKeyboardRemove(true));

        byte[] image = TelegramTestUtil.createSampleImage("JPG");
        ex.getIn().setBody(image);

        template.send(endpoint, ex);

        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);

        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, image));
        assertMultipartFilename(message, "photo", "photo.jpg");
        assertMultipartText(message, "caption", "Photo");
        assertMultipartText(message, "reply_markup", new ReplyKeyboardRemove(true).toJson());
    }

    @Test
    public void testRouteWithJpgAndInlineKeyboardMarkup() throws IOException {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendPhoto");
        mockProcessor.clearRecordedMessages();

        InlineKeyboardMarkup ik = InlineKeyboardMarkup.builder()
                .addRow(Collections.singletonList(InlineKeyboardButton.builder().text("test")
                        .url("https://camel.apache.org").build()))
                .build();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Photo");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.PHOTO_JPG);
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_MARKUP, ik);

        byte[] image = TelegramTestUtil.createSampleImage("JPG");
        ex.getIn().setBody(image);

        template.send(endpoint, ex);

        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);

        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, image));
        assertMultipartFilename(message, "photo", "photo.jpg");
        assertMultipartText(message, "caption", "Photo");
        assertMultipartText(message, "reply_markup", ik.toJson());
    }

    @Test
    public void testRouteWithAudio() throws Exception {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendAudio");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Audio");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.AUDIO);
        byte[] audio = TelegramTestUtil.createSampleAudio();
        ex.getIn().setBody(audio);

        template.send(endpoint, ex);

        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);

        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, audio));
        assertMultipartFilename(message, "audio", "audio.mp3");
        assertMultipartText(message, "title", "Audio");
    }

    @Test
    public void testRouteWithAudioAndReplyMarkup() throws Exception {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendAudio");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Audio");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.AUDIO);
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_MARKUP, new ForceReply(true));
        byte[] audio = TelegramTestUtil.createSampleAudio();
        ex.getIn().setBody(audio);

        template.send(endpoint, ex);

        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);

        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, audio));
        assertMultipartFilename(message, "audio", "audio.mp3");
        assertMultipartText(message, "title", "Audio");
        assertMultipartText(message, "reply_markup", new ForceReply(true).toJson());
    }

    @Test
    public void testRouteWithVideo() throws Exception {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendVideo");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Video");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.VIDEO.name());
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_MARKUP, new ForceReply(true));
        byte[] video = TelegramTestUtil.createSampleVideo();
        ex.getIn().setBody(video);

        template.send(endpoint, ex);

        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, video));
        assertMultipartFilename(message, "video", "video.mp4");
        assertMultipartText(message, "caption", "Video");
        assertMultipartText(message, "reply_markup", new ForceReply(true).toJson());
    }

    @Test
    public void testRouteWithDocument() throws Exception {
        final MockProcessor<byte[]> mockProcessor = getMockRoutes().getMock("sendDocument");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION, "Document");
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.DOCUMENT);
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_MARKUP, new ForceReply(true));
        byte[] document = TelegramTestUtil.createSampleDocument();
        ex.getIn().setBody(document);

        template.send(endpoint, ex);

        final byte[] message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertMultipartText(message, "chat_id", "my-id");
        assertTrue(contains(message, document));
        assertMultipartFilename(message, "document", "file");
        assertMultipartText(message, "caption", "Document");
        assertMultipartText(message, "reply_markup", new ForceReply(true).toJson());
    }

    @Test
    public void testRouteWithText() {
        final MockProcessor<OutgoingTextMessage> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.TEXT.name());
        ex.getIn().setBody("Hello");

        template.send(endpoint, ex);

        final OutgoingTextMessage message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertEquals("my-id", message.getChatId());
        assertEquals("Hello", message.getText());
        assertNull(message.getParseMode());
    }

    @Test
    @Disabled
    public void testRouteWithTextAndCustomKeyBoard() {
        final MockProcessor<OutgoingTextMessage> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();

        OutgoingTextMessage msg = new OutgoingTextMessage.Builder().text("Hello").build();
        withInlineKeyboardContainingTwoRows(msg);

        ex.getIn().setBody(msg);

        template.send(endpoint, ex);

        final OutgoingTextMessage message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertEquals("my-id", message.getChatId());
        assertEquals("Hello", message.getText());
        assertEquals(2, ((ReplyKeyboardMarkup) message.getReplyMarkup()).getKeyboard().size());
        assertEquals(true, ((ReplyKeyboardMarkup) message.getReplyMarkup()).getOneTimeKeyboard());
        assertNull(message.getParseMode());
    }

    @Test
    public void testRouteWithTextHtml() {
        final MockProcessor<OutgoingTextMessage> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.TEXT.name());
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_PARSE_MODE, TelegramParseMode.HTML.name());
        ex.getIn().setBody("Hello");

        template.send(endpoint, ex);

        final OutgoingTextMessage message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertEquals("my-id", message.getChatId());
        assertEquals("Hello", message.getText());
        assertEquals("HTML", message.getParseMode());
    }

    @Test
    public void testRouteWithTextMarkdown() {
        final MockProcessor<OutgoingTextMessage> mockProcessor = getMockRoutes().getMock("sendMessage");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE, TelegramMediaType.TEXT.name());
        ex.getIn().setHeader(TelegramConstants.TELEGRAM_PARSE_MODE, TelegramParseMode.MARKDOWN);
        ex.getIn().setBody("Hello");

        template.send(endpoint, ex);

        final OutgoingTextMessage message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertEquals("my-id", message.getChatId());
        assertEquals("Hello", message.getText());
        assertEquals("Markdown", message.getParseMode());
    }

    @Test
    public void testRouteWithGame() {
        final MockProcessor<OutgoingGameMessage> mockProcessor = getMockRoutes().getMock("sendGame");
        mockProcessor.clearRecordedMessages();

        Exchange ex = endpoint.createExchange();

        OutgoingGameMessage msg = new OutgoingGameMessage();
        msg.setGameShortName("shortName");

        ex.getIn().setBody(msg);

        template.send(endpoint, ex);

        final OutgoingGameMessage message = mockProcessor.awaitRecordedMessages(1, 5000).get(0);
        assertNotNull("my-id", message.getChatId());
        assertNotNull("shortName", message.getGameShortName());
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:telegram").to("telegram:bots?authorizationToken=mock-token&chatId=my-id");
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port.getPort())
                .addEndpoint(
                        "sendPhoto",
                        "POST",
                        byte[].class,
                        TelegramTestUtil.stringResource("messages/send-photo.json"))
                .addEndpoint(
                        "sendAudio",
                        "POST",
                        byte[].class,
                        TelegramTestUtil.stringResource("messages/send-audio.json"))
                .addEndpoint(
                        "sendVideo",
                        "POST",
                        byte[].class,
                        TelegramTestUtil.stringResource("messages/send-video.json"))
                .addEndpoint(
                        "sendDocument",
                        "POST",
                        byte[].class,
                        TelegramTestUtil.stringResource("messages/send-document.json"))
                .addEndpoint(
                        "sendMessage",
                        "POST",
                        OutgoingTextMessage.class,
                        TelegramTestUtil.stringResource("messages/send-message.json"))
                .addEndpoint(
                        "sendGame",
                        "POST",
                        OutgoingGameMessage.class,
                        TelegramTestUtil.stringResource("messages/send-game.json"));
    }

    static void assertMultipartFilename(byte[] message, String name, String filename) {
        assertTrue(
                contains(message, ("name=\"" + name + "\"; filename=\"" + filename + "\"").getBytes(StandardCharsets.UTF_8)));
    }

    static void assertMultipartText(byte[] message, String key, String value) {
        assertTrue(contains(message, ("name=\"" + key + "\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + value)
                .getBytes(StandardCharsets.UTF_8)));
    }

    static boolean contains(byte[] array, byte[] target) {
        if (target.length == 0) {
            return true;
        }
        OUTER_FOR: for (int i = 0; i < array.length - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue OUTER_FOR;
                }
            }
            return true;
        }
        return false;
    }
}
