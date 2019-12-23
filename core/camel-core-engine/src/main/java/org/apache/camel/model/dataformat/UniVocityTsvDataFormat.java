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

import org.apache.camel.spi.Metadata;

/**
 * The uniVocity TSV data format is used for working with TSV (Tabular Separated
 * Values) flat payloads.
 */
@Metadata(firstVersion = "2.15.0", label = "dataformat,transformation,csv", title = "uniVocity TSV")
@XmlRootElement(name = "univocity-tsv")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniVocityTsvDataFormat extends UniVocityAbstractDataFormat {
    @XmlAttribute
    @Metadata(defaultValue = "\\")
    private String escapeChar;

    public UniVocityTsvDataFormat() {
        super("univocity-tsv");
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

}
