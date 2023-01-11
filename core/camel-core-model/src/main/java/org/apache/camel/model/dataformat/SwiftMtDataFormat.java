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
 * Encode and decode SWIFT MT messages.
 */
@Metadata(firstVersion = "3.20.0", label = "dataformat,transformation,swift", title = "SWIFT MT")
@XmlRootElement(name = "swiftMt")
@XmlAccessorType(XmlAccessType.FIELD)
public class SwiftMtDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String writeInJson;

    public SwiftMtDataFormat() {
        super("swiftMt");
    }

    public SwiftMtDataFormat(String writeInJson) {
        this();
        this.writeInJson = writeInJson;
    }

    private SwiftMtDataFormat(Builder builder) {
        this();
        this.writeInJson = builder.writeInJson;
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

    /**
     * {@code Builder} is a specific builder for {@link SwiftMtDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<SwiftMtDataFormat> {

        private String writeInJson;

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

        @Override
        public SwiftMtDataFormat end() {
            return new SwiftMtDataFormat(this);
        }
    }
}
