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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Transform strings to various 1D/2D barcode bitmap formats and back.
 */
@Metadata(firstVersion = "2.14.0", label = "dataformat,transformation", title = "Barcode")
@XmlRootElement(name = "barcode")
@XmlAccessorType(XmlAccessType.FIELD)
public class BarcodeDataFormat extends DataFormatDefinition {

    @XmlAttribute
    private String barcodeFormat;
    @XmlAttribute
    private String imageType;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String width;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String height;

    public BarcodeDataFormat() {
        super("barcode");
    }

    private BarcodeDataFormat(Builder builder) {
        this();
        this.barcodeFormat = builder.barcodeFormat;
        this.imageType = builder.imageType;
        this.width = builder.width;
        this.height = builder.height;
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

    /**
     * {@code Builder} is a specific builder for {@link BarcodeDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<BarcodeDataFormat> {

        private String barcodeFormat;
        private String imageType;
        private String width;
        private String height;

        /**
         * Width of the barcode
         */
        public Builder width(String width) {
            this.width = width;
            return this;
        }

        /**
         * Width of the barcode
         */
        public Builder width(int width) {
            this.width = Integer.toString(width);
            return this;
        }

        /**
         * Height of the barcode
         */
        public Builder height(String height) {
            this.height = height;
            return this;
        }

        /**
         * Height of the barcode
         */
        public Builder height(int height) {
            this.height = Integer.toString(height);
            return this;
        }

        /**
         * Image type of the barcode such as png
         */
        public Builder imageType(String imageType) {
            this.imageType = imageType;
            return this;
        }

        /**
         * Barcode format such as QR-Code
         */
        public Builder barcodeFormat(String barcodeFormat) {
            this.barcodeFormat = barcodeFormat;
            return this;
        }

        @Override
        public BarcodeDataFormat end() {
            return new BarcodeDataFormat(this);
        }
    }
}
