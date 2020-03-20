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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Used to send a game.
 *
 * @see <a href="https://core.telegram.org/bots/api#sendgame">https://core.telegram.org/bots/api#sendgame</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutgoingGameMessage extends OutgoingMessage {

    @JsonProperty("game_short_name")
    private String gameShortName;

    @JsonProperty("reply_markup")
    private InlineKeyboardMarkup replyMarkup;

    /**
     * Builds {@link OutgoingGameMessage} instance.
     *
     * @param chatId              Unique identifier for the target chat.
     * @param replyToMessageId    If the message is a reply, ID of the original message.
     * @param gameShortName       Short name of the game, serves as the unique identifier for the game.
     * @param disableNotification Sends the message silently. Users will receive a notification with no sound.
     * @param replyMarkup         An inline keyboard that appears right next to the message it belongs to.
     *                            If empty, one ‘Play game_title’ button will be shown.
     *                            If not empty, the first button must launch the game.
     */
    public OutgoingGameMessage(String chatId, Long replyToMessageId, String gameShortName,
                               Boolean disableNotification, InlineKeyboardMarkup replyMarkup) {
        super.setChatId(chatId);
        super.setReplyToMessageId(replyToMessageId);
        super.setDisableNotification(disableNotification);
        this.gameShortName = gameShortName;
        this.disableNotification = disableNotification;
        this.replyMarkup = replyMarkup;
    }

    public OutgoingGameMessage() {
    }

    public void setGameShortName(String gameShortName) {
        this.gameShortName = gameShortName;
    }

    public void setReplyMarkup(InlineKeyboardMarkup replyMarkup) {
        this.replyMarkup = replyMarkup;
    }

    public String getGameShortName() {
        return gameShortName;
    }

    public InlineKeyboardMarkup getReplyMarkup() {
        return replyMarkup;
    }

    @Override
    public String toString() {
        return "OutgoingSendGameMessage{"
            + "gameShortName='" + gameShortName + '\''
            + ", replyMarkup=" + replyMarkup
            + ", chatId='" + chatId + '\''
            + ", disableNotification=" + disableNotification
            + ", replyToMessageId=" + replyToMessageId
            + '}';
    }
}
