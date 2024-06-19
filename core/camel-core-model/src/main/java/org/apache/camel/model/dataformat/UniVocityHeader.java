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
import jakarta.xml.bind.annotation.XmlValue;

import org.apache.camel.model.CopyableDefinition;
import org.apache.camel.spi.Metadata;

/**
 * To configure headers for UniVocity data formats.
 */
@Metadata(label = "dataformat,transformation,csv", title = "uniVocity Header")
@XmlRootElement(name = "univocityHeader")
@XmlAccessorType(XmlAccessType.FIELD)
public class UniVocityHeader implements CopyableDefinition<UniVocityHeader> {

    @XmlValue
    private String name;
    @XmlAttribute
    private String length;

    public UniVocityHeader() {
    }

    protected UniVocityHeader(UniVocityHeader source) {
        this.name = source.name;
        this.length = source.length;
    }

    @Override
    public UniVocityHeader copyDefinition() {
        return new UniVocityHeader(this);
    }

    public String getName() {
        return name;
    }

    /**
     * Header name
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getLength() {
        return length;
    }

    /**
     * Header length
     */
    public void setLength(String length) {
        this.length = length;
    }
}
