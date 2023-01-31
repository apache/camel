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
 * Marshal and unmarshal Java objects from and to TSV (Tab-Separated Values) records using UniVocity Parsers.
 */
@Metadata(firstVersion = "2.15.0", label = "dataformat,transformation,csv", title = "uniVocity TSV")
@XmlRootElement(name = "univocityTsv")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniVocityTsvDataFormat extends UniVocityAbstractDataFormat {

    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "\\")
    private String escapeChar;

    public UniVocityTsvDataFormat() {
        super("univocityTsv");
    }

    private UniVocityTsvDataFormat(Builder builder) {
        super("univocityTsv", builder);
        this.escapeChar = builder.escapeChar;
    }

    public String getEscapeChar() {
        return escapeChar;
    }

    /**
     * The escape character.
     */
    public void setEscapeChar(String escapeChar) {
        this.escapeChar = escapeChar;
    }

    /**
     * {@code Builder} is a specific builder for {@link UniVocityTsvDataFormat}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, UniVocityTsvDataFormat> {

        private String escapeChar;

        /**
         * The escape character.
         */
        public Builder escapeChar(String escapeChar) {
            this.escapeChar = escapeChar;
            return this;
        }

        @Override
        public UniVocityTsvDataFormat end() {
            return new UniVocityTsvDataFormat(this);
        }
    }
}
