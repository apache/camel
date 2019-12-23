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
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * The Barcode data format is used for creating barccode images (such as
 * QR-Code)
 */
@Metadata(firstVersion = "2.14.0", label = "dataformat,transformation", title = "Barcode")
@XmlRootElement(name = "barcode")
@XmlAccessorType(XmlAccessType.FIELD)
public class BarcodeDataFormat extends DataFormatDefinition {
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String width;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String height;
    @XmlAttribute
    private String imageType;
    @XmlAttribute
    private String barcodeFormat;

    public BarcodeDataFormat() {
        super("barcode");
    }

    public String getWidth() {
        return width;
    }

    /**
     * Width of the barcode
     */
    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    /**
     * Height of the barcode
     */
    public void setHeight(String height) {
        this.height = height;
    }

    public String getImageType() {
        return imageType;
    }

    /**
     * Image type of the barcode such as png
     */
    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

    public String getBarcodeFormat() {
        return barcodeFormat;
    }

    /**
     * Barcode format such as QR-Code
     */
    public void setBarcodeFormat(String barcodeFormat) {
        this.barcodeFormat = barcodeFormat;
    }

}
