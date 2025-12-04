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
@Metadata(firstVersion = "3.20.0", label = "dataformat,transformation,finance", title = "SWIFT MX")
@XmlRootElement(name = "swiftMx")
@XmlAccessorType(XmlAccessType.FIELD)
public class SwiftMxDataFormat extends DataFormatDefinition {

    @XmlTransient
    private Object writeConfigObject;

    @XmlTransient
    private Object readConfigObject;

    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String writeInJson;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "com.prowidesoftware.swift.model.MxId")
    private String readMessageId;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "com.prowidesoftware.swift.model.mx.MxReadConfiguration")
    private String readConfig;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "com.prowidesoftware.swift.model.mx.MxWriteConfiguration")
    private String writeConfig;

    public SwiftMxDataFormat() {
        super("swiftMx");
    }

    protected SwiftMxDataFormat(SwiftMxDataFormat source) {
        super(source);
        this.writeConfig = source.writeConfig;
        this.writeConfigObject = source.writeConfigObject;
        this.writeInJson = source.writeInJson;
        this.readMessageId = source.readMessageId;
        this.readConfig = source.readConfig;
        this.readConfigObject = source.readConfigObject;
    }

    public SwiftMxDataFormat(boolean writeInJson) {
        this();
        this.writeInJson = Boolean.toString(writeInJson);
    }

    public SwiftMxDataFormat(boolean writeInJson, String readMessageId, Object readConfigObject) {
        this(writeInJson);
        this.readMessageId = readMessageId;
        this.readConfigObject = readConfigObject;
    }

    public SwiftMxDataFormat(boolean writeInJson, String readMessageId, String readConfig) {
        this(writeInJson);
        this.readMessageId = readMessageId;
        this.readConfig = readConfig;
    }

    public SwiftMxDataFormat(Object writeConfigObject, String readMessageId, Object readConfigObject) {
        this();
        this.writeConfigObject = writeConfigObject;
        this.readMessageId = readMessageId;
        this.readConfigObject = readConfigObject;
    }

    public SwiftMxDataFormat(String writeConfig, String readMessageId, String readConfig) {
        this();
        this.writeConfig = writeConfig;
        this.readMessageId = readMessageId;
        this.readConfig = readConfig;
    }

    private SwiftMxDataFormat(Builder builder) {
        this();
        this.writeConfig = builder.writeConfig;
        this.writeConfigObject = builder.writeConfigObject;
        this.writeInJson = builder.writeInJson;
        this.readMessageId = builder.readMessageId;
        this.readConfig = builder.readConfig;
        this.readConfigObject = builder.readConfigObject;
    }

    @Override
    public SwiftMxDataFormat copyDefinition() {
        return new SwiftMxDataFormat(this);
    }

    public Object getWriteConfigObject() {
        return writeConfigObject;
    }

    /**
     * The specific configuration to use when marshalling a message.
     */
    public void setWriteConfigObject(Object writeConfigObject) {
        this.writeConfigObject = writeConfigObject;
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

    public Object getReadConfigObject() {
        return readConfigObject;
    }

    /**
     * The specific configuration to use when unmarshalling an input stream.
     */
    public void setReadConfigObject(Object readConfigObject) {
        this.readConfigObject = readConfigObject;
    }

    public String getWriteConfig() {
        return writeConfig;
    }

    /**
     * Refers to a specific configuration to use when marshalling a message to lookup from the registry.
     */
    public void setWriteConfig(String writeConfig) {
        this.writeConfig = writeConfig;
    }

    public String getReadConfig() {
        return readConfig;
    }

    /**
     * Refers to a specific configuration to use when unmarshalling an input stream to lookup from the registry.
     */
    public void setReadConfig(String readConfig) {
        this.readConfig = readConfig;
    }

    /**
     * {@code Builder} is a specific builder for {@link SwiftMxDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<SwiftMxDataFormat> {

        private String writeConfig;
        private Object writeConfigObject;
        private String writeInJson;
        private String readMessageId;
        private String readConfig;
        private Object readConfigObject;

        /**
         * The specific configuration to use when marshalling a message.
         */
        public Builder writeConfigObject(Object writeConfigObject) {
            this.writeConfigObject = writeConfigObject;
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
        public Builder readConfigObject(Object readConfigObject) {
            this.readConfigObject = readConfigObject;
            return this;
        }

        /**
         * Refers to a specific configuration to use when marshalling a message to lookup from the registry.
         */
        public Builder writeConfigObject(String writeConfig) {
            this.writeConfig = writeConfig;
            return this;
        }

        /**
         * Refers to a specific configuration to use when unmarshalling an input stream to lookup from the registry.
         */
        public Builder readConfig(String readConfig) {
            this.readConfig = readConfig;
            return this;
        }

        @Override
        public SwiftMxDataFormat end() {
            return new SwiftMxDataFormat(this);
        }
    }
}
