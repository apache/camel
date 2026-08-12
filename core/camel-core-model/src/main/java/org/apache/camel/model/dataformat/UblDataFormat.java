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
 * Marshal and unmarshal UBL 2.1 (Universal Business Language) documents.
 */
@Metadata(firstVersion = "4.23.0", label = "dataformat,transformation,xml", title = "UBL",
          description = "Marshal and unmarshal UBL 2.1 (Universal Business Language) documents.")
@XmlRootElement(name = "ubl")
@XmlAccessorType(XmlAccessType.FIELD)
public class UblDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(description = "Whether to enable pretty printing (formatted) output of the XML", defaultValue = "false",
              javaType = "java.lang.Boolean")
    private String prettyPrint;

    public UblDataFormat() {
        super("ubl");
    }

    public UblDataFormat(UblDataFormat source) {
        super(source);
        this.prettyPrint = source.prettyPrint;
    }

    private UblDataFormat(Builder builder) {
        this();
        this.prettyPrint = builder.prettyPrint;
    }

    @Override
    public UblDataFormat copyDefinition() {
        return new UblDataFormat(this);
    }

    public String getPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(String prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * {@code Builder} is a specific builder for {@link UblDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<UblDataFormat> {

        private String prettyPrint;

        /**
         * Whether to enable pretty printing (formatted) output of the XML.
         */
        public Builder prettyPrint(String prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * Whether to enable pretty printing (formatted) output of the XML.
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = Boolean.toString(prettyPrint);
            return this;
        }

        @Override
        public UblDataFormat end() {
            return new UblDataFormat(this);
        }
    }
}
