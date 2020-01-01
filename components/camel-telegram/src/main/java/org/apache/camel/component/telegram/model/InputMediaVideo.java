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
 * Represents a video to be sent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputMediaVideo extends InputMedia {

    private static final String TYPE = "video";

    private Integer width;

    private Integer height;

    private Integer duration;

    @JsonProperty("supports_streaming")
    private String supportsStreaming;

    /**
     * Builds {@link InputMediaVideo} instance.
     *
     * @param media             File to send. Pass a file_id to send a file that exists on the Telegram servers,
     *                          or pass an HTTP URL for Telegram to get a file from the Internet
     * @param caption           Optional. Caption of the video to be sent, 0-1024 characters
     * @param parseMode         Optional. Send 'Markdown' or 'HTML', if you want Telegram apps to show bold, italic,
     *                          fixed-width text or inline URLs in the media caption.
     * @param width             Optional. Video width
     * @param height            Optional. Video height
     * @param duration          Optional. Video duration
     * @param supportsStreaming Optional. Pass True, if the uploaded video is suitable for streaming
     */
    public InputMediaVideo(String media, String caption, String parseMode, Integer width,
                           Integer height, Integer duration, String supportsStreaming) {
        super(TYPE, media, caption, parseMode);
        this.width = width;
        this.height = height;
        this.duration = duration;
        this.supportsStreaming = supportsStreaming;
    }

    public InputMediaVideo() {
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

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getSupportsStreaming() {
        return supportsStreaming;
    }

    public void setSupportsStreaming(String supportsStreaming) {
        this.supportsStreaming = supportsStreaming;
    }
}
