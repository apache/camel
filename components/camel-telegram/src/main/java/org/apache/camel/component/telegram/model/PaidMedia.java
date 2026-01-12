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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * This object describes paid media.
 *
 * @see <a href="https://core.telegram.org/bots/api#paidmedia">https://core.telegram.org/bots/api#paidmedia</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PaidMediaPreview.class, name = "preview"),
        @JsonSubTypes.Type(value = PaidMediaPhoto.class, name = "photo"),
        @JsonSubTypes.Type(value = PaidMediaVideo.class, name = "video")
})
public abstract class PaidMedia implements Serializable {

    @Serial
    private static final long serialVersionUID = -8598592364726649255L;

    /**
     * Type of the paid media.
     */
    private String type;

    public PaidMedia() {
    }

    public PaidMedia(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns this object as {@link PaidMediaPreview} if it is of that type, null otherwise.
     */
    public PaidMediaPreview asPreview() {
        return this instanceof PaidMediaPreview ? (PaidMediaPreview) this : null;
    }

    /**
     * Returns this object as {@link PaidMediaPhoto} if it is of that type, null otherwise.
     */
    public PaidMediaPhoto asPhoto() {
        return this instanceof PaidMediaPhoto ? (PaidMediaPhoto) this : null;
    }

    /**
     * Returns this object as {@link PaidMediaVideo} if it is of that type, null otherwise.
     */
    public PaidMediaVideo asVideo() {
        return this instanceof PaidMediaVideo ? (PaidMediaVideo) this : null;
    }
}
