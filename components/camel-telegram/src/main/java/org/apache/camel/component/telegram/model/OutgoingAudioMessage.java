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
 * An outgoing audio message.
 */
public class OutgoingAudioMessage extends OutgoingMessage {

    private static final long serialVersionUID = 2716544815581270395L;

    private byte[] audio;

    private String filenameWithExtension;

    @JsonProperty("duration")
    private Integer durationSeconds;

    private String performer;

    private String title;

    public OutgoingAudioMessage() {
    }

    public byte[] getAudio() {
        return audio;
    }

    public void setAudio(byte[] audio) {
        this.audio = audio;
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

    public String getPerformer() {
        return performer;
    }

    public void setPerformer(String performer) {
        this.performer = performer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OutgoingAudioMessage{");
        sb.append("audio(length)=").append(audio != null ? audio.length : null);
        sb.append(", filenameWithExtension='").append(filenameWithExtension).append('\'');
        sb.append(", durationSeconds=").append(durationSeconds);
        sb.append(", performer='").append(performer).append('\'');
        sb.append(", title='").append(title).append('\'');
        sb.append('}');
        sb.append(' ');
        sb.append(super.toString());
        return sb.toString();
    }
}
