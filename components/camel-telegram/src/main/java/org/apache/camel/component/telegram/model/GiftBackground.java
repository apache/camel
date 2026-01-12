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
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This object describes the background of a gift.
 *
 * @see <a href=
 *      "https://core.telegram.org/bots/api#giftbackground">https://core.telegram.org/bots/api#giftbackground</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GiftBackground implements Serializable {

    @Serial
    private static final long serialVersionUID = 7347931110373828780L;

    /**
     * Center color of the background in RGB format.
     */
    @JsonProperty("center_color")
    private Integer centerColor;

    /**
     * Edge color of the background in RGB format.
     */
    @JsonProperty("edge_color")
    private Integer edgeColor;

    /**
     * Text color of the background in RGB format.
     */
    @JsonProperty("text_color")
    private Integer textColor;

    public GiftBackground() {
    }

    public GiftBackground(Integer centerColor, Integer edgeColor, Integer textColor) {
        this.centerColor = centerColor;
        this.edgeColor = edgeColor;
        this.textColor = textColor;
    }

    public Integer getCenterColor() {
        return centerColor;
    }

    public void setCenterColor(Integer centerColor) {
        this.centerColor = centerColor;
    }

    public Integer getEdgeColor() {
        return edgeColor;
    }

    public void setEdgeColor(Integer edgeColor) {
        this.edgeColor = edgeColor;
    }

    public Integer getTextColor() {
        return textColor;
    }

    public void setTextColor(Integer textColor) {
        this.textColor = textColor;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("GiftBackground{");
        sb.append("centerColor=").append(centerColor);
        sb.append(", edgeColor=").append(edgeColor);
        sb.append(", textColor=").append(textColor);
        sb.append('}');
        return sb.toString();
    }
}
