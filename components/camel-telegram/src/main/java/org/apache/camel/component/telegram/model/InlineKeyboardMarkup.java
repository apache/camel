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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This object represents an inline keyboard that appears right next to the message it belongs to.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InlineKeyboardMarkup implements Serializable, ReplyMarkup {

    private static final long serialVersionUID = -8608993809697387977L;

    @JsonProperty("inline_keyboard")
    private List<List<InlineKeyboardButton>> inlineKeyboard;

    /**
     * Builds {@link InlineKeyboardMarkup} instance.
     *
     * @param inlineKeyboard Array of {@link InlineKeyboardButton} rows
     */
    public InlineKeyboardMarkup(List<List<InlineKeyboardButton>> inlineKeyboard) {
        this.inlineKeyboard = inlineKeyboard;
    }

    public InlineKeyboardMarkup() {
    }

    public List<List<InlineKeyboardButton>> getInlineKeyboard() {
        return inlineKeyboard;
    }

    public void setInlineKeyboard(List<List<InlineKeyboardButton>> inlineKeyboard) {
        this.inlineKeyboard = inlineKeyboard;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<List<InlineKeyboardButton>> inlineKeyboard = new ArrayList<>();

        private Builder() {
        }

        public Builder inlineKeyboard(List<List<InlineKeyboardButton>> inlineKeyboard) {
            this.inlineKeyboard = inlineKeyboard;
            return this;
        }

        public Builder addRow(List<InlineKeyboardButton> inlineKeyboardButtons) {
            inlineKeyboard.add(inlineKeyboardButtons);
            return this;
        }

        public InlineKeyboardMarkup build() {
            return new InlineKeyboardMarkup(inlineKeyboard);
        }
    }

    @Override
    public String toString() {
        return "InlineKeyboardMarkup{"
            + "inlineKeyboard=" + inlineKeyboard
            + '}';
    }
}
