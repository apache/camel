/**
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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * CSV data format
 */
@Metadata(label = "dataformat,transformation,csv", title = "CSV")
@XmlRootElement(name = "csv")
@XmlAccessorType(XmlAccessType.FIELD)
public class CsvDataFormat extends DataFormatDefinition {
    // Format options
    @XmlAttribute
    private String formatRef;
    @XmlAttribute
    private String formatName;
    @XmlAttribute
    private Boolean commentMarkerDisabled;
    @XmlAttribute
    private String commentMarker;
    @XmlAttribute
    private String delimiter;
    @XmlAttribute
    private Boolean escapeDisabled;
    @XmlAttribute
    private String escape;
    @XmlAttribute
    private Boolean headerDisabled;
    @XmlElement
    private List<String> header;
    @XmlAttribute
    private Boolean allowMissingColumnNames;
    @XmlAttribute
    private Boolean ignoreEmptyLines;
    @XmlAttribute
    private Boolean ignoreSurroundingSpaces;
    @XmlAttribute
    private Boolean nullStringDisabled;
    @XmlAttribute
    private String nullString;
    @XmlAttribute
    private Boolean quoteDisabled;
    @XmlAttribute
    private String quote;
    @XmlAttribute
    private String recordSeparatorDisabled;
    @XmlAttribute
    private String recordSeparator;
    @XmlAttribute
    private Boolean skipHeaderRecord;
    @XmlAttribute
    private String quoteMode;
    // Unmarshall options
    @XmlAttribute
    private Boolean lazyLoad;
    @XmlAttribute
    private Boolean useMaps;
    @XmlAttribute
    private String recordConverterRef;

    public CsvDataFormat() {
        super("csv");
    }

    public CsvDataFormat(String delimiter) {
        this();
        setDelimiter(delimiter);
    }

    public CsvDataFormat(boolean lazyLoad) {
        this();
        setLazyLoad(lazyLoad);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        // Format options
        if (ObjectHelper.isNotEmpty(formatRef)) {
            Object format = CamelContextHelper.mandatoryLookup(camelContext, formatRef);
            setProperty(camelContext, dataFormat, "format", format);
        } else if (ObjectHelper.isNotEmpty(formatName)) {
            setProperty(camelContext, dataFormat, "formatName", formatName);
        }
        if (commentMarkerDisabled != null) {
            setProperty(camelContext, dataFormat, "commentMarkerDisabled", commentMarkerDisabled);
        }
        if (commentMarker != null) {
            setProperty(camelContext, dataFormat, "commentMarker", singleChar(commentMarker, "commentMarker"));
        }
        if (delimiter != null) {
            setProperty(camelContext, dataFormat, "delimiter", singleChar(delimiter, "delimiter"));
        }
        if (escapeDisabled != null) {
            setProperty(camelContext, dataFormat, "escapeDisabled", escapeDisabled);
        }
        if (escape != null) {
            setProperty(camelContext, dataFormat, "escape", singleChar(escape, "escape"));
        }
        if (headerDisabled != null) {
            setProperty(camelContext, dataFormat, "headerDisabled", headerDisabled);
        }
        if (header != null && !header.isEmpty()) {
            setProperty(camelContext, dataFormat, "header", header.toArray(new String[header.size()]));
        }
        if (allowMissingColumnNames != null) {
            setProperty(camelContext, dataFormat, "allowMissingColumnNames", allowMissingColumnNames);
        }
        if (ignoreEmptyLines != null) {
            setProperty(camelContext, dataFormat, "ignoreEmptyLines", ignoreEmptyLines);
        }
        if (ignoreSurroundingSpaces != null) {
            setProperty(camelContext, dataFormat, "ignoreSurroundingSpaces", ignoreSurroundingSpaces);
        }
        if (nullStringDisabled != null) {
            setProperty(camelContext, dataFormat, "nullStringDisabled", nullStringDisabled);
        }
        if (nullString != null) {
            setProperty(camelContext, dataFormat, "nullString", nullString);
        }
        if (quoteDisabled != null) {
            setProperty(camelContext, dataFormat, "quoteDisabled", quoteDisabled);
        }
        if (quote != null) {
            setProperty(camelContext, dataFormat, "quote", singleChar(quote, "quote"));
        }
        if (recordSeparatorDisabled != null) {
            setProperty(camelContext, dataFormat, "recordSeparatorDisabled", recordSeparatorDisabled);
        }
        if (recordSeparator != null) {
            setProperty(camelContext, dataFormat, "recordSeparator", recordSeparator);
        }
        if (skipHeaderRecord != null) {
            setProperty(camelContext, dataFormat, "skipHeaderRecord", skipHeaderRecord);
        }
        if (quoteMode != null) {
            setProperty(camelContext, dataFormat, "quoteMode", quoteMode);
        }
        
        // Unmarshall options
        if (lazyLoad != null) {
            setProperty(camelContext, dataFormat, "lazyLoad", lazyLoad);
        }
        if (useMaps != null) {
            setProperty(camelContext, dataFormat, "useMaps", useMaps);
        }
        if (ObjectHelper.isNotEmpty(recordConverterRef)) {
            Object recordConverter = CamelContextHelper.mandatoryLookup(camelContext, recordConverterRef);
            setProperty(camelContext, dataFormat, "recordConverter", recordConverter);
        }
    }

    private static Character singleChar(String value, String attributeName) {
        if (value.length() != 1) {
            throw new IllegalArgumentException(String.format("The '%s' attribute must be exactly one character long.", attributeName));
        }
        return value.charAt(0);
    }

    public String getFormatRef() {
        return formatRef;
    }

    /**
     * The reference format to use, it will be updated with the other format options, the default value is CSVFormat.DEFAULT
     */
    public void setFormatRef(String formatRef) {
        this.formatRef = formatRef;
    }

    public String getFormatName() {
        return formatName;
    }

    /**
     * The name of the format to use, the default value is CSVFormat.DEFAULT
     */
    public void setFormatName(String formatName) {
        this.formatName = formatName;
    }

    public Boolean getCommentMarkerDisabled() {
        return commentMarkerDisabled;
    }

    /**
     * Disables the comment marker of the reference format.
     */
    public void setCommentMarkerDisabled(Boolean commentMarkerDisabled) {
        this.commentMarkerDisabled = commentMarkerDisabled;
    }

    public String getCommentMarker() {
        return commentMarker;
    }

    /**
     * Sets the comment marker of the reference format.
     */
    public void setCommentMarker(String commentMarker) {
        this.commentMarker = commentMarker;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * Sets the delimiter to use.
     * <p/>
     * The default value is , (comma)
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public Boolean getEscapeDisabled() {
        return escapeDisabled;
    }

    /**
     * Use for disabling using escape character
     */
    public void setEscapeDisabled(Boolean escapeDisabled) {
        this.escapeDisabled = escapeDisabled;
    }

    public String getEscape() {
        return escape;
    }

    /**
     * Sets the escape character to use
     */
    public void setEscape(String escape) {
        this.escape = escape;
    }

    /**
     * Use for disabling headers
     */
    public Boolean getHeaderDisabled() {
        return headerDisabled;
    }

    public void setHeaderDisabled(Boolean headerDisabled) {
        this.headerDisabled = headerDisabled;
    }

    public List<String> getHeader() {
        return header;
    }

    /**
     * To configure the CSV headers
     */
    public void setHeader(List<String> header) {
        this.header = header;
    }

    public Boolean getAllowMissingColumnNames() {
        return allowMissingColumnNames;
    }

    /**
     * Whether to allow missing column names.
     */
    public void setAllowMissingColumnNames(Boolean allowMissingColumnNames) {
        this.allowMissingColumnNames = allowMissingColumnNames;
    }

    public Boolean getIgnoreEmptyLines() {
        return ignoreEmptyLines;
    }

    /**
     * Whether to ignore empty lines.
     */
    public void setIgnoreEmptyLines(Boolean ignoreEmptyLines) {
        this.ignoreEmptyLines = ignoreEmptyLines;
    }

    public Boolean getIgnoreSurroundingSpaces() {
        return ignoreSurroundingSpaces;
    }

    /**
     * Whether to ignore surrounding spaces
     */
    public void setIgnoreSurroundingSpaces(Boolean ignoreSurroundingSpaces) {
        this.ignoreSurroundingSpaces = ignoreSurroundingSpaces;
    }

    public Boolean getNullStringDisabled() {
        return nullStringDisabled;
    }

    /**
     * Used to disable null strings
     */
    public void setNullStringDisabled(Boolean nullStringDisabled) {
        this.nullStringDisabled = nullStringDisabled;
    }

    public String getNullString() {
        return nullString;
    }

    /**
     * Sets the null string
     */
    public void setNullString(String nullString) {
        this.nullString = nullString;
    }

    public Boolean getQuoteDisabled() {
        return quoteDisabled;
    }

    /**
     * Used to disable quotes
     */
    public void setQuoteDisabled(Boolean quoteDisabled) {
        this.quoteDisabled = quoteDisabled;
    }

    public String getQuote() {
        return quote;
    }

    /**
     * Sets the quote which by default is "
     */
    public void setQuote(String quote) {
        this.quote = quote;
    }

    public String getRecordSeparatorDisabled() {
        return recordSeparatorDisabled;
    }

    /**
     * Used for disabling record separator
     */
    public void setRecordSeparatorDisabled(String recordSeparatorDisabled) {
        this.recordSeparatorDisabled = recordSeparatorDisabled;
    }

    public String getRecordSeparator() {
        return recordSeparator;
    }

    /**
     * Sets the record separator (aka new line) which by default is \r\n (CRLF)
     */
    public void setRecordSeparator(String recordSeparator) {
        this.recordSeparator = recordSeparator;
    }

    public Boolean getSkipHeaderRecord() {
        return skipHeaderRecord;
    }

    /**
     * Whether to skip the header record in the output
     */
    public void setSkipHeaderRecord(Boolean skipHeaderRecord) {
        this.skipHeaderRecord = skipHeaderRecord;
    }

    public String getQuoteMode() {
        return quoteMode;
    }

    /**
     * Sets the quote mode
     */
    public void setQuoteMode(String quoteMode) {
        this.quoteMode = quoteMode;
    }

    public Boolean getLazyLoad() {
        return lazyLoad;
    }

    /**
     * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all the lines must be read at one.
     */
    public void setLazyLoad(Boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    public Boolean getUseMaps() {
        return useMaps;
    }

    /**
     * Whether the unmarshalling should produce maps for the lines values instead of lists. It requires to have header (either defined or collected).
     */
    public void setUseMaps(Boolean useMaps) {
        this.useMaps = useMaps;
    }

    public String getRecordConverterRef() {
        return recordConverterRef;
    }

    /**
     * Refers to a custom <tt>CsvRecordConverter</tt> to lookup from the registry to use.
     */
    public void setRecordConverterRef(String recordConverterRef) {
        this.recordConverterRef = recordConverterRef;
    }

}
