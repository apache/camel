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
package org.apache.camel.component.slack.helper;

import java.util.List;

public class SlackMessage {

    private String text;
    private String channel;
    private String username;
    private String iconUrl;
    private String iconEmoji;
    private List<Attachment> attachments;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getIconEmoji() {
        return iconEmoji;
    }

    public void setIconEmoji(String iconEmoji) {
        this.iconEmoji = iconEmoji;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public class Attachment {

        private String fallback;
        private String color;
        private String pretext;
        private String authorName;
        private String authorLink;
        private String authorIcon;
        private String title;
        private String titleLink;
        private String text;
        private String imageUrl;
        private String thumbUrl;
        private String footer;
        private String footerIcon;
        private Long ts;
        private List<Field> fields;

        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getPretext() {
            return pretext;
        }

        public void setPretext(String pretext) {
            this.pretext = pretext;
        }

        public String getAuthorName() {
            return authorName;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
        }

        public String getAuthorLink() {
            return authorLink;
        }

        public void setAuthorLink(String authorLink) {
            this.authorLink = authorLink;
        }

        public String getAuthorIcon() {
            return authorIcon;
        }

        public void setAuthorIcon(String authorIcon) {
            this.authorIcon = authorIcon;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitleLink() {
            return titleLink;
        }

        public void setTitleLink(String titleLink) {
            this.titleLink = titleLink;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getThumbUrl() {
            return thumbUrl;
        }

        public void setThumbUrl(String thumbUrl) {
            this.thumbUrl = thumbUrl;
        }

        public String getFooter() {
            return footer;
        }

        public void setFooter(String footer) {
            this.footer = footer;
        }

        public String getFooterIcon() {
            return footerIcon;
        }

        public void setFooterIcon(String footerIcon) {
            this.footerIcon = footerIcon;
        }

        public Long getTs() {
            return ts;
        }

        public void setTs(Long ts) {
            this.ts = ts;
        }

        public List<Field> getFields() {
            return fields;
        }

        public void setFields(List<Field> fields) {
            this.fields = fields;
        }

        public class Field {

            private String title;
            private String value;
            private Boolean shortValue;

            public String getTitle() {
                return title;
            }
            public void setTitle(String title) {
                this.title = title;
            }
            public String getValue() {
                return value;
            }
            public void setValue(String value) {
                this.value = value;
            }
            public Boolean isShortValue() {
                return shortValue;
            }
            public void setShortValue(Boolean shortValue) {
                this.shortValue = shortValue;
            }
        }
    }

}

