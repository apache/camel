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

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A message that is exchanged with the Telegram network.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingMessage implements Serializable {

    private static final long serialVersionUID = -7592193511885686637L;

    @JsonProperty("message_id")
    private Long messageId;

    @JsonDeserialize(using = UnixTimestampDeserializer.class)
    @JsonSerialize(using = UnixTimestampSerializer.class)
    private Instant date;

    private User from;

    private String text;

    private Chat chat;

    private List<IncomingPhotoSize> photo;

    private IncomingVideo video;

    private IncomingAudio audio;

    private IncomingDocument document;

    private IncomingSticker sticker;

    @JsonProperty("location")
    private Location location;

    private List<IncomingMessageEntity> entities;

    @JsonProperty("caption_entities")
    private List<IncomingMessageEntity> captionEntities;

    @JsonProperty("reply_markup")
    private InlineKeyboardMarkup replyMarkup;

    private String caption;

    private IncomingGame game;

    public IncomingMessage() {
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public User getFrom() {
        return from;
    }

    public void setFrom(User from) {
        this.from = from;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public List<IncomingPhotoSize> getPhoto() {
        return photo;
    }

    public void setPhoto(List<IncomingPhotoSize> photo) {
        this.photo = photo;
    }

    public IncomingVideo getVideo() {
        return video;
    }

    public void setVideo(IncomingVideo video) {
        this.video = video;
    }

    public IncomingAudio getAudio() {
        return audio;
    }

    public void setAudio(IncomingAudio audio) {
        this.audio = audio;
    }

    public IncomingDocument getDocument() {
        return document;
    }

    public void setDocument(IncomingDocument document) {
        this.document = document;
    }

    public IncomingSticker getSticker() {
        return sticker;
    }

    public void setSticker(IncomingSticker sticker) {
        this.sticker = sticker;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public List<IncomingMessageEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<IncomingMessageEntity> entities) {
        this.entities = entities;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public List<IncomingMessageEntity> getCaptionEntities() {
        return captionEntities;
    }

    public void setCaptionEntities(List<IncomingMessageEntity> captionEntities) {
        this.captionEntities = captionEntities;
    }

    public InlineKeyboardMarkup getReplyMarkup() {
        return replyMarkup;
    }

    public void setReplyMarkup(InlineKeyboardMarkup replyMarkup) {
        this.replyMarkup = replyMarkup;
    }

    public IncomingGame getGame() {
        return game;
    }

    public void setGame(IncomingGame game) {
        this.game = game;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IncomingMessage{");
        sb.append("messageId=").append(messageId);
        sb.append(", date=").append(date);
        sb.append(", from=").append(from);
        sb.append(", text='").append(text).append('\'');
        sb.append(", chat=").append(chat);
        sb.append(", photo=").append(photo);
        sb.append(", video=").append(video);
        sb.append(", audio=").append(audio);
        sb.append(", document=").append(document);
        sb.append(", sticker=").append(sticker);
        sb.append(", location=").append(location);
        sb.append(", entities=").append(entities);
        sb.append(", caption=").append(caption);
        sb.append(", captionEntities=").append(captionEntities);
        sb.append(", replyMarkup=").append(replyMarkup);
        sb.append(", game=").append(game);
        sb.append('}');
        return sb.toString();
    }
}
