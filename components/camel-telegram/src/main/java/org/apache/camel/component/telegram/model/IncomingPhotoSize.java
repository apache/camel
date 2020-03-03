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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains information about a photo.
 *
 * @see <a href="https://core.telegram.org/bots/api#photosize">https://core.telegram.org/bots/api#photosize</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingPhotoSize implements Serializable {

    private static final long serialVersionUID = -7793605325953618846L;

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("file_unique_id")
    private String fileUniqueId;

    private Integer width;

    private Integer height;

    @JsonProperty("file_size")
    private Long fileSize;

    public IncomingPhotoSize() {
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

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileUniqueId() {
        return fileUniqueId;
    }

    public void setFileUniqueId(String fileUniqueId) {
        this.fileUniqueId = fileUniqueId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IncomingPhotoSize{");
        sb.append("fileId='").append(fileId).append('\'');
        sb.append(", width=").append(width);
        sb.append(", height=").append(height);
        sb.append(", fileSize=").append(fileSize);
        sb.append(", fileUniqueId=").append(fileUniqueId);
        sb.append('}');
        return sb.toString();
    }
}
