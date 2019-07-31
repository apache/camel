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
package org.apache.camel.reifier.dataformat;

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public class CsvDataFormatReifier extends DataFormatReifier<CsvDataFormat> {

    public CsvDataFormatReifier(DataFormatDefinition definition) {
        super((CsvDataFormat) definition);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        // Format options
        if (ObjectHelper.isNotEmpty(definition.getFormatRef())) {
            Object format = CamelContextHelper.mandatoryLookup(camelContext, definition.getFormatRef());
            setProperty(camelContext, dataFormat, "format", format);
        } else if (ObjectHelper.isNotEmpty(definition.getFormatName())) {
            setProperty(camelContext, dataFormat, "formatName", definition.getFormatName());
        }
        if (definition.getCommentMarkerDisabled() != null) {
            setProperty(camelContext, dataFormat, "commentMarkerDisabled", definition.getCommentMarkerDisabled());
        }
        if (definition.getCommentMarker() != null) {
            setProperty(camelContext, dataFormat, "commentMarker", singleChar(definition.getCommentMarker(), "commentMarker"));
        }
        if (definition.getDelimiter() != null) {
            setProperty(camelContext, dataFormat, "delimiter", singleChar(definition.getDelimiter(), "delimiter"));
        }
        if (definition.getEscapeDisabled() != null) {
            setProperty(camelContext, dataFormat, "escapeDisabled", definition.getEscapeDisabled());
        }
        if (definition.getEscape() != null) {
            setProperty(camelContext, dataFormat, "escape", singleChar(definition.getEscape(), "escape"));
        }
        if (definition.getHeaderDisabled() != null) {
            setProperty(camelContext, dataFormat, "headerDisabled", definition.getHeaderDisabled());
        }
        if (definition.getHeader() != null && !definition.getHeader().isEmpty()) {
            setProperty(camelContext, dataFormat, "header", definition.getHeader().toArray(new String[definition.getHeader().size()]));
        }
        if (definition.getAllowMissingColumnNames() != null) {
            setProperty(camelContext, dataFormat, "allowMissingColumnNames", definition.getAllowMissingColumnNames());
        }
        if (definition.getIgnoreEmptyLines() != null) {
            setProperty(camelContext, dataFormat, "ignoreEmptyLines", definition.getIgnoreEmptyLines());
        }
        if (definition.getIgnoreSurroundingSpaces() != null) {
            setProperty(camelContext, dataFormat, "ignoreSurroundingSpaces", definition.getIgnoreSurroundingSpaces());
        }
        if (definition.getNullStringDisabled() != null) {
            setProperty(camelContext, dataFormat, "nullStringDisabled", definition.getNullStringDisabled());
        }
        if (definition.getNullString() != null) {
            setProperty(camelContext, dataFormat, "nullString", definition.getNullString());
        }
        if (definition.getQuoteDisabled() != null) {
            setProperty(camelContext, dataFormat, "quoteDisabled", definition.getQuoteDisabled());
        }
        if (definition.getQuote() != null) {
            setProperty(camelContext, dataFormat, "quote", singleChar(definition.getQuote(), "quote"));
        }
        if (definition.getRecordSeparatorDisabled() != null) {
            setProperty(camelContext, dataFormat, "recordSeparatorDisabled", definition.getRecordSeparatorDisabled());
        }
        if (definition.getRecordSeparator() != null) {
            setProperty(camelContext, dataFormat, "recordSeparator", definition.getRecordSeparator());
        }
        if (definition.getSkipHeaderRecord() != null) {
            setProperty(camelContext, dataFormat, "skipHeaderRecord", definition.getSkipHeaderRecord());
        }
        if (definition.getQuoteMode() != null) {
            setProperty(camelContext, dataFormat, "quoteMode", definition.getQuoteMode());
        }
        if (definition.getTrim() != null) {
            setProperty(camelContext, dataFormat, "trim", definition.getTrim());
        }
        if (definition.getIgnoreHeaderCase() != null) {
            setProperty(camelContext, dataFormat, "ignoreHeaderCase", definition.getIgnoreHeaderCase());
        }
        if (definition.getTrailingDelimiter() != null) {
            setProperty(camelContext, dataFormat, "trailingDelimiter", definition.getTrailingDelimiter());
        }

        // Unmarshall options
        if (definition.getLazyLoad() != null) {
            setProperty(camelContext, dataFormat, "lazyLoad", definition.getLazyLoad());
        }
        if (definition.getUseMaps() != null) {
            setProperty(camelContext, dataFormat, "useMaps", definition.getUseMaps());
        }
        if (definition.getUseOrderedMaps() != null) {
            setProperty(camelContext, dataFormat, "useOrderedMaps", definition.getUseOrderedMaps());
        }
        if (ObjectHelper.isNotEmpty(definition.getRecordConverterRef())) {
            Object recordConverter = CamelContextHelper.mandatoryLookup(camelContext, definition.getRecordConverterRef());
            setProperty(camelContext, dataFormat, "recordConverter", recordConverter);
        }
        if (ObjectHelper.isNotEmpty(definition.getMarshallerFactoryRef())) {
            Object marshallerFactory = CamelContextHelper.mandatoryLookup(camelContext, definition.getMarshallerFactoryRef().trim());
            setProperty(camelContext, dataFormat, "marshallerFactory", marshallerFactory);
        }
    }


    private static Character singleChar(String value, String attributeName) {
        if (value.length() != 1) {
            throw new IllegalArgumentException(String.format("The '%s' attribute must be exactly one character long.", attributeName));
        }
        return value.charAt(0);
    }

}
