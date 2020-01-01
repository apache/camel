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

/**
 * Represents an animation file (GIF or H.264/MPEG-4 AVC video without sound) to be sent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputMediaAnimation extends InputMedia {

    private static final String TYPE = "animation";

    private Integer width;

    private Integer height;

    private Integer duration;

    /**
     * Builds {@link InputMediaAnimation} instance.
     *
     * @param media     File to send. Pass a file_id to send a file that exists on the Telegram servers, or pass an
     *                  HTTP URL for Telegram to get a file from the Internet
     * @param caption   Optional. Caption of the video to be sent, 0-1024 characters
     * @param parseMode Optional. Send 'Markdown' or 'HTML', if you want Telegram apps to show bold, italic,
     *                  fixed-width text or inline URLs in the media caption.
     * @param width     Optional. width.
     * @param height    Optional. height.
     * @param duration  Optional. duration.
     */
    public InputMediaAnimation(String media, String caption, String parseMode, Integer width, Integer height,
                               Integer duration) {
        super(TYPE, media, caption, parseMode);
        this.width = width;
        this.height = height;
        this.duration = duration;
    }

    public InputMediaAnimation() {
        super.setType(TYPE);
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer heigth) {
        this.height = heigth;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}
