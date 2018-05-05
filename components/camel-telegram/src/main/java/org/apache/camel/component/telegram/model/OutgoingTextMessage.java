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
package org.apache.camel.component.telegram.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An outgoing text message.
 */
public class OutgoingTextMessage extends OutgoingMessage {

    private static final long serialVersionUID = -8684079202025229263L;

    private String text;

    @JsonProperty("parse_mode")
    private String parseMode;

    @JsonProperty("disable_web_page_preview")
    private Boolean disableWebPagePreview;
    
    @JsonProperty("reply_markup")
    private ReplyKeyboardMarkup replyKeyboardMarkup;

    public OutgoingTextMessage() {
        
    }

    public OutgoingTextMessage(String text, String parseMode, Boolean disableWebPagePreview, ReplyKeyboardMarkup replyKeyboardMarkup) {
        
        this.text = text;
        this.parseMode = parseMode;
        this.disableWebPagePreview = disableWebPagePreview;
        this.replyKeyboardMarkup = replyKeyboardMarkup;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getParseMode() {
        return parseMode;
    }

    public void setParseMode(String parseMode) {
        this.parseMode = parseMode;
    }

    public Boolean getDisableWebPagePreview() {
        return disableWebPagePreview;
    }

    public void setDisableWebPagePreview(Boolean disableWebPagePreview) {
        this.disableWebPagePreview = disableWebPagePreview;
    }

    public ReplyKeyboardMarkup getReplyKeyboardMarkup() {
        return replyKeyboardMarkup;
    }

    public void setReplyKeyboardMarkup(ReplyKeyboardMarkup replyKeyboardMarkup) {
        this.replyKeyboardMarkup = replyKeyboardMarkup;
    }
    
    public static Builder builder() {

        return new Builder();
    }

    public static class Builder {

        private String text;
        private String parseMode;
        private Boolean disableWebPagePreview;
        private ReplyKeyboardMarkup replyKeyboardMarkup;

        public Builder text(String text) {

            this.text = text;
            return this;
        }
        
        public Builder parseMode(String parseMode) {

            this.parseMode = parseMode;
            return this;
        }
        
        public Builder disableWebPagePreview(Boolean disableWebPagePreview) {

            this.disableWebPagePreview = disableWebPagePreview;
            return this;
        }
        
        public Builder replyKeyboardMarkup(ReplyKeyboardMarkup replyKeyboardMarkup) {

            this.replyKeyboardMarkup = replyKeyboardMarkup;
            return this;
        }        

        public OutgoingTextMessage build() {
            
            return new OutgoingTextMessage(text, parseMode, disableWebPagePreview, replyKeyboardMarkup);
        }
    }    

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OutgoingTextMessage{");
        sb.append("text='").append(text).append('\'');
        sb.append(", parseMode='").append(parseMode).append('\'');
        sb.append(", disableWebPagePreview=").append(disableWebPagePreview).append('\'');
        sb.append(", replyKeyboardMarkup=").append(replyKeyboardMarkup);
        sb.append('}');
        sb.append(' ');
        sb.append(super.toString());
        return sb.toString();
    }
}
