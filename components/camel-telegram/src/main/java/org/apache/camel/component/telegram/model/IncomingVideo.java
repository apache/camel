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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains information about a video.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingVideo implements Serializable {

    @Serial
    private static final long serialVersionUID = 5280714879829232835L;

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("file_unique_id")
    private String fileUniqueId;

    private Integer width;

    private Integer height;

    @JsonProperty("duration")
    private Integer durationSeconds;

    /**
     * Optional. Video thumbnail in the .WEBP or .JPG format.
     *
     * @deprecated Use {@link #thumbnail} instead. Kept for backward compatibility.
     */
    @Deprecated
    private IncomingPhotoSize thumb;

    private IncomingPhotoSize thumbnail;

    private List<IncomingPhotoSize> cover;

    @JsonProperty("start_timestamp")
    private Integer startTimestamp;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("file_size")
    private Long fileSize;

    public IncomingVideo() {
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

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
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

    public List<IncomingPhotoSize> getCover() {
        return cover;
    }

    public void setCover(List<IncomingPhotoSize> cover) {
        this.cover = cover;
    }

    public Integer getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Integer startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IncomingVideo{");
        sb.append("fileId='").append(fileId).append('\'');
        sb.append(", fileUniqueId='").append(fileUniqueId).append('\'');
        sb.append(", width=").append(width);
        sb.append(", height=").append(height);
        sb.append(", durationSeconds=").append(durationSeconds);
        sb.append(", thumb=").append(thumb);
        sb.append(", thumbnail=").append(thumbnail);
        sb.append(", cover=").append(cover);
        sb.append(", startTimestamp=").append(startTimestamp);
        sb.append(", fileName='").append(fileName).append('\'');
        sb.append(", mimeType='").append(mimeType).append('\'');
        sb.append(", fileSize=").append(fileSize);
        sb.append('}');
        return sb.toString();
    }
}
