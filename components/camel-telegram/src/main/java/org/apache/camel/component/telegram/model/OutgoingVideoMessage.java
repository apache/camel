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
 * An outgoing video message.
 */
public class OutgoingVideoMessage extends OutgoingMessage {

    private static final long serialVersionUID = 1617845992454497132L;

    private byte[] video;

    private String filenameWithExtension;

    @JsonProperty("duration")
    private Integer durationSeconds;

    private Integer width;

    private Integer height;

    private String caption;

    public OutgoingVideoMessage() {
    }

    public byte[] getVideo() {
        return video;
    }

    public void setVideo(byte[] video) {
        this.video = video;
    }

    public String getFilenameWithExtension() {
        return filenameWithExtension;
    }

    public void setFilenameWithExtension(String filenameWithExtension) {
        this.filenameWithExtension = filenameWithExtension;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
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

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OutgoingVideoMessage{");
        sb.append("video(length)=").append(video != null ? video.length : null);
        sb.append(", filenameWithExtension='").append(filenameWithExtension).append('\'');
        sb.append(", durationSeconds=").append(durationSeconds);
        sb.append(", width=").append(width);
        sb.append(", height=").append(height);
        sb.append(", caption='").append(caption).append('\'');
        sb.append('}');
        sb.append(' ');
        sb.append(super.toString());
        return sb.toString();
    }
}
