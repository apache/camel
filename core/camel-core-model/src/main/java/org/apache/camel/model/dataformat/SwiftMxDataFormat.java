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
 * Encode and decode SWIFT MX messages.
 */
@Metadata(firstVersion = "3.20.0", label = "dataformat,transformation,swift", title = "SWIFT MX")
@XmlRootElement(name = "swiftMx")
@XmlAccessorType(XmlAccessType.FIELD)
public class SwiftMxDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(label = "advanced")
    private String writeConfigRef;
    @XmlTransient
    private Object writeConfig;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String writeInJson;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String readMessageId;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String readConfigRef;
    @XmlTransient
    private Object readConfig;

    public SwiftMxDataFormat() {
        super("swiftMx");
    }

    public SwiftMxDataFormat(boolean writeInJson) {
        this();
        this.writeInJson = Boolean.toString(writeInJson);
    }

    public SwiftMxDataFormat(boolean writeInJson, String readMessageId, Object readConfig) {
        this(writeInJson);
        this.readMessageId = readMessageId;
        this.readConfig = readConfig;
    }

    public SwiftMxDataFormat(boolean writeInJson, String readMessageId, String readConfigRef) {
        this(writeInJson);
        this.readMessageId = readMessageId;
        this.readConfigRef = readConfigRef;
    }

    public SwiftMxDataFormat(Object writeConfig, String readMessageId, Object readConfig) {
        this();
        this.writeConfig = writeConfig;
        this.readMessageId = readMessageId;
        this.readConfig = readConfig;
    }

    public SwiftMxDataFormat(String writeConfigRef, String readMessageId, String readConfigRef) {
        this();
        this.writeConfigRef = writeConfigRef;
        this.readMessageId = readMessageId;
        this.readConfigRef = readConfigRef;
    }

    private SwiftMxDataFormat(Builder builder) {
        this();
        this.writeConfigRef = builder.writeConfigRef;
        this.writeConfig = builder.writeConfig;
        this.writeInJson = builder.writeInJson;
        this.readMessageId = builder.readMessageId;
        this.readConfigRef = builder.readConfigRef;
        this.readConfig = builder.readConfig;
    }

    public Object getWriteConfig() {
        return writeConfig;
    }

    /**
     * The specific configuration to use when marshalling a message.
     */
    public void setWriteConfig(Object writeConfig) {
        this.writeConfig = writeConfig;
    }

    public String getWriteInJson() {
        return writeInJson;
    }

    /**
     * The flag indicating that messages must be marshalled in a JSON format.
     *
     * @param writeInJson {@code true} if messages must be marshalled in a JSON format, {@code false} otherwise.
     */
    public void setWriteInJson(String writeInJson) {
        this.writeInJson = writeInJson;
    }

    public String getReadMessageId() {
        return readMessageId;
    }

    /**
     * The type of MX message to produce when unmarshalling an input stream. If not set, it will be automatically
     * detected from the namespace used.
     */
    public void setReadMessageId(String readMessageId) {
        this.readMessageId = readMessageId;
    }

    public Object getReadConfig() {
        return readConfig;
    }

    /**
     * The specific configuration to use when unmarshalling an input stream.
     */
    public void setReadConfig(Object readConfig) {
        this.readConfig = readConfig;
    }

    public String getWriteConfigRef() {
        return writeConfigRef;
    }

    /**
     * Refers to a specific configuration to use when marshalling a message to lookup from the registry.
     */
    public void setWriteConfigRef(String writeConfigRef) {
        this.writeConfigRef = writeConfigRef;
    }

    public String getReadConfigRef() {
        return readConfigRef;
    }

    /**
     * Refers to a specific configuration to use when unmarshalling an input stream to lookup from the registry.
     */
    public void setReadConfigRef(String readConfigRef) {
        this.readConfigRef = readConfigRef;
    }

    /**
     * {@code Builder} is a specific builder for {@link SwiftMxDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<SwiftMxDataFormat> {

        private String writeConfigRef;
        private Object writeConfig;
        private String writeInJson;
        private String readMessageId;
        private String readConfigRef;
        private Object readConfig;

        /**
         * The specific configuration to use when marshalling a message.
         */
        public Builder writeConfig(Object writeConfig) {
            this.writeConfig = writeConfig;
            return this;
        }

        /**
         * The flag indicating that messages must be marshalled in a JSON format.
         *
         * @param writeInJson {@code true} if messages must be marshalled in a JSON format, {@code false} otherwise.
         */
        public Builder writeInJson(String writeInJson) {
            this.writeInJson = writeInJson;
            return this;
        }

        /**
         * The flag indicating that messages must be marshalled in a JSON format.
         *
         * @param writeInJson {@code true} if messages must be marshalled in a JSON format, {@code false} otherwise.
         */
        public Builder writeInJson(boolean writeInJson) {
            this.writeInJson = Boolean.toString(writeInJson);
            return this;
        }

        /**
         * The type of MX message to produce when unmarshalling an input stream. If not set, it will be automatically
         * detected from the namespace used.
         */
        public Builder readMessageId(String readMessageId) {
            this.readMessageId = readMessageId;
            return this;
        }

        /**
         * The specific configuration to use when unmarshalling an input stream.
         */
        public Builder readConfig(Object readConfig) {
            this.readConfig = readConfig;
            return this;
        }

        /**
         * Refers to a specific configuration to use when marshalling a message to lookup from the registry.
         */
        public Builder writeConfigRef(String writeConfigRef) {
            this.writeConfigRef = writeConfigRef;
            return this;
        }

        /**
         * Refers to a specific configuration to use when unmarshalling an input stream to lookup from the registry.
         */
        public Builder readConfigRef(String readConfigRef) {
            this.readConfigRef = readConfigRef;
            return this;
        }

        @Override
        public SwiftMxDataFormat end() {
            return new SwiftMxDataFormat(this);
        }
    }
}
