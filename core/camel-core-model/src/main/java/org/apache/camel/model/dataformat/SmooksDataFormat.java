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
 * Transform and bind XML as well as non-XML data using Smooks.
 */
@Metadata(firstVersion = "4.9.0", label = "dataformat,transformation,smooks", title = "Smooks")
@XmlRootElement(name = "smooks")
@XmlAccessorType(XmlAccessType.FIELD)
public class SmooksDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(required = true)
    private String smooksConfig;

    public SmooksDataFormat() {
        super("smooks");
    }

    protected SmooksDataFormat(SmooksDataFormat source) {
        super(source);
        this.smooksConfig = source.smooksConfig;
    }

    private SmooksDataFormat(Builder builder) {
        this();
        this.smooksConfig = builder.smooksConfig;
    }

    @Override
    public SmooksDataFormat copyDefinition() {
        return new SmooksDataFormat(this);
    }

    /**
     * Path to the Smooks configuration file.
     */
    public void setSmooksConfig(String smooksConfig) {
        this.smooksConfig = smooksConfig;
    }

    public String getSmooksConfig() {
        return smooksConfig;
    }

    /**
     * {@code Builder} is a specific builder for {@link SmooksDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<SmooksDataFormat> {

        private String smooksConfig;

        /**
         * Path to the Smooks configuration file.
         */
        public Builder smooksConfig(String smooksConfig) {
            this.smooksConfig = smooksConfig;
            return this;
        }

        @Override
        public SmooksDataFormat end() {
            return new SmooksDataFormat(this);
        }
    }
}
