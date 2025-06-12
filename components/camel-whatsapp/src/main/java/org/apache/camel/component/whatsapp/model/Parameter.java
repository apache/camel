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

import com.fasterxml.jackson.annotation.JsonProperty;

public class Parameter {

    private String type;
    private String text;
    private Currency currency;
    @JsonProperty("date_time")
    private DateTime dateTime;
    private MediaMessage image;
    private MediaMessage document;
    private MediaMessage video;
    private String payload;

    public Parameter() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public DateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(DateTime dateTime) {
        this.dateTime = dateTime;
    }

    public MediaMessage getImage() {
        return image;
    }

    public void setImage(MediaMessage image) {
        this.image = image;
    }

    public MediaMessage getDocument() {
        return document;
    }

    public void setDocument(MediaMessage document) {
        this.document = document;
    }

    public MediaMessage getVideo() {
        return video;
    }

    public void setVideo(MediaMessage video) {
        this.video = video;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

}
