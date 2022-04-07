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

    @Metadata(label = "producer", description = "This header is used by the producer endpoint in order to\n" +
                                                "resolve the chat id that will receive the message. The recipient chat id can be\n"
                                                +
                                                "placed (in order of priority) in message body, in the `CamelTelegramChatId` header\n"
                                                +
                                                "or in the endpoint configuration (`chatId` option).\n" +
                                                "This header is also present in all incoming messages.",
              javaType = "Object")
    public static final String TELEGRAM_CHAT_ID = "CamelTelegramChatId";
    @Metadata(description = "This header is used to identify the media type when\n" +
                            "the outgoing message is composed of pure binary data. Possible values are strings or enum values\n"
                            +
                            "belonging to the `org.apache.camel.component.telegram.TelegramMediaType` enumeration.",
              javaType = "org.apache.camel.component.telegram.TelegramMediaType or String")
    public static final String TELEGRAM_MEDIA_TYPE = "CamelTelegramMediaType";
    @Metadata(description = "This header is used to provide a caption or title\n" +
                            "for outgoing binary messages.",
              javaType = "String")
    public static final String TELEGRAM_MEDIA_TITLE_CAPTION = "CamelTelegramMediaTitleCaption";
    @Metadata(description = "The reply markup.", javaType = "org.apache.camel.component.telegram.model.ReplyMarkup")
    public static final String TELEGRAM_MEDIA_MARKUP = "CamelTelegramMediaMarkup";
    @Metadata(description = "This header is used to format text messages using HTML or Markdown",
              javaType = "org.apache.camel.component.telegram.TelegramParseMode")
    public static final String TELEGRAM_PARSE_MODE = "CamelTelegramParseMode";
    @Metadata(description = "The message timestamp.", javaType = "long")
    public static final String MESSAGE_TIMESTAMP = Exchange.MESSAGE_TIMESTAMP;

    private TelegramConstants() {
    }

}
