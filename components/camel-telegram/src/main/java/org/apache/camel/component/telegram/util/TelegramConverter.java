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
package org.apache.camel.component.telegram.util;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.TelegramMediaType;
import org.apache.camel.component.telegram.TelegramParseMode;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.OutgoingAudioMessage;
import org.apache.camel.component.telegram.model.OutgoingDocumentMessage;
import org.apache.camel.component.telegram.model.OutgoingMessage;
import org.apache.camel.component.telegram.model.OutgoingPhotoMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.OutgoingVideoMessage;
import org.apache.camel.component.telegram.model.Update;

/**
 * Utilities for converting between Telegram APIs and standard java objects.
 */
@Converter
public final class TelegramConverter {

    private TelegramConverter() {
    }

    @Converter
    public static String toString(Update update) {
        return update != null ? toString(update.getMessage()) : null;
    }

    @Converter
    public static String toString(IncomingMessage message) {
        return message != null ? message.getText() : null;
    }

    /**
     * Fallback converter for any unlisted object, using String default mapping.
     */
    @Converter
    public static OutgoingMessage toOutgoingMessage(Object message, Exchange exchange) {
        String content = exchange.getIn().getBody(String.class);
        return toOutgoingMessage(content, exchange);
    }

    @Converter
    public static OutgoingMessage toOutgoingMessage(String message, Exchange exchange) {
        if (message == null) {
            // fail fast
            return null;
        }

        Object typeObj = exchange.getIn().getHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE);
        TelegramMediaType type;
        if (typeObj instanceof String) {
            type = TelegramMediaType.valueOf((String) typeObj);
        } else {
            type = (TelegramMediaType) typeObj;
        }

        // If the message is a string, it will be converted to a OutgoingTextMessage
        if (type == null) {
            type = TelegramMediaType.TEXT;
        }

        OutgoingMessage result;

        switch (type) {
        case TEXT: {
            OutgoingTextMessage txt = new OutgoingTextMessage();
            txt.setText(message);

            TelegramParseMode parseMode = getParseMode(exchange);
            if (parseMode != null) {
                txt.setParseMode(parseMode.getCode());
            }

            result = txt;
            break;
        }
        default: {
            throw new IllegalArgumentException("Unsupported conversion from String to media type " + type);
        }
        }


        return result;
    }

    @Converter
    public static OutgoingMessage toOutgoingMessage(byte[] message, Exchange exchange) {
        if (message == null) {
            // fail fast
            return null;
        }

        Object typeObj = exchange.getIn().getHeader(TelegramConstants.TELEGRAM_MEDIA_TYPE);
        TelegramMediaType type;
        if (typeObj instanceof String) {
            type = TelegramMediaType.valueOf((String) typeObj);
        } else {
            type = (TelegramMediaType) typeObj;
        }

        // If the message is a string, it will be converted to a OutgoingTextMessage
        if (type == null) {
            throw new IllegalStateException("Binary message require the header " + TelegramConstants.TELEGRAM_MEDIA_TYPE + " to be set with an appropriate org.apache.camel.component.telegram"
                    + ".TelegramMediaType object");
        }

        OutgoingMessage result;

        switch (type) {
        case PHOTO_JPG:
        case PHOTO_PNG: {
            OutgoingPhotoMessage img = new OutgoingPhotoMessage();
            String caption = (String) exchange.getIn().getHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION);
            String fileName = "photo." + type.getFileExtension();

            img.setCaption(caption);
            img.setFilenameWithExtension(fileName);
            img.setPhoto(message);

            result = img;
            break;
        }
        case AUDIO: {
            OutgoingAudioMessage audio = new OutgoingAudioMessage();
            String title = (String) exchange.getIn().getHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION);
            String fileName = "audio." + type.getFileExtension();

            audio.setTitle(title);
            audio.setFilenameWithExtension(fileName);
            audio.setAudio(message);

            result = audio;
            break;
        }
        case VIDEO: {
            OutgoingVideoMessage video = new OutgoingVideoMessage();
            String title = (String) exchange.getIn().getHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION);
            String fileName = "video." + type.getFileExtension();

            video.setCaption(title);
            video.setFilenameWithExtension(fileName);
            video.setVideo(message);

            result = video;
            break;
        }
        case DOCUMENT:
        default: {
            // this can be any file
            OutgoingDocumentMessage document = new OutgoingDocumentMessage();
            String title = (String) exchange.getIn().getHeader(TelegramConstants.TELEGRAM_MEDIA_TITLE_CAPTION);

            document.setCaption(title);
            document.setFilenameWithExtension("file");
            document.setDocument(message);

            result = document;
            break;
        }
        }

        return result;
    }

    private static TelegramParseMode getParseMode(Exchange exchange) {
        TelegramParseMode mode = null;
        Object parseMode = exchange.getIn().getHeader(TelegramConstants.TELEGRAM_PARSE_MODE);
        if (parseMode instanceof String) {
            mode = TelegramParseMode.valueOf((String) parseMode);
        } else {
            mode = (TelegramParseMode) parseMode;
        }

        return mode;
    }


}
