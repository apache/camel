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
 * Create, edit and read ISO-8583 messages.
 */
@Metadata(firstVersion = "4.14.0", label = "dataformat,transformation,finance", title = "ISO-8583")
@XmlRootElement(name = "j8583")
@XmlAccessorType(XmlAccessType.FIELD)
public class J8583DataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(description = "The j8583 configuration file to load from classpath", defaultValue = "j8583-config.xml")
    private String configFile;
    @XmlAttribute
    @Metadata(description = "The default ISO-Type to use")
    private String isoType;
    @XmlAttribute
    @Metadata(label = "advanced", description = "Whether to auto-discover J8583 MessageFormat from the registry",
              defaultValue = "true",
              javaType = "java.lang.Boolean")
    private String allowAutoWiredMessageFormat;

    public J8583DataFormat() {
        super("j8583");
    }

    public J8583DataFormat(J8583DataFormat source) {
        super(source);
        this.configFile = source.configFile;
        this.isoType = source.isoType;
        this.allowAutoWiredMessageFormat = source.allowAutoWiredMessageFormat;
    }

    private J8583DataFormat(Builder builder) {
        this.configFile = builder.configFile;
        this.isoType = builder.isoType;
        this.allowAutoWiredMessageFormat = builder.allowAutoWiredMessageFormat;
    }

    @Override
    public J8583DataFormat copyDefinition() {
        return new J8583DataFormat(this);
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public String getIsoType() {
        return isoType;
    }

    public void setIsoType(String isoType) {
        this.isoType = isoType;
    }

    public String getAllowAutoWiredMessageFormat() {
        return allowAutoWiredMessageFormat;
    }

    public void setAllowAutoWiredMessageFormat(String allowAutoWiredMessageFormat) {
        this.allowAutoWiredMessageFormat = allowAutoWiredMessageFormat;
    }

    /**
     * {@code Builder} is a specific builder for {@link J8583DataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<J8583DataFormat> {

        private String configFile;
        private String isoType;
        private String allowAutoWiredMessageFormat;

        public Builder configFile(String configFile) {
            this.configFile = configFile;
            return this;
        }

        public Builder isoType(String isoType) {
            this.isoType = isoType;
            return this;
        }

        public Builder allowAutoWiredMessageFormat(String allowAutoWiredMessageFormat) {
            this.allowAutoWiredMessageFormat = allowAutoWiredMessageFormat;
            return this;
        }

        @Override
        public J8583DataFormat end() {
            return new J8583DataFormat(this);
        }
    }
}
