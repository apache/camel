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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a game.
 *
 * @see <a href="https://core.telegram.org/bots/api#game">https://core.telegram.org/bots/api#game</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingGame implements Serializable {

    private static final long serialVersionUID = -7405711494235116932L;

    public String title;

    public String description;

    public List<IncomingPhotoSize> photo;

    public String text;

    @JsonProperty("text_entities")
    public List<IncomingMessageEntity> textEntities;

    public IncomingAnimation animation;

    public IncomingGame() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<IncomingPhotoSize> getPhoto() {
        return photo;
    }

    public void setPhoto(List<IncomingPhotoSize> photo) {
        this.photo = photo;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<IncomingMessageEntity> getTextEntities() {
        return textEntities;
    }

    public void setTextEntities(List<IncomingMessageEntity> textEntities) {
        this.textEntities = textEntities;
    }

    public IncomingAnimation getAnimation() {
        return animation;
    }

    public void setAnimation(IncomingAnimation animation) {
        this.animation = animation;
    }

    @Override
    public String toString() {
        return "IncomingGame{"
               + "title='" + title + '\''
               + ", description='" + description + '\''
               + ", photo=" + photo
               + ", text='" + text + '\''
               + ", textEntities=" + textEntities
               + ", animation=" + animation
               + '}';
    }
}
