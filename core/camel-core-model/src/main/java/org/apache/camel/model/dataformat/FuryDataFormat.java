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
 * Serialize and deserialize messages using Apache Fury
 */
@Metadata(firstVersion = "4.9.0", label = "dataformat,transformation", title = "Fury")
@XmlRootElement(name = "fury")
@XmlAccessorType(XmlAccessType.FIELD)
public class FuryDataFormat extends DataFormatDefinition {
    @XmlTransient
    private Class<?> unmarshalType;

    @XmlAttribute(name = "unmarshalType")
    @Metadata(description = "Class of the java type to use when unmarshalling")
    private String unmarshalTypeName;

    public FuryDataFormat() {
        super("fury");
    }

    public FuryDataFormat(FuryDataFormat source) {
        super(source);
        this.unmarshalType = source.unmarshalType;
        this.unmarshalTypeName = source.unmarshalTypeName;
    }

    private FuryDataFormat(Builder builder) {
        this.unmarshalTypeName = builder.unmarshalTypeName;
    }

    @Override
    public FuryDataFormat copyDefinition() {
        return new FuryDataFormat(this);
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class of the java type to use when unmarshalling
     */
    public void setUnmarshalType(final Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    public void setUnmarshalTypeName(final String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    /**
     * {@code Builder} is a specific builder for {@link FuryDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<FuryDataFormat> {
        private String unmarshalTypeName;

        /**
         * Class of the java type to use when unmarshalling
         */
        public Builder unmarshalType(String unmarshalTypeName) {
            this.unmarshalTypeName = unmarshalTypeName;
            return this;
        }

        @Override
        public FuryDataFormat end() {
            return new FuryDataFormat(this);
        }
    }
}
