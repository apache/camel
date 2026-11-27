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

import org.apache.camel.Exchange;
import org.apache.camel.spi.Metadata;

/**
 * Useful constants for the Telegram component.
 */
public final class TelegramConstants {

    @Metadata(label = "producer",
              description = "The identifier of the chat. The consumer sets it for a received message or channel post. The"
                            + " producer sends to this chat when the message in the body has no chat id, and uses the chatId"
                            + " option when this header is not set either.",
              javaType = "Object")
    public static final String TELEGRAM_CHAT_ID = "CamelTelegramChatId";
    @Metadata(description = "The type of the media that the producer sends from a body of binary data, such as a byte array, a"
                            + " stream or a file: TEXT, PHOTO_PNG, PHOTO_JPG, AUDIO, VIDEO or DOCUMENT, as a TelegramMediaType"
                            + " or its name. Without this header, a byte array fails, and a stream or a file is sent as text.",
              javaType = "org.apache.camel.component.telegram.TelegramMediaType or String")
    public static final String TELEGRAM_MEDIA_TYPE = "CamelTelegramMediaType";
    @Metadata(description = "The caption of a photo, video or document, or the title of an audio, that the producer sends from a"
                            + " body of binary data as the CamelTelegramMediaType header sets.",
              javaType = "String")
    public static final String TELEGRAM_MEDIA_TITLE_CAPTION = "CamelTelegramMediaTitleCaption";
    @Metadata(description = "The reply markup, such as an inline keyboard, of a photo, audio, video or document that the producer"
                            + " sends from a body of binary data. It is not used for a text message.",
              javaType = "org.apache.camel.component.telegram.model.ReplyMarkup")
    public static final String TELEGRAM_MEDIA_MARKUP = "CamelTelegramMediaMarkup";
    @Metadata(description = "The parse mode, HTML or MARKDOWN, of a text message that the producer creates from the body, as a"
                            + " TelegramParseMode or its name. MARKDOWN is the legacy Markdown style of Telegram, not"
                            + " MarkdownV2. It is not used when the body is already a Telegram message, such as an"
                            + " OutgoingTextMessage.",
              javaType = "org.apache.camel.component.telegram.TelegramParseMode")
    public static final String TELEGRAM_PARSE_MODE = "CamelTelegramParseMode";
    @Metadata(description = "The date of a received message or channel post, in milliseconds since the epoch. The consumer"
                            + " sets it.",
              javaType = "long")
    public static final String MESSAGE_TIMESTAMP = Exchange.MESSAGE_TIMESTAMP;

    private TelegramConstants() {
    }

}
