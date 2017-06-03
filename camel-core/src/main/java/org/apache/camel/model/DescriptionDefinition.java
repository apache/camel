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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

import org.apache.camel.spi.Metadata;

/**
 * To provide comments about the node.
 *
 * @version 
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "description")
@XmlAccessorType(XmlAccessType.FIELD)
public class DescriptionDefinition {
    @XmlAttribute
    private String lang;
    @XmlAttribute
    @Deprecated
    private Double layoutX;
    @XmlAttribute
    @Deprecated
    private Double layoutY;
    @XmlAttribute
    @Deprecated
    private Double layoutWidth;
    @XmlAttribute
    @Deprecated
    private Double layoutHeight;
    @XmlValue
    private String text;

    public String getLang() {
        return lang;
    }

    /**
     * Language, such as en for english.
     */
    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getText() {
        return text;
    }

    /**
     * The description as human readable text
     */
    public void setText(String text) {
        this.text = text;
    }

    public Double getLayoutHeight() {
        return layoutHeight;
    }

    /**
     * Layout height
     */
    @Deprecated
    public void setLayoutHeight(Double layoutHeight) {
        this.layoutHeight = layoutHeight;
    }

    public Double getLayoutWidth() {
        return layoutWidth;
    }

    /**
     * Layout width
     */
    @Deprecated
    public void setLayoutWidth(Double layoutWidth) {
        this.layoutWidth = layoutWidth;
    }

    public Double getLayoutX() {
        return layoutX;
    }

    /**
     * Layout position X
     */
    @Deprecated
    public void setLayoutX(Double layoutX) {
        this.layoutX = layoutX;
    }

    public Double getLayoutY() {
        return layoutY;
    }

    /**
     * Layout position Y
     */
    @Deprecated
    public void setLayoutY(Double layoutY) {
        this.layoutY = layoutY;
    }
}
