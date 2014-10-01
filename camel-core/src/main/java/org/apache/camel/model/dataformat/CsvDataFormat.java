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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a CSV (Comma Separated Values) {@link org.apache.camel.spi.DataFormat}
 */
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
    @XmlElement(name = "header")
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

    //region Getters/Setters

    public String getFormatRef() {
        return formatRef;
    }

    public void setFormatRef(String formatRef) {
        this.formatRef = formatRef;
    }

    public String getFormatName() {
        return formatName;
    }

    public void setFormatName(String formatName) {
        this.formatName = formatName;
    }

    public Boolean getCommentMarkerDisabled() {
        return commentMarkerDisabled;
    }

    public void setCommentMarkerDisabled(Boolean commentMarkerDisabled) {
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

    public Boolean getEscapeDisabled() {
        return escapeDisabled;
    }

    public void setEscapeDisabled(Boolean escapeDisabled) {
        this.escapeDisabled = escapeDisabled;
    }

    public String getEscape() {
        return escape;
    }

    public void setEscape(String escape) {
        this.escape = escape;
    }

    public Boolean getHeaderDisabled() {
        return headerDisabled;
    }

    public void setHeaderDisabled(Boolean headerDisabled) {
        this.headerDisabled = headerDisabled;
    }

    public List<String> getHeader() {
        return header;
    }

    public void setHeader(List<String> header) {
        this.header = header;
    }

    public Boolean getAllowMissingColumnNames() {
        return allowMissingColumnNames;
    }

    public void setAllowMissingColumnNames(Boolean allowMissingColumnNames) {
        this.allowMissingColumnNames = allowMissingColumnNames;
    }

    public Boolean getIgnoreEmptyLines() {
        return ignoreEmptyLines;
    }

    public void setIgnoreEmptyLines(Boolean ignoreEmptyLines) {
        this.ignoreEmptyLines = ignoreEmptyLines;
    }

    public Boolean getIgnoreSurroundingSpaces() {
        return ignoreSurroundingSpaces;
    }

    public void setIgnoreSurroundingSpaces(Boolean ignoreSurroundingSpaces) {
        this.ignoreSurroundingSpaces = ignoreSurroundingSpaces;
    }

    public Boolean getNullStringDisabled() {
        return nullStringDisabled;
    }

    public void setNullStringDisabled(Boolean nullStringDisabled) {
        this.nullStringDisabled = nullStringDisabled;
    }

    public String getNullString() {
        return nullString;
    }

    public void setNullString(String nullString) {
        this.nullString = nullString;
    }

    public Boolean getQuoteDisabled() {
        return quoteDisabled;
    }

    public void setQuoteDisabled(Boolean quoteDisabled) {
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

    public Boolean getSkipHeaderRecord() {
        return skipHeaderRecord;
    }

    public void setSkipHeaderRecord(Boolean skipHeaderRecord) {
        this.skipHeaderRecord = skipHeaderRecord;
    }

    public Boolean getLazyLoad() {
        return lazyLoad;
    }

    public void setLazyLoad(Boolean lazyLoad) {
        this.lazyLoad = lazyLoad;
    }

    public Boolean getUseMaps() {
        return useMaps;
    }

    public void setUseMaps(Boolean useMaps) {
        this.useMaps = useMaps;
    }

    public String getRecordConverterRef() {
        return recordConverterRef;
    }

    public void setRecordConverterRef(String recordConverterRef) {
        this.recordConverterRef = recordConverterRef;
    }
    //endregion
}
