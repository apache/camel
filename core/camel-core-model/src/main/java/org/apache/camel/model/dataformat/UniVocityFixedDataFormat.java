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

import org.apache.camel.spi.Metadata;

/**
 * Marshal and unmarshal Java objects from and to fixed length records using UniVocity Parsers.
 */
@Metadata(firstVersion = "2.15.0", label = "dataformat,transformation,csv", title = "uniVocity Fixed Length")
@XmlRootElement(name = "univocityFixed")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniVocityFixedDataFormat extends UniVocityAbstractDataFormat {

    @XmlAttribute
    private String padding;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String skipTrailingCharsUntilNewline;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String recordEndsOnNewline;

    public UniVocityFixedDataFormat() {
        super("univocityFixed");
    }

    private UniVocityFixedDataFormat(Builder builder) {
        super("univocityFixed", builder);
        this.padding = builder.padding;
        this.skipTrailingCharsUntilNewline = builder.skipTrailingCharsUntilNewline;
        this.recordEndsOnNewline = builder.recordEndsOnNewline;
    }

    public String getSkipTrailingCharsUntilNewline() {
        return skipTrailingCharsUntilNewline;
    }

    /**
     * Whether or not the trailing characters until new line must be ignored.
     * <p/>
     * The default value is false
     */
    public void setSkipTrailingCharsUntilNewline(String skipTrailingCharsUntilNewline) {
        this.skipTrailingCharsUntilNewline = skipTrailingCharsUntilNewline;
    }

    public String getRecordEndsOnNewline() {
        return recordEndsOnNewline;
    }

    /**
     * Whether or not the record ends on new line.
     * <p/>
     * The default value is false
     */
    public void setRecordEndsOnNewline(String recordEndsOnNewline) {
        this.recordEndsOnNewline = recordEndsOnNewline;
    }

    public String getPadding() {
        return padding;
    }

    /**
     * The padding character.
     * <p/>
     * The default value is a space
     */
    public void setPadding(String padding) {
        this.padding = padding;
    }

    /**
     * {@code Builder} is a specific builder for {@link UniVocityFixedDataFormat}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, UniVocityFixedDataFormat> {

        private String padding;
        private String skipTrailingCharsUntilNewline;
        private String recordEndsOnNewline;

        /**
         * Whether or not the trailing characters until new line must be ignored.
         * <p/>
         * The default value is false
         */
        public Builder skipTrailingCharsUntilNewline(String skipTrailingCharsUntilNewline) {
            this.skipTrailingCharsUntilNewline = skipTrailingCharsUntilNewline;
            return this;
        }

        /**
         * Whether or not the trailing characters until new line must be ignored.
         * <p/>
         * The default value is false
         */
        public Builder skipTrailingCharsUntilNewline(boolean skipTrailingCharsUntilNewline) {
            this.skipTrailingCharsUntilNewline = Boolean.toString(skipTrailingCharsUntilNewline);
            return this;
        }

        /**
         * Whether or not the record ends on new line.
         * <p/>
         * The default value is false
         */
        public Builder recordEndsOnNewline(String recordEndsOnNewline) {
            this.recordEndsOnNewline = recordEndsOnNewline;
            return this;
        }

        /**
         * Whether or not the record ends on new line.
         * <p/>
         * The default value is false
         */
        public Builder recordEndsOnNewline(boolean recordEndsOnNewline) {
            this.recordEndsOnNewline = Boolean.toString(recordEndsOnNewline);
            return this;
        }

        /**
         * The padding character.
         * <p/>
         * The default value is a space
         */
        public Builder padding(String padding) {
            this.padding = padding;
            return this;
        }

        @Override
        public UniVocityFixedDataFormat end() {
            return new UniVocityFixedDataFormat(this);
        }
    }
}
