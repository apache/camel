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
 * Represents a Game.
 *
 * @see <a href="https://core.telegram.org/bots/api#inlinequeryresultgame">
 * https://core.telegram.org/bots/api#inlinequeryresultgame</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InlineQueryResultGame extends InlineQueryResult {

    private static final String TYPE = "game";

    @JsonProperty("game_short_name")
    private String gameShortName;

    public InlineQueryResultGame() {
        super(TYPE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private InlineKeyboardMarkup replyMarkup;
        private String gameShortName;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder replyMarkup(InlineKeyboardMarkup replyMarkup) {
            this.replyMarkup = replyMarkup;
            return this;
        }

        public Builder gameShortName(String gameShortName) {
            this.gameShortName = gameShortName;
            return this;
        }

        public InlineQueryResultGame build() {
            InlineQueryResultGame inlineQueryResultGame = new InlineQueryResultGame();
            inlineQueryResultGame.setType(TYPE);
            inlineQueryResultGame.setId(id);
            inlineQueryResultGame.setReplyMarkup(replyMarkup);
            inlineQueryResultGame.gameShortName = this.gameShortName;
            return inlineQueryResultGame;
        }

        public String getId() {
            return id;
        }

        public InlineKeyboardMarkup getReplyMarkup() {
            return replyMarkup;
        }

        public String getGameShortName() {
            return gameShortName;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setReplyMarkup(InlineKeyboardMarkup replyMarkup) {
            this.replyMarkup = replyMarkup;
        }

        public void setGameShortName(String gameShortName) {
            this.gameShortName = gameShortName;
        }
    }
}
