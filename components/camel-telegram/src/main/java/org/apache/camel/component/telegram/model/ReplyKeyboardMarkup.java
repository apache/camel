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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplyKeyboardMarkup implements Serializable, ReplyMarkup {

    private static final long serialVersionUID = 1L;

    @JsonProperty("one_time_keyboard")
    private Boolean oneTimeKeyboard;

    @JsonProperty("remove_keyboard")
    private Boolean removeKeyboard;

    @JsonProperty("resize_keyboard")
    private Boolean resizeKeyboard;

    private Boolean selective;

    private List<List<InlineKeyboardButton>> keyboard;

    public ReplyKeyboardMarkup() {

    }

    public ReplyKeyboardMarkup(Boolean oneTimeKeyboard, Boolean removeKeyboard, Boolean resizeKeyboard,
                               Boolean selective, List<List<InlineKeyboardButton>> keyboard) {
        this.oneTimeKeyboard = oneTimeKeyboard;
        this.removeKeyboard = removeKeyboard;
        this.resizeKeyboard = resizeKeyboard;
        this.selective = selective;
        this.keyboard = keyboard;
    }

    public Boolean getOneTimeKeyboard() {
        return oneTimeKeyboard;
    }

    public void setOneTimeKeyboard(Boolean oneTimeKeyboard) {
        this.oneTimeKeyboard = oneTimeKeyboard;
    }

    public Boolean getRemoveKeyboard() {
        return removeKeyboard;
    }

    public void setRemoveKeyboard(Boolean removeKeyboard) {
        this.removeKeyboard = removeKeyboard;
    }

    public List<List<InlineKeyboardButton>> getKeyboard() {
        return keyboard;
    }

    public void setKeyboard(List<List<InlineKeyboardButton>> keyboard) {
        this.keyboard = keyboard;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ReplyKeyboardMarkup{");
        sb.append("oneTimeKeyboard='").append(oneTimeKeyboard).append('\'');
        sb.append(", keyboard='").append(keyboard);
        sb.append('}');
        return sb.toString();
    }    

    public static Builder builder() {
        return new Builder();
    }

    public Boolean getResizeKeyboard() {
        return resizeKeyboard;
    }

    public void setResizeKeyboard(Boolean resizeKeyboard) {
        this.resizeKeyboard = resizeKeyboard;
    }

    public Boolean getSelective() {
        return selective;
    }

    public void setSelective(Boolean selective) {
        this.selective = selective;
    }

    public static class Builder {

        private Boolean oneTimeKeyboard;
        private Boolean removeKeyboard;
        private Boolean resizeKeyboard;
        private Boolean selective;
        private List<List<InlineKeyboardButton>> keyboard;

        public Builder oneTimeKeyboard(Boolean oneTimeKeyboard) {

            this.oneTimeKeyboard = oneTimeKeyboard;
            return this;
        }

        public Builder removeKeyboard(Boolean removeKeyboard) {

            this.removeKeyboard = removeKeyboard;
            return this;
        }

        public Builder resizeKeyboard(Boolean resizeKeyboard) {
            this.resizeKeyboard = resizeKeyboard;
            return this;
        }

        public Builder selective(Boolean selective) {
            this.selective = selective;
            return this;
        }

        public ReplyKeyboardMarkup build() {

            return new ReplyKeyboardMarkup(oneTimeKeyboard, removeKeyboard, resizeKeyboard, selective, keyboard);
        }

        public KeyboardBuilder keyboard() {

            return new KeyboardBuilder(this);
        }

        public static class KeyboardBuilder {

            private Builder builder;
            private List<List<InlineKeyboardButton>> keyboard;

            public KeyboardBuilder(Builder builder) {

                this.builder = builder;
                this.keyboard = new ArrayList<>();
            }

            public KeyboardBuilder addRow(List<InlineKeyboardButton> inlineKeyboardButtons) {

                keyboard.add(inlineKeyboardButtons);
                return this;
            }

            public KeyboardBuilder addOneRowByEachButton(List<InlineKeyboardButton> inlineKeyboardButtons) {

                for (Iterator<InlineKeyboardButton> iterator = inlineKeyboardButtons.iterator(); iterator.hasNext();) {

                    keyboard.add(Arrays.asList(iterator.next()));
                }

                return this;
            }

            public Builder close() {

                builder.keyboard = keyboard;
                return builder;
            }
        }
    }
}
