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
package org.apache.camel.component.yammer.model;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Attachment {

    @JsonProperty("last_uploaded_at")
    private String lastUploadedAt;
    private String description;
    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;
    @JsonProperty("large_preview_url")
    private String largePreviewUrl;
    private String url;
    @JsonProperty("streaming_url")
    private String streamingUrl;
    @JsonProperty("group_id")
    private String groupId;
    private Long id;
    @JsonProperty("last_uploaded_by_id")
    private Long lastUploadedById;
    private Long size;
    @JsonProperty("owner_type")
    private String ownerType;
    @JsonProperty("content_type")
    private String contentType;
    @JsonProperty("small_icon_url")
    private String smallIconUrl;
    @JsonProperty("original_name")
    private String originalName;
    private String type;
    private Boolean official;
    private Long height;
    private String transcoded;
    private String path;
    @JsonProperty("preview_url")
    private String previewUrl;
    private String name;
    @JsonProperty("y_id")
    private Long yId;
    @JsonProperty("download_url")
    private String downloadUrl;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("owner_id")
    private Long ownerId;
    @JsonProperty("content_class")
    private String contentClass;
    private String privacy;
    @JsonProperty("full_name")
    private String fullName;
    @JsonProperty("last_uploaded_by_type")
    private String lastUploadedByType;
    @JsonProperty("overlay_url")
    private String overlayUrl;
    @JsonProperty("real_type")
    private String realType;
    @JsonProperty("large_icon_url")
    private String largeIconUrl;
    @JsonProperty("scaled_url")
    private String scaledUrl;
    private Long width;
    private Image image;
    @JsonProperty("web_url")
    private String webUrl;
    private String uuid;

    public String getLastUploadedAt() {
        return lastUploadedAt;
    }

    public void setLastUploadedAt(String lastUploadedAt) {
        this.lastUploadedAt = lastUploadedAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getLargePreviewUrl() {
        return largePreviewUrl;
    }

    public void setLargePreviewUrl(String largePreviewUrl) {
        this.largePreviewUrl = largePreviewUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getStreamingUrl() {
        return streamingUrl;
    }

    public void setStreamingUrl(String streamingUrl) {
        this.streamingUrl = streamingUrl;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLastUploadedById() {
        return lastUploadedById;
    }

    public void setLastUploadedById(Long lastUploadedById) {
        this.lastUploadedById = lastUploadedById;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getSmallIconUrl() {
        return smallIconUrl;
    }

    public void setSmallIconUrl(String smallIconUrl) {
        this.smallIconUrl = smallIconUrl;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getOfficial() {
        return official;
    }

    public void setOfficial(Boolean official) {
        this.official = official;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public String getTranscoded() {
        return transcoded;
    }

    public void setTranscoded(String transcoded) {
        this.transcoded = transcoded;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getYId() {
        return yId;
    }

    public void setYId(Long yId) {
        this.yId = yId;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getContentClass() {
        return contentClass;
    }

    public void setContentClass(String contentClass) {
        this.contentClass = contentClass;
    }

    public String getPrivacy() {
        return privacy;
    }

    public void setPrivacy(String privacy) {
        this.privacy = privacy;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getLastUploadedByType() {
        return lastUploadedByType;
    }

    public void setLastUploadedByType(String lastUploadedByType) {
        this.lastUploadedByType = lastUploadedByType;
    }

    public String getOverlayUrl() {
        return overlayUrl;
    }

    public void setOverlayUrl(String overlayUrl) {
        this.overlayUrl = overlayUrl;
    }

    public String getRealType() {
        return realType;
    }

    public void setRealType(String realType) {
        this.realType = realType;
    }

    public String getLargeIconUrl() {
        return largeIconUrl;
    }

    public void setLargeIconUrl(String largeIconUrl) {
        this.largeIconUrl = largeIconUrl;
    }

    public String getScaledUrl() {
        return scaledUrl;
    }

    public void setScaledUrl(String scaledUrl) {
        this.scaledUrl = scaledUrl;
    }

    public Long getWidth() {
        return width;
    }

    public void setWidth(Long width) {
        this.width = width;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String toString() {
        return "Attachment [lastUploadedAt=" + lastUploadedAt + ", description=" + description + ", thumbnailUrl=" + thumbnailUrl + ", largePreviewUrl=" + largePreviewUrl + ", url=" + url
                + ", streamingUrl=" + streamingUrl + ", groupId=" + groupId + ", id=" + id + ", lastUploadedById=" + lastUploadedById + ", size=" + size + ", ownerType=" + ownerType
                + ", contentType=" + contentType + ", smallIconUrl=" + smallIconUrl + ", originalName=" + originalName + ", type=" + type + ", official=" + official + ", height=" + height
                + ", transcoded=" + transcoded + ", path=" + path + ", previewUrl=" + previewUrl + ", name=" + name + ", yId=" + yId + ", downloadUrl=" + downloadUrl + ", createdAt=" + createdAt
                + ", ownerId=" + ownerId + ", contentClass=" + contentClass + ", privacy=" + privacy + ", fullName=" + fullName + ", lastUploadedByType=" + lastUploadedByType + ", overlayUrl="
                + overlayUrl + ", realType=" + realType + ", largeIconUrl=" + largeIconUrl + ", scaledUrl=" + scaledUrl + ", width=" + width + ", image=" + image + ", webUrl=" + webUrl + ", uuid="
                + uuid + "]";
    }

}
