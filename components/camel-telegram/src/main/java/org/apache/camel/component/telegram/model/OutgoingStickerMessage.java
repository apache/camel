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
package org.apache.camel.component.telegram.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * An outgoing sticker message.
 */
public final class OutgoingStickerMessage extends OutgoingMessage {

    private static final long serialVersionUID = 5118405983382009364L;

    private String sticker;

    private byte[] stickerImage;

    private String filenameWithExtension;

    private OutgoingStickerMessage(String sticker, byte[] stickerImage, String filenameWithExtension,
                                   String chatId, Boolean disableNotification, Long replyToMessageId) {
        this.sticker = sticker;
        this.stickerImage = stickerImage;
        this.filenameWithExtension = filenameWithExtension;
        this.chatId = chatId;
        this.disableNotification = disableNotification;
        this.replyToMessageId = replyToMessageId;
    }

    /**
     * Creates {@link OutgoingStickerMessage} based on a given webp image.
     *
     * @param image                 the image
     * @param filenameWithExtension the name of the file to send. Example: file.webp
     * @param chatId                Unique identifier for the target chat or username of the target channel
     * @param disableNotification   Sends the message silently. Users will receive a notification with no sound.
     * @param replyToMessageId      If the message is a reply, ID of the original message
     * @return Sticker message.
     */
    public static OutgoingStickerMessage createWithImage(
        byte[] image, String filenameWithExtension,
        String chatId, Boolean disableNotification, Long replyToMessageId
    ) {
        Objects.requireNonNull(image);
        Objects.requireNonNull(filenameWithExtension);
        return new OutgoingStickerMessage(
            null, image, filenameWithExtension, chatId, disableNotification, replyToMessageId);
    }

    /**
     * Creates {@link OutgoingStickerMessage} based on a HTTP URL as a String for Telegram to get a .webp file from
     * the Internet.
     *
     * @param url                 image URL
     * @param chatId              Unique identifier for the target chat or username of the target channel
     * @param disableNotification Sends the message silently. Users will receive a notification with no sound.
     * @param replyToMessageId    If the message is a reply, ID of the original message
     * @return Sticker message.
     */
    public static OutgoingStickerMessage createWithUrl(
        String url, String chatId, Boolean disableNotification, Long replyToMessageId
    ) {
        Objects.requireNonNull(url);
        return createWithFileId(url, chatId, disableNotification, replyToMessageId);
    }

    /**
     * Creates {@link OutgoingStickerMessage} based on a file_id to send a file that exists on the Telegram servers.
     *
     * @param fileId              file_id as {@link String} to send a file that exists on the Telegram servers
     * @param chatId              Unique identifier for the target chat or username of the target channel
     * @param disableNotification Sends the message silently. Users will receive a notification with no sound.
     * @param replyToMessageId    If the message is a reply, ID of the original message
     * @return Sticker message.
     */
    public static OutgoingStickerMessage createWithFileId(String fileId, String chatId, Boolean disableNotification,
                                                          Long replyToMessageId) {
        Objects.requireNonNull(fileId);
        return new OutgoingStickerMessage(fileId, null, null, chatId, disableNotification, replyToMessageId);
    }

    public String getSticker() {
        return sticker;
    }

    public byte[] getStickerImage() {
        return stickerImage;
    }

    public String getFilenameWithExtension() {
        return filenameWithExtension;
    }

    @Override
    public String toString() {
        return "OutgoingStickerMessage{"
            + "sticker='" + sticker + '\''
            + ", stickerImage=" + Arrays.toString(stickerImage)
            + ", filenameWithExtension='" + filenameWithExtension + '\''
            + ", chatId='" + chatId + '\''
            + ", disableNotification=" + disableNotification
            + ", replyToMessageId=" + replyToMessageId
            + '}';
    }
}
