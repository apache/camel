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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The paid media isn't available before the payment.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#paidmediapreview">https://core.telegram.org/bots/api#paidmediapreview</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaidMediaPreview extends PaidMedia {

    @Serial
    private static final long serialVersionUID = -344658841316219278L;

    /**
     * Media width as defined by the sender.
     */
    private Integer width;

    /**
     * Media height as defined by the sender.
     */
    private Integer height;

    /**
     * Duration of the media in seconds as defined by the sender.
     */
    private Integer duration;

    public PaidMediaPreview() {
        super("preview");
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

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PaidMediaPreview{");
        sb.append("type='").append(getType()).append('\'');
        sb.append(", width=").append(width);
        sb.append(", height=").append(height);
        sb.append(", duration=").append(duration);
        sb.append('}');
        return sb.toString();
    }
}
