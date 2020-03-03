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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains information about a sticker.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingSticker {

    @JsonProperty("file_id")
    private String fileId;

    private Integer width;

    private Integer height;

    @JsonProperty("is_animated")
    private Boolean isAnimated;

    private IncomingPhotoSize thumb;

    private String emoji;

    @JsonProperty("set_name")
    private String setName;

    @JsonProperty("mask_position")
    private IncomingMaskPosition maskPosition;

    public IncomingSticker() {
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
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

    public Boolean getAnimated() {
        return isAnimated;
    }

    public void setAnimated(Boolean animated) {
        isAnimated = animated;
    }

    public IncomingPhotoSize getThumb() {
        return thumb;
    }

    public void setThumb(IncomingPhotoSize thumb) {
        this.thumb = thumb;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public String getSetName() {
        return setName;
    }

    public void setSetName(String setName) {
        this.setName = setName;
    }

    public IncomingMaskPosition getMaskPosition() {
        return maskPosition;
    }

    public void setMaskPosition(IncomingMaskPosition maskPosition) {
        this.maskPosition = maskPosition;
    }

    @Override
    public String toString() {
        return "IncomingSticker{"
            + "fileId='" + fileId + '\''
            + ", width=" + width
            + ", height=" + height
            + ", isAnimated=" + isAnimated
            + ", thumb=" + thumb
            + ", emoji='" + emoji + '\''
            + ", setName='" + setName + '\''
            + ", maskPosition=" + maskPosition
            + '}';
    }
}
