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
 * Encode and decode data structures using Abstract Syntax Notation One (ASN.1).
 */
@Metadata(firstVersion = "2.20.0", label = "dataformat,transformation,file", title = "ASN.1 File")
@XmlRootElement(name = "asn1")
@XmlAccessorType(XmlAccessType.FIELD)
public class ASN1DataFormat extends DataFormatDefinition {

    @XmlTransient
    private Class<?> unmarshalType;

    @XmlAttribute(name = "unmarshalType")
    private String unmarshalTypeName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String usingIterator;

    public ASN1DataFormat() {
        super("asn1");
    }

    public ASN1DataFormat(Boolean usingIterator) {
        this();
        setUsingIterator(usingIterator != null ? usingIterator.toString() : null);
    }

    public ASN1DataFormat(String unmarshalTypeName) {
        this();
        setUsingIterator(Boolean.toString(true));
        setUnmarshalTypeName(unmarshalTypeName);
    }

    public ASN1DataFormat(Class<?> unmarshalType) {
        this();
        setUsingIterator(Boolean.toString(true));
        this.unmarshalType = unmarshalType;
    }

    private ASN1DataFormat(Builder builder) {
        this();
        this.usingIterator = builder.usingIterator;
        this.unmarshalTypeName = builder.unmarshalTypeName;
        this.unmarshalType = builder.unmarshalType;
    }

    public String getUsingIterator() {
        return usingIterator;
    }

    /**
     * If the asn1 file has more than one entry, the setting this option to true, allows working with the splitter EIP,
     * to split the data using an iterator in a streaming mode.
     */
    public void setUsingIterator(String usingIterator) {
        this.usingIterator = usingIterator;
    }

    public String getUnmarshalTypeName() {
        return unmarshalTypeName;
    }

    /**
     * Class to use when unmarshalling.
     */
    public void setUnmarshalTypeName(String unmarshalTypeName) {
        this.unmarshalTypeName = unmarshalTypeName;
    }

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    /**
     * Class to use when unmarshalling.
     */
    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    /**
     * {@code Builder} is a specific builder for {@link ASN1DataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<ASN1DataFormat> {

        private Class<?> unmarshalType;
        private String unmarshalTypeName;
        private String usingIterator;

        /**
         * If the asn1 file has more than one entry, the setting this option to true, allows working with the splitter
         * EIP, to split the data using an iterator in a streaming mode.
         */
        public Builder usingIterator(String usingIterator) {
            this.usingIterator = usingIterator;
            return this;
        }

        /**
         * If the asn1 file has more than one entry, the setting this option to true, allows working with the splitter
         * EIP, to split the data using an iterator in a streaming mode.
         */
        public Builder usingIterator(boolean usingIterator) {
            this.usingIterator = Boolean.toString(usingIterator);
            return this;
        }

        /**
         * Class to use when unmarshalling.
         */
        public Builder unmarshalTypeName(String unmarshalTypeName) {
            this.unmarshalTypeName = unmarshalTypeName;
            return this;
        }

        /**
         * Class to use when unmarshalling.
         */
        public Builder unmarshalType(Class<?> unmarshalType) {
            this.unmarshalType = unmarshalType;
            return this;
        }

        @Override
        public ASN1DataFormat end() {
            return new ASN1DataFormat(this);
        }
    }
}
