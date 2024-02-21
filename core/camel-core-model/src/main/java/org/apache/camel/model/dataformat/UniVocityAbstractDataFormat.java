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

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Represents the common parts of all uniVocity {@link org.apache.camel.spi.DataFormat} parsers.
 */
@Metadata(label = "dataformat,transformation,csv", title = "uniVocity")
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class UniVocityAbstractDataFormat extends DataFormatDefinition {

    @XmlAttribute
    @Metadata(label = "advanced")
    protected String nullValue;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    protected String skipEmptyLines;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    protected String ignoreTrailingWhitespaces;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    protected String ignoreLeadingWhitespaces;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    protected String headersDisabled;
    @XmlElementRef
    protected List<UniVocityHeader> headers;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    protected String headerExtractionEnabled;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Integer")
    protected String numberOfRecordsToRead;
    @XmlAttribute
    @Metadata(label = "advanced")
    protected String emptyValue;
    @XmlAttribute
    @Metadata(label = "advanced")
    protected String lineSeparator;
    @XmlAttribute
    @Metadata(label = "advanced")
    protected String normalizedLineSeparator;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "#")
    protected String comment;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    protected String lazyLoad;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    protected String asMap;

    protected UniVocityAbstractDataFormat() {
        // This constructor is needed by jaxb for schema generation
    }

    protected UniVocityAbstractDataFormat(String dataFormatName) {
        super(dataFormatName);
    }

    protected UniVocityAbstractDataFormat(String dataFormatName, AbstractBuilder<?, ?> builder) {
        this(dataFormatName);
        this.nullValue = builder.nullValue;
        this.skipEmptyLines = builder.skipEmptyLines;
        this.ignoreTrailingWhitespaces = builder.ignoreTrailingWhitespaces;
        this.ignoreLeadingWhitespaces = builder.ignoreLeadingWhitespaces;
        this.headersDisabled = builder.headersDisabled;
        this.headers = builder.headers;
        this.headerExtractionEnabled = builder.headerExtractionEnabled;
        this.numberOfRecordsToRead = builder.numberOfRecordsToRead;
        this.emptyValue = builder.emptyValue;
        this.lineSeparator = builder.lineSeparator;
        this.normalizedLineSeparator = builder.normalizedLineSeparator;
        this.comment = builder.comment;
        this.lazyLoad = builder.lazyLoad;
        this.asMap = builder.asMap;
    }

    public String getNullValue() {
        return nullValue;
    }

    /**
     * The string representation of a null value.
     * <p/>
     * The default value is null
     */
    public void setNullValue(String nullValue) {
        this.nullValue = nullValue;
    }

    public String getSkipEmptyLines() {
        return skipEmptyLines;
    }

    /**
     * Whether or not the empty lines must be ignored.
     * <p/>
     * The default value is true
     */
    public void setSkipEmptyLines(String skipEmptyLines) {
        this.skipEmptyLines = skipEmptyLines;
    }

    public String getIgnoreTrailingWhitespaces() {
        return ignoreTrailingWhitespaces;
    }

    /**
     * Whether or not the trailing white spaces must be ignored.
     * <p/>
     * The default value is true
     */
    public void setIgnoreTrailingWhitespaces(String ignoreTrailingWhitespaces) {
        this.ignoreTrailingWhitespaces = ignoreTrailingWhitespaces;
    }

    public String getIgnoreLeadingWhitespaces() {
        return ignoreLeadingWhitespaces;
    }

    /**
     * Whether or not the leading white spaces must be ignored.
     * <p/>
     * The default value is true
     */
    public void setIgnoreLeadingWhitespaces(String ignoreLeadingWhitespaces) {
        this.ignoreLeadingWhitespaces = ignoreLeadingWhitespaces;
    }

    public String getHeadersDisabled() {
        return headersDisabled;
    }

    /**
     * Whether or not the headers are disabled. When defined, this option explicitly sets the headers as null which
     * indicates that there is no header.
     * <p/>
     * The default value is false
     */
    public void setHeadersDisabled(String headersDisabled) {
        this.headersDisabled = headersDisabled;
    }

    public List<UniVocityHeader> getHeaders() {
        return headers;
    }

    /**
     * The headers to use.
     */
    public void setHeaders(List<UniVocityHeader> headers) {
        this.headers = headers;
    }

    public String getHeaderExtractionEnabled() {
        return headerExtractionEnabled;
    }

    /**
     * Whether or not the header must be read in the first line of the test document.
     * <p/>
     * The default value is false
     */
    public void setHeaderExtractionEnabled(String headerExtractionEnabled) {
        this.headerExtractionEnabled = headerExtractionEnabled;
    }

    public String getNumberOfRecordsToRead() {
        return numberOfRecordsToRead;
    }

    /**
     * The maximum number of record to read.
     */
    public void setNumberOfRecordsToRead(String numberOfRecordsToRead) {
        this.numberOfRecordsToRead = numberOfRecordsToRead;
    }

    public String getEmptyValue() {
        return emptyValue;
    }

    /**
     * The String representation of an empty value.
     */
    public void setEmptyValue(String emptyValue) {
        this.emptyValue = emptyValue;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    /**
     * The line separator of the files.
     * <p/>
     * The default value is to use the JVM platform line separator
     */
    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    public String getNormalizedLineSeparator() {
        return normalizedLineSeparator;
    }

    /**
     * The normalized line separator of the files.
     * <p/>
     * The default value is a new line character.
     */
    public void setNormalizedLineSeparator(String normalizedLineSeparator) {
        this.normalizedLineSeparator = normalizedLineSeparator;
    }

    public String getComment() {
        return comment;
    }

    /**
     * The comment symbol.
     * <p/>
     * The default value is #
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getLazyLoad() {
        return lazyLoad;
    }

    /**
     * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all the lines must be
     * read at once.
     * <p/>
     * The default value is false
     */
    public void setLazyLoad(String lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    public String getAsMap() {
        return asMap;
    }

    /**
     * Whether the unmarshalling should produce maps for the lines values instead of lists. It requires to have header
     * (either defined or collected).
     * <p/>
     * The default value is false
     */
    public void setAsMap(String asMap) {
        this.asMap = asMap;
    }

    /**
     * {@code AbstractBuilder} is the base builder for {@link UniVocityAbstractDataFormat}.
     */
    @XmlTransient
    @SuppressWarnings("unchecked")
    abstract static class AbstractBuilder<T extends AbstractBuilder<T, F>, F extends UniVocityAbstractDataFormat>
            implements DataFormatBuilder<F> {

        private String nullValue;
        private String skipEmptyLines;
        private String ignoreTrailingWhitespaces;
        private String ignoreLeadingWhitespaces;
        private String headersDisabled;
        private List<UniVocityHeader> headers;
        private String headerExtractionEnabled;
        private String numberOfRecordsToRead;
        private String emptyValue;
        private String lineSeparator;
        private String normalizedLineSeparator;
        private String comment;
        private String lazyLoad;
        private String asMap;

        /**
         * The string representation of a null value.
         * <p/>
         * The default value is null
         */
        public T nullValue(String nullValue) {
            this.nullValue = nullValue;
            return (T) this;
        }

        /**
         * Whether or not the empty lines must be ignored.
         * <p/>
         * The default value is true
         */
        public T skipEmptyLines(String skipEmptyLines) {
            this.skipEmptyLines = skipEmptyLines;
            return (T) this;
        }

        /**
         * Whether or not the empty lines must be ignored.
         * <p/>
         * The default value is true
         */
        public T skipEmptyLines(boolean skipEmptyLines) {
            this.skipEmptyLines = Boolean.toString(skipEmptyLines);
            return (T) this;
        }

        /**
         * Whether or not the trailing white spaces must be ignored.
         * <p/>
         * The default value is true
         */
        public T ignoreTrailingWhitespaces(String ignoreTrailingWhitespaces) {
            this.ignoreTrailingWhitespaces = ignoreTrailingWhitespaces;
            return (T) this;
        }

        /**
         * Whether or not the trailing white spaces must be ignored.
         * <p/>
         * The default value is true
         */
        public T ignoreTrailingWhitespaces(boolean ignoreTrailingWhitespaces) {
            this.ignoreTrailingWhitespaces = Boolean.toString(ignoreTrailingWhitespaces);
            return (T) this;
        }

        /**
         * Whether or not the leading white spaces must be ignored.
         * <p/>
         * The default value is true
         */
        public T ignoreLeadingWhitespaces(String ignoreLeadingWhitespaces) {
            this.ignoreLeadingWhitespaces = ignoreLeadingWhitespaces;
            return (T) this;
        }

        /**
         * Whether or not the leading white spaces must be ignored.
         * <p/>
         * The default value is true
         */
        public T ignoreLeadingWhitespaces(boolean ignoreLeadingWhitespaces) {
            this.ignoreLeadingWhitespaces = Boolean.toString(ignoreLeadingWhitespaces);
            return (T) this;
        }

        /**
         * Whether or not the headers are disabled. When defined, this option explicitly sets the headers as null which
         * indicates that there is no header.
         * <p/>
         * The default value is false
         */
        public T headersDisabled(String headersDisabled) {
            this.headersDisabled = headersDisabled;
            return (T) this;
        }

        /**
         * Whether or not the headers are disabled. When defined, this option explicitly sets the headers as null which
         * indicates that there is no header.
         * <p/>
         * The default value is false
         */
        public T headersDisabled(boolean headersDisabled) {
            this.headersDisabled = Boolean.toString(headersDisabled);
            return (T) this;
        }

        /**
         * The headers to use.
         */
        public T headers(List<UniVocityHeader> headers) {
            this.headers = headers;
            return (T) this;
        }

        /**
         * Whether or not the header must be read in the first line of the test document.
         * <p/>
         * The default value is false
         */
        public T headerExtractionEnabled(String headerExtractionEnabled) {
            this.headerExtractionEnabled = headerExtractionEnabled;
            return (T) this;
        }

        /**
         * Whether or not the header must be read in the first line of the test document.
         * <p/>
         * The default value is false
         */
        public T headerExtractionEnabled(boolean headerExtractionEnabled) {
            this.headerExtractionEnabled = Boolean.toString(headerExtractionEnabled);
            return (T) this;
        }

        /**
         * The maximum number of record to read.
         */
        public T numberOfRecordsToRead(String numberOfRecordsToRead) {
            this.numberOfRecordsToRead = numberOfRecordsToRead;
            return (T) this;
        }

        /**
         * The maximum number of record to read.
         */
        public T numberOfRecordsToRead(int numberOfRecordsToRead) {
            this.numberOfRecordsToRead = Integer.toString(numberOfRecordsToRead);
            return (T) this;
        }

        /**
         * The String representation of an empty value.
         */
        public T emptyValue(String emptyValue) {
            this.emptyValue = emptyValue;
            return (T) this;
        }

        /**
         * The line separator of the files.
         * <p/>
         * The default value is to use the JVM platform line separator
         */
        public T lineSeparator(String lineSeparator) {
            this.lineSeparator = lineSeparator;
            return (T) this;
        }

        /**
         * The normalized line separator of the files.
         * <p/>
         * The default value is a new line character.
         */
        public T normalizedLineSeparator(String normalizedLineSeparator) {
            this.normalizedLineSeparator = normalizedLineSeparator;
            return (T) this;
        }

        /**
         * The comment symbol.
         * <p/>
         * The default value is #
         */
        public T comment(String comment) {
            this.comment = comment;
            return (T) this;
        }

        /**
         * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all the lines must
         * be read at once.
         * <p/>
         * The default value is false
         */
        public T lazyLoad(String lazyLoad) {
            this.lazyLoad = lazyLoad;
            return (T) this;
        }

        /**
         * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all the lines must
         * be read at once.
         * <p/>
         * The default value is false
         */
        public T lazyLoad(boolean lazyLoad) {
            this.lazyLoad = Boolean.toString(lazyLoad);
            return (T) this;
        }

        /**
         * Whether the unmarshalling should produce maps for the lines values instead of lists. It requires to have
         * header (either defined or collected).
         * <p/>
         * The default value is false
         */
        public T asMap(String asMap) {
            this.asMap = asMap;
            return (T) this;
        }

        /**
         * Whether the unmarshalling should produce maps for the lines values instead of lists. It requires to have
         * header (either defined or collected).
         * <p/>
         * The default value is false
         */
        public T asMap(boolean asMap) {
            this.asMap = Boolean.toString(asMap);
            return (T) this;
        }
    }
}
