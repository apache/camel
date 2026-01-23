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

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This object represents a sticker.
 *
 * @see <a href="https://core.telegram.org/bots/api#sticker">https://core.telegram.org/bots/api#sticker</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingSticker implements Serializable {

    @Serial
    private static final long serialVersionUID = -5622918749834262276L;

    /**
     * Identifier for this file, which can be used to download or reuse the file.
     */
    @JsonProperty("file_id")
    private String fileId;

    /**
     * Unique identifier for this file, which is supposed to be the same over time and for different bots. Can't be used
     * to download or reuse the file.
     */
    @JsonProperty("file_unique_id")
    private String fileUniqueId;

    /**
     * Type of the sticker, currently one of "regular", "mask", "custom_emoji".
     */
    private String type;

    /**
     * Sticker width.
     */
    private Integer width;

    /**
     * Sticker height.
     */
    private Integer height;

    /**
     * True, if the sticker is animated.
     */
    @JsonProperty("is_animated")
    private Boolean isAnimated;

    /**
     * True, if the sticker is a video sticker.
     */
    @JsonProperty("is_video")
    private Boolean isVideo;

    /**
     * Optional. Sticker thumbnail in the .WEBP or .JPG format.
     *
     * @deprecated Use {@link #thumbnail} instead. Kept for backward compatibility.
     */
    @Deprecated
    private IncomingPhotoSize thumb;

    /**
     * Optional. Sticker thumbnail in the .WEBP or .JPG format.
     */
    private IncomingPhotoSize thumbnail;

    /**
     * Optional. Emoji associated with the sticker.
     */
    private String emoji;

    /**
     * Optional. Name of the sticker set to which the sticker belongs.
     */
    @JsonProperty("set_name")
    private String setName;

    /**
     * Optional. For premium regular stickers, premium animation for the sticker.
     */
    @JsonProperty("premium_animation")
    private IncomingFile premiumAnimation;

    /**
     * Optional. For mask stickers, the position where the mask should be placed.
     */
    @JsonProperty("mask_position")
    private IncomingMaskPosition maskPosition;

    /**
     * Optional. For custom emoji stickers, unique identifier of the custom emoji.
     */
    @JsonProperty("custom_emoji_id")
    private String customEmojiId;

    /**
     * Optional. True, if the sticker must be repainted to a text color in messages, the color of the Telegram Premium
     * badge in emoji status, white color on chat photos, or another appropriate color in other places.
     */
    @JsonProperty("needs_repainting")
    private Boolean needsRepainting;

    /**
     * Optional. File size in bytes.
     */
    @JsonProperty("file_size")
    private Long fileSize;

    public IncomingSticker() {
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileUniqueId() {
        return fileUniqueId;
    }

    public void setFileUniqueId(String fileUniqueId) {
        this.fileUniqueId = fileUniqueId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public Boolean getIsAnimated() {
        return isAnimated;
    }

    public void setIsAnimated(Boolean isAnimated) {
        this.isAnimated = isAnimated;
    }

    public Boolean getIsVideo() {
        return isVideo;
    }

    public void setIsVideo(Boolean isVideo) {
        this.isVideo = isVideo;
    }

    /**
     * @deprecated Use {@link #getThumbnail()} instead.
     */
    @Deprecated
    public IncomingPhotoSize getThumb() {
        return thumb;
    }

    /**
     * @deprecated Use {@link #setThumbnail(IncomingPhotoSize)} instead.
     */
    @Deprecated
    public void setThumb(IncomingPhotoSize thumb) {
        this.thumb = thumb;
    }

    public IncomingPhotoSize getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(IncomingPhotoSize thumbnail) {
        this.thumbnail = thumbnail;
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

    public IncomingFile getPremiumAnimation() {
        return premiumAnimation;
    }

    public void setPremiumAnimation(IncomingFile premiumAnimation) {
        this.premiumAnimation = premiumAnimation;
    }

    public IncomingMaskPosition getMaskPosition() {
        return maskPosition;
    }

    public void setMaskPosition(IncomingMaskPosition maskPosition) {
        this.maskPosition = maskPosition;
    }

    public String getCustomEmojiId() {
        return customEmojiId;
    }

    public void setCustomEmojiId(String customEmojiId) {
        this.customEmojiId = customEmojiId;
    }

    public Boolean getNeedsRepainting() {
        return needsRepainting;
    }

    public void setNeedsRepainting(Boolean needsRepainting) {
        this.needsRepainting = needsRepainting;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IncomingSticker{");
        sb.append("fileId='").append(fileId).append('\'');
        sb.append(", fileUniqueId='").append(fileUniqueId).append('\'');
        sb.append(", type='").append(type).append('\'');
        sb.append(", width=").append(width);
        sb.append(", height=").append(height);
        sb.append(", isAnimated=").append(isAnimated);
        sb.append(", isVideo=").append(isVideo);
        sb.append(", thumb=").append(thumb);
        sb.append(", thumbnail=").append(thumbnail);
        sb.append(", emoji='").append(emoji).append('\'');
        sb.append(", setName='").append(setName).append('\'');
        sb.append(", premiumAnimation=").append(premiumAnimation);
        sb.append(", maskPosition=").append(maskPosition);
        sb.append(", customEmojiId='").append(customEmojiId).append('\'');
        sb.append(", needsRepainting=").append(needsRepainting);
        sb.append(", fileSize=").append(fileSize);
        sb.append('}');
        return sb.toString();
    }
}
