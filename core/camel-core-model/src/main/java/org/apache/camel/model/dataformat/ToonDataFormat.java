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
 * Marshal JSON-compatible Java values to TOON (Token-Oriented Object Notation) and unmarshal TOON back to Java objects.
 */
@Metadata(firstVersion = "4.23.0", label = "dataformat,transformation,json", title = "TOON",
          description = "Marshal JSON-compatible Java values to TOON (Token-Oriented Object Notation) and unmarshal TOON back to Java objects.")
@XmlRootElement(name = "toon")
@XmlAccessorType(XmlAccessType.FIELD)
public class ToonDataFormat extends DataFormatDefinition implements ContentTypeHeaderAware {

    @XmlAttribute
    @Metadata(description = "Number of spaces per indentation level.", defaultValue = "2", javaType = "java.lang.Integer")
    private String indent;
    @XmlAttribute
    @Metadata(description = "Delimiter used for tabular array rows and inline primitive arrays.", defaultValue = "COMMA",
              enums = "COMMA,TAB,PIPE")
    private String delimiter;
    @XmlAttribute
    @Metadata(description = "Whether to prefix array lengths with a hash marker so arrays render as hash-prefixed lengths instead of plain lengths.",
              defaultValue = "false",
              javaType = "java.lang.Boolean")
    private String lengthMarker;
    @XmlAttribute
    @Metadata(description = "Whether to enable strict validation when unmarshalling TOON. When false, JToon uses best-effort parsing.",
              defaultValue = "true", javaType = "java.lang.Boolean")
    private String strict;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true",
              description = "Whether the data format should set the Content-Type header to text/toon when marshalling.")
    private String contentTypeHeader;

    public ToonDataFormat() {
        super("toon");
    }

    public ToonDataFormat(ToonDataFormat source) {
        super(source);
        this.indent = source.indent;
        this.delimiter = source.delimiter;
        this.lengthMarker = source.lengthMarker;
        this.strict = source.strict;
        this.contentTypeHeader = source.contentTypeHeader;
    }

    private ToonDataFormat(Builder builder) {
        this();
        this.indent = builder.indent;
        this.delimiter = builder.delimiter;
        this.lengthMarker = builder.lengthMarker;
        this.strict = builder.strict;
        this.contentTypeHeader = builder.contentTypeHeader;
    }

    @Override
    public ToonDataFormat copyDefinition() {
        return new ToonDataFormat(this);
    }

    public String getIndent() {
        return indent;
    }

    public void setIndent(String indent) {
        this.indent = indent;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getLengthMarker() {
        return lengthMarker;
    }

    public void setLengthMarker(String lengthMarker) {
        this.lengthMarker = lengthMarker;
    }

    public String getStrict() {
        return strict;
    }

    public void setStrict(String strict) {
        this.strict = strict;
    }

    @Override
    public String getContentTypeHeader() {
        return contentTypeHeader;
    }

    @Override
    public void setContentTypeHeader(String contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;
    }

    /**
     * {@code Builder} is a specific builder for {@link ToonDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<ToonDataFormat> {

        private String indent;
        private String delimiter;
        private String lengthMarker;
        private String strict;
        private String contentTypeHeader;

        /**
         * Number of spaces per indentation level.
         */
        public Builder indent(String indent) {
            this.indent = indent;
            return this;
        }

        /**
         * Number of spaces per indentation level.
         */
        public Builder indent(int indent) {
            this.indent = Integer.toString(indent);
            return this;
        }

        /**
         * Delimiter used for tabular array rows and inline primitive arrays. One of COMMA, TAB, or PIPE.
         */
        public Builder delimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        /**
         * Whether to prefix array lengths with a hash marker so arrays render as hash-prefixed lengths instead of plain
         * lengths.
         */
        public Builder lengthMarker(String lengthMarker) {
            this.lengthMarker = lengthMarker;
            return this;
        }

        /**
         * Whether to prefix array lengths with a hash marker so arrays render as hash-prefixed lengths instead of plain
         * lengths.
         */
        public Builder lengthMarker(boolean lengthMarker) {
            this.lengthMarker = Boolean.toString(lengthMarker);
            return this;
        }

        /**
         * Whether to enable strict validation when unmarshalling TOON.
         */
        public Builder strict(String strict) {
            this.strict = strict;
            return this;
        }

        /**
         * Whether to enable strict validation when unmarshalling TOON.
         */
        public Builder strict(boolean strict) {
            this.strict = Boolean.toString(strict);
            return this;
        }

        /**
         * Whether the data format should set the Content-Type header to text/toon when marshalling.
         */
        public Builder contentTypeHeader(String contentTypeHeader) {
            this.contentTypeHeader = contentTypeHeader;
            return this;
        }

        /**
         * Whether the data format should set the Content-Type header to text/toon when marshalling.
         */
        public Builder contentTypeHeader(boolean contentTypeHeader) {
            this.contentTypeHeader = Boolean.toString(contentTypeHeader);
            return this;
        }

        @Override
        public ToonDataFormat end() {
            return new ToonDataFormat(this);
        }
    }
}
