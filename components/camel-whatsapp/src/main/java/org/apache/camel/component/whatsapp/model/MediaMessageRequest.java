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
package org.apache.camel.component.whatsapp.model;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class MediaMessageRequest extends BaseMessage {

    /**
     * one of audio, document, image, video, and sticker
     */
    @JsonIgnore
    private String objectName;

    @JsonIgnore
    private MediaMessage mediaMessage;

    public MediaMessageRequest(String objectName, MediaMessage mediaMessage) {
        this.objectName = objectName;
        this.mediaMessage = mediaMessage;

        setType(objectName);
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return Collections.singletonMap(objectName, mediaMessage);
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public MediaMessage getMediaMessage() {
        return mediaMessage;
    }

    public void setMediaMessage(MediaMessage mediaMessage) {
        this.mediaMessage = mediaMessage;
    }
}
