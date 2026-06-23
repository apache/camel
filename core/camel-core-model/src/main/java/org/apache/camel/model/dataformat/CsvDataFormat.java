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
 * Handle CSV (Comma Separated Values) payloads.
 */
@Metadata(firstVersion = "1.3.0", label = "dataformat,transformation,csv", title = "CSV")
@XmlRootElement(name = "csv")
@XmlAccessorType(XmlAccessType.FIELD)
public class CsvDataFormat extends DataFormatDefinition {

    // Format options
    @XmlAttribute
    @Metadata(enums = "DEFAULT,EXCEL,INFORMIX_UNLOAD,INFORMIX_UNLOAD_CSV,MONGODB_CSV,MONGODB_TSV,MYSQL,ORACLE,POSTGRESQL_CSV,POSTGRESQL_TEXT,RFC4180",
              defaultValue = "DEFAULT",
              description = "The format to use.")
    private String format;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "Disables the comment marker of the reference format.")
    private String commentMarkerDisabled;
    @XmlAttribute
    @Metadata(label = "advanced", description = "Sets the comment marker of the reference format.")
    private String commentMarker;
    @XmlAttribute
    @Metadata(description = "The delimiter to use. The default value is , (comma).")
    private String delimiter;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "Whether to disable the escape character.")
    private String escapeDisabled;
    @XmlAttribute
    @Metadata(label = "advanced", description = "Sets the escape character to use.")
    private String escape;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", description = "Whether to disable headers.")
    private String headerDisabled;
    @XmlAttribute
    @Metadata(description = "To configure the CSV headers. Multiple headers can be separated by comma.")
    private String header;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", description = "Whether to allow missing column names.")
    private String allowMissingColumnNames;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", description = "Whether to ignore empty lines.")
    private String ignoreEmptyLines;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", description = "Whether to ignore surrounding spaces.")
    private String ignoreSurroundingSpaces;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "Whether to disable null string handling.")
    private String nullStringDisabled;
    @XmlAttribute
    @Metadata(label = "advanced", description = "Sets the null string.")
    private String nullString;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", description = "Whether to disable quoting.")
    private String quoteDisabled;
    @XmlAttribute
    @Metadata(description = "The quote character to use. The default is double-quote character.")
    private String quote;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "Whether to disable the record separator.")
    private String recordSeparatorDisabled;
    @XmlAttribute
    @Metadata(description = "The record separator (aka new line) which by default is new line characters (CRLF).")
    private String recordSeparator;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", description = "Whether to skip the header record in the output.")
    private String skipHeaderRecord;
    @XmlAttribute
    @Metadata(enums = "ALL,ALL_NON_NULL,MINIMAL,NON_NUMERIC,NONE",
              description = "Sets the quote mode.")
    private String quoteMode;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "Whether to ignore case when accessing header names.")
    private String ignoreHeaderCase;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "Whether to trim leading and trailing blanks.")
    private String trim;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "Whether to add a trailing delimiter.")
    private String trailingDelimiter;
    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Sets the implementation of the CsvMarshallerFactory interface which is able to customize marshalling/unmarshalling behavior.")
    private String marshallerFactoryRef;

    // Unmarshall options
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all the lines must be read at one.")
    private String lazyLoad;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "Whether the unmarshalling should produce maps (HashMap) for the lines values instead of lists. It requires to have header (either defined or collected).")
    private String useMaps;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean",
              description = "Whether the unmarshalling should produce ordered maps (LinkedHashMap) for the lines values instead of lists. It requires to have header (either defined or collected).")
    private String useOrderedMaps;
    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Refers to a custom CsvRecordConverter to lookup from the registry to use.")
    private String recordConverterRef;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean",
              description = "Whether the unmarshalling should capture the header record and store it in the message header.")
    private String captureHeaderRecord;

    public CsvDataFormat() {
        super("csv");
    }

    protected CsvDataFormat(CsvDataFormat source) {
        super(source);
        this.format = source.format;
        this.commentMarkerDisabled = source.commentMarkerDisabled;
        this.commentMarker = source.commentMarker;
        this.delimiter = source.delimiter;
        this.escapeDisabled = source.escapeDisabled;
        this.escape = source.escape;
        this.headerDisabled = source.headerDisabled;
        this.header = source.header;
        this.allowMissingColumnNames = source.allowMissingColumnNames;
        this.ignoreEmptyLines = source.ignoreEmptyLines;
        this.ignoreSurroundingSpaces = source.ignoreSurroundingSpaces;
        this.nullStringDisabled = source.nullStringDisabled;
        this.nullString = source.nullString;
        this.quoteDisabled = source.quoteDisabled;
        this.quote = source.quote;
        this.recordSeparatorDisabled = source.recordSeparatorDisabled;
        this.recordSeparator = source.recordSeparator;
        this.skipHeaderRecord = source.skipHeaderRecord;
        this.quoteMode = source.quoteMode;
        this.ignoreHeaderCase = source.ignoreHeaderCase;
        this.trim = source.trim;
        this.trailingDelimiter = source.trailingDelimiter;
        this.marshallerFactoryRef = source.marshallerFactoryRef;
        this.lazyLoad = source.lazyLoad;
        this.useMaps = source.useMaps;
        this.useOrderedMaps = source.useOrderedMaps;
        this.recordConverterRef = source.recordConverterRef;
        this.captureHeaderRecord = source.captureHeaderRecord;
    }

    public CsvDataFormat(String delimiter) {
        this();
        setDelimiter(delimiter);
    }

    public CsvDataFormat(boolean lazyLoad) {
        this();
        setLazyLoad(Boolean.toString(lazyLoad));
    }

    private CsvDataFormat(Builder builder) {
        this();
        this.format = builder.format;
        this.commentMarkerDisabled = builder.commentMarkerDisabled;
        this.commentMarker = builder.commentMarker;
        this.delimiter = builder.delimiter;
        this.escapeDisabled = builder.escapeDisabled;
        this.escape = builder.escape;
        this.headerDisabled = builder.headerDisabled;
        this.header = builder.header;
        this.allowMissingColumnNames = builder.allowMissingColumnNames;
        this.ignoreEmptyLines = builder.ignoreEmptyLines;
        this.ignoreSurroundingSpaces = builder.ignoreSurroundingSpaces;
        this.nullStringDisabled = builder.nullStringDisabled;
        this.nullString = builder.nullString;
        this.quoteDisabled = builder.quoteDisabled;
        this.quote = builder.quote;
        this.recordSeparatorDisabled = builder.recordSeparatorDisabled;
        this.recordSeparator = builder.recordSeparator;
        this.skipHeaderRecord = builder.skipHeaderRecord;
        this.quoteMode = builder.quoteMode;
        this.ignoreHeaderCase = builder.ignoreHeaderCase;
        this.trim = builder.trim;
        this.trailingDelimiter = builder.trailingDelimiter;
        this.marshallerFactoryRef = builder.marshallerFactoryRef;
        this.lazyLoad = builder.lazyLoad;
        this.useMaps = builder.useMaps;
        this.useOrderedMaps = builder.useOrderedMaps;
        this.recordConverterRef = builder.recordConverterRef;
        this.captureHeaderRecord = builder.captureHeaderRecord;
    }

    @Override
    public CsvDataFormat copyDefinition() {
        return new CsvDataFormat(this);
    }

    public void setMarshallerFactoryRef(String marshallerFactoryRef) {
        this.marshallerFactoryRef = marshallerFactoryRef;
    }

    public String getMarshallerFactoryRef() {
        return marshallerFactoryRef;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getCommentMarkerDisabled() {
        return commentMarkerDisabled;
    }

    public void setCommentMarkerDisabled(String commentMarkerDisabled) {
        this.commentMarkerDisabled = commentMarkerDisabled;
    }

    public String getCommentMarker() {
        return commentMarker;
    }

    public void setCommentMarker(String commentMarker) {
        this.commentMarker = commentMarker;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getEscapeDisabled() {
        return escapeDisabled;
    }

    public void setEscapeDisabled(String escapeDisabled) {
        this.escapeDisabled = escapeDisabled;
    }

    public String getEscape() {
        return escape;
    }

    public void setEscape(String escape) {
        this.escape = escape;
    }

    public String getHeaderDisabled() {
        return headerDisabled;
    }

    public void setHeaderDisabled(String headerDisabled) {
        this.headerDisabled = headerDisabled;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getAllowMissingColumnNames() {
        return allowMissingColumnNames;
    }

    public void setAllowMissingColumnNames(String allowMissingColumnNames) {
        this.allowMissingColumnNames = allowMissingColumnNames;
    }

    public String getIgnoreEmptyLines() {
        return ignoreEmptyLines;
    }

    public void setIgnoreEmptyLines(String ignoreEmptyLines) {
        this.ignoreEmptyLines = ignoreEmptyLines;
    }

    public String getIgnoreSurroundingSpaces() {
        return ignoreSurroundingSpaces;
    }

    public void setIgnoreSurroundingSpaces(String ignoreSurroundingSpaces) {
        this.ignoreSurroundingSpaces = ignoreSurroundingSpaces;
    }

    public String getNullStringDisabled() {
        return nullStringDisabled;
    }

    public void setNullStringDisabled(String nullStringDisabled) {
        this.nullStringDisabled = nullStringDisabled;
    }

    public String getNullString() {
        return nullString;
    }

    public void setNullString(String nullString) {
        this.nullString = nullString;
    }

    public String getQuoteDisabled() {
        return quoteDisabled;
    }

    public void setQuoteDisabled(String quoteDisabled) {
        this.quoteDisabled = quoteDisabled;
    }

    public String getQuote() {
        return quote;
    }

    public void setQuote(String quote) {
        this.quote = quote;
    }

    public String getRecordSeparatorDisabled() {
        return recordSeparatorDisabled;
    }

    public void setRecordSeparatorDisabled(String recordSeparatorDisabled) {
        this.recordSeparatorDisabled = recordSeparatorDisabled;
    }

    public String getRecordSeparator() {
        return recordSeparator;
    }

    public void setRecordSeparator(String recordSeparator) {
        this.recordSeparator = recordSeparator;
    }

    public String getSkipHeaderRecord() {
        return skipHeaderRecord;
    }

    public void setSkipHeaderRecord(String skipHeaderRecord) {
        this.skipHeaderRecord = skipHeaderRecord;
    }

    public String getQuoteMode() {
        return quoteMode;
    }

    public void setQuoteMode(String quoteMode) {
        this.quoteMode = quoteMode;
    }

    public String getLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(String lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    public String getUseMaps() {
        return useMaps;
    }

    public void setUseMaps(String useMaps) {
        this.useMaps = useMaps;
    }

    public String getUseOrderedMaps() {
        return useOrderedMaps;
    }

    public void setUseOrderedMaps(String useOrderedMaps) {
        this.useOrderedMaps = useOrderedMaps;
    }

    public String getRecordConverterRef() {
        return recordConverterRef;
    }

    public void setRecordConverterRef(String recordConverterRef) {
        this.recordConverterRef = recordConverterRef;
    }

    public void setTrim(String trim) {
        this.trim = trim;
    }

    public String getTrim() {
        return trim;
    }

    public void setIgnoreHeaderCase(String ignoreHeaderCase) {
        this.ignoreHeaderCase = ignoreHeaderCase;
    }

    public String getIgnoreHeaderCase() {
        return ignoreHeaderCase;
    }

    public void setTrailingDelimiter(String trailingDelimiter) {
        this.trailingDelimiter = trailingDelimiter;
    }

    public String getTrailingDelimiter() {
        return trailingDelimiter;
    }

    public String getCaptureHeaderRecord() {
        return captureHeaderRecord;
    }

    public void setCaptureHeaderRecord(String captureHeaderRecord) {
        this.captureHeaderRecord = captureHeaderRecord;
    }

    /**
     * {@code Builder} is a specific builder for {@link CsvDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<CsvDataFormat> {
        private String format;
        private String commentMarkerDisabled;
        private String commentMarker;
        private String delimiter;
        private String escapeDisabled;
        private String escape;
        private String headerDisabled;
        private String header;
        private String allowMissingColumnNames;
        private String ignoreEmptyLines;
        private String ignoreSurroundingSpaces;
        private String nullStringDisabled;
        private String nullString;
        private String quoteDisabled;
        private String quote;
        private String recordSeparatorDisabled;
        private String recordSeparator;
        private String skipHeaderRecord;
        private String quoteMode;
        private String ignoreHeaderCase;
        private String trim;
        private String trailingDelimiter;
        private String marshallerFactoryRef;
        private String lazyLoad;
        private String useMaps;
        private String useOrderedMaps;
        private String recordConverterRef;
        private String captureHeaderRecord;

        /**
         * Sets the implementation of the CsvMarshallerFactory interface which is able to customize
         * marshalling/unmarshalling behavior by extending CsvMarshaller or creating it from scratch.
         *
         * @param marshallerFactoryRef the <code>CsvMarshallerFactory</code> reference.
         */
        public Builder marshallerFactoryRef(String marshallerFactoryRef) {
            this.marshallerFactoryRef = marshallerFactoryRef;
            return this;
        }

        /**
         * The format to use.
         */
        public Builder format(String format) {
            this.format = format;
            return this;
        }

        /**
         * Disables the comment marker of the reference format.
         */
        public Builder commentMarkerDisabled(String commentMarkerDisabled) {
            this.commentMarkerDisabled = commentMarkerDisabled;
            return this;
        }

        /**
         * Disables the comment marker of the reference format.
         */
        public Builder commentMarkerDisabled(boolean commentMarkerDisabled) {
            this.commentMarkerDisabled = Boolean.toString(commentMarkerDisabled);
            return this;
        }

        /**
         * Sets the comment marker of the reference format.
         */
        public Builder commentMarker(String commentMarker) {
            this.commentMarker = commentMarker;
            return this;
        }

        /**
         * Sets the delimiter to use.
         * <p/>
         * The default value is , (comma)
         */
        public Builder delimiter(String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        /**
         * Use for disabling using escape character
         */
        public Builder escapeDisabled(String escapeDisabled) {
            this.escapeDisabled = escapeDisabled;
            return this;
        }

        /**
         * Use for disabling using escape character
         */
        public Builder escapeDisabled(boolean escapeDisabled) {
            this.escapeDisabled = Boolean.toString(escapeDisabled);
            return this;
        }

        /**
         * Sets the escape character to use
         */
        public Builder escape(String escape) {
            this.escape = escape;
            return this;
        }

        public Builder headerDisabled(String headerDisabled) {
            this.headerDisabled = headerDisabled;
            return this;
        }

        public Builder headerDisabled(boolean headerDisabled) {
            this.headerDisabled = Boolean.toString(headerDisabled);
            return this;
        }

        /**
         * To configure the CSV headers. Multiple headers can be separated by comma.
         */
        public Builder header(String header) {
            this.header = header;
            return this;
        }

        /**
         * Whether to allow missing column names.
         */
        public Builder allowMissingColumnNames(String allowMissingColumnNames) {
            this.allowMissingColumnNames = allowMissingColumnNames;
            return this;
        }

        /**
         * Whether to allow missing column names.
         */
        public Builder allowMissingColumnNames(boolean allowMissingColumnNames) {
            this.allowMissingColumnNames = Boolean.toString(allowMissingColumnNames);
            return this;
        }

        /**
         * Whether to ignore empty lines.
         */
        public Builder ignoreEmptyLines(String ignoreEmptyLines) {
            this.ignoreEmptyLines = ignoreEmptyLines;
            return this;
        }

        /**
         * Whether to ignore empty lines.
         */
        public Builder ignoreEmptyLines(boolean ignoreEmptyLines) {
            this.ignoreEmptyLines = Boolean.toString(ignoreEmptyLines);
            return this;
        }

        /**
         * Whether to ignore surrounding spaces
         */
        public Builder ignoreSurroundingSpaces(String ignoreSurroundingSpaces) {
            this.ignoreSurroundingSpaces = ignoreSurroundingSpaces;
            return this;
        }

        /**
         * Whether to ignore surrounding spaces
         */
        public Builder ignoreSurroundingSpaces(boolean ignoreSurroundingSpaces) {
            this.ignoreSurroundingSpaces = Boolean.toString(ignoreSurroundingSpaces);
            return this;
        }

        /**
         * Used to disable null strings
         */
        public Builder nullStringDisabled(String nullStringDisabled) {
            this.nullStringDisabled = nullStringDisabled;
            return this;
        }

        /**
         * Used to disable null strings
         */
        public Builder nullStringDisabled(boolean nullStringDisabled) {
            this.nullStringDisabled = Boolean.toString(nullStringDisabled);
            return this;
        }

        /**
         * Sets the null string
         */
        public Builder nullString(String nullString) {
            this.nullString = nullString;
            return this;
        }

        /**
         * Used to disable quotes
         */
        public Builder quoteDisabled(String quoteDisabled) {
            this.quoteDisabled = quoteDisabled;
            return this;
        }

        /**
         * Used to disable quotes
         */
        public Builder quoteDisabled(boolean quoteDisabled) {
            this.quoteDisabled = Boolean.toString(quoteDisabled);
            return this;
        }

        /**
         * Sets the quote which by default is "
         */
        public Builder quote(String quote) {
            this.quote = quote;
            return this;
        }

        /**
         * Used for disabling record separator
         */
        public Builder recordSeparatorDisabled(String recordSeparatorDisabled) {
            this.recordSeparatorDisabled = recordSeparatorDisabled;
            return this;
        }

        /**
         * Sets the record separator (aka new line) which by default is new line characters (CRLF)
         */
        public Builder recordSeparator(String recordSeparator) {
            this.recordSeparator = recordSeparator;
            return this;
        }

        /**
         * Whether to skip the header record in the output
         */
        public Builder skipHeaderRecord(String skipHeaderRecord) {
            this.skipHeaderRecord = skipHeaderRecord;
            return this;
        }

        /**
         * Whether to skip the header record in the output
         */
        public Builder skipHeaderRecord(boolean skipHeaderRecord) {
            this.skipHeaderRecord = Boolean.toString(skipHeaderRecord);
            return this;
        }

        /**
         * Sets the quote mode
         */
        public Builder quoteMode(String quoteMode) {
            this.quoteMode = quoteMode;
            return this;
        }

        /**
         * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all the lines must
         * be read at one.
         */
        public Builder lazyLoad(String lazyLoad) {
            this.lazyLoad = lazyLoad;
            return this;
        }

        /**
         * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all the lines must
         * be read at one.
         */
        public Builder lazyLoad(boolean lazyLoad) {
            this.lazyLoad = Boolean.toString(lazyLoad);
            return this;
        }

        /**
         * Whether the unmarshalling should produce maps (HashMap)for the lines values instead of lists. It requires to
         * have header (either defined or collected).
         */
        public Builder useMaps(String useMaps) {
            this.useMaps = useMaps;
            return this;
        }

        /**
         * Whether the unmarshalling should produce maps (HashMap)for the lines values instead of lists. It requires to
         * have header (either defined or collected).
         */
        public Builder useMaps(boolean useMaps) {
            this.useMaps = Boolean.toString(useMaps);
            return this;
        }

        /**
         * Whether the unmarshalling should produce ordered maps (LinkedHashMap) for the lines values instead of lists.
         * It requires to have header (either defined or collected).
         */
        public Builder useOrderedMaps(String useOrderedMaps) {
            this.useOrderedMaps = useOrderedMaps;
            return this;
        }

        /**
         * Whether the unmarshalling should produce ordered maps (LinkedHashMap) for the lines values instead of lists.
         * It requires to have header (either defined or collected).
         */
        public Builder useOrderedMaps(boolean useOrderedMaps) {
            this.useOrderedMaps = Boolean.toString(useOrderedMaps);
            return this;
        }

        /**
         * Refers to a custom <tt>CsvRecordConverter</tt> to lookup from the registry to use.
         */
        public Builder recordConverterRef(String recordConverterRef) {
            this.recordConverterRef = recordConverterRef;
            return this;
        }

        /**
         * Sets whether or not to trim leading and trailing blanks.
         */
        public Builder trim(String trim) {
            this.trim = trim;
            return this;
        }

        /**
         * Sets whether or not to trim leading and trailing blanks.
         */
        public Builder trim(boolean trim) {
            this.trim = Boolean.toString(trim);
            return this;
        }

        /**
         * Sets whether or not to ignore case when accessing header names.
         */
        public Builder ignoreHeaderCase(String ignoreHeaderCase) {
            this.ignoreHeaderCase = ignoreHeaderCase;
            return this;
        }

        /**
         * Sets whether or not to ignore case when accessing header names.
         */
        public Builder ignoreHeaderCase(boolean ignoreHeaderCase) {
            this.ignoreHeaderCase = Boolean.toString(ignoreHeaderCase);
            return this;
        }

        /**
         * Sets whether or not to add a trailing delimiter.
         */
        public Builder trailingDelimiter(String trailingDelimiter) {
            this.trailingDelimiter = trailingDelimiter;
            return this;
        }

        /**
         * Sets whether or not to add a trailing delimiter.
         */
        public Builder trailingDelimiter(boolean trailingDelimiter) {
            this.trailingDelimiter = Boolean.toString(trailingDelimiter);
            return this;
        }

        /**
         * Whether the unmarshalling should capture the header record and store it in the message header
         */
        public Builder captureHeaderRecord(String captureHeaderRecord) {
            this.captureHeaderRecord = captureHeaderRecord;
            return this;
        }

        /**
         * Whether the unmarshalling should capture the header record and store it in the message header
         */
        public Builder captureHeaderRecord(boolean captureHeaderRecord) {
            this.captureHeaderRecord = Boolean.toString(captureHeaderRecord);
            return this;
        }

        @Override
        public CsvDataFormat end() {
            return new CsvDataFormat(this);
        }
    }
}
